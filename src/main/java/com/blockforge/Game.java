package com.blockforge;

import com.blockforge.player.Player;
import com.blockforge.render.Hud;
import com.blockforge.render.Renderer;
import com.blockforge.world.Block;
import com.blockforge.world.Blocks;
import com.blockforge.world.Raycast;
import com.blockforge.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Ties everything together: world streaming, fixed-timestep physics,
 * block interaction, the day/night cycle and the hotbar/palette.
 */
public final class Game {

    private static final int VIEW_RADIUS = 8;       // chunks
    private static final float REACH = 5.5f;
    private static final float DAY_LENGTH_SECONDS = 600f;
    private static final float FIXED_DT = 1f / 120f;

    private final long window;
    private final Input input;
    private final World world;
    private final Player player;
    private final Renderer renderer;
    private final Hud hud;

    /** All placeable blocks; the hotbar is a 9-wide window into this list. */
    private final List<Block> palette = new ArrayList<>();
    private int paletteOffset = 0;
    private int hotbarSlot = 0;

    private float timeOfDay = 0.3f; // 0..1, 0.25 = noon-ish rise
    private double accumulator = 0;
    private boolean mouseCaptured = true;
    private float breakCooldown = 0;
    private float placeCooldown = 0;

    private final Vector3f skyColor = new Vector3f();

    public Game(long window, Input input, long seed) {
        this.window = window;
        this.input = input;
        world = new World(seed);
        player = new Player(world);
        renderer = new Renderer(VIEW_RADIUS);
        hud = new Hud();

        for (Block b : Blocks.all()) {
            if (!b.isAir()) palette.add(b);
        }

        // generate spawn area synchronously, then drop the player in
        world.ensureChunksAround(0.5, 0.5, 3, 64);
        player.spawnAt(0, 0);
        System.out.println("BlockForge: " + Blocks.count() + " block types registered.");
    }

    public void frame(double dt, int width, int height) {
        handleGlobalKeys();

        // world streaming, budgeted so frames stay smooth
        world.ensureChunksAround(player.position.x, player.position.z, VIEW_RADIUS, 6);
        world.unloadFarChunks(player.position.x, player.position.z, VIEW_RADIUS + 2);
        renderer.updateMeshes(world, player, 6);

        // fixed-timestep simulation
        accumulator += Math.min(dt, 0.25);
        while (accumulator >= FIXED_DT) {
            tick(FIXED_DT);
            accumulator -= FIXED_DT;
        }

        timeOfDay = (timeOfDay + (float) dt / DAY_LENGTH_SECONDS) % 1f;
        float dayLight = dayLight();
        skyColor.set(0.45f, 0.68f, 0.95f).mul(Math.max(dayLight, 0.16f));

        Raycast.Hit hit = mouseCaptured
                ? Raycast.cast(world, player.eyePosition(), player.lookDir(), REACH)
                : null;
        handleInteraction(hit, (float) dt);

        renderer.render(world, player, width / (float) Math.max(1, height), dayLight, skyColor, hit);
        hud.render(width, height, hotbar(), hotbarSlot, renderer.atlasTexture);

        input.endFrame();
    }

    private void tick(float dt) {
        float forward = 0, strafe = 0;
        boolean jump = false, sneak = false, sprint = false;
        if (mouseCaptured) {
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

    private void handleGlobalKeys() {
        if (input.pressed(GLFW_KEY_ESCAPE)) {
            mouseCaptured = !mouseCaptured;
            glfwSetInputMode(window, GLFW_CURSOR,
                    mouseCaptured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
            input.resetMouse();
        }
        if (input.pressed(GLFW_KEY_F)) {
            player.flying = !player.flying;
            player.velocity.y = 0;
        }

        if (mouseCaptured) {
            float sens = 0.11f;
            player.turn((float) input.mouseDX * sens, (float) input.mouseDY * sens);

            for (int i = 0; i < 9; i++) {
                if (input.pressed(GLFW_KEY_1 + i)) hotbarSlot = i;
            }
            if (input.scrollY != 0) {
                hotbarSlot = Math.floorMod(hotbarSlot - (int) Math.signum(input.scrollY), 9);
            }
            // page through the full palette (9 at a time)
            int pages = (palette.size() + 8) / 9;
            if (input.pressed(GLFW_KEY_X) || input.pressed(GLFW_KEY_PAGE_DOWN)) {
                paletteOffset = (paletteOffset + 9) % (pages * 9);
            }
            if (input.pressed(GLFW_KEY_Z) || input.pressed(GLFW_KEY_PAGE_UP)) {
                paletteOffset = Math.floorMod(paletteOffset - 9, pages * 9);
            }
        }
    }

    private void handleInteraction(Raycast.Hit hit, float dt) {
        breakCooldown = Math.max(0, breakCooldown - dt);
        placeCooldown = Math.max(0, placeCooldown - dt);
        if (!mouseCaptured || hit == null) return;

        if (input.mouseDown(GLFW_MOUSE_BUTTON_LEFT)
                && (breakCooldown == 0 || input.mouseClicked(GLFW_MOUSE_BUTTON_LEFT))) {
            if (world.getBlockId(hit.x, hit.y, hit.z) != Blocks.BEDROCK.id) {
                world.setBlock(hit.x, hit.y, hit.z, 0);
            }
            breakCooldown = 0.22f;
        }

        if (input.mouseDown(GLFW_MOUSE_BUTTON_RIGHT)
                && (placeCooldown == 0 || input.mouseClicked(GLFW_MOUSE_BUTTON_RIGHT))) {
            Block held = hotbar().get(hotbarSlot);
            if (held != null && !held.isAir()
                    && world.getBlockId(hit.px, hit.py, hit.pz) == 0
                    && (!held.solid || !player.intersectsBlock(hit.px, hit.py, hit.pz))) {
                world.setBlock(hit.px, hit.py, hit.pz, held.id);
            }
            placeCooldown = 0.22f;
        }

        // middle click: pick the targeted block into the current slot's page
        if (input.mouseClicked(GLFW_MOUSE_BUTTON_MIDDLE)) {
            int id = world.getBlockId(hit.x, hit.y, hit.z);
            int idx = palette.indexOf(Blocks.get(id));
            if (idx >= 0) {
                paletteOffset = (idx / 9) * 9;
                hotbarSlot = idx % 9;
            }
        }
    }

    private List<Block> hotbar() {
        List<Block> bar = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            int idx = paletteOffset + i;
            bar.add(idx < palette.size() ? palette.get(idx) : null);
        }
        return bar;
    }

    private float dayLight() {
        // smooth day/night curve: 1.0 at midday, 0.15 at midnight
        float angle = (float) (timeOfDay * Math.PI * 2);
        float raw = (float) Math.sin(angle);
        return Math.max(0.15f, Math.min(1f, 0.5f + raw * 0.7f));
    }

    public String debugTitle(double fps) {
        Block held = hotbar().get(hotbarSlot);
        return String.format("BlockForge — %.0f fps | XYZ %.1f / %.1f / %.1f | %s%s | blocks: %d",
                fps, player.position.x, player.position.y, player.position.z,
                held == null ? "empty" : held.name,
                player.flying ? " | flying" : "",
                Blocks.count());
    }

    public void delete() {
        renderer.delete();
        hud.delete();
    }
}
