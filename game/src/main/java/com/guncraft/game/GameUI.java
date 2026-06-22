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

    private void drawQuadTex(float x, float y, float w, float h, int texId) {
        float x0 = nx(x), y0 = ny(y), x1 = nx(x + w), y1 = ny(y + h);
        float[] verts = {
                x0, y0, 0, 0,  x1, y0, 1, 0,  x1, y1, 1, 1,
                x1, y1, 1, 1,  x0, y1, 0, 1,  x0, y0, 0, 0
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
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        drawQuad(cx - len - thick, cy - thick / 2, len * 2 + thick * 2, thick, 1, 1, 1, 0.95f);
        drawQuad(cx - thick / 2, cy - len - thick, thick, len * 2 + thick * 2, 1, 1, 1, 0.95f);
        glDisable(GL_BLEND);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private static final float HOTBAR_SLOT_SIZE = 40;
    private static final float HOTBAR_GAP = 4;
    private static final float HOTBAR_Y_OFFSET = 24;

    /** 绘制 9 格物品栏：泥土用材质贴图，格洛克17用色块；选中格高亮；上方显示当前物品名。 */
    public void drawHotbar(Inventory inv) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) return;
        float totalW = Inventory.HOTBAR_SIZE * (HOTBAR_SLOT_SIZE + HOTBAR_GAP) - HOTBAR_GAP + 16;
        float startX = (framebufferWidth - totalW) * 0.5f;
        float startY = framebufferHeight - HOTBAR_SLOT_SIZE - HOTBAR_Y_OFFSET - 8;

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            float x = startX + 8 + i * (HOTBAR_SLOT_SIZE + HOTBAR_GAP);
            float y = startY + 4;
            drawQuad(x - 2, y - 2, HOTBAR_SLOT_SIZE + 4, HOTBAR_SLOT_SIZE + 4, 0.2f, 0.2f, 0.2f, 0.9f);
            if (i == inv.getSelectedHotbar())
                drawQuad(x - 2, y - 2, HOTBAR_SLOT_SIZE + 4, HOTBAR_SLOT_SIZE + 4, 1, 1, 0.3f, 0.6f);
            drawQuad(x, y, HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE, 0.12f, 0.12f, 0.12f, 0.95f);
            InventorySlot slot = inv.getHotbarSlot(i);
            if (slot != null && !slot.isEmpty())
                drawSlotItem(x + 4, y + 4, HOTBAR_SLOT_SIZE - 8, HOTBAR_SLOT_SIZE - 8, slot.getType());
        }
        String name = inv.getSelectedItemName();
        if (name != null && !name.isEmpty()) {
            try {
                int texId = textRenderer.getTextureFor(name);
                int tw = textRenderer.getLastWidth();
                int th = textRenderer.getLastHeight();
                float scale = 1.5f;
                drawQuadTex((framebufferWidth - tw * scale) * 0.5f, startY - th * scale - 8, tw * scale, th * scale, texId);
            } catch (Throwable ignored) { }
        }
        glDisable(GL_BLEND);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    /** 背包界面：上方 3×9，下方 9 格快捷栏；泥土格显示泥土材质。 */
    public void drawInventory(Inventory inv) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) return;
        float slotSize = 36;
        float gap = 2;
        int cols = 9;
        int invRows = 3;
        float panelW = cols * (slotSize + gap) - gap + 32;
        float panelH = (invRows + 1) * (slotSize + gap) - gap + 48;
        float startX = (framebufferWidth - panelW) * 0.5f;
        float startY = (framebufferHeight - panelH) * 0.5f;

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        drawQuad(startX, startY, panelW, panelH, 0.2f, 0.2f, 0.25f, 0.95f);
        float slotStartX = startX + 16;
        float slotStartY = startY + 16;
        for (int row = 0; row < invRows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                float x = slotStartX + col * (slotSize + gap);
                float y = slotStartY + row * (slotSize + gap);
                drawQuad(x, y, slotSize, slotSize, 0.25f, 0.25f, 0.3f, 0.95f);
                InventorySlot slot = inv.getBackpackSlot(idx);
                if (slot != null && !slot.isEmpty())
                    drawSlotItem(x + 4, y + 4, slotSize - 8, slotSize - 8, slot.getType());
            }
        }
        float hotbarY = slotStartY + invRows * (slotSize + gap) + 8;
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            float x = slotStartX + i * (slotSize + gap);
            drawQuad(x, hotbarY, slotSize, slotSize, 0.25f, 0.25f, 0.3f, 0.95f);
            if (i == inv.getSelectedHotbar())
                drawQuad(x - 2, hotbarY - 2, slotSize + 4, slotSize + 4, 1, 0.8f, 0.2f, 0.5f);
            InventorySlot slot = inv.getHotbarSlot(i);
            if (slot != null && !slot.isEmpty())
                drawSlotItem(x + 4, hotbarY + 4, slotSize - 8, slotSize - 8, slot.getType());
        }
        glDisable(GL_BLEND);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
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
