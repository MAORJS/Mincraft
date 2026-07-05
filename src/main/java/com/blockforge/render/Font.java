package com.blockforge.render;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Procedural 5x7 bitmap font. Glyph shapes are defined below as bit rows and
 * baked into a small texture at startup — like all art in the game, drawn in
 * code. Lowercase input renders as uppercase.
 */
public final class Font {

    public static final int GW = 5;   // glyph width in pixels
    public static final int GH = 7;   // glyph height in pixels
    private static final int CELL_W = GW + 1;
    private static final int CELL_H = GH + 1;
    private static final int COLS = 16;

    private static final Map<Character, Integer> CELL = new HashMap<>();
    private static final StringBuilder GLYPH_ROWS = new StringBuilder();
    private static int glyphCount = 0;

    private final int texture;
    private final int texW;
    private final int texH;

    private static void g(char c, String rows) {
        if (rows.length() != GW * GH) {
            throw new IllegalStateException("glyph '" + c + "' has " + rows.length() + " bits, expected " + (GW * GH));
        }
        CELL.put(c, glyphCount++);
        GLYPH_ROWS.append(rows);
    }

    static {
        g(' ', "00000000000000000000000000000000000");
        g('A', "01110100011000111111100011000110001");
        g('B', "11110100011111010001100011000111110");
        g('C', "01111100001000010000100001000001111");
        g('D', "11110100011000110001100011000111110");
        g('E', "11111100001111010000100001000011111");
        g('F', "11111100001111010000100001000010000");
        g('G', "01111100001000010111100011000101110");
        g('H', "10001100011111110001100011000110001");
        g('I', "11111001000010000100001000010011111");
        g('J', "00111000100001000010000101001001100");
        g('K', "10001100101110010100100101000110001");
        g('L', "10000100001000010000100001000011111");
        g('M', "10001110111010110101100011000110001");
        g('N', "10001110011010110011100011000110001");
        g('O', "01110100011000110001100011000101110");
        g('P', "11110100011000111110100001000010000");
        g('Q', "01110100011000110001101011001001101");
        g('R', "11110100011000111110100101000110001");
        g('S', "01111100001000001110000010000111110");
        g('T', "11111001000010000100001000010000100");
        g('U', "10001100011000110001100011000101110");
        g('V', "10001100011000110001100010101000100");
        g('W', "10001100011000110101101011101110001");
        g('X', "10001100010101000100010101000110001");
        g('Y', "10001100010101000100001000010000100");
        g('Z', "11111000010001000100010001000011111");
        g('0', "01110100011001110101110011000101110");
        g('1', "00100011000010000100001000010001110");
        g('2', "01110100010000100110010001000011111");
        g('3', "11110000010000101110000010000111110");
        g('4', "00010001100101010010111110001000010");
        g('5', "11111100001111000001000011000101110");
        g('6', "01110100001000011110100011000101110");
        g('7', "11111000010001000100010000100001000");
        g('8', "01110100011000101110100011000101110");
        g('9', "01110100011000101111000010000101110");
        g('.', "00000000000000000000000000110001100");
        g(',', "00000000000000000000001100011001000");
        g(':', "00000011000110000000011000110000000");
        g(';', "00000011000110000000011000110001000");
        g('!', "00100001000010000100001000000000100");
        g('?', "01110100010000100110001000000000100");
        g('-', "00000000000000011111000000000000000");
        g('+', "00000001000010011111001000010000000");
        g('/', "00001000010001000100010001000010000");
        g('(', "00010001000100001000010000010000010");
        g(')', "01000001000001000010000100010001000");
        g('\'', "00100001000000000000000000000000000");
        g('"', "01010010100000000000000000000000000");
        g('<', "00010001000100010000010000010000010");
        g('>', "01000001000001000001000010001001000");
        g('=', "00000000001111100000111110000000000");
        g('_', "00000000000000000000000000000011111");
        g('%', "11001110100001000100010000101110011");
        g('*', "00000101010111011111011101010100000");
        g('#', "01010111110101001010010101111101010");
        g('[', "01110010000100001000010000100001110");
        g(']', "01110000100001000010000100001001110");
    }

    public Font() {
        int rows = (glyphCount + COLS - 1) / COLS;
        texW = COLS * CELL_W;
        texH = rows * CELL_H;
        ByteBuffer buf = BufferUtils.createByteBuffer(texW * texH * 4);

        for (Map.Entry<Character, Integer> e : CELL.entrySet()) {
            int cell = e.getValue();
            int base = cell * GW * GH;
            int cx = (cell % COLS) * CELL_W;
            int cy = (cell / COLS) * CELL_H;
            for (int y = 0; y < GH; y++) {
                for (int x = 0; x < GW; x++) {
                    if (GLYPH_ROWS.charAt(base + y * GW + x) != '1') continue;
                    // flip rows: GL v axis points up
                    int py = cy + (GH - 1 - y);
                    int idx = (py * texW + cx + x) * 4;
                    buf.put(idx, (byte) 255);
                    buf.put(idx + 1, (byte) 255);
                    buf.put(idx + 2, (byte) 255);
                    buf.put(idx + 3, (byte) 255);
                }
            }
        }

        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texW, texH, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    public int texture() {
        return texture;
    }

    public float width(String text, float scale) {
        return text.length() * (GW + 1) * scale - scale;
    }

    public float height(float scale) {
        return GH * scale;
    }

    /** Adds text quads to the batch. Draw the batch with {@link #texture()}. */
    public void draw(Batch2D batch, String text, float x, float y, float scale,
                     float r, float g, float b, float a) {
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            Integer cell = CELL.get(c);
            if (cell == null) cell = CELL.get('?');
            int col = cell % COLS;
            int row = cell / COLS;
            float u0 = col * CELL_W / (float) texW;
            float v0 = row * CELL_H / (float) texH;
            float u1 = (col * CELL_W + GW) / (float) texW;
            float v1 = (row * CELL_H + GH) / (float) texH;
            batch.quad(cx, y, GW * scale, GH * scale, u0, v0, u1, v1, r, g, b, a);
            cx += (GW + 1) * scale;
        }
    }

    /** Text with a dark drop shadow. */
    public void drawShadow(Batch2D batch, String text, float x, float y, float scale,
                           float r, float g, float b, float a) {
        draw(batch, text, x + scale, y + scale, scale, 0.1f, 0.1f, 0.1f, a * 0.8f);
        draw(batch, text, x, y, scale, r, g, b, a);
    }

    public void delete() {
        glDeleteTextures(texture);
    }
}
