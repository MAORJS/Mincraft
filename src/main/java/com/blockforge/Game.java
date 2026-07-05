package com.blockforge;

import com.blockforge.audio.SoundEngine;
import com.blockforge.gui.Gui;
import com.blockforge.gui.OptionsMenu;
import com.blockforge.player.Player;
import com.blockforge.render.Renderer;
import com.blockforge.save.WorldStorage;
import com.blockforge.save.WorldStorage.WorldInfo;
import com.blockforge.world.Block;
import com.blockforge.world.Blocks;
import com.blockforge.world.Raycast;
import com.blockforge.world.World;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * One play session in a loaded world. Handles simulation, streaming,
 * interaction, the HUD, and the in-game screens (pause menu, options,
 * creative inventory).
 */
public final class Game {

    private enum State {PLAYING, PAUSED, OPTIONS, INVENTORY}

    private static final float REACH = 5.5f;
    private static final float DAY_LENGTH_SECONDS = 600f;
    private static final float FIXED_DT = 1f / 120f;
    private static final float AUTOSAVE_SECONDS = 30f;

    private final long window;
    private final Input input;
    private final Gui gui;
    private final SoundEngine sound;
    private final Settings settings;
    private final OptionsMenu optionsMenu;

    private final WorldInfo info;
    private final World world;
    private final Player player;
    private final Renderer renderer;

    private State state = State.PLAYING;
    private final Block[] hotbar = new Block[9];
    private int hotbarSlot = 0;

    // inventory screen
    private int invScroll = 0;
    private Block cursorBlock = null;

    private float timeOfDay = 0.3f;
    private double accumulator = 0;
    private float breakCooldown = 0;
    private float placeCooldown = 0;
    private float autosaveTimer = 0;
    private boolean quitToTitle = false;

    private final Vector3f skyColor = new Vector3f();

    public Game(long window, Input input, Gui gui, SoundEngine sound, Settings settings,
                OptionsMenu optionsMenu, WorldInfo info, int atlasTexture) {
        this.window = window;
        this.input = input;
        this.gui = gui;
        this.sound = sound;
        this.settings = settings;
        this.optionsMenu = optionsMenu;
        this.info = info;

        world = new World(info.seed(), new WorldStorage(info.dir));
        player = new Player(world);
        renderer = new Renderer(settings.renderDistance, atlasTexture);

        defaultHotbar();
        loadPlayerState();

        world.ensureChunksAround(player.position.x, player.position.z, 3, 64);
        if (!info.props.containsKey("playerX")) {
            player.spawnAt((int) Math.floor(player.position.x), (int) Math.floor(player.position.z));
        }

        captureMouse(true);
    }

    private void defaultHotbar() {
        hotbar[0] = Blocks.STONE;
        hotbar[1] = Blocks.COBBLESTONE;
        hotbar[2] = Blocks.DIRT;
        hotbar[3] = Blocks.get(Blocks.OAK_LOG.id + 4); // oak planks sit after the logs
        hotbar[4] = Blocks.OAK_LOG;
        hotbar[5] = Blocks.SAND;
        hotbar[6] = Blocks.GLOWLAMP;
        hotbar[7] = Blocks.OAK_LEAVES;
        hotbar[8] = Blocks.WATER;
    }

