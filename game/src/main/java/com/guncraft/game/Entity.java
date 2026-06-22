package com.guncraft.game;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL30.*;

/**
 * 生物：红色方块，会在地图内随机移动。
 */
public class Entity {

    private static final float[] CUBE = {
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f, -0.5f,
            -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
            -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f,
            -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f,
            0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,
            -0.5f, -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f, -0.5f, -0.5f, -0.5f, -0.5f
    };

    private static final String ENTITY_VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            uniform mat4 uMvp;
            void main() { gl_Position = uMvp * vec4(aPos, 1.0); }
            """;
    private static final String ENTITY_FRAGMENT = """
            #version 330 core
            out vec4 FragColor;
            uniform vec3 uColor;
            void main() { FragColor = vec4(uColor, 1.0); }
            """;

    private static final float GRAVITY = 24f;
    /** 生物体型缩放（相对 1 格方块） */
    private static final float ENTITY_SCALE = 0.5f;

    private float x, y, z;
    private float vx, vz, velY;
    private boolean onGround;
    private final int worldSizeX, worldSizeZ;
    private final Random rnd = new Random();
    private float changeDirTimer;
    private int vao, vbo;
    private int program;
    private int uniformMvp;
    private int uniformColor;
    private final Matrix4f model = new Matrix4f();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private boolean initialized;

    public Entity(float x, float y, float z, int worldSizeX, int worldSizeZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.worldSizeX = worldSizeX;
        this.worldSizeZ = worldSizeZ;
        this.vx = (rnd.nextFloat() - 0.5f) * 2f;
        this.vz = (rnd.nextFloat() - 0.5f) * 2f;
        this.changeDirTimer = 1f + rnd.nextFloat() * 2f;
    }

    private void ensureVao() {
        if (initialized) return;
        float[] verts = new float[CUBE.length];
        for (int i = 0; i < CUBE.length; i += 3) {
            verts[i]     = CUBE[i]     + 0.5f;
            verts[i + 1] = CUBE[i + 1] + 0.5f;
            verts[i + 2] = CUBE[i + 2] + 0.5f;
        }
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        int vs = compileShader(GL_VERTEX_SHADER, ENTITY_VERTEX);
        int fs = compileShader(GL_FRAGMENT_SHADER, ENTITY_FRAGMENT);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        glDeleteShader(vs);
        glDeleteShader(fs);
        uniformMvp = glGetUniformLocation(program, "uMvp");
        uniformColor = glGetUniformLocation(program, "uColor");
        initialized = true;
    }

    private int compileShader(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE)
            throw new IllegalStateException("Entity shader: " + glGetShaderInfoLog(id));
        return id;
    }

    public void update(float delta, World world) {
        changeDirTimer -= delta;
        if (changeDirTimer <= 0) {
            changeDirTimer = 1f + rnd.nextFloat() * 2f;
            vx = (rnd.nextFloat() - 0.5f) * 4f;
            vz = (rnd.nextFloat() - 0.5f) * 4f;
        }
        float speed = 2f * delta;
        float nx = x + vx * speed;
        float nz = z + vz * speed;
        float half = ENTITY_SCALE * 0.5f;
        if (nx < half || nx >= worldSizeX - half) { vx = -vx; nx = x; }
        if (nz < half || nz >= worldSizeZ - half) { vz = -vz; nz = z; }
        int ix = (int) nx;
        int iz = (int) nz;
        float footY = y + 0.05f;
        float headY = y + ENTITY_SCALE + 0.05f;
        if (!world.isSolid(ix, (int) footY, iz) && !world.isSolid(ix, (int) headY, iz)) {
            x = nx;
            z = nz;
        } else {
            vx = -vx;
            vz = -vz;
        }

        velY -= GRAVITY * delta;
        float ny = y + velY * delta;
        if (velY < 0 && world.isSolid((int) x, (int) (ny + 0.05f), (int) z)) {
            ny = (int) ny + 1;
            velY = 0;
            onGround = true;
        } else if (velY > 0 && world.isSolid((int) x, (int) (ny + ENTITY_SCALE), (int) z)) {
            velY = 0;
        }
        y = ny;
        if (y < 0) { y = 0; velY = 0; onGround = true; }
    }

    public void render(Matrix4f viewProj, FloatBuffer matrixBuffer) {
        ensureVao();
        glUseProgram(program);
        glUniform3f(uniformColor, 0.8f, 0.2f, 0.2f);
        model.identity().translate(x, y, z).scale(ENTITY_SCALE);
        viewProj.mul(model, model);
        model.get(matrixBuffer);
        glUniformMatrix4fv(uniformMvp, false, matrixBuffer);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);
    }

    public void cleanup() {
        if (vao != 0) glDeleteVertexArrays(vao);
        if (vbo != 0) glDeleteBuffers(vbo);
        if (program != 0) glDeleteProgram(program);
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public static float getEntityScale() { return ENTITY_SCALE; }
}
