package com.guncraft.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * 玩家：第一人称相机，WASD / 空格 / Shift / G。
 */
public class Player {

    private float x, y, z;
    private float yaw, pitch; // 弧度
    private float velY;
    private boolean onGround;
    private static final float MOVE_SPEED = 8f;
    private static final float JUMP_SPEED = 10f;
    private static final float GRAVITY = 24f;
    private static final float EYE_HEIGHT = 1.62f;
    private static final float CROUCH_HEIGHT = 1.0f;
    private static final float NORMAL_HEIGHT = 1.62f;

    private final boolean[] keys = new boolean[512];
    private boolean crouching;

    private float spawnX, spawnY, spawnZ;

    public Player(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.yaw = 0;
        this.pitch = -0.3f;
    }

    /** 掉入虚空后回到出生点。 */
    public void respawnAtSpawn() {
        this.x = spawnX;
        this.y = spawnY;
        this.z = spawnZ;
        this.velY = 0;
        this.onGround = true;
    }

    public void key(int key, boolean pressed) {
        if (key >= 0 && key < keys.length) keys[key] = pressed;
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT)
            crouching = pressed;
    }

    public void update(float delta, World world) {
        float moveSpeed = MOVE_SPEED * delta;
        if (crouching) moveSpeed *= 0.5f;

        float dx = 0, dz = 0;
        if (keys[GLFW.GLFW_KEY_W]) { dx -= (float) java.lang.Math.sin(yaw); dz -= (float) java.lang.Math.cos(yaw); }
        if (keys[GLFW.GLFW_KEY_S]) { dx += (float) java.lang.Math.sin(yaw); dz += (float) java.lang.Math.cos(yaw); }
        if (keys[GLFW.GLFW_KEY_A]) { dx -= (float) java.lang.Math.cos(yaw); dz += (float) java.lang.Math.sin(yaw); }
        if (keys[GLFW.GLFW_KEY_D]) { dx += (float) java.lang.Math.cos(yaw); dz -= (float) java.lang.Math.sin(yaw); }
        if (dx != 0 || dz != 0) {
            float len = (float) java.lang.Math.sqrt(dx * dx + dz * dz);
            dx /= len;
            dz /= len;
            float nx = x + dx * moveSpeed;
            float nz = z + dz * moveSpeed;
            if (!world.isSolid((int) nx, (int) (y + 0.2f), (int) z)) x = nx;
            if (!world.isSolid((int) x, (int) (y + 0.2f), (int) nz)) z = nz;
        }

        if (keys[GLFW.GLFW_KEY_SPACE] && onGround) {
            velY = JUMP_SPEED;
            onGround = false;
        }

        velY -= GRAVITY * delta;
        float ny = y + velY * delta;
        // 落地：检测脚下方块，用 (int)(ny-ε) 避免站在 y=1 时下一帧被当成悬空再落一次导致抖动
        if (velY < 0) {
            int footBlockY = (int) (ny - 1e-5f);
            if (world.isSolid((int) x, footBlockY, (int) z)) {
                ny = footBlockY + 1f;
                velY = 0;
                onGround = true;
            }
        } else if (velY > 0 && world.isSolid((int) x, (int) (ny + EYE_HEIGHT), (int) z)) {
            velY = 0;
        }
        y = ny;
        if (y < 0) respawnAtSpawn();
    }

    public Matrix4f getViewMatrix(int width, int height) {
        float eyeY = y + (crouching ? CROUCH_HEIGHT : NORMAL_HEIGHT);
        Vector3f eye = new Vector3f(x, eyeY, z);
        Vector3f forward = new Vector3f(
                (float) (-java.lang.Math.sin(yaw) * java.lang.Math.cos(pitch)),
                (float) java.lang.Math.sin(pitch),
                (float) (-java.lang.Math.cos(yaw) * java.lang.Math.cos(pitch))
        );
        Vector3f center = eye.add(forward, new Vector3f());
        Matrix4f view = new Matrix4f().lookAt(eye, center, new Vector3f(0, 1, 0));
        int h = Math.max(height, 1);
        int w = Math.max(width, 1);
        Matrix4f proj = new Matrix4f().setPerspective((float) java.lang.Math.toRadians(70), (float) w / h, 0.1f, 500f);
        return proj.mul(view);
    }

    /** 视线起点（眼睛位置），用于射线检测。 */
    public void getEyePosition(Vector3f out) {
        float eyeY = y + (crouching ? CROUCH_HEIGHT : NORMAL_HEIGHT);
        out.set(x, eyeY, z);
    }

    /** 视线方向（单位向量），用于射线检测。 */
    public void getLookDirection(Vector3f out) {
        out.set(
                (float) (-java.lang.Math.sin(yaw) * java.lang.Math.cos(pitch)),
                (float) java.lang.Math.sin(pitch),
                (float) (-java.lang.Math.cos(yaw) * java.lang.Math.cos(pitch))
        );
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public boolean isCrouching() { return crouching; }
}
