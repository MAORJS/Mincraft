package com.blockforge.save;

import com.blockforge.Settings;
import com.blockforge.world.Chunk;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * On-disk persistence. Each world lives in its own directory under
 * ~/.blockforge/worlds/:
 *
 *   world.properties      name, seed, player state, time of day, hotbar
 *   chunks/c.<x>.<z>.gz   gzipped block ids for chunks the player changed
 *
 * Unmodified chunks are never written — terrain generation is deterministic
 * per seed, so they are simply regenerated on load.
 */
public final class WorldStorage {

    private static final int MAGIC = 0xB10CF04E;
    private static final int VERSION = 1;

    /** Metadata handle for one saved world. */
    public static final class WorldInfo {
        public final Path dir;
        public final Properties props;

        WorldInfo(Path dir, Properties props) {
            this.dir = dir;
            this.props = props;
        }

        public String name() {
            return props.getProperty("name", dir.getFileName().toString());
        }

        public long seed() {
            try {
                return Long.parseLong(props.getProperty("seed", "0"));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public long lastPlayed() {
            try {
                return Long.parseLong(props.getProperty("lastPlayed", "0"));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public void save() {
            try {
                Files.createDirectories(dir);
                try (OutputStream out = Files.newOutputStream(dir.resolve("world.properties"))) {
                    props.store(out, "BlockForge world");
                }
            } catch (IOException e) {
                System.err.println("could not save world metadata: " + e.getMessage());
            }
        }
    }

    public static Path worldsDir() {
        return Settings.gameDir().resolve("worlds");
    }

    /** All saved worlds, most recently played first. */
    public static List<WorldInfo> listWorlds() {
        List<WorldInfo> worlds = new ArrayList<>();
        Path root = worldsDir();
        if (!Files.isDirectory(root)) return worlds;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            for (Path dir : ds) {
                Path meta = dir.resolve("world.properties");
                if (!Files.isRegularFile(meta)) continue;
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(meta)) {
                    p.load(in);
                }
                worlds.add(new WorldInfo(dir, p));
            }
        } catch (IOException e) {
            System.err.println("could not list worlds: " + e.getMessage());
        }
        worlds.sort(Comparator.comparingLong(WorldInfo::lastPlayed).reversed());
        return worlds;
    }

    public static WorldInfo createWorld(String name, long seed, String gamemode) {
        String base = name.strip().replaceAll("[^A-Za-z0-9-_ ]", "").replace(' ', '_');
        if (base.isEmpty()) base = "world";
        Path dir = worldsDir().resolve(base);
        int n = 2;
        while (Files.exists(dir)) {
            dir = worldsDir().resolve(base + "_" + n++);
        }
        Properties p = new Properties();
        p.setProperty("name", name.strip().isEmpty() ? "New World" : name.strip());
        p.setProperty("seed", String.valueOf(seed));
        p.setProperty("gamemode", gamemode);
        p.setProperty("created", String.valueOf(System.currentTimeMillis()));
        p.setProperty("lastPlayed", String.valueOf(System.currentTimeMillis()));
        WorldInfo info = new WorldInfo(dir, p);
        info.save();
        return info;
    }

    public static void renameWorld(WorldInfo info, String newName) {
        if (newName.strip().isEmpty()) return;
        info.props.setProperty("name", newName.strip());
        info.save();
    }

    public static void deleteWorld(WorldInfo info) {
        try (var walk = Files.walk(info.dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            System.err.println("could not delete world: " + e.getMessage());
        }
    }

    // ------------------------------------------------------- chunk save/load

    private final Path chunkDir;

    public WorldStorage(Path worldDir) {
        this.chunkDir = worldDir.resolve("chunks");
    }

    private Path chunkFile(int cx, int cz) {
        return chunkDir.resolve("c." + cx + "." + cz + ".gz");
    }

    /** Loads chunk block data from disk into the chunk; false if not saved. */
    public boolean loadChunk(Chunk chunk) {
        Path f = chunkFile(chunk.cx, chunk.cz);
        if (!Files.isRegularFile(f)) return false;
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(Files.newInputStream(f)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return false;
            if (in.readInt() != chunk.cx || in.readInt() != chunk.cz) return false;
            short[] raw = chunk.raw();
            for (int i = 0; i < raw.length; i++) {
                raw[i] = in.readShort();
            }
            return true;
        } catch (IOException e) {
            System.err.println("could not load chunk " + chunk.cx + "," + chunk.cz + ": " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------- entities / block entities

    private Path extrasFile(String name) {
        return chunkDir.getParent().resolve(name);
    }

    /** Saves entities, block-entity data, and the player inventory. */
    public void saveExtras(com.blockforge.world.World world,
                           com.blockforge.item.Inventory inventory,
                           Properties props) {
        // inventory into properties
        StringBuilder inv = new StringBuilder();
        for (int i = 0; i < com.blockforge.item.Inventory.SIZE; i++) {
            var s = inventory.slots[i];
            if (i > 0) inv.append(';');
            if (s != null) inv.append(s.item.id).append(':').append(s.count).append(':').append(s.damage);
        }
        props.setProperty("inventory", inv.toString());

        try {
            Files.createDirectories(chunkDir.getParent());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                    Files.newOutputStream(extrasFile("entities.gz"))))) {
                java.util.List<com.blockforge.entity.Entity> toSave = new ArrayList<>();
                for (var e : world.entities) {
                    if (e instanceof com.blockforge.entity.Mob || e instanceof com.blockforge.entity.ItemEntity) {
                        toSave.add(e);
                    }
                }
                out.writeInt(toSave.size());
                for (var e : toSave) {
                    if (e instanceof com.blockforge.entity.Mob mob) {
                        out.writeByte(0);
                        out.writeByte(mob.type.ordinal());
                        out.writeFloat(mob.position.x);
                        out.writeFloat(mob.position.y);
                        out.writeFloat(mob.position.z);
                        out.writeFloat(mob.health);
                    } else {
                        var item = (com.blockforge.entity.ItemEntity) e;
                        out.writeByte(1);
                        out.writeInt(item.stack.item.id);
                        out.writeInt(item.stack.count);
                        out.writeInt(item.stack.damage);
                        out.writeFloat(item.position.x);
                        out.writeFloat(item.position.y);
                        out.writeFloat(item.position.z);
                    }
                }
            }
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                    Files.newOutputStream(extrasFile("blockentities.gz"))))) {
                var store = world.blockEntities;
                out.writeInt(store.furnaces.size());
                for (var e : store.furnaces.entrySet()) {
                    out.writeLong(e.getKey());
                    writeStack(out, e.getValue().input);
                    writeStack(out, e.getValue().fuel);
                    writeStack(out, e.getValue().output);
                    out.writeFloat(e.getValue().progress);
                    out.writeFloat(e.getValue().burnLeft);
                    out.writeFloat(e.getValue().burnTotal);
                }
                out.writeInt(store.chests.size());
                for (var e : store.chests.entrySet()) {
                    out.writeLong(e.getKey());
                    for (int i = 0; i < 27; i++) writeStack(out, e.getValue().slots[i]);
                }
                out.writeInt(store.spawners.size());
                for (var e : store.spawners.entrySet()) {
                    out.writeLong(e.getKey());
                    out.writeByte(e.getValue().mobType.ordinal());
                }
            }
        } catch (IOException e) {
            System.err.println("could not save entities: " + e.getMessage());
        }
    }

