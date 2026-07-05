package com.blockforge.gui;

import com.blockforge.Input;
import com.blockforge.audio.SoundEngine;
import com.blockforge.render.Batch2D;
import com.blockforge.render.Font;
import com.blockforge.render.TextureAtlas;
import com.blockforge.world.Block;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Immediate-mode GUI: call begin() each frame, then widgets, then end().
 * Two internal batches keep draw order simple — shapes and block icons render
 * first (atlas texture), text renders on top (font texture).
 */
public final class Gui {

    public int width, height;
    public float mouseX, mouseY;
    public boolean clicked;        // left click this frame
    public boolean rightClicked;
    public boolean mouseDown;
    public double scroll;
    public double time;

    private final Batch2D shapes = new Batch2D();
    private final Batch2D textBatch = new Batch2D();
    private final Font font = new Font();
    private final int atlasTexture;
    private final SoundEngine sound;

    private Input input;
    private int focusId = 0;       // focused text field
    private int activeSlider = 0;  // slider currently being dragged

    public Gui(int atlasTexture, SoundEngine sound) {
        this.atlasTexture = atlasTexture;
        this.sound = sound;
    }

    public Font font() {
        return font;
    }

    public void begin(Input input, int width, int height, double time) {
        this.input = input;
        this.width = width;
        this.height = height;
        this.time = time;
        mouseX = (float) input.mouseX();
        mouseY = (float) input.mouseY();
        clicked = input.mouseClicked(GLFW_MOUSE_BUTTON_LEFT);
        rightClicked = input.mouseClicked(GLFW_MOUSE_BUTTON_RIGHT);
        mouseDown = input.mouseDown(GLFW_MOUSE_BUTTON_LEFT);
        scroll = input.scrollY;
        if (!mouseDown) activeSlider = 0;
        shapes.begin();
        textBatch.begin();
    }

    public void end() {
        shapes.flush(atlasTexture, width, height);
        textBatch.flush(font.texture(), width, height);
    }

    // ------------------------------------------------------------- primitives

    public boolean hover(float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        shapes.solid(x, y, w, h, r, g, b, a);
    }

    public void frame(float x, float y, float w, float h, float t,
                      float r, float g, float b, float a) {
        rect(x, y, w, t, r, g, b, a);
        rect(x, y + h - t, w, t, r, g, b, a);
        rect(x, y, t, h, r, g, b, a);
        rect(x + w - t, y, t, h, r, g, b, a);
    }

    /** Draws one atlas tile (e.g. a block texture) as a 2D quad. */
    public void tile(float x, float y, float w, float h, int tileIndex, float brightness) {
        float u0 = TextureAtlas.u0(tileIndex) + TextureAtlas.UV_INSET;
        float v0 = TextureAtlas.v0(tileIndex) + TextureAtlas.UV_INSET;
        float u1 = TextureAtlas.u0(tileIndex) + TextureAtlas.UV_SPAN - TextureAtlas.UV_INSET;
        float v1 = TextureAtlas.v0(tileIndex) + TextureAtlas.UV_SPAN - TextureAtlas.UV_INSET;
        shapes.quad(x, y, w, h, u0, v0, u1, v1, brightness, brightness, brightness, 1f);
    }

    public void blockIcon(Block b, float x, float y, float size) {
        tile(x, y, size, size, b.texSide, 1f);
    }

    public void text(String s, float x, float y, float scale, float r, float g, float b) {
        font.drawShadow(textBatch, s, x, y, scale, r, g, b, 1f);
    }

    public void textCentered(String s, float cx, float y, float scale, float r, float g, float b) {
        text(s, cx - font.width(s, scale) / 2f, y, scale, r, g, b);
    }

    /** Fullscreen tiled texture background (menus). */
    public void tiledBackground(int tileIndex, float tileSize, float brightness) {
        for (float y = 0; y < height; y += tileSize) {
            for (float x = 0; x < width; x += tileSize) {
                tile(x, y, tileSize, tileSize, tileIndex, brightness);
            }
        }
    }

    // ---------------------------------------------------------------- widgets

    public boolean button(String label, float x, float y, float w, float h) {
        return button(label, x, y, w, h, true);
    }

