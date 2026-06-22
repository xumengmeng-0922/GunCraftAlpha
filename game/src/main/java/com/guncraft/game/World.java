package com.guncraft.game;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL30.*;

/**
 * 32×32 地图：方块网格、材质、破坏/放置、生物。
 */
public class World {

    public static final int AIR = 0;
    public static final int DIRT = 1;

    private final int sizeX;
    private final int sizeZ;
    private final int sizeY = 16;
    /** blocks[x][y][z]: 0=空气, 1=泥土 */
    private final byte[][][] blocks;
    private final List<Entity> entities = new ArrayList<>();

    private int chunkVao;
    private int chunkVbo;
    private int program;
    private int uniformMvp;
    private int uniformTex;
    private int textureId;
    private final Matrix4f model = new Matrix4f();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    /** 单块立方体：每顶点 5 分量 (x,y,z, u,v)，6 面×6 顶点 */
    private static final float[] CUBE_POS_UV = {
            // 底面 Y-
            -0.5f, -0.5f, -0.5f,  0,0,   0.5f, -0.5f, -0.5f,  1,0,   0.5f, -0.5f,  0.5f,  1,1,
            0.5f, -0.5f,  0.5f,  1,1,  -0.5f, -0.5f,  0.5f,  0,1,  -0.5f, -0.5f, -0.5f,  0,0,
            // 顶面 Y+
            -0.5f,  0.5f, -0.5f,  0,0,  -0.5f,  0.5f,  0.5f,  0,1,   0.5f,  0.5f,  0.5f,  1,1,
            0.5f,  0.5f,  0.5f,  1,1,   0.5f,  0.5f, -0.5f,  1,0,  -0.5f,  0.5f, -0.5f,  0,0,
            // 前面 Z+
            -0.5f, -0.5f,  0.5f,  0,0,   0.5f, -0.5f,  0.5f,  1,0,   0.5f,  0.5f,  0.5f,  1,1,
            0.5f,  0.5f,  0.5f,  1,1,  -0.5f,  0.5f,  0.5f,  0,1,  -0.5f, -0.5f,  0.5f,  0,0,
            // 后面 Z-
            -0.5f, -0.5f, -0.5f,  0,0,  -0.5f,  0.5f, -0.5f,  0,1,   0.5f,  0.5f, -0.5f,  1,1,
            0.5f,  0.5f, -0.5f,  1,1,   0.5f, -0.5f, -0.5f,  1,0,  -0.5f, -0.5f, -0.5f,  0,0,
            // 右 X+
            0.5f, -0.5f, -0.5f,  0,0,   0.5f,  0.5f, -0.5f,  0,1,   0.5f,  0.5f,  0.5f,  1,1,
            0.5f,  0.5f,  0.5f,  1,1,   0.5f, -0.5f,  0.5f,  1,0,   0.5f, -0.5f, -0.5f,  0,0,
            // 左 X-
            -0.5f, -0.5f, -0.5f,  0,0,  -0.5f, -0.5f,  0.5f,  0,1,  -0.5f,  0.5f,  0.5f,  1,1,
            -0.5f,  0.5f,  0.5f,  1,1,  -0.5f,  0.5f, -0.5f,  1,0,  -0.5f, -0.5f, -0.5f,  0,0
    };
    private static final int FLOATS_PER_VERTEX = 5;
    private static final int VERTS_PER_BLOCK = 36;

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            uniform mat4 uMvp;
            out vec2 vUv;
            void main() {
                gl_Position = uMvp * vec4(aPos, 1.0);
                vUv = aUv;
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vUv;
            out vec4 FragColor;
            uniform sampler2D uTex;
            void main() { FragColor = texture(uTex, vUv); }
            """;

    public World(int sizeX, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.blocks = new byte[sizeX][sizeY][sizeZ];
        for (int x = 0; x < sizeX; x++)
            for (int z = 0; z < sizeZ; z++)
                blocks[x][0][z] = DIRT;
        textureId = TextureLoader.load("/textures/dirt.png");
        createProgram();
        rebuildMesh();
    }

    private void createProgram() {
        int vs = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new IllegalStateException("Program: " + glGetProgramInfoLog(program));
        glDeleteShader(vs);
        glDeleteShader(fs);
        uniformMvp = glGetUniformLocation(program, "uMvp");
        uniformTex = glGetUniformLocation(program, "uTex");
    }

    private int compileShader(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE)
            throw new IllegalStateException("Shader: " + glGetShaderInfoLog(id));
        return id;
    }

    public byte getBlock(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) return AIR;
        return blocks[x][y][z];
    }

    public void setBlock(int x, int y, int z, byte type) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) return;
        blocks[x][y][z] = type;
        rebuildMesh();
    }

    private void rebuildMesh() {
        List<Float> verts = new ArrayList<>();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (blocks[x][y][z] == AIR) continue;
                    float bx = x + 0.5f;
                    float by = y + 0.5f;
                    float bz = z + 0.5f;
                    for (int i = 0; i < CUBE_POS_UV.length; i += FLOATS_PER_VERTEX) {
                        verts.add(CUBE_POS_UV[i]     + bx);
                        verts.add(CUBE_POS_UV[i + 1] + by);
                        verts.add(CUBE_POS_UV[i + 2] + bz);
                        verts.add(CUBE_POS_UV[i + 3]);
                        verts.add(CUBE_POS_UV[i + 4]);
                    }
                }
            }
        }
        float[] arr = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) arr[i] = verts.get(i);

        if (chunkVao != 0) glDeleteVertexArrays(chunkVao);
        if (chunkVbo != 0) glDeleteBuffers(chunkVbo);
        chunkVao = glGenVertexArrays();
        chunkVbo = glGenBuffers();
        glBindVertexArray(chunkVao);
        glBindBuffer(GL_ARRAY_BUFFER, chunkVbo);
        glBufferData(GL_ARRAY_BUFFER, arr, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
        meshVertexCount = verts.size() / FLOATS_PER_VERTEX;
    }

    private int meshVertexCount;

    /** 射线检测：从 eye 沿 dir 方向，返回击中的方块坐标 [bx, by, bz]，未击中返回 null。 */
    public int[] raycast(Vector3f eye, Vector3f dir, float maxDist) {
        float step = 0.05f;
        float skip = 0.3f; // 略过起点附近，避免点到脚下的块
        float x = eye.x + dir.x * skip;
        float y = eye.y + dir.y * skip;
        float z = eye.z + dir.z * skip;
        float dx = dir.x;
        float dy = dir.y;
        float dz = dir.z;
        int steps = (int) (maxDist / step);
        for (int i = 0; i < steps; i++) {
            int bx = (int) java.lang.Math.floor(x);
            int by = (int) java.lang.Math.floor(y);
            int bz = (int) java.lang.Math.floor(z);
            if (getBlock(bx, by, bz) != AIR)
                return new int[] { bx, by, bz };
            x += dx * step;
            y += dy * step;
            z += dz * step;
        }
        return null;
    }

    /** 根据击中方块和视线起点，得到放置新方块的相邻格子 (nx, ny, nz)。保证 0 <= y < sizeY，解决第一层无法放置。 */
    public int[] getPlacePosition(int hitX, int hitY, int hitZ, Vector3f rayOrigin) {
        float cx = hitX + 0.5f;
        float cy = hitY + 0.5f;
        float cz = hitZ + 0.5f;
        float nx = rayOrigin.x - cx;
        float ny = rayOrigin.y - cy;
        float nz = rayOrigin.z - cz;
        float ax = java.lang.Math.abs(nx);
        float ay = java.lang.Math.abs(ny);
        float az = java.lang.Math.abs(nz);
        int px, py, pz;
        if (ax >= ay && ax >= az) { px = hitX + (nx > 0 ? 1 : -1); py = hitY; pz = hitZ; }
        else if (ay >= ax && ay >= az) { px = hitX; py = hitY + (ny > 0 ? 1 : -1); pz = hitZ; }
        else { px = hitX; py = hitY; pz = hitZ + (nz > 0 ? 1 : -1); }
        if (py < 0) py = 0;
        if (py >= sizeY) py = sizeY - 1;
        return new int[] { px, py, pz };
    }

    public void spawnCreature(float x, float y, float z) {
        entities.add(new Entity(x, y, z, sizeX, sizeZ));
    }

    /** 射线检测实体：从 eye 沿 dir，返回第一个击中的实体，未击中返回 null。用于枪击。 */
    public Entity raycastEntity(Vector3f eye, Vector3f dir, float maxDist) {
        float half = Entity.getEntityScale() * 0.5f;
        Entity hitEntity = null;
        float hitDist = maxDist + 1f;
        for (Entity e : entities) {
            float t = rayAabb(eye.x, eye.y, eye.z, dir.x, dir.y, dir.z,
                    e.getX(), e.getY(), e.getZ(), e.getX() + Entity.getEntityScale(),
                    e.getY() + Entity.getEntityScale(), e.getZ() + Entity.getEntityScale());
            if (t >= 0 && t < hitDist && t <= maxDist) {
                hitDist = t;
                hitEntity = e;
            }
        }
        return hitEntity;
    }

    /** 射线与 AABB 相交，返回 t（沿 dir 的距离），未相交返回 -1。 */
    private static float rayAabb(float ox, float oy, float oz, float dx, float dy, float dz,
                                 float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float tMin = (minX - ox) / (dx == 0 ? 1e-8f : dx);
        float tMax = (maxX - ox) / (dx == 0 ? 1e-8f : dx);
        if (tMin > tMax) { float t = tMin; tMin = tMax; tMax = t; }
        float tyMin = (minY - oy) / (dy == 0 ? 1e-8f : dy);
        float tyMax = (maxY - oy) / (dy == 0 ? 1e-8f : dy);
        if (tyMin > tyMax) { float t = tyMin; tyMin = tyMax; tyMax = t; }
        if (tMin > tyMax || tyMin > tMax) return -1;
        tMin = java.lang.Math.max(tMin, tyMin);
        tMax = java.lang.Math.min(tMax, tyMax);
        float tzMin = (minZ - oz) / (dz == 0 ? 1e-8f : dz);
        float tzMax = (maxZ - oz) / (dz == 0 ? 1e-8f : dz);
        if (tzMin > tzMax) { float t = tzMin; tzMin = tzMax; tzMax = t; }
        if (tMin > tzMax || tzMin > tMax) return -1;
        tMin = java.lang.Math.max(tMin, tzMin);
        return tMin >= 0 ? tMin : -1;
    }

    public void removeEntity(Entity e) {
        entities.remove(e);
        e.cleanup();
    }

    public void update(float delta) {
        for (Entity e : entities) e.update(delta, this);
    }

    public void render(Matrix4f viewProj) {
        glUseProgram(program);
        glUniform1i(uniformTex, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        model.identity();
        viewProj.mul(model, model);
        model.get(matrixBuffer);
        glUniformMatrix4fv(uniformMvp, false, matrixBuffer);
        glBindVertexArray(chunkVao);
        glDrawArrays(GL_TRIANGLES, 0, meshVertexCount);
        glBindVertexArray(0);

        for (Entity e : entities) e.render(viewProj, matrixBuffer);
    }

    public void cleanup() {
        for (Entity e : entities) e.cleanup();
        if (chunkVao != 0) glDeleteVertexArrays(chunkVao);
        if (chunkVbo != 0) glDeleteBuffers(chunkVbo);
        if (program != 0) glDeleteProgram(program);
        if (textureId != 0) glDeleteTextures(textureId);
    }

    public boolean isSolid(int x, int y, int z) {
        return getBlock(x, y, z) != AIR;
    }

    public int getSizeX() { return sizeX; }
    public int getSizeZ() { return sizeZ; }
    public int getSizeY() { return sizeY; }
    public List<Entity> getEntities() { return entities; }
}
