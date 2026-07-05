package com.blockforge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * User settings, persisted to ~/.blockforge/options.txt.
 */
public final class Settings {

    // video
    public boolean fullscreen = false;
    public boolean vsync = true;
    public float fov = 72f;                 // 60..110
    public int renderDistance = 8;          // chunks, 4..16

    // graphics
    public float brightness = 0.5f;         // 0..1 night light floor
    public boolean fog = true;
    public boolean dayNightCycle = true;
    public boolean blockOutline = true;

    // audio
    public float masterVolume = 1f;
    public float effectsVolume = 1f;
    public boolean uiSounds = true;

    public static Path gameDir() {
        return Path.of(System.getProperty("user.home"), ".blockforge");
    }

    private static Path file() {
        return gameDir().resolve("options.txt");
    }

    public static Settings load() {
        Settings s = new Settings();
        Path f = file();
        if (!Files.exists(f)) return s;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
            s.fullscreen = bool(p, "fullscreen", s.fullscreen);
            s.vsync = bool(p, "vsync", s.vsync);
            s.fov = clamp(f32(p, "fov", s.fov), 60f, 110f);
            s.renderDistance = (int) clamp(f32(p, "renderDistance", s.renderDistance), 4, 16);
            s.brightness = clamp(f32(p, "brightness", s.brightness), 0f, 1f);
            s.fog = bool(p, "fog", s.fog);
            s.dayNightCycle = bool(p, "dayNightCycle", s.dayNightCycle);
            s.blockOutline = bool(p, "blockOutline", s.blockOutline);
            s.masterVolume = clamp(f32(p, "masterVolume", s.masterVolume), 0f, 1f);
            s.effectsVolume = clamp(f32(p, "effectsVolume", s.effectsVolume), 0f, 1f);
            s.uiSounds = bool(p, "uiSounds", s.uiSounds);
        } catch (IOException e) {
            System.err.println("could not read options: " + e.getMessage());
        }
        return s;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("fullscreen", String.valueOf(fullscreen));
        p.setProperty("vsync", String.valueOf(vsync));
        p.setProperty("fov", String.valueOf(fov));
        p.setProperty("renderDistance", String.valueOf(renderDistance));
        p.setProperty("brightness", String.valueOf(brightness));
        p.setProperty("fog", String.valueOf(fog));
        p.setProperty("dayNightCycle", String.valueOf(dayNightCycle));
        p.setProperty("blockOutline", String.valueOf(blockOutline));
        p.setProperty("masterVolume", String.valueOf(masterVolume));
        p.setProperty("effectsVolume", String.valueOf(effectsVolume));
        p.setProperty("uiSounds", String.valueOf(uiSounds));
        try {
            Files.createDirectories(gameDir());
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "BlockForge options");
            }
        } catch (IOException e) {
            System.err.println("could not save options: " + e.getMessage());
        }
    }

    private static boolean bool(Properties p, String k, boolean def) {
        String v = p.getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    private static float f32(Properties p, String k, float def) {
        try {
            return Float.parseFloat(p.getProperty(k, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