    public boolean button(String label, float x, float y, float w, float h, boolean enabled) {
        boolean hov = enabled && hover(x, y, w, h);
        float base = enabled ? (hov ? 0.42f : 0.30f) : 0.18f;
        rect(x, y, w, h, base, base, base + 0.03f, 0.92f);
        frame(x, y, w, h, 2, hov ? 0.95f : 0.65f, hov ? 0.95f : 0.65f, hov ? 0.85f : 0.65f, 0.9f);
        float scale = 2f;
        float tr = enabled ? 1f : 0.55f;
        textCentered(label, x + w / 2f, y + (h - font.height(scale)) / 2f, scale, tr, tr, enabled ? 0.92f : 0.55f);
        boolean hit = hov && clicked;
        if (hit && sound != null) sound.play(SoundEngine.CLICK);
        return hit;
    }

    /** Toggle drawn as a button with an ON/OFF suffix; returns the new state. */
    public boolean toggle(String label, boolean on, float x, float y, float w, float h) {
        String suffix = on ? "ON" : "OFF";
        if (button(label + ": " + suffix, x, y, w, h)) {
            return !on;
        }
        return on;
    }

    /**
     * Horizontal slider; value in [0,1]. The display string is drawn centered.
     * Returns the (possibly updated) value.
     */
    public float slider(String display, float x, float y, float w, float h, float value) {
        int id = (int) (x * 31 + y * 7 + 1);
        boolean hov = hover(x, y, w, h);
        if (hov && clicked) activeSlider = id;
        if (activeSlider == id && mouseDown) {
            value = Math.max(0f, Math.min(1f, (mouseX - x) / w));
        }
        rect(x, y, w, h, 0.16f, 0.16f, 0.18f, 0.92f);
        frame(x, y, w, h, 2, 0.6f, 0.6f, 0.6f, 0.9f);
        float knobW = 10;
        float kx = x + value * (w - knobW);
        rect(kx, y + 2, knobW, h - 4, hov || activeSlider == id ? 0.95f : 0.75f, 0.85f, 0.55f, 1f);
        float scale = 2f;
        textCentered(display, x + w / 2f, y + (h - font.height(scale)) / 2f, scale, 1f, 1f, 0.92f);
        return value;
    }

    /**
     * Single-line text field. Click to focus; typed characters and backspace
     * are consumed while focused. Returns true if focused and Enter pressed.
     */
    public boolean textField(int id, StringBuilder content, int maxLen,
                             float x, float y, float w, float h) {
        boolean hov = hover(x, y, w, h);
        if (clicked) {
            if (hov) focusId = id;
            else if (focusId == id) focusId = 0;
        }
        boolean focused = focusId == id;

        if (focused) {
            for (int i = 0; i < input.typed.length() && content.length() < maxLen; i++) {
                content.append(input.typed.charAt(i));
            }
            if (input.pressed(GLFW_KEY_BACKSPACE) && content.length() > 0) {
                content.setLength(content.length() - 1);
            }
        }

        rect(x, y, w, h, 0.05f, 0.05f, 0.06f, 0.95f);
        frame(x, y, w, h, 2, focused ? 0.95f : 0.55f, focused ? 0.9f : 0.55f, 0.5f, 0.95f);
        float scale = 2f;
        String shown = content.toString();
        float maxTextW = w - 16;
        while (!shown.isEmpty() && font.width(shown, scale) > maxTextW) {
            shown = shown.substring(1);
        }
        float tx = x + 8;
        float ty = y + (h - font.height(scale)) / 2f;
        text(shown, tx, ty, scale, 1f, 1f, 1f);
        if (focused && ((int) (time * 2) % 2 == 0)) {
            rect(tx + font.width(shown, scale) + (shown.isEmpty() ? 0 : scale), ty, 2, font.height(scale), 1f, 1f, 1f, 0.9f);
        }
        return focused && input.pressed(GLFW_KEY_ENTER);
    }

    public void unfocus() {
        focusId = 0;
    }

    public void delete() {
        shapes.delete();
        textBatch.delete();
        font.delete();
    }
}
