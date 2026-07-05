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

    public static WorldInfo createWorld(String name, long seed) {
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
