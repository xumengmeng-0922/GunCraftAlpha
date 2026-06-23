package com.guncraft.game;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * 游戏 UI：准星、9 格物品栏（泥土材质+格洛克17）、选中物品名、MC 风格背包界面。
 */
public class GameUI {
    private static final String UI_VERTEX = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUv;
            uniform mat4 uMvp;
            out vec2 vUv;
            void main() { gl_Position = uMvp * vec4(aPos, 0.0, 1.0); vUv = aUv; }
            """;
    private static final String UI_FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            out vec4 FragColor;
            uniform vec4 uColor;
            uniform sampler2D uTex;
            uniform bool uUseTex;
            void main() {
                if (uUseTex) FragColor = texture(uTex, vUv);
                else FragColor = uColor;
            }
            """;

    private int program;
    private int vao, vbo;
    private int uniformMvp, uniformColor, uniformTex, uniformUseTex;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final Matrix4f ortho = new Matrix4f();
    private final TextRenderer textRenderer = new TextRenderer();
    /** 泥土材质，用于物品栏里泥土图标 */
    private int dirtTextureId;
    private int framebufferWidth, framebufferHeight;
    private final InventoryScreenLayout invLayout = new InventoryScreenLayout();
    private final PauseScreenLayout pauseLayout = new PauseScreenLayout();

    public GameUI() {
        int vs = compileShader(GL_VERTEX_SHADER, UI_VERTEX);
        int fs = compileShader(GL_FRAGMENT_SHADER, UI_FRAGMENT);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        glDeleteShader(vs);
        glDeleteShader(fs);
        uniformMvp = glGetUniformLocation(program, "uMvp");
        uniformColor = glGetUniformLocation(program, "uColor");
        uniformTex = glGetUniformLocation(program, "uTex");
        uniformUseTex = glGetUniformLocation(program, "uUseTex");

        try {
            dirtTextureId = TextureLoader.load("/textures/dirt.png");
        } catch (Throwable t) {
            dirtTextureId = 0;
        }

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
    }

