package com.guncraft.game;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL30.*;

/**
 * 用 AWT 将字符串渲染为纹理，用于 HUD 显示物品名等。
 */
public class TextRenderer {
    private static final int FONT_SIZE = 28;
    /** 优先中文，否则用系统默认无衬线字体，保证各系统都能显示。 */
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE);
    private final Map<String, Integer> cache = new HashMap<>();
    private int currentTexW;
    private int currentTexH;

    public int getTextureFor(String text) {
        if (text == null || text.isEmpty()) {
            if (cache.containsKey("")) return cache.get("");
            int tex = createEmptyTexture();
            cache.put("", tex);
            return tex;
        }
        return cache.computeIfAbsent(text, this::createTexture);
    }

    private int createEmptyTexture() {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        ByteBuffer buf = ByteBuffer.allocateDirect(4).put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 0);
        buf.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);
        currentTexW = 1;
        currentTexH = 1;
        return tex;
    }

    private int createTexture(String text) {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setFont(FONT);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 4;
        int h = fm.getHeight() + 4;
        g.dispose();

        img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setFont(FONT);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);
        g.drawString(text, 2, fm.getAscent() + 2);
        g.dispose();

        w = img.getWidth();
        h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);

        ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int p = pixels[y * w + x];
                buf.put((byte) ((p >> 16) & 0xFF));
                buf.put((byte) ((p >> 8) & 0xFF));
                buf.put((byte) (p & 0xFF));
                buf.put((byte) ((p >> 24) & 0xFF));
            }
        }
        buf.flip();

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);
        currentTexW = w;
        currentTexH = h;
        return tex;
    }

    public int getLastWidth() { return currentTexW; }
    public int getLastHeight() { return currentTexH; }

    public void cleanup() {
        for (int tex : cache.values())
            if (tex != 0) glDeleteTextures(tex);
        cache.clear();
    }
}
