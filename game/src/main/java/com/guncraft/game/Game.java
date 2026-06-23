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
    /** 默认 1080p；窗口可拖动缩放，实际渲染随帧缓冲尺寸变化。 */
    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    private static final int MIN_WIDTH = 960;
    private static final int MIN_HEIGHT = 540;
    private static final String TITLE = "GunCraft Alpha 1.4 - WASD 空格 G生物 E物品栏 左键整堆 右键单个 Shift快速转移";

    private int windowWidth = DEFAULT_WIDTH;
    private int windowHeight = DEFAULT_HEIGHT;
    /** 实际帧缓冲尺寸（随窗口缩放更新） */
    private int framebufferWidth = DEFAULT_WIDTH;
    private int framebufferHeight = DEFAULT_HEIGHT;
    private double invMouseX;
    private double invMouseY;
    private double pauseMouseX;
    private double pauseMouseY;

    private long window;
    private World world;
    private Player player;
    private Inventory inventory;
    private GameUI gameUI;
    private boolean inventoryOpen = false;
    private boolean pauseOpen = false;
    private PauseScreen pauseScreen = PauseScreen.MENU;
    private boolean running = true;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public void run() {
        init();
        loop();
        shutdown();
    }

    /** 默认 1080p，不超过主显示器可用区域（留任务栏边距）。 */
    private static int[] resolveWindowSize() {
        int w = DEFAULT_WIDTH;
        int h = DEFAULT_HEIGHT;
        GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (mode != null) {
            int maxW = mode.width();
            int maxH = (int) (mode.height() * 0.92);
            if (w > maxW || h > maxH) {
                w = Math.min(w, maxW);
                h = Math.min(h, maxH);
                float aspect = 16f / 9f;
                if (w / (float) h > aspect) w = Math.max(MIN_WIDTH, (int) (h * aspect));
                else h = Math.max(MIN_HEIGHT, (int) (w / aspect));
            }
        }
        return new int[]{
                Math.max(MIN_WIDTH, w),
                Math.max(MIN_HEIGHT, h)
        };
    }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("无法初始化 GLFW");
        GameSettings.load();
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        int[] size = resolveWindowSize();
        windowWidth = size[0];
        windowHeight = size[1];
        window = glfwCreateWindow(windowWidth, windowHeight, TITLE, NULL, NULL);
        if (window == NULL) throw new IllegalStateException("无法创建窗口");

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            windowWidth = Math.max(1, pWidth.get(0));
            windowHeight = Math.max(1, pHeight.get(0));
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
        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            windowWidth = Math.max(1, w);
            windowHeight = Math.max(1, h);
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
                if (inventoryOpen) closeInventory();
                else if (pauseOpen) {
                    if (pauseScreen == PauseScreen.LANGUAGE) pauseScreen = PauseScreen.SETTINGS;
                    else if (pauseScreen == PauseScreen.SETTINGS) pauseScreen = PauseScreen.MENU;
                    else closePause();
                } else openPause();
                return;
            }
            if (key == GLFW_KEY_E && action == GLFW_PRESS && !pauseOpen) {
                if (inventoryOpen) closeInventory();
                else openInventory();
            }
            if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9 && action == GLFW_PRESS && !pauseOpen)
                inventory.setSelectedHotbar(key - GLFW_KEY_1);
            if (key == GLFW_KEY_G && action == GLFW_PRESS && !inventoryOpen && !pauseOpen) {
                float dx = (float) -java.lang.Math.sin(player.getYaw());
                float dz = (float) -java.lang.Math.cos(player.getYaw());
                world.spawnCreature(
                        player.getX() + dx * 2f,
                        player.getY() + 1f,
                        player.getZ() + dz * 2f
                );
            }
            if (action == GLFW_PRESS || action == GLFW_RELEASE) {
                if (!pauseOpen) player.key(key, action == GLFW_PRESS);
                else if (action == GLFW_RELEASE) player.key(key, false);
            }
        });

        glfwSetScrollCallback(window, (win, dx, dy) -> {
            inventory.scrollHotbar((int) dy);
        });

        Vector3f rayEye = new Vector3f();
        Vector3f rayDir = new Vector3f();
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (pauseOpen) {
                if (action == GLFW_PRESS && button == GLFW_MOUSE_BUTTON_LEFT && gameUI != null) {
                    handlePauseClick();
                }
                return;
            }
            if (inventoryOpen) {
                if (action == GLFW_PRESS && gameUI != null
                        && (button == GLFW_MOUSE_BUTTON_LEFT || button == GLFW_MOUSE_BUTTON_RIGHT)) {
                    int slot = gameUI.pickInventorySlot(toFramebufferX(invMouseX), toFramebufferY(invMouseY));
                    if (slot >= 0) {
                        boolean right = button == GLFW_MOUSE_BUTTON_RIGHT;
                        boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
                        inventory.handleSlotClick(slot, right, shift);
                    }
                }
                return;
            }
            if (action != GLFW_PRESS) return;
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
            if (inventoryOpen) {
                invMouseX = xpos;
                invMouseY = ypos;
                return;
            }
            if (pauseOpen) {
                pauseMouseX = xpos;
                pauseMouseY = ypos;
                return;
            }
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

    private void openPause() {
        pauseOpen = true;
        pauseScreen = PauseScreen.MENU;
        player.releaseAllKeys();
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
        updateCursorMode();
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer wx = stack.mallocDouble(1);
            DoubleBuffer wy = stack.mallocDouble(1);
            glfwGetCursorPos(window, wx, wy);
            pauseMouseX = wx.get(0);
            pauseMouseY = wy.get(0);
        }
    }

    private void closePause() {
        pauseOpen = false;
        pauseScreen = PauseScreen.MENU;
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
        updateCursorMode();
    }

    private void handlePauseClick() {
        float mx = toFramebufferX(pauseMouseX);
        float my = toFramebufferY(pauseMouseY);
        int btn = gameUI.pickPauseButton(pauseScreen, mx, my);
        if (pauseScreen == PauseScreen.MENU) {
            if (btn == PauseScreenLayout.BTN_RESUME) closePause();
            else if (btn == PauseScreenLayout.BTN_SETTINGS) pauseScreen = PauseScreen.SETTINGS;
            else if (btn == PauseScreenLayout.BTN_LAUNCHER) quitToLauncher();
        } else if (pauseScreen == PauseScreen.SETTINGS) {
            if (btn == PauseScreenLayout.BTN_BACK) pauseScreen = PauseScreen.MENU;
            else if (btn == PauseScreenLayout.BTN_LANGUAGE) pauseScreen = PauseScreen.LANGUAGE;
        } else if (pauseScreen == PauseScreen.LANGUAGE) {
            if (btn == PauseScreenLayout.BTN_BACK) pauseScreen = PauseScreen.SETTINGS;
            else if (btn == PauseScreenLayout.BTN_LANG_ZH) GameSettings.setLanguage(GameLanguage.ZH);
            else if (btn == PauseScreenLayout.BTN_LANG_EN) GameSettings.setLanguage(GameLanguage.EN);
            else if (btn == PauseScreenLayout.BTN_LANG_REVERSE)
                GameSettings.setLanguage(GameLanguage.REVERSE_ZH);
        }
    }

    private void quitToLauncher() {
        running = false;
        glfwSetWindowShouldClose(window, true);
    }

    private void openInventory() {
        inventoryOpen = true;
        updateCursorMode();
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer wx = stack.mallocDouble(1);
            DoubleBuffer wy = stack.mallocDouble(1);
            glfwGetCursorPos(window, wx, wy);
            invMouseX = wx.get(0);
            invMouseY = wy.get(0);
        }
    }

    private void closeInventory() {
        inventory.returnCursorStack();
        inventoryOpen = false;
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
        updateCursorMode();
    }

    private float toFramebufferX(double windowX) {
        return (float) (windowX * framebufferWidth / (double) windowWidth);
    }

    private float toFramebufferY(double windowY) {
        return (float) (windowY * framebufferHeight / (double) windowHeight);
    }

    private void updateCursorMode() {
        boolean needMouse = inventoryOpen || pauseOpen;
        glfwSetInputMode(window, GLFW_CURSOR, needMouse ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
    }

    private void loop() {
        double lastTime = glfwGetTime();
        while (running && !glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float delta = (float) (now - lastTime);
            lastTime = now;

            if (!pauseOpen) {
                player.update(delta, world);
                world.update(delta);
            }

            glClearColor(0.4f, 0.6f, 0.9f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glViewport(0, 0, framebufferWidth, framebufferHeight);
            world.render(player.getViewMatrix(framebufferWidth, framebufferHeight));

            if (gameUI != null) {
                gameUI.setFramebufferSize(framebufferWidth, framebufferHeight);
                glClear(GL_DEPTH_BUFFER_BIT);
                if (inventoryOpen) {
                    gameUI.drawInventory(inventory, toFramebufferX(invMouseX), toFramebufferY(invMouseY));
                } else {
                    gameUI.drawHotbar(inventory);
                    if (!pauseOpen) gameUI.drawCrosshair();
                    if (pauseOpen) {
                        gameUI.drawPauseMenu(pauseScreen, toFramebufferX(pauseMouseX), toFramebufferY(pauseMouseY));
                    }
                }
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
