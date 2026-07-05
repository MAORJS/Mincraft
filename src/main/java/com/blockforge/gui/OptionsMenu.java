package com.blockforge.gui;

import com.blockforge.Settings;
import com.blockforge.audio.SoundEngine;

/**
 * Options screens (opened from the in-game pause menu): a root page linking
 * to Video, Audio, and Graphics settings. Changes apply immediately and are
 * saved when the menu closes.
 */
public final class OptionsMenu {

    /** Applies window-level settings (owned by Main). */
    public interface WindowController {
        void applyVsync(boolean vsync);

        void applyFullscreen(boolean fullscreen);
    }

    private enum Page {ROOT, VIDEO, AUDIO, GRAPHICS}

    private Page page = Page.ROOT;
    private final Settings settings;
    private final WindowController windowController;
    private final SoundEngine sound;

    public OptionsMenu(Settings settings, WindowController windowController, SoundEngine sound) {
        this.settings = settings;
        this.windowController = windowController;
        this.sound = sound;
    }

    public void open() {
        page = Page.ROOT;
    }

    /** Renders the current page. Returns true when the user closes options. */
    public boolean frame(Gui gui) {
        float cx = gui.width / 2f;
        float bw = Math.min(440, gui.width - 60), bh = 42, gap = 12;
        float x = cx - bw / 2;
        float y = gui.height * 0.2f;

        switch (page) {
            case ROOT -> {
                gui.textCentered("OPTIONS", cx, y - 50, 3f, 1f, 1f, 1f);
                if (gui.button("VIDEO SETTINGS...", x, y, bw, bh)) page = Page.VIDEO;
                y += bh + gap;
                if (gui.button("AUDIO SETTINGS...", x, y, bw, bh)) page = Page.AUDIO;
                y += bh + gap;
                if (gui.button("GRAPHICS SETTINGS...", x, y, bw, bh)) page = Page.GRAPHICS;
                y += bh + gap * 2;
                if (gui.button("DONE", x, y, bw, bh)) {
                    settings.save();
                    return true;
                }
            }
            case VIDEO -> {
                gui.textCentered("VIDEO SETTINGS", cx, y - 50, 3f, 1f, 1f, 1f);
                boolean fs = gui.toggle("FULLSCREEN", settings.fullscreen, x, y, bw, bh);
                if (fs != settings.fullscreen) {
                    settings.fullscreen = fs;
                    windowController.applyFullscreen(fs);
                }
                y += bh + gap;
                boolean vs = gui.toggle("VSYNC", settings.vsync, x, y, bw, bh);
                if (vs != settings.vsync) {
                    settings.vsync = vs;
                    windowController.applyVsync(vs);
                }
                y += bh + gap;
                float fov01 = (settings.fov - 60f) / 50f;
                fov01 = gui.slider("FOV: " + Math.round(settings.fov), x, y, bw, bh, fov01);
                settings.fov = 60f + fov01 * 50f;
                y += bh + gap;
                float rd01 = (settings.renderDistance - 4) / 12f;
                rd01 = gui.slider("RENDER DISTANCE: " + settings.renderDistance + " CHUNKS", x, y, bw, bh, rd01);
                settings.renderDistance = 4 + Math.round(rd01 * 12f);
                y += bh + gap * 2;
                if (gui.button("BACK", x, y, bw, bh)) page = Page.ROOT;
            }
            case AUDIO -> {
                gui.textCentered("AUDIO SETTINGS", cx, y - 50, 3f, 1f, 1f, 1f);
                float mv = gui.slider("MASTER VOLUME: " + pct(settings.masterVolume),
                        x, y, bw, bh, settings.masterVolume);
                if (mv != settings.masterVolume) {
                    settings.masterVolume = mv;
                }
                y += bh + gap;
                float ev = gui.slider("EFFECTS VOLUME: " + pct(settings.effectsVolume),
                        x, y, bw, bh, settings.effectsVolume);
                if (ev != settings.effectsVolume) {
                    settings.effectsVolume = ev;
                }
                y += bh + gap;
                settings.uiSounds = gui.toggle("UI SOUNDS", settings.uiSounds, x, y, bw, bh);
                y += bh + gap;
                if (gui.button("TEST SOUND", x, y, bw, bh) && sound != null) {
                    sound.play(SoundEngine.PLACE);
                }
                y += bh + gap * 2;
                if (gui.button("BACK", x, y, bw, bh)) page = Page.ROOT;
            }
            case GRAPHICS -> {
                gui.textCentered("GRAPHICS SETTINGS", cx, y - 50, 3f, 1f, 1f, 1f);
                settings.brightness = gui.slider("BRIGHTNESS: " + pct(settings.brightness),
                        x, y, bw, bh, settings.brightness);
                y += bh + gap;
                settings.fog = gui.toggle("FOG", settings.fog, x, y, bw, bh);
                y += bh + gap;
                settings.dayNightCycle = gui.toggle("DAY-NIGHT CYCLE", settings.dayNightCycle, x, y, bw, bh);
                y += bh + gap;
                settings.blockOutline = gui.toggle("BLOCK OUTLINE", settings.blockOutline, x, y, bw, bh);
                y += bh + gap * 2;
                if (gui.button("BACK", x, y, bw, bh)) page = Page.ROOT;
            }
        }
        return false;
    }

    private static String pct(float v) {
        return Math.round(v * 100) + "%";
    }
}
