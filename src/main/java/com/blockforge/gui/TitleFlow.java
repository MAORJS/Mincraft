package com.blockforge.gui;

import com.blockforge.Input;
import com.blockforge.save.WorldStorage;
import com.blockforge.save.WorldStorage.WorldInfo;
import com.blockforge.world.Blocks;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * Pre-game screens: title (logo + Singleplayer), world list with
 * Play/Rename/Delete/New World, world creation, and world rename.
 */
public final class TitleFlow {

    private enum Screen {TITLE, WORLD_LIST, CREATE, RENAME}

    private Screen screen = Screen.TITLE;
    private List<WorldInfo> worlds = List.of();
    private int selected = -1;
    private int listScroll = 0;
    private boolean confirmDelete = false;

    private final StringBuilder nameField = new StringBuilder("New World");
    private final StringBuilder seedField = new StringBuilder();
    private final StringBuilder renameField = new StringBuilder();
    private boolean createCreative = false;

    private WorldInfo startWorld;
    private boolean quitRequested;

    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    /** Reset to the title screen (returning from a game). */
    public void reset() {
        screen = Screen.TITLE;
        startWorld = null;
        selected = -1;
        confirmDelete = false;
    }

    /** World the user chose to play, or null. Cleared after reading. */
    public WorldInfo consumeStart() {
        WorldInfo w = startWorld;
        startWorld = null;
        return w;
    }

    public boolean quitRequested() {
        return quitRequested;
    }

    public void frame(Gui gui, Input input) {
        switch (screen) {
            case TITLE -> title(gui);
            case WORLD_LIST -> worldList(gui, input);
            case CREATE -> createWorld(gui, input);
            case RENAME -> renameWorld(gui, input);
        }
    }

    // ------------------------------------------------------------------ title

    private void title(Gui gui) {
        gui.tiledBackground(Blocks.STONE.texSide, 64, 0.35f);
        float cx = gui.width / 2f;

        // logo: big blocky wordmark with layered shadow, on a soil strip
        float logoScale = Math.max(4f, Math.min(9f, gui.width / 130f));
        float logoY = gui.height * 0.16f;
        String logo = "BLOCKFORGE";
        float lw = gui.font().width(logo, logoScale);
        int[] logoTiles = {Blocks.GRASS.texTop, Blocks.DIRT.texSide, Blocks.STONE.texSide,
                Blocks.OAK_LOG.texSide, Blocks.COAL_ORE.texSide, Blocks.IRON_ORE.texSide,
                Blocks.GOLD_ORE.texSide, Blocks.DIAMOND_ORE.texSide, Blocks.GLOWLAMP.texSide,
                Blocks.OBSIDIAN.texSide};
        float band = logoScale * 2.2f;
        for (int i = 0; i < logoTiles.length; i++) {
            gui.tile(cx - lw / 2 + i * (lw / logoTiles.length), logoY - band - 6,
                    lw / logoTiles.length, band, logoTiles[i], 1f);
        }
        gui.text(logo, cx - lw / 2 + logoScale, logoY + logoScale, logoScale, 0.15f, 0.15f, 0.15f);
        gui.text(logo, cx - lw / 2, logoY, logoScale, 0.98f, 0.82f, 0.25f);
        gui.textCentered("AN ORIGINAL VOXEL SANDBOX", cx, logoY + gui.font().height(logoScale) + 14, 2f, 0.85f, 0.85f, 0.85f);

        float bw = 380, bh = 46, by = gui.height * 0.48f;
        if (gui.button("SINGLEPLAYER", cx - bw / 2, by, bw, bh)) {
            worlds = WorldStorage.listWorlds();
            if (worlds.isEmpty()) {
                resetCreateFields();
                screen = Screen.CREATE;
            } else {
                selected = 0;
                listScroll = 0;
                confirmDelete = false;
                screen = Screen.WORLD_LIST;
            }
        }
        if (gui.button("QUIT GAME", cx - bw / 2, by + bh + 14, bw, bh)) {
            quitRequested = true;
        }

        gui.text("BLOCKFORGE 1.0.0", 8, gui.height - 22, 2f, 0.7f, 0.7f, 0.7f);
        gui.text(Blocks.count() + " BLOCK TYPES", gui.width - 180, gui.height - 22, 2f, 0.7f, 0.7f, 0.7f);
    }

    // ------------------------------------------------------------- world list

