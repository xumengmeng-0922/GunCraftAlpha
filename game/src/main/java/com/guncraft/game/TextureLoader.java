package com.guncraft.game;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL30.*;

/**
 * 从 classpath 加载 PNG 并上传为 OpenGL 纹理。
 * 若 /textures/dirt.png 不存在则使用程序生成的泥土占位图。
 */
public class TextureLoader {

    public static int load(String resourcePath) {
        try (InputStream in = TextureLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                if ("/textures/dirt.png".equals(resourcePath)) return createDirtPlaceholder();
                throw new IllegalStateException("找不到资源: " + resourcePath);
            }
            BufferedImage img = ImageIO.read(in);
            int w = img.getWidth();
            int h = img.getHeight();
            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);

            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
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
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glBindTexture(GL_TEXTURE_2D, 0);
            return tex;
        } catch (Exception e) {
            if ("/textures/dirt.png".equals(resourcePath)) return createDirtPlaceholder();
            throw new RuntimeException("加载纹理失败: " + resourcePath, e);
        }
    }

    /** 无 dirt.png 时生成 16x16 泥土色占位纹理。 */
    public static int createDirtPlaceholder() {
        int size = 16;
        Random r = new Random(12345);
        ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int brown = 0x8B4513;
                int variation = r.nextInt(30) - 15;
                int r1 = Math.max(0, Math.min(255, ((brown >> 16) & 0xFF) + variation));
                int g1 = Math.max(0, Math.min(255, ((brown >> 8) & 0xFF) + variation));
                int b1 = Math.max(0, Math.min(255, (brown & 0xFF) + variation));
                buf.put((byte) r1).put((byte) g1).put((byte) b1).put((byte) 255);
            }
        }
        buf.flip();
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);
        return tex;
    }
}