    /** Loads entities, block-entity data, and the player inventory. */
    public void loadExtras(com.blockforge.world.World world,
                           com.blockforge.item.Inventory inventory,
                           Properties props) {
        String inv = props.getProperty("inventory");
        if (inv != null) {
            String[] parts = inv.split(";", -1);
            for (int i = 0; i < Math.min(parts.length, com.blockforge.item.Inventory.SIZE); i++) {
                if (parts[i].isEmpty()) continue;
                String[] f = parts[i].split(":");
                try {
                    int id = Integer.parseInt(f[0]);
                    if (id >= 0 && id < com.blockforge.item.Items.count()) {
                        inventory.slots[i] = new com.blockforge.item.ItemStack(
                                com.blockforge.item.Items.get(id),
                                Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }

        Path ents = extrasFile("entities.gz");
        if (Files.isRegularFile(ents)) {
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(
                    Files.newInputStream(ents)))) {
                int n = in.readInt();
                var types = com.blockforge.entity.MobType.values();
                for (int i = 0; i < n; i++) {
                    int kind = in.readByte();
                    if (kind == 0) {
                        int t = in.readByte();
                        float x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
                        float hp = in.readFloat();
                        if (t >= 0 && t < types.length) {
                            var mob = new com.blockforge.entity.Mob(world, types[t], x, y, z);
                            mob.health = hp;
                            world.entities.add(mob);
                        }
                    } else {
                        int id = in.readInt();
                        int count = in.readInt();
                        int dmg = in.readInt();
                        float x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
                        if (id >= 0 && id < com.blockforge.item.Items.count()) {
                            world.entities.add(new com.blockforge.entity.ItemEntity(world,
                                    new com.blockforge.item.ItemStack(
                                            com.blockforge.item.Items.get(id), count, dmg),
                                    x, y, z, 0, 0, 0));
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("could not load entities: " + e.getMessage());
            }
        }

        Path bes = extrasFile("blockentities.gz");
        if (Files.isRegularFile(bes)) {
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(
                    Files.newInputStream(bes)))) {
                var store = world.blockEntities;
                int nf = in.readInt();
                for (int i = 0; i < nf; i++) {
                    long key = in.readLong();
                    var f = new com.blockforge.world.BlockEntityStore.Furnace();
                    f.input = readStack(in);
                    f.fuel = readStack(in);
                    f.output = readStack(in);
                    f.progress = in.readFloat();
                    f.burnLeft = in.readFloat();
                    f.burnTotal = in.readFloat();
                    store.furnaces.put(key, f);
                }
                int nc = in.readInt();
                for (int i = 0; i < nc; i++) {
                    long key = in.readLong();
                    var c = new com.blockforge.world.BlockEntityStore.Chest();
                    for (int j = 0; j < 27; j++) c.slots[j] = readStack(in);
                    store.chests.put(key, c);
                }
                int ns = in.readInt();
                var types = com.blockforge.entity.MobType.values();
                for (int i = 0; i < ns; i++) {
                    long key = in.readLong();
                    int t = in.readByte();
                    var sp = new com.blockforge.world.BlockEntityStore.Spawner();
                    if (t >= 0 && t < types.length) sp.mobType = types[t];
                    store.spawners.put(key, sp);
                }
            } catch (IOException e) {
                System.err.println("could not load block entities: " + e.getMessage());
            }
        }
    }

    private static void writeStack(DataOutputStream out, com.blockforge.item.ItemStack s)
            throws IOException {
        if (s == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(s.item.id);
            out.writeInt(s.count);
            out.writeInt(s.damage);
        }
    }

    private static com.blockforge.item.ItemStack readStack(DataInputStream in) throws IOException {
        int id = in.readInt();
        if (id < 0) return null;
        int count = in.readInt();
        int dmg = in.readInt();
        if (id >= com.blockforge.item.Items.count()) return null;
        return new com.blockforge.item.ItemStack(com.blockforge.item.Items.get(id), count, dmg);
    }

    public void saveChunk(Chunk chunk) {
        try {
            Files.createDirectories(chunkDir);
            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(chunkFile(chunk.cx, chunk.cz))))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(chunk.cx);
                out.writeInt(chunk.cz);
                for (short s : chunk.raw()) {
                    out.writeShort(s);
                }
            }
        } catch (IOException e) {
            System.err.println("could not save chunk " + chunk.cx + "," + chunk.cz + ": " + e.getMessage());
        }
    }
}