    private void worldList(Gui gui, Input input) {
        gui.tiledBackground(Blocks.DIRT.texSide, 64, 0.3f);
        float cx = gui.width / 2f;
        gui.textCentered("SELECT WORLD", cx, 24, 3f, 1f, 1f, 1f);

        float listW = Math.min(640, gui.width - 80);
        float rowH = 56, gap = 6;
        float listX = cx - listW / 2;
        float listY = 70;
        int visible = Math.max(3, (int) ((gui.height - 210) / (rowH + gap)));

        if (gui.scroll != 0) {
            listScroll -= (int) Math.signum(gui.scroll);
        }
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, worlds.size() - visible)));

        for (int i = 0; i < visible; i++) {
            int idx = listScroll + i;
            if (idx >= worlds.size()) break;
            WorldInfo w = worlds.get(idx);
            float y = listY + i * (rowH + gap);
            boolean sel = idx == selected;
            boolean hov = gui.hover(listX, y, listW, rowH);
            gui.rect(listX, y, listW, rowH, 0.1f, 0.1f, 0.12f, hov ? 0.95f : 0.8f);
            gui.frame(listX, y, listW, rowH, 2,
                    sel ? 0.98f : 0.45f, sel ? 0.85f : 0.45f, sel ? 0.3f : 0.45f, 1f);
            gui.blockIcon(Blocks.GRASS, listX + 8, y + 8, rowH - 16);
            gui.text(w.name(), listX + rowH + 4, y + 10, 2.4f, 1f, 1f, 1f);
            String sub = "SEED " + w.seed()
                    + (w.lastPlayed() > 0 ? "   PLAYED " + DATE.format(new Date(w.lastPlayed())) : "");
            gui.text(sub, listX + rowH + 4, y + 34, 1.6f, 0.65f, 0.65f, 0.65f);
            if (hov && gui.clicked) {
                if (sel) {
                    play(w); // double-click style: click selected row again
                } else {
                    selected = idx;
                    confirmDelete = false;
                }
            }
        }
        if (worlds.size() > visible) {
            gui.textCentered("SCROLL FOR MORE (" + worlds.size() + " WORLDS)", cx,
                    listY + visible * (rowH + gap) + 2, 1.6f, 0.7f, 0.7f, 0.7f);
        }

        boolean has = selected >= 0 && selected < worlds.size();
        float bw = (listW - 3 * 10) / 4f, bh = 42;
        float by = gui.height - 2 * (bh + 12);
        if (gui.button("PLAY SELECTED", listX, by, bw, bh, has) && has) {
            play(worlds.get(selected));
        }
        if (gui.button("RENAME", listX + (bw + 10), by, bw, bh, has) && has) {
            renameField.setLength(0);
            renameField.append(worlds.get(selected).name());
            screen = Screen.RENAME;
        }
        if (gui.button(confirmDelete ? "REALLY DELETE?" : "DELETE", listX + 2 * (bw + 10), by, bw, bh, has) && has) {
            if (!confirmDelete) {
                confirmDelete = true;
            } else {
                WorldStorage.deleteWorld(worlds.get(selected));
                worlds = WorldStorage.listWorlds();
                selected = worlds.isEmpty() ? -1 : 0;
                confirmDelete = false;
            }
        }
        if (gui.button("NEW WORLD", listX + 3 * (bw + 10), by, bw, bh)) {
            resetCreateFields();
            screen = Screen.CREATE;
        }
        if (gui.button("BACK", cx - 190, by + bh + 12, 380, bh) || input.pressed(GLFW_KEY_ESCAPE)) {
            screen = Screen.TITLE;
        }
    }

    private void play(WorldInfo w) {
        w.props.setProperty("lastPlayed", String.valueOf(System.currentTimeMillis()));
        w.save();
        startWorld = w;
    }

    // ------------------------------------------------------------ create world

    private void resetCreateFields() {
        nameField.setLength(0);
        nameField.append("New World");
        seedField.setLength(0);
    }

    private void createWorld(Gui gui, Input input) {
        gui.tiledBackground(Blocks.OAK_LOG.texSide, 64, 0.3f);
        float cx = gui.width / 2f;
        gui.textCentered("CREATE NEW WORLD", cx, 36, 3f, 1f, 1f, 1f);

        float fw = Math.min(520, gui.width - 80), fh = 44;
        float x = cx - fw / 2;
        float y = 120;
        gui.text("WORLD NAME", x, y - 24, 2f, 0.85f, 0.85f, 0.85f);
        boolean enter1 = gui.textField(1, nameField, 32, x, y, fw, fh);

        y += 100;
        gui.text("SEED (LEAVE BLANK FOR RANDOM)", x, y - 24, 2f, 0.85f, 0.85f, 0.85f);
        boolean enter2 = gui.textField(2, seedField, 20, x, y, fw, fh);

        y += 70;
        if (gui.button("GAME MODE: " + (createCreative ? "CREATIVE" : "SURVIVAL"), x, y, fw, fh)) {
            createCreative = !createCreative;
        }

        y += fh + 20;
        if (gui.button("CREATE WORLD", x, y, fw, fh) || enter1 || enter2) {
            long seed = parseSeed(seedField.toString());
            WorldInfo info = WorldStorage.createWorld(nameField.toString(), seed,
                    createCreative ? "creative" : "survival");
            gui.unfocus();
            startWorld = info;
        }
        if (gui.button("BACK", x, y + fh + 14, fw, fh) || input.pressed(GLFW_KEY_ESCAPE)) {
            gui.unfocus();
            worlds = WorldStorage.listWorlds();
            screen = worlds.isEmpty() ? Screen.TITLE : Screen.WORLD_LIST;
        }
    }

    private static long parseSeed(String s) {
        s = s.strip();
        if (s.isEmpty()) return new Random().nextLong();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }

    // ------------------------------------------------------------ rename world

    private void renameWorld(Gui gui, Input input) {
        gui.tiledBackground(Blocks.DIRT.texSide, 64, 0.3f);
        float cx = gui.width / 2f;
        gui.textCentered("RENAME WORLD", cx, 36, 3f, 1f, 1f, 1f);

        float fw = Math.min(520, gui.width - 80), fh = 44;
        float x = cx - fw / 2;
        float y = 140;
        gui.text("NEW NAME", x, y - 24, 2f, 0.85f, 0.85f, 0.85f);
        boolean enter = gui.textField(3, renameField, 32, x, y, fw, fh);

        y += 90;
        boolean has = selected >= 0 && selected < worlds.size();
        if ((gui.button("SAVE", x, y, fw, fh) || enter) && has) {
            WorldStorage.renameWorld(worlds.get(selected), renameField.toString());
            worlds = WorldStorage.listWorlds();
            gui.unfocus();
            screen = Screen.WORLD_LIST;
        }
        if (gui.button("BACK", x, y + fh + 14, fw, fh) || input.pressed(GLFW_KEY_ESCAPE)) {
            gui.unfocus();
            screen = Screen.WORLD_LIST;
        }
    }
}
