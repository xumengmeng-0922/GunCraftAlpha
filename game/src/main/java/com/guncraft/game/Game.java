package com.guncraft.game;

import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * 游戏主循环：窗口、输入、渲染。
 */
public class Game {

    private static final int MAP_SIZE = 32;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final String TITLE = "GunCraft Alpha 1.3 - WASD 空格 G生物 E背包 1-9/滚轮 左键射击/破坏 右键泥土";

    private long window;
    private World world;
    private Player player;
    private Inventory inventory;
    private GameUI gameUI;
    private boolean inventoryOpen = false;
    private boolean running = true;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    /** 实际帧缓冲尺寸（随窗口缩放更新） */
    private int framebufferWidth = WIDTH;
    private int framebufferHeight = HEIGHT;

    public void run() {
        init();
        loop();
        shutdown();
    }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("无法初始化 GLFW");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(WIDTH, HEIGHT, TITLE, NULL, NULL);
        if (window == NULL) throw new IllegalStateException("无法创建窗口");

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2);
            }
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // vsync
        glfwShowWindow(window);

        try (MemoryStack stack = stackPush()) {
            IntBuffer fw = stack.mallocInt(1);
            IntBuffer fh = stack.mallocInt(1);
            glfwGetFramebufferSize(window, fw, fh);
            framebufferWidth = fw.get(0);
            framebufferHeight = fh.get(0);
        }
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            framebufferWidth = w;
            framebufferHeight = h;
        });

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        world = new World(MAP_SIZE, MAP_SIZE);
        player = new Player(16, 0, 16);
        inventory = new Inventory();
        try {
            gameUI = new GameUI();
            gameUI.setFramebufferSize(framebufferWidth, framebufferHeight);
        } catch (Throwable t) {
            t.printStackTrace();
            gameUI = null;
        }

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                if (inventoryOpen) {
                    inventoryOpen = false;
                    lastMouseX = Double.NaN;
                    lastMouseY = Double.NaN;
                } else glfwSetWindowShouldClose(win, true);
                updateCursorMode();
            }
            if (key == GLFW_KEY_E && action == GLFW_PRESS) {
                inventoryOpen = !inventoryOpen;
                if (!inventoryOpen) { lastMouseX = Double.NaN; lastMouseY = Double.NaN; }
                updateCursorMode();
            }
            if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9 && action == GLFW_PRESS)
                inventory.setSelectedHotbar(key - GLFW_KEY_1);
            if (key == GLFW_KEY_G && action == GLFW_PRESS && !inventoryOpen) {
                float dx = (float) -java.lang.Math.sin(player.getYaw());
                float dz = (float) -java.lang.Math.cos(player.getYaw());
                world.spawnCreature(
                        player.getX() + dx * 2f,
                        player.getY() + 1f,
                        player.getZ() + dz * 2f
                );
            }
            if (action == GLFW_PRESS || action == GLFW_RELEASE)
                player.key(key, action == GLFW_PRESS);
        });

        glfwSetScrollCallback(window, (win, dx, dy) -> {
            if (!inventoryOpen) inventory.scrollHotbar((int) dy);
        });

        Vector3f rayEye = new Vector3f();
        Vector3f rayDir = new Vector3f();
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action != GLFW_PRESS || inventoryOpen) return;
            player.getEyePosition(rayEye);
            player.getLookDirection(rayDir);
            ItemType held = inventory.getSelectedItemType();
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (held == ItemType.GLOCK_17) {
                    Entity hitEntity = world.raycastEntity(rayEye, rayDir, 64f);
                    if (hitEntity != null) world.removeEntity(hitEntity);
                } else {
                    int[] hit = world.raycast(rayEye, rayDir, 8f);
                    if (hit != null) world.setBlock(hit[0], hit[1], hit[2], (byte) World.AIR);
                }
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                if (held == ItemType.DIRT) {
                    int[] hit = world.raycast(rayEye, rayDir, 8f);
                    if (hit != null) {
                        int[] place = world.getPlacePosition(hit[0], hit[1], hit[2], rayEye);
                        if (place != null && place[1] >= 0 && world.getBlock(place[0], place[1], place[2]) == World.AIR) {
                            world.setBlock(place[0], place[1], place[2], (byte) World.DIRT);
                            inventory.consumeSelectedOne();
                        }
                    }
                }
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (inventoryOpen) return;
            if (Double.isNaN(lastMouseX)) { lastMouseX = xpos; lastMouseY = ypos; return; }
            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;
            lastMouseX = xpos;
            lastMouseY = ypos;
            float sens = 0.003f;
            player.setYaw(player.getYaw() - (float) (dx * sens));
            player.setPitch(player.getPitch() - (float) (dy * sens));
            float p = player.getPitch();
            if (p > 1.5f) player.setPitch(1.5f);
            if (p < -1.5f) player.setPitch(-1.5f);
        });
        updateCursorMode();
    }

    private void updateCursorMode() {
        glfwSetInputMode(window, GLFW_CURSOR, inventoryOpen ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
    }

    private void loop() {
        double lastTime = glfwGetTime();
        while (running && !glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float delta = (float) (now - lastTime);
            lastTime = now;

            player.update(delta, world);
            world.update(delta);

            glClearColor(0.4f, 0.6f, 0.9f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glViewport(0, 0, framebufferWidth, framebufferHeight);
            world.render(player.getViewMatrix(framebufferWidth, framebufferHeight));

            if (gameUI != null) {
                gameUI.setFramebufferSize(framebufferWidth, framebufferHeight);
                glClear(GL_DEPTH_BUFFER_BIT);
                gameUI.drawHotbar(inventory);
                if (!inventoryOpen) gameUI.drawCrosshair();
                else gameUI.drawInventory(inventory);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void shutdown() {
        if (gameUI != null) gameUI.cleanup();
        if (world != null) world.cleanup();
        if (window != NULL) glfwFreeCallbacks(window);
        if (window != NULL) glfwDestroyWindow(window);
        glfwTerminate();
    }
}