    private void loadPlayerState() {
        var p = info.props;
        try {
            if (p.containsKey("playerX")) {
                player.position.set(
                        Float.parseFloat(p.getProperty("playerX")),
                        Float.parseFloat(p.getProperty("playerY")),
                        Float.parseFloat(p.getProperty("playerZ")));
                player.yaw = Float.parseFloat(p.getProperty("playerYaw", "0"));
                player.pitch = Float.parseFloat(p.getProperty("playerPitch", "0"));
                player.flying = Boolean.parseBoolean(p.getProperty("playerFlying", "false"));
            }
            timeOfDay = Float.parseFloat(p.getProperty("timeOfDay", "0.3"));
            String bar = p.getProperty("hotbar", "");
            if (!bar.isEmpty()) {
                String[] ids = bar.split(",");
                for (int i = 0; i < Math.min(9, ids.length); i++) {
                    int id = Integer.parseInt(ids[i]);
                    if (id > 0 && id < Blocks.count()) hotbar[i] = Blocks.get(id);
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void savePlayerState() {
        var p = info.props;
        p.setProperty("playerX", String.valueOf(player.position.x));
        p.setProperty("playerY", String.valueOf(player.position.y));
        p.setProperty("playerZ", String.valueOf(player.position.z));
        p.setProperty("playerYaw", String.valueOf(player.yaw));
        p.setProperty("playerPitch", String.valueOf(player.pitch));
        p.setProperty("playerFlying", String.valueOf(player.flying));
        p.setProperty("timeOfDay", String.valueOf(timeOfDay));
        p.setProperty("lastPlayed", String.valueOf(System.currentTimeMillis()));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            if (i > 0) bar.append(',');
            bar.append(hotbar[i] == null ? 0 : hotbar[i].id);
        }
        p.setProperty("hotbar", bar.toString());
        info.save();
    }

    public void saveAll() {
        savePlayerState();
        int n = world.saveModifiedChunks();
        if (n > 0) System.out.println("saved " + n + " chunks");
    }

    public boolean quitToTitleRequested() {
        return quitToTitle;
    }

    private void captureMouse(boolean capture) {
        glfwSetInputMode(window, GLFW_CURSOR, capture ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        input.resetMouse();
    }

    // ------------------------------------------------------------------ frame

    public void frame(double dt, int width, int height) {
        renderer.viewRadiusChunks = settings.renderDistance;
        renderer.fovDegrees = settings.fov;
        renderer.fogEnabled = settings.fog;

        handleStateKeys();

        world.ensureChunksAround(player.position.x, player.position.z, settings.renderDistance, 6);
        world.unloadFarChunks(player.position.x, player.position.z, settings.renderDistance + 2);
        renderer.updateMeshes(world, player, 6);

        boolean simulate = state == State.PLAYING || state == State.INVENTORY;
        if (simulate) {
            accumulator += Math.min(dt, 0.25);
            while (accumulator >= FIXED_DT) {
                tick(FIXED_DT);
                accumulator -= FIXED_DT;
            }
            if (settings.dayNightCycle) {
                timeOfDay = (timeOfDay + (float) dt / DAY_LENGTH_SECONDS) % 1f;
            }
            autosaveTimer += (float) dt;
            if (autosaveTimer >= AUTOSAVE_SECONDS) {
                autosaveTimer = 0;
                saveAll();
            }
        }

        float dayLight = dayLight();
        skyColor.set(0.45f, 0.68f, 0.95f).mul(Math.max(dayLight, 0.16f));

        Raycast.Hit hit = state == State.PLAYING
                ? Raycast.cast(world, player.eyePosition(), player.lookDir(), REACH)
                : null;
        if (state == State.PLAYING) {
            handleInteraction(hit, (float) dt);
        }

        renderer.render(world, player, width / (float) Math.max(1, height), dayLight, skyColor,
                settings.blockOutline ? hit : null);

        // 2D overlays
        gui.begin(input, width, height, glfwGetTime());
        switch (state) {
            case PLAYING -> drawHud(true);
            case INVENTORY -> {
                drawHud(false);
                drawInventory();
            }
            case PAUSED -> {
                drawHud(false);
                drawPauseMenu();
            }
            case OPTIONS -> {
                gui.rect(0, 0, width, height, 0f, 0f, 0f, 0.55f);
                if (optionsMenu.frame(gui)) {
                    state = State.PAUSED;
                }
            }
        }
        gui.end();
    }

    private void handleStateKeys() {
        if (input.pressed(GLFW_KEY_ESCAPE)) {
            switch (state) {
                case PLAYING -> {
                    state = State.PAUSED;
                    captureMouse(false);
                }
                case PAUSED -> {
                    state = State.PLAYING;
                    captureMouse(true);
                }
                case OPTIONS -> {
                    settings.save();
                    state = State.PAUSED;
                }
                case INVENTORY -> closeInventory();
            }
        }
        if (input.pressed(GLFW_KEY_E)) {
            if (state == State.PLAYING) {
                state = State.INVENTORY;
                captureMouse(false);
            } else if (state == State.INVENTORY) {
                closeInventory();
            }
        }
        if (state == State.PLAYING && input.pressed(GLFW_KEY_F)) {
            player.flying = !player.flying;
            player.velocity.y = 0;
        }
    }

    private void closeInventory() {
        cursorBlock = null;
        state = State.PLAYING;
        captureMouse(true);
    }

    private void tick(float dt) {
        float forward = 0, strafe = 0;
        boolean jump = false, sneak = false, sprint = false;
        if (state == State.PLAYING) {
            if (input.down(GLFW_KEY_W)) forward += 1;
            if (input.down(GLFW_KEY_S)) forward -= 1;
            if (input.down(GLFW_KEY_D)) strafe += 1;
            if (input.down(GLFW_KEY_A)) strafe -= 1;
            jump = input.down(GLFW_KEY_SPACE);
            sneak = input.down(GLFW_KEY_LEFT_SHIFT);
            sprint = input.down(GLFW_KEY_LEFT_CONTROL);
        }
        player.tick(dt, forward, strafe, jump, sneak, sprint);
    }

    private void handleInteraction(Raycast.Hit hit, float dt) {
        float sens = 0.11f;
        player.turn((float) input.mouseDX * sens, (float) input.mouseDY * sens);

        for (int i = 0; i < 9; i++) {
            if (input.pressed(GLFW_KEY_1 + i)) hotbarSlot = i;
        }
        if (input.scrollY != 0) {
            hotbarSlot = Math.floorMod(hotbarSlot - (int) Math.signum(input.scrollY), 9);
        }

        breakCooldown = Math.max(0, breakCooldown - dt);
        placeCooldown = Math.max(0, placeCooldown - dt);
        if (hit == null) return;

        if (input.mouseDown(GLFW_MOUSE_BUTTON_LEFT)
                && (breakCooldown == 0 || input.mouseClicked(GLFW_MOUSE_BUTTON_LEFT))) {
            if (world.getBlockId(hit.x, hit.y, hit.z) != Blocks.BEDROCK.id) {
                world.setBlock(hit.x, hit.y, hit.z, 0);
                sound.play(SoundEngine.BREAK);
            }
            breakCooldown = 0.22f;
        }

        if (input.mouseDown(GLFW_MOUSE_BUTTON_RIGHT)
                && (placeCooldown == 0 || input.mouseClicked(GLFW_MOUSE_BUTTON_RIGHT))) {
            Block held = hotbar[hotbarSlot];
            if (held != null && !held.isAir()
                    && world.getBlockId(hit.px, hit.py, hit.pz) == 0
                    && (!held.solid || !player.intersectsBlock(hit.px, hit.py, hit.pz))) {
                world.setBlock(hit.px, hit.py, hit.pz, held.id);
                sound.play(SoundEngine.PLACE);
            }
            placeCooldown = 0.22f;
        }

        if (input.mouseClicked(GLFW_MOUSE_BUTTON_MIDDLE)) {
            int id = world.getBlockId(hit.x, hit.y, hit.z);
            if (id != 0) hotbar[hotbarSlot] = Blocks.get(id);
        }
    }

    // -------------------------------------------------------------------- hud

    private void drawHud(boolean crosshair) {
        float cx = gui.width / 2f, cy = gui.height / 2f;
        if (crosshair) {
            gui.rect(cx - 10, cy - 1.5f, 20, 3, 0.95f, 0.95f, 0.95f, 0.75f);
            gui.rect(cx - 1.5f, cy - 10, 3, 20, 0.95f, 0.95f, 0.95f, 0.75f);
        }

        float slot = 52, pad = 5;
        float barW = 9 * slot + 10 * pad;
        float x0 = cx - barW / 2f;
        float y0 = gui.height - slot - 18;
        gui.rect(x0 - 2, y0 - pad - 2, barW + 4, slot + pad * 2 + 4, 0.08f, 0.08f, 0.08f, 0.6f);
        for (int i = 0; i < 9; i++) {
            float sx = x0 + pad + i * (slot + pad);
            if (i == hotbarSlot) {
                gui.rect(sx - 4, y0 - 4, slot + 8, slot + 8, 0.98f, 0.98f, 0.98f, 0.85f);
            }
            gui.rect(sx, y0, slot, slot, 0.25f, 0.25f, 0.28f, 0.85f);
            if (hotbar[i] != null && !hotbar[i].isAir()) {
                gui.blockIcon(hotbar[i], sx + 6, y0 + 6, slot - 12);
            }
        }
        Block held = hotbar[hotbarSlot];
        if (held != null && !held.isAir()) {
            gui.textCentered(held.name, cx, y0 - 26, 2f, 1f, 1f, 1f);
        }
    }

    // -------------------------------------------------------------- inventory

    private static final int INV_COLS = 9;
    private static final int INV_ROWS = 6;

    private void drawInventory() {
        var all = Blocks.all();
        int total = all.size() - 1; // skip air
        int totalRows = (total + INV_COLS - 1) / INV_COLS;

        float slot = 46, pad = 6;
        float panelW = INV_COLS * (slot + pad) + pad;
        float panelH = INV_ROWS * (slot + pad) + pad + 60;
        float px = gui.width / 2f - panelW / 2f;
        float py = Math.max(20, gui.height / 2f - panelH / 2f - 40);

        gui.rect(0, 0, gui.width, gui.height, 0f, 0f, 0f, 0.45f);
        gui.rect(px - 6, py - 34, panelW + 12, panelH + 46, 0.12f, 0.12f, 0.14f, 0.96f);
        gui.frame(px - 6, py - 34, panelW + 12, panelH + 46, 2, 0.6f, 0.6f, 0.6f, 1f);
        gui.text("INVENTORY (" + total + " BLOCKS)", px + 2, py - 26, 2f, 1f, 1f, 1f);
        gui.text("SCROLL TO BROWSE - RIGHT CLICK = TO HOTBAR", px + panelW - 390, py - 26, 1.6f, 0.7f, 0.7f, 0.7f);

        if (gui.scroll != 0) {
            invScroll -= (int) Math.signum(gui.scroll);
        }
        invScroll = Math.max(0, Math.min(invScroll, Math.max(0, totalRows - INV_ROWS)));

        Block hovered = null;
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int idx = (invScroll + row) * INV_COLS + col + 1; // +1 skips air
                if (idx >= all.size()) break;
                Block b = all.get(idx);
                float sx = px + pad + col * (slot + pad);
                float sy = py + pad + row * (slot + pad);
                boolean hov = gui.hover(sx, sy, slot, slot);
                gui.rect(sx, sy, slot, slot, hov ? 0.35f : 0.22f, hov ? 0.35f : 0.22f, hov ? 0.38f : 0.25f, 1f);
                gui.blockIcon(b, sx + 4, sy + 4, slot - 8);
                if (hov) {
                    hovered = b;
                    if (gui.clicked) {
                        cursorBlock = b;
                        sound.play(SoundEngine.CLICK);
                    }
                    if (gui.rightClicked) {
                        hotbar[hotbarSlot] = b;
                        sound.play(SoundEngine.CLICK);
                    }
                }
            }
        }

        // scrollbar
        if (totalRows > INV_ROWS) {
            float trackH = INV_ROWS * (slot + pad);
            float barH = Math.max(24, trackH * INV_ROWS / totalRows);
            float barY = py + pad + (trackH - barH) * invScroll / Math.max(1, totalRows - INV_ROWS);
            gui.rect(px + panelW + 2, py + pad, 6, trackH, 0.2f, 0.2f, 0.2f, 1f);
            gui.rect(px + panelW + 2, barY, 6, barH, 0.7f, 0.7f, 0.7f, 1f);
        }

        // hotbar row inside the inventory panel
        float hy = py + INV_ROWS * (slot + pad) + pad + 16;
        gui.text("HOTBAR", px + 2, hy - 4, 1.6f, 0.75f, 0.75f, 0.75f);
        for (int i = 0; i < 9; i++) {
            float sx = px + pad + i * (slot + pad);
            float sy = hy + 12;
            boolean hov = gui.hover(sx, sy, slot, slot);
            if (i == hotbarSlot) {
                gui.frame(sx - 3, sy - 3, slot + 6, slot + 6, 2, 0.98f, 0.85f, 0.3f, 1f);
            }
            gui.rect(sx, sy, slot, slot, hov ? 0.4f : 0.28f, hov ? 0.4f : 0.28f, hov ? 0.42f : 0.3f, 1f);
            if (hotbar[i] != null && !hotbar[i].isAir()) {
                gui.blockIcon(hotbar[i], sx + 4, sy + 4, slot - 8);
            }
            if (hov && gui.clicked) {
                if (cursorBlock != null) {
                    hotbar[i] = cursorBlock;
                    cursorBlock = null;
                } else {
                    hotbarSlot = i;
                }
                sound.play(SoundEngine.CLICK);
            }
        }

        // block on cursor + tooltip
        if (cursorBlock != null) {
            gui.blockIcon(cursorBlock, gui.mouseX - 18, gui.mouseY - 18, 36);
        } else if (hovered != null) {
            float tw = gui.font().width(hovered.name, 2f) + 12;
            float tx = Math.min(gui.mouseX + 14, gui.width - tw - 4);
            gui.rect(tx - 6, gui.mouseY + 8, tw, 26, 0.05f, 0.05f, 0.05f, 0.9f);
            gui.text(hovered.name, tx, gui.mouseY + 13, 2f, 1f, 1f, 0.85f);
        }
    }

    // ------------------------------------------------------------- pause menu

    private void drawPauseMenu() {
        gui.rect(0, 0, gui.width, gui.height, 0f, 0f, 0f, 0.55f);
        float cx = gui.width / 2f;
        float bw = Math.min(440, gui.width - 60), bh = 44, gap = 14;
        float y = gui.height * 0.28f;

        gui.textCentered("GAME PAUSED", cx, y - 60, 3f, 1f, 1f, 1f);
        gui.textCentered(info.name(), cx, y - 30, 2f, 0.75f, 0.75f, 0.75f);

        if (gui.button("BACK TO GAME", cx - bw / 2, y, bw, bh)) {
            state = State.PLAYING;
            captureMouse(true);
        }
        y += bh + gap;
        if (gui.button("OPTIONS...", cx - bw / 2, y, bw, bh)) {
            optionsMenu.open();
            state = State.OPTIONS;
        }
        y += bh + gap;
        if (gui.button("SAVE AND QUIT TO TITLE", cx - bw / 2, y, bw, bh)) {
            saveAll();
            quitToTitle = true;
        }
    }

    private float dayLight() {
        float angle = (float) (timeOfDay * Math.PI * 2);
        float raw = (float) Math.sin(angle);
        float floor = 0.10f + settings.brightness * 0.25f;
        return Math.max(floor, Math.min(1f, 0.5f + raw * 0.7f));
    }

    public String debugTitle(double fps) {
        Block held = hotbar[hotbarSlot];
        return String.format("BlockForge — %s | %.0f fps | XYZ %.1f / %.1f / %.1f | %s%s",
                info.name(), fps, player.position.x, player.position.y, player.position.z,
                held == null ? "empty" : held.name,
                player.flying ? " | flying" : "");
    }

    public void delete() {
        renderer.delete();
    }
}
