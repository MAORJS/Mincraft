package com.blockforge;

import com.blockforge.audio.SoundEngine;
import com.blockforge.entity.Arrow;
import com.blockforge.entity.Entity;
import com.blockforge.entity.ItemEntity;
import com.blockforge.entity.Mob;
import com.blockforge.entity.MobType;
import com.blockforge.gui.Gui;
import com.blockforge.gui.OptionsMenu;
import com.blockforge.item.Inventory;
import com.blockforge.item.Item;
import com.blockforge.item.ItemStack;
import com.blockforge.item.Items;
import com.blockforge.item.Recipes;
import com.blockforge.player.Player;
import com.blockforge.render.Renderer;
import com.blockforge.save.WorldStorage;
import com.blockforge.save.WorldStorage.WorldInfo;
import com.blockforge.survival.BlockProps;
import com.blockforge.world.Block;
import com.blockforge.world.BlockEntityStore;
import com.blockforge.world.Blocks;
import com.blockforge.world.Chunk;
import com.blockforge.world.Raycast;
import com.blockforge.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;

/**
 * One play session in a loaded world: simulation, streaming, interaction,
 * entities, survival systems (health, hunger, mining, crafting, containers),
 * the HUD, and every in-game screen.
 */
public final class Game {

    private enum State {PLAYING, PAUSED, OPTIONS, INVENTORY, WORKBENCH, FURNACE, CHEST, DEAD}

    private static final float REACH = 5.0f;
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
    private final WorldStorage storage;
    private final World world;
    private final Player player;
    private final Renderer renderer;
    private final Random rng = new Random();

    public final boolean creative;

    private State state = State.PLAYING;
    private final Inventory inventory = new Inventory();
    private int hotbarSlot = 0;
    private ItemStack cursor;                    // stack on the mouse in menus

    // crafting grids (transient)
    private final ItemStack[] invCraft = new ItemStack[4];
    private final ItemStack[] benchCraft = new ItemStack[9];
    // open container
    private BlockEntityStore.Furnace openFurnace;
    private int furnaceX, furnaceY, furnaceZ;
    private BlockEntityStore.Chest openChest;

    // creative browser
    private int creativeScroll = 0;

    // mining
    private int breakX, breakY, breakZ = Integer.MIN_VALUE;
    private float breakProgress;
    private float breakTotal;

    private float timeOfDay = 0.3f;
    private double accumulator = 0;
    private float placeCooldown, attackCooldown, eatCooldown, spawnTimer, autosaveTimer;
    private float hurtFlash;
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
        this.creative = "creative".equals(info.props.getProperty("gamemode", "survival"));

        storage = new WorldStorage(info.dir);
        world = new World(info.seed(), storage);
        player = new Player(world);
        renderer = new Renderer(settings.renderDistance, atlasTexture);
        BlockProps.init();
        Recipes.init();