    private static int compileShader(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE)
            throw new IllegalStateException("UI shader: " + glGetShaderInfoLog(id));
        return id;
    }

    public void setFramebufferSize(int w, int h) {
        this.framebufferWidth = Math.max(1, w);
        this.framebufferHeight = Math.max(1, h);
        ortho.setOrtho(0, 1, 1, 0, -1, 1);
    }

    private float nx(float px) { return px / framebufferWidth; }
    private float ny(float px) { return px / framebufferHeight; }

    private void drawQuad(float x, float y, float w, float h, float r, float g, float b, float a) {
        float x0 = nx(x), y0 = ny(y), x1 = nx(x + w), y1 = ny(y + h);
        float[] verts = {
                x0, y0, 0, 0,  x1, y0, 1, 0,  x1, y1, 1, 1,
                x1, y1, 1, 1,  x0, y1, 0, 1,  x0, y0, 0, 0
        };
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STREAM_DRAW);
        glUseProgram(program);
        glUniform4f(uniformColor, r, g, b, a);
        glUniform1i(uniformUseTex, 0);
        ortho.get(matrixBuffer);
        glUniformMatrix4fv(uniformMvp, false, matrixBuffer);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private void uiBegin() {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void uiEnd() {
        glBindTexture(GL_TEXTURE_2D, 0);
        glUseProgram(0);
        glDisable(GL_BLEND);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private void drawText(float x, float y, float scale, String text) {
        if (text == null || text.isEmpty()) return;
        try {
            int texId = textRenderer.getTextureFor(text);
            int tw = textRenderer.getLastWidth();
            int th = textRenderer.getLastHeight();
            drawQuadTex(x, y, tw * scale, th * scale, texId, Lang.isMirrorText(), true);
        } catch (Throwable ignored) { }
    }

    private void drawQuadTex(float x, float y, float w, float h, int texId) {
        drawQuadTex(x, y, w, h, texId, false, false);
    }

    private void drawQuadTex(float x, float y, float w, float h, int texId, boolean flipU, boolean flipV) {
        float u0 = flipU ? 1f : 0f, u1 = flipU ? 0f : 1f;
        float v0 = flipV ? 1f : 0f, v1 = flipV ? 0f : 1f;
        float x0 = nx(x), y0 = ny(y), x1 = nx(x + w), y1 = ny(y + h);
        float[] verts = {
                x0, y0, u0, v0,  x1, y0, u1, v0,  x1, y1, u1, v1,
                x1, y1, u1, v1,  x0, y1, u0, v1,  x0, y0, u0, v0
        };
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STREAM_DRAW);
        glUseProgram(program);
        glUniform1i(uniformUseTex, 1);
        glUniform1i(uniformTex, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texId);
        ortho.get(matrixBuffer);
        glUniformMatrix4fv(uniformMvp, false, matrixBuffer);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    public void drawCrosshair() {
        float cx = framebufferWidth * 0.5f;
        float cy = framebufferHeight * 0.5f;
        float len = 8;
        float thick = 2;
        uiBegin();
        drawQuad(cx - len - thick, cy - thick / 2, len * 2 + thick * 2, thick, 1, 1, 1, 0.95f);
        drawQuad(cx - thick / 2, cy - len - thick, thick, len * 2 + thick * 2, 1, 1, 1, 0.95f);
        uiEnd();
    }

    /** 绘制 9 格快捷栏（按屏高放大 + MC 风格底栏）。 */
    public void drawHotbar(Inventory inv) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) return;
        float slotSize = McGuiScale.slotSize(framebufferWidth, framebufferHeight);
        float bottomPad = McGuiScale.scaled(8, framebufferWidth, framebufferHeight);
        float gridW = Inventory.HOTBAR_SIZE * slotSize;
        float startX = (framebufferWidth - gridW) * 0.5f;
        float startY = framebufferHeight - slotSize - bottomPad;
        float bgPadX = slotSize * 0.12f;
        float bgPadY = slotSize * 0.18f;

        uiBegin();
        drawQuad(startX - bgPadX, startY - bgPadY, gridW + bgPadX * 2, slotSize + bgPadY * 2,
                0f, 0f, 0f, 0.42f);
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            float x = startX + i * slotSize;
            drawMcSlotFrame(x, startY, slotSize);
            if (i == inv.getSelectedHotbar())
                drawQuad(x - 1, startY - 1, slotSize + 2, slotSize + 2, 1f, 1f, 1f, 0.85f);
            InventorySlot slot = inv.getHotbarSlot(i);
            if (slot != null && !slot.isEmpty())
                drawSlotContent(x, startY, slotSize, slot);
        }
        String name = inv.getSelectedItemName();
        if (name != null && !name.isEmpty()) {
            float nameScale = Math.max(1.05f, slotSize / 56f);
            textRenderer.getTextureFor(name);
            float nameH = textRenderer.getLastHeight() * nameScale;
            drawText((framebufferWidth - textRenderer.getLastWidth() * nameScale) * 0.5f,
                    startY - nameH - slotSize * 0.22f, nameScale, name);
        }
        uiEnd();
    }

    /** 物品栏打开时鼠标命中检测（帧缓冲坐标）。 */
    public int pickInventorySlot(float mouseX, float mouseY) {
        return invLayout.pickSlot(mouseX, mouseY);
    }

    /** Minecraft 风格物品栏：3×9 主背包 + 1×9 快捷栏（同一 36 格），光标手持物。 */
    public void drawInventory(Inventory inv, float mouseX, float mouseY) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) return;
        invLayout.compute(framebufferWidth, framebufferHeight);
        float slotSize = invLayout.getSlotSize();
        float startX = invLayout.getPanelX();
        float startY = invLayout.getPanelY();
        float panelW = invLayout.getPanelW();
        float panelH = invLayout.getPanelH();
        float panelPad = invLayout.getPanelPad();
        float slotStartX = invLayout.getSlotStartX();
        float slotStartY = invLayout.getSlotStartY();
        float hotbarY = invLayout.getHotbarY();
        float border = Math.max(1f, McGuiScale.inventoryScale(framebufferWidth, framebufferHeight));

        uiBegin();
        drawQuad(0, 0, framebufferWidth, framebufferHeight, 0f, 0f, 0f, 0.6f);
        drawQuad(startX - border, startY - border, panelW + border * 2, panelH + border * 2, 0.08f, 0.08f, 0.08f, 1f);
        drawQuad(startX, startY, panelW, panelH, 0.78f, 0.78f, 0.78f, 1f);

        float titleScale = Math.max(1.05f, border * 0.42f);
        String title = Lang.inventoryTitle();
        textRenderer.getTextureFor(title);
        float titleTextH = textRenderer.getLastHeight() * titleScale;
        float titleBandH = invLayout.getTitleH();
        float titleY = startY + panelPad + Math.max(0f, (titleBandH - panelPad - titleTextH) * 0.5f);
        drawText(startX + panelPad, titleY, titleScale, title);

        for (int row = 0; row < InventoryScreenLayout.MAIN_ROWS; row++) {
            for (int col = 0; col < InventoryScreenLayout.COLS; col++) {
                int slotIndex = Inventory.MAIN_START + row * InventoryScreenLayout.COLS + col;
                float x = slotStartX + col * slotSize;
                float y = slotStartY + row * slotSize;
                drawMcSlotFrame(x, y, slotSize);
                InventorySlot slot = inv.getSlot(slotIndex);
                if (slot != null && !slot.isEmpty())
                    drawSlotContent(x, y, slotSize, slot);
            }
        }
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            float x = slotStartX + i * slotSize;
            drawMcSlotFrame(x, hotbarY, slotSize);
            if (i == inv.getSelectedHotbar())
                drawQuad(x - 1, hotbarY - 1, slotSize + 2, slotSize + 2, 1f, 1f, 1f, 0.85f);
            InventorySlot slot = inv.getSlot(i);
            if (slot != null && !slot.isEmpty())
                drawSlotContent(x, hotbarY, slotSize, slot);
        }
        if (inv.hasCursor())
            drawCursorStack(inv.getCursorType(), inv.getCursorCount(), mouseX, mouseY, slotSize);
        uiEnd();
    }

    public int pickPauseButton(PauseScreen screen, float mouseX, float mouseY) {
        return switch (screen) {
            case SETTINGS -> pauseLayout.pickSettingsButton(mouseX, mouseY);
            case LANGUAGE -> pauseLayout.pickLanguageButton(mouseX, mouseY);
            default -> pauseLayout.pickMenuButton(mouseX, mouseY);
        };
    }

    /** Minecraft 风格暂停菜单（主菜单 / 设置 / 语言）。 */
    public void drawPauseMenu(PauseScreen screen, float mouseX, float mouseY) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) return;
        switch (screen) {
            case SETTINGS -> drawPauseSettings(mouseX, mouseY);
            case LANGUAGE -> drawPauseLanguage(mouseX, mouseY);
            default -> drawPauseMainMenu(mouseX, mouseY);
        }
    }

    private float uiTitleScale() {
        return Math.max(1.45f, McGuiScale.inventoryScale(framebufferWidth, framebufferHeight) * 0.36f);
    }

    private float uiButtonTextScale() {
        return 1.05f;
    }

    private void drawPauseMainMenu(float mouseX, float mouseY) {
        pauseLayout.computeMenu(framebufferWidth, framebufferHeight);

        uiBegin();
        drawQuad(0, 0, framebufferWidth, framebufferHeight, 0f, 0f, 0f, 0.65f);

        float titleScale = uiTitleScale();
        String title = Lang.pauseTitle();
        textRenderer.getTextureFor(title);
        float titleW = textRenderer.getLastWidth() * titleScale;
        drawText((framebufferWidth - titleW) * 0.5f, framebufferHeight * 0.22f, titleScale, title);

        drawPauseButton(pauseLayout.getResumeX(), pauseLayout.getResumeY(),
                pauseLayout.getBtnW(), pauseLayout.getBtnH(), Lang.resume(), mouseX, mouseY, false);
        drawPauseButton(pauseLayout.getSettingsX(), pauseLayout.getSettingsY(),
                pauseLayout.getBtnW(), pauseLayout.getBtnH(), Lang.settings(), mouseX, mouseY, false);
        drawPauseButton(pauseLayout.getLauncherX(), pauseLayout.getLauncherY(),
                pauseLayout.getBtnW(), pauseLayout.getBtnH(), Lang.backToLauncher(), mouseX, mouseY, false);
        uiEnd();
    }

    private void drawPauseSettings(float mouseX, float mouseY) {
        pauseLayout.computeSettings(framebufferWidth, framebufferHeight);

        uiBegin();
        drawQuad(0, 0, framebufferWidth, framebufferHeight, 0f, 0f, 0f, 0.65f);

        float titleScale = uiTitleScale() * 0.9f;
        String title = Lang.settingsTitle();
        textRenderer.getTextureFor(title);
        float titleW = textRenderer.getLastWidth() * titleScale;
        drawText((framebufferWidth - titleW) * 0.5f, framebufferHeight * 0.20f, titleScale, title);

        drawPauseButton(pauseLayout.getLanguageX(), pauseLayout.getLanguageY(),
                pauseLayout.getBtnW(), pauseLayout.getBtnH(), Lang.languageMenu(), mouseX, mouseY, false);
        drawPauseButton(pauseLayout.getBackX(), pauseLayout.getBackY(),
                pauseLayout.getBtnW(), pauseLayout.getBtnH(), Lang.back(), mouseX, mouseY, false);
        uiEnd();
    }

    private void drawPauseLanguage(float mouseX, float mouseY) {
        pauseLayout.computeLanguage(framebufferWidth, framebufferHeight);
        GameLanguage current = GameSettings.getLanguage();

        uiBegin();
        drawQuad(0, 0, framebufferWidth, framebufferHeight, 0f, 0f, 0f, 0.65f);

        float titleScale = uiTitleScale() * 0.85f;
        String title = Lang.selectLanguageTitle();
        textRenderer.getTextureFor(title);
        float titleW = textRenderer.getLastWidth() * titleScale;
        drawText((framebufferWidth - titleW) * 0.5f, framebufferHeight * 0.16f, titleScale, title);

        drawPauseButton(pauseLayout.getLangZhX(), pauseLayout.getLangZhY(),
                pauseLayout.getBtnW(), pauseLayout.getBtnH(), Lang.langChinese(), mouseX, mouseY,
                current == GameLanguage.ZH);
        drawPauseButton(pauseLayout.getLangEnX(), pauseLayout.getLangEnY(),
                pauseLayout.getBtnW(), pauseLayout.getBtnH(), Lang.langEnglish(), mouseX, mouseY,
                current == GameLanguage.EN);
        drawPauseButton(pauseLayout.getLangRevX(), pauseLayout.getLangRevY(),
                pauseLayout.getBtnW(), pauseLayout.getBtnH(), Lang.langReverseChinese(), mouseX, mouseY,
                current == GameLanguage.REVERSE_ZH);
        drawPauseButton(pauseLayout.getBackX(), pauseLayout.getBackY(),
                pauseLayout.getBtnW(), pauseLayout.getBtnH(), Lang.back(), mouseX, mouseY, false);
        uiEnd();
    }

    private void drawPauseButton(float x, float y, float w, float h, String label, float mx, float my,
                                 boolean selected) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        if (selected) {
            drawQuad(x - 2, y - 2, w + 4, h + 4, 1f, 1f, 1f, 0.95f);
            drawQuad(x, y, w, h, 0.65f, 0.72f, 0.82f, 1f);
        } else if (hover) {
            drawQuad(x - 2, y - 2, w + 4, h + 4, 1f, 1f, 1f, 0.9f);
            drawQuad(x, y, w, h, 0.72f, 0.78f, 0.88f, 1f);
        } else {
            drawQuad(x - 1, y - 1, w + 2, h + 2, 0.1f, 0.1f, 0.1f, 1f);
            drawQuad(x, y, w, h, 0.55f, 0.55f, 0.55f, 1f);
        }
        drawQuad(x, y, w, 1, 0.92f, 0.92f, 0.92f, 1f);
        drawQuad(x, y + h - 1, w, 1, 0.22f, 0.22f, 0.22f, 1f);
        textRenderer.getTextureFor(label);
        float tw = textRenderer.getLastWidth();
        float th = textRenderer.getLastHeight();
        float labelScale = uiButtonTextScale();
        drawText(x + (w - tw * labelScale) * 0.5f, y + (h - th * labelScale) * 0.5f, labelScale, label);
    }

    private void drawCursorStack(ItemType type, int count, float mouseX, float mouseY, float slotSize) {
        float x = mouseX - slotSize * 0.5f;
        float y = mouseY - slotSize * 0.5f;
        float pad = slotSize * 0.12f;
        float inner = slotSize - pad * 2f;
        drawSlotItem(x + pad, y + pad, inner, inner, type);
        if (count > 1) {
            float countScale = Math.max(0.9f, slotSize / 80f);
            drawText(x + slotSize * 0.55f, y + slotSize * 0.55f, countScale, String.valueOf(count));
        }
    }

    /** MC 风格槽位：深灰底 + 细亮/暗边。 */
    private void drawMcSlotFrame(float x, float y, float size) {
        float b = Math.max(1f, Math.min(2f, size / 36f));
        drawQuad(x, y, size, size, 0.55f, 0.55f, 0.55f, 1f);
        drawQuad(x, y, size, b, 0.95f, 0.95f, 0.95f, 1f);
        drawQuad(x, y + size - b, size, b, 0.25f, 0.25f, 0.25f, 1f);
        drawQuad(x, y, b, size, 0.95f, 0.95f, 0.95f, 1f);
        drawQuad(x + size - b, y, b, size, 0.25f, 0.25f, 0.25f, 1f);
    }

    private void drawSlotFrame(float x, float y, float size, boolean selected) {
        drawQuad(x - 2, y - 2, size + 4, size + 4, 0.12f, 0.18f, 0.28f, 1f);
        if (selected)
            drawQuad(x - 2, y - 2, size + 4, size + 4, 1f, 0.85f, 0.2f, 0.75f);
        drawQuad(x, y, size, size, 0.55f, 0.62f, 0.72f, 0.95f);
        drawQuad(x, y, size, 2, 0.75f, 0.82f, 0.92f, 0.9f);
        drawQuad(x, y + size - 2, size, 2, 0.35f, 0.42f, 0.52f, 0.9f);
    }

    private void drawSlotContent(float x, float y, float size, InventorySlot slot) {
        float pad = size * 0.12f;
        float inner = size - pad * 2f;
        drawSlotItem(x + pad, y + pad, inner, inner, slot.getType());
        if (slot.getCount() > 1) {
            float countScale = Math.max(0.9f, size / 80f);
            drawText(x + size * 0.55f, y + size * 0.55f, countScale, String.valueOf(slot.getCount()));
        }
    }

    /** 泥土用材质贴图（若无贴图则用棕色块），格洛克17用深灰色块。 */
    private void drawSlotItem(float x, float y, float w, float h, ItemType t) {
        if (t == ItemType.DIRT) {
            if (dirtTextureId != 0) drawQuadTex(x, y, w, h, dirtTextureId);
            else drawQuad(x, y, w, h, 0.5f, 0.35f, 0.2f, 1f);
        } else if (t == ItemType.GLOCK_17) {
            drawQuad(x, y, w, h, 0.22f, 0.22f, 0.22f, 1f);
        } else {
            drawQuad(x, y, w, h, 0.4f, 0.4f, 0.4f, 1f);
        }
    }

    public void cleanup() {
        textRenderer.cleanup();
        if (dirtTextureId != 0) glDeleteTextures(dirtTextureId);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (vbo != 0) glDeleteBuffers(vbo);
        if (program != 0) glDeleteProgram(program);
    }
}