        loadState();
        world.ensureChunksAround(player.position.x, player.position.z, 3, 64);
        if (!info.props.containsKey("playerX")) {
            player.spawnAt(0, 0);
            if (creative) defaultCreativeHotbar();
        }
        storage.loadExtras(world, inventory, info.props);
        captureMouse(true);
    }

    private void defaultCreativeHotbar() {
        Block[] defaults = {Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIRT,
                Blocks.get(Blocks.OAK_LOG.id + 4), Blocks.OAK_LOG, Blocks.SAND,
                Blocks.GLOWLAMP, Blocks.OAK_LEAVES, Blocks.WATER};
        for (int i = 0; i < 9; i++) {
            inventory.slots[i] = new ItemStack(Items.forBlock(defaults[i]), 64);
        }
    }

    private void loadState() {
        var p = info.props;
        try {
            if (p.containsKey("playerX")) {
                player.position.set(
                        Float.parseFloat(p.getProperty("playerX")),
                        Float.parseFloat(p.getProperty("playerY")),
                        Float.parseFloat(p.getProperty("playerZ")));
                player.yaw = Float.parseFloat(p.getProperty("playerYaw", "0"));
                player.pitch = Float.parseFloat(p.getProperty("playerPitch", "0"));
                player.flying = creative && Boolean.parseBoolean(p.getProperty("playerFlying", "false"));
                player.health = Float.parseFloat(p.getProperty("playerHealth", "20"));
                player.hunger = Float.parseFloat(p.getProperty("playerHunger", "20"));
            }
            timeOfDay = Float.parseFloat(p.getProperty("timeOfDay", "0.3"));
        } catch (NumberFormatException ignored) {
        }
    }

    public void saveAll() {
        var p = info.props;
        p.setProperty("playerX", String.valueOf(player.position.x));
        p.setProperty("playerY", String.valueOf(player.position.y));
        p.setProperty("playerZ", String.valueOf(player.position.z));
        p.setProperty("playerYaw", String.valueOf(player.yaw));
        p.setProperty("playerPitch", String.valueOf(player.pitch));
        p.setProperty("playerFlying", String.valueOf(player.flying));
        p.setProperty("playerHealth", String.valueOf(player.health));
        p.setProperty("playerHunger", String.valueOf(player.hunger));
        p.setProperty("timeOfDay", String.valueOf(timeOfDay));
        p.setProperty("lastPlayed", String.valueOf(System.currentTimeMillis()));
        info.save();
        world.saveModifiedChunks();
        storage.saveExtras(world, inventory, info.props);
        info.save();
    }

    public boolean quitToTitleRequested() {
        return quitToTitle;
    }

    private void captureMouse(boolean capture) {
        glfwSetInputMode(window, GLFW_CURSOR, capture ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        input.resetMouse();
    }

    private ItemStack held() {
        return inventory.slots[hotbarSlot];
    }

    // ================================================================= frame

    public void frame(double dt, int width, int height) {
        renderer.viewRadiusChunks = settings.renderDistance;
        renderer.fovDegrees = settings.fov;
        renderer.fogEnabled = settings.fog;

        handleStateKeys();

        world.ensureChunksAround(player.position.x, player.position.z, settings.renderDistance, 6);
        world.unloadFarChunks(player.position.x, player.position.z, settings.renderDistance + 2);
        renderer.updateMeshes(world, player, 6);

        boolean paused = state == State.PAUSED || state == State.OPTIONS;
        if (!paused && !player.isDead) {
            accumulator += Math.min(dt, 0.25);
            while (accumulator >= FIXED_DT) {
                tick(FIXED_DT);
                accumulator -= FIXED_DT;
            }
            if (settings.dayNightCycle) {
                timeOfDay = (timeOfDay + (float) dt / DAY_LENGTH_SECONDS) % 1f;
            }
            tickWorld((float) dt);
            autosaveTimer += (float) dt;
            if (autosaveTimer >= AUTOSAVE_SECONDS) {
                autosaveTimer = 0;
                saveAll();
            }
        }
        hurtFlash = Math.max(0, hurtFlash - (float) dt);
        if (player.damagedThisTick) {
            player.damagedThisTick = false;
            hurtFlash = 0.4f;
            sound.play(SoundEngine.HURT);
            if (player.isDead && state != State.DEAD) {
                onDeath();
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
                settings.blockOutline ? hit : null, world.entities, glfwGetTime());

        gui.begin(input, width, height, glfwGetTime());
        switch (state) {
            case PLAYING -> drawHud(true, hit);
            case INVENTORY -> {
                drawHud(false, null);
                if (creative) drawCreativeInventory();
                else drawSurvivalInventory();
            }
            case WORKBENCH -> {
                drawHud(false, null);
                drawWorkbench();
            }
            case FURNACE -> {
                drawHud(false, null);
                drawFurnace();
            }
            case CHEST -> {
                drawHud(false, null);
                drawChest();
            }
            case PAUSED -> {
                drawHud(false, null);
                drawPauseMenu();
            }
            case OPTIONS -> {
                gui.rect(0, 0, width, height, 0f, 0f, 0f, 0.55f);
                if (optionsMenu.frame(gui)) state = State.PAUSED;
            }
            case DEAD -> drawDeathScreen();
        }
        if (hurtFlash > 0) {
            gui.rect(0, 0, width, height, 0.8f, 0.05f, 0.05f, hurtFlash * 0.6f);
        }
        gui.end();
    }

    // =========================================================== state keys

    private boolean inContainer() {
        return state == State.INVENTORY || state == State.WORKBENCH
                || state == State.FURNACE || state == State.CHEST;
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
                case DEAD -> {
                }
                default -> closeContainer();
            }
        }
        if (input.pressed(GLFW_KEY_E)) {
            if (state == State.PLAYING) {
                state = State.INVENTORY;
                captureMouse(false);
            } else if (inContainer()) {
                closeContainer();
            }
        }
        if (creative && state == State.PLAYING && input.pressed(GLFW_KEY_F)) {
            player.flying = !player.flying;
            player.velocity.y = 0;
        }
        if (state == State.PLAYING && input.pressed(GLFW_KEY_Q)) {
            dropHeldItem(false);
        }
    }

    private void closeContainer() {
        // return crafting grids and the cursor stack
        returnGrid(invCraft);
        returnGrid(benchCraft);
        if (cursor != null) {
            giveOrDrop(cursor);
            cursor = null;
        }
        openFurnace = null;
        openChest = null;
        state = State.PLAYING;
        captureMouse(true);
    }

    private void returnGrid(ItemStack[] grid) {
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] != null) {
                giveOrDrop(grid[i]);
                grid[i] = null;
            }
        }
    }

    private void giveOrDrop(ItemStack stack) {
        int leftover = inventory.add(stack);
        if (leftover > 0) {
            stack.count = leftover;
            dropStack(stack, player.position.x, player.position.y + 1, player.position.z, 0, 2, 0);
        }
    }

    // ================================================================== tick

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
            sprint = input.down(GLFW_KEY_LEFT_CONTROL) && (creative || player.hunger > 6);
        }
        player.tick(dt, forward, strafe, jump, sneak, sprint);
        if (!creative) {
            player.survivalTick(dt, sprint && forward != 0);
        }
    }

    /** Entities, block entities, spawning — runs on frame time. */
    private void tickWorld(float dt) {
        // entities
        List<Entity> entities = world.entities;
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e instanceof Mob mob) {
                mob.playerPos = player.position;
                mob.playerDead = player.isDead || creative;
                mob.tick(dt);
                if (mob.pendingArrow != null) {
                    entities.add(mob.pendingArrow);
                    mob.pendingArrow = null;
                    sound.play(SoundEngine.SHOOT, 0.7f);
                }
                if (!creative && mob.tryMeleeAttack()) {
                    Vector3f d = new Vector3f(player.position).sub(mob.position);
                    float len = Math.max(0.01f, (float) Math.sqrt(d.x * d.x + d.z * d.z));
                    player.damage(mob.type.attackDamage, d.x / len * 5f, d.z / len * 5f);
                }
                // monsters burn in daylight when the sky is overhead
                if (mob.type.hostile && dayLight() > 0.65f && skyVisible(mob.position)) {
                    mob.burning = true;
                    mob.burnTimer += dt;
                    if (mob.burnTimer > 0.8f) {
                        mob.burnTimer = 0;
                        if (mob.damage(3, 0, 0)) dropMobLoot(mob);
                    }
                } else {
                    mob.burning = false;
                    mob.burnTimer = 0;
                }
                // despawn far hostiles
                if (mob.type.hostile && mob.distanceTo(player.position) > 60) mob.dead = true;
            } else if (e instanceof ItemEntity item) {
                item.tick(dt);
                if (!player.isDead) {
                    item.attractTo(player.position, dt);
                    if (item.pickupDelay <= 0
                            && item.distanceTo(new Vector3f(player.position.x,
                            player.position.y + 0.8f, player.position.z)) < ItemEntity.PICKUP_RADIUS) {
                        int leftover = inventory.add(item.stack);
                        if (leftover <= 0) {
                            item.dead = true;
                            sound.play(SoundEngine.POP, 0.8f);
                        } else {
                            item.stack.count = leftover;
                        }
                    }
                }
                // merge with nearby drops
                for (Entity o : entities) {
                    if (o instanceof ItemEntity other && item.canMerge(other)) {
                        item.stack.count += other.stack.count;
                        other.dead = true;
                    }
                }
            } else if (e instanceof Arrow arrow) {
                arrow.tick(dt);
                if (!arrow.stuck) {
                    if (arrow.fromPlayer) {
                        for (Entity o : entities) {
                            if (o instanceof Mob mob
                                    && mob.intersects(arrow.position, arrow.width, arrow.height, 0.1f)) {
                                if (mob.damage(arrow.damage, arrow.velocity.x * 0.06f, arrow.velocity.z * 0.06f)) {
                                    dropMobLoot(mob);
                                }
                                arrow.dead = true;
                                break;
                            }
                        }
                    } else if (!creative && !player.isDead
                            && player.intersectsBlock((int) Math.floor(arrow.position.x),
                            (int) Math.floor(arrow.position.y), (int) Math.floor(arrow.position.z))) {
                        player.damage(arrow.damage, arrow.velocity.x * 0.15f, arrow.velocity.z * 0.15f);
                        arrow.dead = true;
                    }
                }
            }
        }
        entities.removeIf(e -> e.dead);

        // furnaces smelt in the background; swap lit/unlit stove blocks
        var changed = world.blockEntities.tickFurnaces(dt);
        for (var entry : changed.entrySet()) {
            long k = entry.getKey();
            int x = (int) (k >> 38);
            int z = (int) ((k << 26) >> 38);
            int y = (int) (k & 0xFFF);
            int id = world.getBlockId(x, y, z);
            if (entry.getValue() && id == Blocks.STOVE.id) {
                world.setBlock(x, y, z, Blocks.STOVE_LIT.id);
            } else if (!entry.getValue() && id == Blocks.STOVE_LIT.id) {
                world.setBlock(x, y, z, Blocks.STOVE.id);
            }
        }

        // spawner blocks
        if (!creative) {
            for (var entry : world.blockEntities.spawners.entrySet()) {
                long k = entry.getKey();
                int x = (int) (k >> 38);
                int z = (int) ((k << 26) >> 38);
                int y = (int) (k & 0xFFF);
                if (world.getBlockId(x, y, z) != Blocks.SPAWNER.id) continue;
                float dx = x + 0.5f - player.position.x;
                float dy = y + 0.5f - player.position.y;
                float dz = z + 0.5f - player.position.z;
                if (dx * dx + dy * dy + dz * dz > 12 * 12) continue;
                var sp = entry.getValue();
                sp.cooldown -= dt;
                if (sp.cooldown <= 0) {
                    sp.cooldown = 4f + rng.nextFloat() * 4f;
                    if (countMobsNear(x, y, z, 9) < 6) {
                        int sx = x + rng.nextInt(5) - 2;
                        int sz = z + rng.nextInt(5) - 2;
                        int sy = y + rng.nextInt(2) - 1;
                        if (canStand(sx, sy, sz, sp.mobType)) {
                            world.entities.add(new Mob(world, sp.mobType, sx + 0.5f, sy, sz + 0.5f));
                        }
                    }
                }
            }
        }

        // natural spawning
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnTimer = 1.5f;
            if (!creative) trySpawnMobs();
        }
    }

    private int countMobsNear(int x, int y, int z, float r) {
        int n = 0;
        for (Entity e : world.entities) {
            if (e instanceof Mob) {
                float dx = e.position.x - x, dy = e.position.y - y, dz = e.position.z - z;
                if (dx * dx + dy * dy + dz * dz < r * r) n++;
            }
        }
        return n;
    }

    private boolean canStand(int x, int y, int z, MobType type) {
        if (y < 1 || y + Math.ceil(type.height) >= Chunk.SY) return false;
        if (!Blocks.get(world.getBlockId(x, y - 1, z)).solid) return false;
        for (int i = 0; i <= (int) Math.ceil(type.height); i++) {
            Block b = Blocks.get(world.getBlockId(x, y + i, z));
            if (b.solid || b.liquid) return false;
        }
        return true;
    }

    private boolean skyVisible(Vector3f pos) {
        int x = (int) Math.floor(pos.x), z = (int) Math.floor(pos.z);
        for (int y = (int) Math.ceil(pos.y + 1); y < Chunk.SY; y++) {
            if (Blocks.get(world.getBlockId(x, y, z)).opaque) return false;
        }
        return true;
    }

    /** Max mobs of one class allowed within {@code AREA_RADIUS} of a spawn point. */
    private static final int AREA_CAP = 5;
    private static final float AREA_RADIUS = 20f;

    private int mobsNearOfClass(int x, int y, int z, boolean hostile) {
        int n = 0;
        for (Entity e : world.entities) {
            if (e instanceof Mob m && m.type.hostile == hostile) {
                float dx = e.position.x - x, dy = e.position.y - y, dz = e.position.z - z;
                if (dx * dx + dy * dy + dz * dz < AREA_RADIUS * AREA_RADIUS) n++;
            }
        }
        return n;
    }

    private void trySpawnMobs() {
        boolean night = dayLight() < 0.4f;
        int hostiles = 0, passives = 0;
        for (Entity e : world.entities) {
            if (e instanceof Mob m) {
                if (m.type.hostile) hostiles++;
                else passives++;
            }
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = 16 + rng.nextDouble() * 22;
            int x = (int) Math.floor(player.position.x + Math.sin(angle) * dist);
            int z = (int) Math.floor(player.position.z + Math.cos(angle) * dist);

            if (rng.nextBoolean()) {
                // surface spawn
                int y = world.surfaceHeight(x, z) + 1;
                boolean dark = night || !skyVisible(new Vector3f(x + 0.5f, y, z + 0.5f));
                if (dark && hostiles < 20 && mobsNearOfClass(x, y, z, true) < AREA_CAP) {
                    MobType t = switch (rng.nextInt(3)) {
                        case 0 -> MobType.ZOMBIE;
                        case 1 -> MobType.SPIDER;
                        default -> MobType.SKELETON;
                    };
                    if (canStand(x, y, z, t)) {
                        world.entities.add(new Mob(world, t, x + 0.5f, y, z + 0.5f));
                        hostiles++;
                    }
                } else if (!night && passives < 10
                        && mobsNearOfClass(x, y, z, false) < AREA_CAP
                        && world.getBlockId(x, y - 1, z) == Blocks.GRASS.id) {
                    MobType t = switch (rng.nextInt(4)) {
                        case 0 -> MobType.COW;
                        case 1 -> MobType.PIG;
                        case 2 -> MobType.SHEEP;
                        default -> MobType.CHICKEN;
                    };
                    if (canStand(x, y, z, t)) {
                        world.entities.add(new Mob(world, t, x + 0.5f, y, z + 0.5f));
                        passives++;
                    }
                }
            } else if (hostiles < 20) {
                // cave spawn: random depth below the surface
                int surface = world.surfaceHeight(x, z);
                int y = 8 + rng.nextInt(Math.max(1, surface - 16));
                if (y < surface - 6 && mobsNearOfClass(x, y, z, true) < AREA_CAP) {
                    MobType t = rng.nextBoolean() ? MobType.ZOMBIE : MobType.SKELETON;
                    if (canStand(x, y, z, t)) {
                        world.entities.add(new Mob(world, t, x + 0.5f, y, z + 0.5f));
                        hostiles++;
                    }
                }
            }
        }
    }

    // ========================================================== interaction

    private void handleInteraction(Raycast.Hit hit, float dt) {
        float sens = 0.11f;
        player.turn((float) input.mouseDX * sens, (float) input.mouseDY * sens);

        for (int i = 0; i < 9; i++) {
            if (input.pressed(GLFW_KEY_1 + i)) hotbarSlot = i;
        }
        if (input.scrollY != 0) {
            hotbarSlot = Math.floorMod(hotbarSlot - (int) Math.signum(input.scrollY), 9);
        }

        placeCooldown = Math.max(0, placeCooldown - dt);
        attackCooldown = Math.max(0, attackCooldown - dt);
        eatCooldown = Math.max(0, eatCooldown - dt);

        // attacking mobs takes priority over mining
        Mob targetMob = pickMob();
        if (input.mouseClicked(GLFW_MOUSE_BUTTON_LEFT) && targetMob != null && attackCooldown <= 0) {
            attackCooldown = 0.4f;
            ItemStack heldStack = held();
            float dmg = heldStack != null ? heldStack.item.attackDamage : 1f;
            Vector3f look = player.lookDir();
            if (targetMob.damage(dmg, look.x * 6f, look.z * 6f)) {
                dropMobLoot(targetMob);
            }
            sound.play(SoundEngine.HURT, 0.6f);
            if (!creative && heldStack != null && heldStack.item.toolClass == Item.ToolClass.SWORD
                    && heldStack.damageTool()) {
                inventory.slots[hotbarSlot] = null;
                sound.play(SoundEngine.BREAK, 0.7f);
            }
            resetBreaking();
            return;
        }

        // mining
        if (input.mouseDown(GLFW_MOUSE_BUTTON_LEFT) && targetMob == null && hit != null) {
            mine(hit, dt);
        } else {
            resetBreaking();
        }

        // right click: use / open / eat / shoot / place
        if (input.mouseDown(GLFW_MOUSE_BUTTON_RIGHT) && placeCooldown <= 0) {
            placeCooldown = 0.22f;
            useOrPlace(hit);
        }

        // middle click: pick block (creative)
        if (creative && input.mouseClicked(GLFW_MOUSE_BUTTON_MIDDLE) && hit != null) {
            int id = world.getBlockId(hit.x, hit.y, hit.z);
            if (id != 0) inventory.slots[hotbarSlot] = new ItemStack(Items.forBlock(Blocks.get(id)), 64);
        }
    }

    private Mob pickMob() {
        Vector3f eye = player.eyePosition();
        Vector3f dir = player.lookDir();
        Mob best = null;
        float bestD = 4f;
        for (Entity e : world.entities) {
            if (e instanceof Mob mob) {
                float d = mob.rayIntersect(eye, dir, 4f);
                if (d >= 0 && d < bestD) {
                    bestD = d;
                    best = mob;
                }
            }
        }
        return best;
    }

    private void resetBreaking() {
        breakZ = Integer.MIN_VALUE;
        breakProgress = 0;
    }

    private void mine(Raycast.Hit hit, float dt) {
        int id = world.getBlockId(hit.x, hit.y, hit.z);
        if (id == 0) {
            resetBreaking();
            return;
        }
        if (creative) {
            if (attackCooldown <= 0 || input.mouseClicked(GLFW_MOUSE_BUTTON_LEFT)) {
                attackCooldown = 0.22f;
                if (id != Blocks.BEDROCK.id) {
                    removeBlock(hit.x, hit.y, hit.z, false);
                }
            }
            return;
        }

        if (hit.x != breakX || hit.y != breakY || hit.z != breakZ) {
            breakX = hit.x;
            breakY = hit.y;
            breakZ = hit.z;
            breakProgress = 0;
            breakTotal = BlockProps.breakTime(id, held());
        }
        if (Float.isInfinite(breakTotal)) return;
        breakProgress += dt;
        if (breakProgress >= breakTotal) {
            boolean harvest = BlockProps.canHarvest(id, held());
            removeBlock(hit.x, hit.y, hit.z, harvest);
            ItemStack tool = held();
            if (tool != null && tool.item.kind == Item.Kind.TOOL
                    && tool.item.toolClass != Item.ToolClass.SWORD && tool.damageTool()) {
                inventory.slots[hotbarSlot] = null;
                sound.play(SoundEngine.BREAK, 0.7f);
            }
            player.hunger = Math.max(0, player.hunger - 0.01f);
            resetBreaking();
        }
    }

    /** Removes a block, spilling drops and container contents as needed. */
    private void removeBlock(int x, int y, int z, boolean withDrops) {
        int id = world.getBlockId(x, y, z);
        if (id == Blocks.CHEST.id) {
            var chest = world.blockEntities.chests.get(BlockEntityStore.key(x, y, z));
            if (chest != null) {
                for (ItemStack s : chest.slots) {
                    if (s != null) dropStack(s, x + 0.5f, y + 0.5f, z + 0.5f,
                            rng.nextFloat() * 2 - 1, 3, rng.nextFloat() * 2 - 1);
                }
            }
        }
        world.blockEntities.removeAt(x, y, z);
        world.setBlock(x, y, z, 0);
        sound.play(SoundEngine.BREAK);
        if (withDrops) {
            ItemStack drop = BlockProps.drop(id, rng);
            if (drop != null) dropStack(drop, x + 0.5f, y + 0.3f, z + 0.5f, 0, 1.5f, 0);
            ItemStack bonus = BlockProps.bonusDrop(id, rng);
            if (bonus != null) dropStack(bonus, x + 0.5f, y + 0.3f, z + 0.5f, 0, 1.5f, 0);
        }
    }

    private void useOrPlace(Raycast.Hit hit) {
        // open containers
        if (hit != null && !input.down(GLFW_KEY_LEFT_SHIFT)) {
            int id = world.getBlockId(hit.x, hit.y, hit.z);
            if (id == Blocks.WORKBENCH.id) {
                state = State.WORKBENCH;
                captureMouse(false);
                return;
            }
            if (id == Blocks.STOVE.id || id == Blocks.STOVE_LIT.id) {
                openFurnace = world.blockEntities.furnaceAt(hit.x, hit.y, hit.z);
                furnaceX = hit.x;
                furnaceY = hit.y;
                furnaceZ = hit.z;
                state = State.FURNACE;
                captureMouse(false);
                return;
            }
            if (id == Blocks.CHEST.id) {
                openChest = world.blockEntities.chestAt(hit.x, hit.y, hit.z);
                state = State.CHEST;
                captureMouse(false);
                return;
            }
        }

        ItemStack heldStack = held();
        if (heldStack == null) return;

        // eat
        if (heldStack.item.isFood() && !creative) {
            if (player.hunger < Player.MAX_HUNGER - 0.5f && eatCooldown <= 0) {
                eatCooldown = 1.2f;
                player.hunger = Math.min(Player.MAX_HUNGER, player.hunger + heldStack.item.foodValue);
                heldStack.count--;
                if (heldStack.count <= 0) inventory.slots[hotbarSlot] = null;
                sound.play(SoundEngine.EAT);
            }
            return;
        }

        // bow
        if (heldStack.item.toolClass == Item.ToolClass.BOW) {
            if (creative || inventory.countOf(Items.ARROW) > 0) {
                if (!creative) inventory.remove(Items.ARROW, 1);
                Vector3f eye = player.eyePosition();
                Vector3f dir = player.lookDir();
                world.entities.add(new Arrow(world, eye,
                        new Vector3f(dir).mul(24f), 5, true));
                sound.play(SoundEngine.SHOOT);
                if (!creative && heldStack.damageTool()) {
                    inventory.slots[hotbarSlot] = null;
                }
            }
            return;
        }

        // place block
        if (heldStack.item.isBlock() && hit != null) {
            Block b = Blocks.get(heldStack.item.blockId);
            if (b.isAir()) return;
            if (world.getBlockId(hit.px, hit.py, hit.pz) != 0) return;
            if (b.solid && player.intersectsBlock(hit.px, hit.py, hit.pz)) return;
            // don't place inside mobs
            for (Entity e : world.entities) {
                if (e instanceof Mob && b.solid
                        && e.intersects(new Vector3f(hit.px + 0.5f, hit.py, hit.pz + 0.5f), 1f, 1f, 0)) {
                    return;
                }
            }
            world.setBlock(hit.px, hit.py, hit.pz, b.id);
            sound.play(SoundEngine.PLACE);
            if (!creative) {
                heldStack.count--;
                if (heldStack.count <= 0) inventory.slots[hotbarSlot] = null;
            }
        }
    }

    private void dropStack(ItemStack stack, float x, float y, float z,
                           float vx, float vy, float vz) {
        world.entities.add(new ItemEntity(world, stack, x, y, z, vx, vy, vz));
    }

    private void dropMobLoot(Mob mob) {
        for (ItemStack s : mob.type.drops(rng)) {
            dropStack(s, mob.position.x, mob.position.y + 0.4f, mob.position.z,
                    rng.nextFloat() * 2 - 1, 2.5f, rng.nextFloat() * 2 - 1);
        }
    }

    private void dropHeldItem(boolean all) {
        ItemStack heldStack = held();
        if (heldStack == null) return;
        int n = all ? heldStack.count : 1;
        ItemStack thrown = new ItemStack(heldStack.item, n, heldStack.damage);
        heldStack.count -= n;
        if (heldStack.count <= 0) inventory.slots[hotbarSlot] = null;
        Vector3f dir = player.lookDir();
        Vector3f eye = player.eyePosition();
        dropStack(thrown, eye.x + dir.x * 0.5f, eye.y - 0.3f, eye.z + dir.z * 0.5f,
                dir.x * 5f, 2f, dir.z * 5f);
    }

    private void onDeath() {
        // spill the whole inventory
        for (int i = 0; i < Inventory.SIZE; i++) {
            if (inventory.slots[i] != null) {
                dropStack(inventory.slots[i], player.position.x, player.position.y + 1f,
                        player.position.z, rng.nextFloat() * 4 - 2, 3, rng.nextFloat() * 4 - 2);
                inventory.slots[i] = null;
            }
        }
        returnGrid(invCraft);
        returnGrid(benchCraft);
        cursor = null;
        state = State.DEAD;
        captureMouse(false);
    }

    // ================================================================== HUD

    private void drawHud(boolean crosshair, Raycast.Hit hit) {
        float cx = gui.width / 2f, cy = gui.height / 2f;
        if (crosshair) {
            gui.rect(cx - 10, cy - 1.5f, 20, 3, 0.95f, 0.95f, 0.95f, 0.75f);
            gui.rect(cx - 1.5f, cy - 10, 3, 20, 0.95f, 0.95f, 0.95f, 0.75f);
            // mining progress
            if (breakZ != Integer.MIN_VALUE && breakTotal > 0 && !creative) {
                float frac = Math.min(1f, breakProgress / breakTotal);
                gui.rect(cx - 25, cy + 16, 50, 6, 0.1f, 0.1f, 0.1f, 0.7f);
                gui.rect(cx - 24, cy + 17, 48 * frac, 4, 0.9f, 0.9f, 0.3f, 0.95f);
            }
        }

        float slot = 48, pad = 5;
        float barW = 9 * slot + 10 * pad;
        float x0 = cx - barW / 2f;
        float y0 = gui.height - slot - 14;
        gui.rect(x0 - 2, y0 - pad - 2, barW + 4, slot + pad * 2 + 4, 0.08f, 0.08f, 0.08f, 0.6f);
        for (int i = 0; i < 9; i++) {
            float sx = x0 + pad + i * (slot + pad);
            if (i == hotbarSlot) {
                gui.rect(sx - 4, y0 - 4, slot + 8, slot + 8, 0.98f, 0.98f, 0.98f, 0.85f);
            }
            gui.rect(sx, y0, slot, slot, 0.25f, 0.25f, 0.28f, 0.85f);
            drawStackIcon(inventory.slots[i], sx, y0, slot);
        }
        ItemStack heldStack = held();
        if (heldStack != null) {
            gui.textCentered(heldStack.item.name, cx, y0 - 24, 2f, 1f, 1f, 1f);
        }

        if (!creative) {
            // hearts above the hotbar (left)
            float iconS = 18, iy = y0 - pad - 26;
            for (int i = 0; i < 10; i++) {
                int tile = player.health >= (i + 1) * 2 ? Items.TILE_HEART_FULL
                        : player.health >= i * 2 + 1 ? Items.TILE_HEART_HALF : Items.TILE_HEART_EMPTY;
                gui.tile(x0 + i * (iconS + 1), iy, iconS, iconS, tile, 1f);
            }
            // hunger (right, filled right-to-left)
            for (int i = 0; i < 10; i++) {
                int tile = player.hunger >= (i + 1) * 2 - 1 ? Items.TILE_HUNGER_FULL : Items.TILE_HUNGER_EMPTY;
                gui.tile(x0 + barW - (i + 1) * (iconS + 1), iy, iconS, iconS, tile, 1f);
            }
            // air bubbles when underwater
            if (player.air < Player.MAX_AIR - 0.1f) {
                for (int i = 0; i < 10; i++) {
                    if (player.air >= i + 0.5f) {
                        gui.tile(x0 + barW - (i + 1) * (iconS + 1), iy - iconS - 3, iconS, iconS,
                                Items.TILE_BUBBLE, 1f);
                    }
                }
            }
        }
    }

    private void drawStackIcon(ItemStack s, float x, float y, float size) {
        if (s == null) return;
        gui.tile(x + 5, y + 5, size - 10, size - 10, s.item.icon, 1f);
        if (s.count > 1) {
            gui.text(String.valueOf(s.count), x + size - 6 - gui.font().width(String.valueOf(s.count), 1.8f),
                    y + size - 16, 1.8f, 1f, 1f, 1f);
        }
        if (s.item.durability > 0 && s.damage > 0) {
            float frac = 1f - s.damage / (float) s.item.durability;
            gui.rect(x + 5, y + size - 8, (size - 10), 3, 0.1f, 0.1f, 0.1f, 0.8f);
            gui.rect(x + 5, y + size - 8, (size - 10) * frac, 3, 1f - frac, frac, 0.1f, 0.95f);
        }
    }

    // ======================================================== slot handling

    /** Draws one interactive slot over a backing array; handles cursor ops. */
    private ItemStack hoveredStack;

    private void slotWidget(ItemStack[] array, int idx, float x, float y, float size,
                            boolean takeOnly) {
        boolean hov = gui.hover(x, y, size, size);
        gui.rect(x, y, size, size, hov ? 0.36f : 0.24f, hov ? 0.36f : 0.24f, hov ? 0.4f : 0.27f, 1f);
        drawStackIcon(array[idx], x, y, size);
        if (!hov) return;
        if (array[idx] != null) hoveredStack = array[idx];

        if (gui.clicked) {
            if (takeOnly) {
                takeResult(array, idx);
            } else if (cursor == null) {
                cursor = array[idx];
                array[idx] = null;
            } else if (array[idx] == null) {
                array[idx] = cursor;
                cursor = null;
            } else if (array[idx].canMergeWith(cursor)) {
                int room = array[idx].item.maxStack - array[idx].count;
                int take = Math.min(room, cursor.count);
                array[idx].count += take;
                cursor.count -= take;
                if (cursor.count <= 0) cursor = null;
            } else {
                ItemStack tmp = array[idx];
                array[idx] = cursor;
                cursor = tmp;
            }
            sound.play(SoundEngine.CLICK, 0.5f);
        } else if (gui.rightClicked && !takeOnly) {
            if (cursor == null && array[idx] != null) {
                int half = (array[idx].count + 1) / 2;
                cursor = new ItemStack(array[idx].item, half, array[idx].damage);
                array[idx].count -= half;
                if (array[idx].count <= 0) array[idx] = null;
            } else if (cursor != null && (array[idx] == null || array[idx].canMergeWith(cursor))) {
                if (array[idx] == null) {
                    array[idx] = new ItemStack(cursor.item, 1, cursor.damage);
                } else if (array[idx].count < array[idx].item.maxStack) {
                    array[idx].count++;
                } else {
                    return;
                }
                cursor.count--;
                if (cursor.count <= 0) cursor = null;
            }
            sound.play(SoundEngine.CLICK, 0.5f);
        }
    }

    /** Craft-result pickup: consumes one item from every grid cell. */
    private ItemStack[] activeCraftGrid;

    private void takeResult(ItemStack[] resultHolder, int idx) {
        ItemStack result = resultHolder[idx];
        if (result == null) return;
        if (cursor != null && !(cursor.canMergeWith(result)
                && cursor.count + result.count <= cursor.item.maxStack)) {
            return;
        }
        if (cursor == null) {
            cursor = result.copy();
        } else {
            cursor.count += result.count;
        }
        if (activeCraftGrid != null) {
            for (int i = 0; i < activeCraftGrid.length; i++) {
                if (activeCraftGrid[i] != null) {
                    activeCraftGrid[i].count--;
                    if (activeCraftGrid[i].count <= 0) activeCraftGrid[i] = null;
                }
            }
        } else {
            resultHolder[idx] = null; // furnace output
        }
        sound.play(SoundEngine.POP, 0.7f);
    }

    /** 27 backpack slots + 9 hotbar slots. Returns total height used. */
    private float drawPlayerSlots(float px, float py, float slot, float pad) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = 9 + row * 9 + col;
                slotWidget(inventory.slots, idx,
                        px + col * (slot + pad), py + row * (slot + pad), slot, false);
            }
        }
        float hy = py + 3 * (slot + pad) + 8;
        for (int col = 0; col < 9; col++) {
            if (col == hotbarSlot) {
                gui.frame(px + col * (slot + pad) - 2, hy - 2, slot + 4, slot + 4, 2,
                        0.98f, 0.85f, 0.3f, 1f);
            }
            slotWidget(inventory.slots, col, px + col * (slot + pad), hy, slot, false);
        }
        return 3 * (slot + pad) + 8 + slot;
    }

    private void drawCursorAndTooltip() {
        if (cursor != null) {
            drawStackIcon(cursor, gui.mouseX - 20, gui.mouseY - 20, 40);
        } else if (hoveredStack != null) {
            String name = hoveredStack.item.name;
            float tw = gui.font().width(name, 2f) + 12;
            float tx = Math.min(gui.mouseX + 14, gui.width - tw - 4);
            gui.rect(tx - 6, gui.mouseY + 8, tw, 26, 0.05f, 0.05f, 0.05f, 0.92f);
            gui.text(name, tx, gui.mouseY + 13, 2f, 1f, 1f, 0.85f);
        }
        hoveredStack = null;
    }

    private float panelStart(float panelW, float panelH, String title) {
        float px = gui.width / 2f - panelW / 2f;
        float py = Math.max(16, gui.height / 2f - panelH / 2f);
        gui.rect(0, 0, gui.width, gui.height, 0f, 0f, 0f, 0.45f);
        gui.rect(px - 8, py - 32, panelW + 16, panelH + 44, 0.12f, 0.12f, 0.14f, 0.96f);
        gui.frame(px - 8, py - 32, panelW + 16, panelH + 44, 2, 0.6f, 0.6f, 0.6f, 1f);
        gui.text(title, px, py - 24, 2f, 1f, 1f, 1f);
        return px;
    }

    // ====================================================== survival screens

    private void drawSurvivalInventory() {
        float slot = 44, pad = 4;
        float panelW = 9 * (slot + pad) - pad;
        float panelH = 2 * (slot + pad) + 20 + 4 * (slot + pad) + 8;
        float px = panelStart(panelW, panelH, "INVENTORY");
        float py = Math.max(16, gui.height / 2f - panelH / 2f);

        // 2x2 crafting + result
        gui.text("CRAFT", px, py + 4, 1.6f, 0.75f, 0.75f, 0.75f);
        activeCraftGrid = invCraft;
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                slotWidget(invCraft, r * 2 + c, px + 70 + c * (slot + pad), py + r * (slot + pad), slot, false);
            }
        }
        gui.text("=", px + 70 + 2 * (slot + pad) + 12, py + slot - 8, 3f, 0.8f, 0.8f, 0.8f);
        ItemStack[] result = {Recipes.match(invCraft, 2)};
        slotWidget(result, 0, px + 70 + 2 * (slot + pad) + 44, py + (slot + pad) / 2f, slot, true);
        activeCraftGrid = null;

        float invY = py + 2 * (slot + pad) + 20;
        drawPlayerSlots(px, invY, slot, pad);
        drawCursorAndTooltip();
    }

    private void drawWorkbench() {
        float slot = 44, pad = 4;
        float panelW = 9 * (slot + pad) - pad;
        float panelH = 3 * (slot + pad) + 20 + 4 * (slot + pad) + 8;
        float px = panelStart(panelW, panelH, "WORKBENCH");
        float py = Math.max(16, gui.height / 2f - panelH / 2f);

        activeCraftGrid = benchCraft;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                slotWidget(benchCraft, r * 3 + c, px + 40 + c * (slot + pad), py + r * (slot + pad), slot, false);
            }
        }
        gui.text("=", px + 40 + 3 * (slot + pad) + 14, py + slot + 8, 3f, 0.8f, 0.8f, 0.8f);
        ItemStack[] result = {Recipes.match(benchCraft, 3)};
        slotWidget(result, 0, px + 40 + 3 * (slot + pad) + 48, py + slot + pad / 2f, slot, true);
        activeCraftGrid = null;

        float invY = py + 3 * (slot + pad) + 20;
        drawPlayerSlots(px, invY, slot, pad);
        drawCursorAndTooltip();
    }

    private void drawFurnace() {
        if (openFurnace == null) {
            closeContainer();
            return;
        }
        float slot = 44, pad = 4;
        float panelW = 9 * (slot + pad) - pad;
        float panelH = 3 * (slot + pad) + 20 + 4 * (slot + pad) + 8;
        float px = panelStart(panelW, panelH, "STOVE");
        float py = Math.max(16, gui.height / 2f - panelH / 2f);

        var f = openFurnace;
        ItemStack[] io = {f.input, f.fuel, f.output};
        slotWidget(io, 0, px + 120, py, slot, false);
        // flame gauge between input and fuel
        float flame = f.burnLeft > 0 ? f.burnLeft / Math.max(1, f.burnTotal) : 0;
        gui.rect(px + 128, py + slot + 6, 28, 8, 0.15f, 0.15f, 0.15f, 1f);
        gui.rect(px + 128, py + slot + 6, 28 * Math.min(1, flame), 8, 0.95f, 0.5f, 0.1f, 1f);
        slotWidget(io, 1, px + 120, py + slot + 20, slot, false);
        // progress arrow
        float prog = f.progress / BlockEntityStore.SMELT_SECONDS;
        gui.rect(px + 120 + slot + 16, py + slot / 2f + 8, 60, 10, 0.15f, 0.15f, 0.15f, 1f);
        gui.rect(px + 120 + slot + 16, py + slot / 2f + 8, 60 * prog, 10, 0.9f, 0.9f, 0.3f, 1f);
        slotWidget(io, 2, px + 120 + slot + 92, py + slot / 2f, slot, true);
        f.input = io[0];
        f.fuel = io[1];
        f.output = io[2];

        float invY = py + 3 * (slot + pad) + 20;
        drawPlayerSlots(px, invY, slot, pad);
        drawCursorAndTooltip();
    }

    private void drawChest() {
        if (openChest == null) {
            closeContainer();
            return;
        }
        float slot = 44, pad = 4;
        float panelW = 9 * (slot + pad) - pad;
        float panelH = 3 * (slot + pad) + 20 + 4 * (slot + pad) + 8;
        float px = panelStart(panelW, panelH, "CHEST");
        float py = Math.max(16, gui.height / 2f - panelH / 2f);

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                slotWidget(openChest.slots, r * 9 + c, px + c * (slot + pad), py + r * (slot + pad), slot, false);
            }
        }
        float invY = py + 3 * (slot + pad) + 20;
        drawPlayerSlots(px, invY, slot, pad);
        drawCursorAndTooltip();
    }

    // ====================================================== creative screen

    private static final int C_COLS = 9, C_ROWS = 5;

    private void drawCreativeInventory() {
        var all = Items.all();
        int total = all.size() - 1; // skip air's block-item
        int totalRows = (total + C_COLS - 1) / C_COLS;

        float slot = 44, pad = 4;
        float panelW = C_COLS * (slot + pad) - pad;
        float panelH = C_ROWS * (slot + pad) + 20 + slot;
        float px = panelStart(panelW, panelH, "ALL ITEMS (" + total + ") - SCROLL TO BROWSE");
        float py = Math.max(16, gui.height / 2f - panelH / 2f);

        if (gui.scroll != 0) creativeScroll -= (int) Math.signum(gui.scroll);
        creativeScroll = Math.max(0, Math.min(creativeScroll, Math.max(0, totalRows - C_ROWS)));

        for (int row = 0; row < C_ROWS; row++) {
            for (int col = 0; col < C_COLS; col++) {
                int idx = (creativeScroll + row) * C_COLS + col + 1;
                if (idx >= all.size()) break;
                Item it = all.get(idx);
                float sx = px + col * (slot + pad);
                float sy = py + row * (slot + pad);
                boolean hov = gui.hover(sx, sy, slot, slot);
                gui.rect(sx, sy, slot, slot, hov ? 0.36f : 0.24f, hov ? 0.36f : 0.24f, hov ? 0.4f : 0.27f, 1f);
                gui.tile(sx + 5, sy + 5, slot - 10, slot - 10, it.icon, 1f);
                if (hov) {
                    hoveredStack = new ItemStack(it, 1);
                    if (gui.clicked) {
                        cursor = new ItemStack(it, it.maxStack > 1 ? it.maxStack : 1);
                        sound.play(SoundEngine.CLICK, 0.5f);
                    }
                    if (gui.rightClicked) {
                        inventory.slots[hotbarSlot] = new ItemStack(it, it.maxStack > 1 ? it.maxStack : 1);
                        sound.play(SoundEngine.CLICK, 0.5f);
                    }
                }
            }
        }
        // scrollbar
        if (totalRows > C_ROWS) {
            float trackH = C_ROWS * (slot + pad);
            float barH = Math.max(24, trackH * C_ROWS / totalRows);
            float barY = py + (trackH - barH) * creativeScroll / Math.max(1, totalRows - C_ROWS);
            gui.rect(px + panelW + 3, py, 6, trackH, 0.2f, 0.2f, 0.2f, 1f);
            gui.rect(px + panelW + 3, barY, 6, barH, 0.7f, 0.7f, 0.7f, 1f);
        }

        // hotbar row
        float hy = py + C_ROWS * (slot + pad) + 20;
        for (int col = 0; col < 9; col++) {
            if (col == hotbarSlot) {
                gui.frame(px + col * (slot + pad) - 2, hy - 2, slot + 4, slot + 4, 2, 0.98f, 0.85f, 0.3f, 1f);
            }
            slotWidget(inventory.slots, col, px + col * (slot + pad), hy, slot, false);
        }
        drawCursorAndTooltip();
    }

    // ============================================================ pause/dead

    private void drawPauseMenu() {
        gui.rect(0, 0, gui.width, gui.height, 0f, 0f, 0f, 0.55f);
        float cx = gui.width / 2f;
        float bw = Math.min(440, gui.width - 60), bh = 44, gap = 14;
        float y = gui.height * 0.28f;

        gui.textCentered("GAME PAUSED", cx, y - 60, 3f, 1f, 1f, 1f);
        gui.textCentered(info.name() + " (" + (creative ? "CREATIVE" : "SURVIVAL") + ")",
                cx, y - 30, 2f, 0.75f, 0.75f, 0.75f);

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

    private void drawDeathScreen() {
        gui.rect(0, 0, gui.width, gui.height, 0.45f, 0.02f, 0.02f, 0.55f);
        float cx = gui.width / 2f;
        float bw = Math.min(440, gui.width - 60), bh = 46;
        float y = gui.height * 0.32f;
        gui.textCentered("YOU DIED", cx, y - 80, 5f, 1f, 0.9f, 0.9f);
        if (gui.button("RESPAWN", cx - bw / 2, y, bw, bh)) {
            player.respawn(0, 0);
            world.ensureChunksAround(0.5, 0.5, 3, 64);
            state = State.PLAYING;
            captureMouse(true);
        }
        if (gui.button("SAVE AND QUIT TO TITLE", cx - bw / 2, y + bh + 14, bw, bh)) {
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
        ItemStack heldStack = held();
        return String.format("BlockForge — %s | %.0f fps | XYZ %.1f / %.1f / %.1f | %s | %d entities",
                info.name(), fps, player.position.x, player.position.y, player.position.z,
                heldStack == null ? "empty hand" : heldStack.item.name, world.entities.size());
    }

    public void delete() {
        renderer.delete();
    }
}
