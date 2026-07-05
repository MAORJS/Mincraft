# BlockForge

An original open-source voxel sandbox game written from scratch in Java 17 with
LWJGL 3 (OpenGL 3.3). It is inspired by the classic block-building genre but
contains **no copied code, art, sounds, or names** — every texture in the game
is generated procedurally at runtime from math, so the project ships zero
image assets.

## Features

- **190+ block types** — stones, soils, sands, 4 wood species (logs, planks,
  leaves, saplings), 14 ores in surface *and* deep variants, 14 refined mineral
  blocks, 20 masonry blocks, 16-color cloth / glass / plaster / ceramic
  families, 9 glowing lamps, flowers, mushrooms, crops, and utility blocks.
- **Infinite procedurally generated terrain** — fractal-noise heightmaps with
  five biomes (plains, forest, desert, tundra, mountains), beaches, frozen
  lakes, and snow-capped peaks.
- **Caves and ore veins** — 3D-noise cave systems with lava pools at depth and
  depth-tiered ore distribution (coal near the surface, diamond in the deeps).
- **Trees and vegetation** — oak, birch, pine, and walnut trees, cacti, tall
  grass, ferns, six flower species, and mushrooms.
- **First-person physics** — gravity, jumping, swimming, sprinting, axis-swept
  AABB collision, and a fly mode.
- **Block interaction** — raycast targeting with a selection outline, breaking,
  placing, and middle-click block picking.
- **Creative inventory** (`E`) — scrollable grid of every block with tooltips;
  left-click picks a block onto the cursor, drop it into any of the nine free
  hotbar slots, right-click sends it straight to the selected slot.
- **Title screen & world management** — logo screen with a Singleplayer
  button leading to a world list (play, rename, delete, new world) or straight
  to world creation when no worlds exist; worlds take a name and optional seed.
- **World saving** — player-modified chunks, player state, hotbar, and time of
  day persist per world under `~/.blockforge/worlds/`; autosaves every 30
  seconds and on quit. Untouched terrain regenerates deterministically from
  the seed, keeping saves small.
- **Pause & options menus** (`Esc`) — video settings (fullscreen, vsync, FOV,
  render distance), audio settings (master/effects volume, UI sounds), and
  graphics settings (brightness, fog, day-night cycle, block outline), all
  applied live and persisted to `~/.blockforge/options.txt`.
- **Synthesized audio** — OpenAL sound engine whose click/break/place sounds
  are generated from waveforms at startup; no audio files ship with the game.
- **Procedural bitmap font** — the entire UI is drawn with a 5x7 pixel font
  defined in code, like every other art asset.
- **Day/night cycle** with sky tint, dynamic lighting, and distance fog.
- **Fully procedural texture atlas** — 16x16 tiles painted at startup by the
  `Tiles` painter (wood grain, brick courses, ore blobs, cloth weave, ...).
- **Chunked renderer** — 16x128x16 chunks, hidden-face culling, separate
  opaque/cut-out and translucent passes, per-frame streaming budgets so the
  world loads without stutter.

## Requirements

- Java 17 or newer
- Maven 3.8+
- A GPU/driver with OpenGL 3.3 support

## Build & run

```bash
mvn package
java -jar target/blockforge-1.0.0.jar          # optional: append a world seed
```

`mvn package` also produces **`target/BlockForge.exe`** — a Windows launcher
(built with Launch4j) that double-click runs the game on any Windows machine
with Java 17+ installed. If Java is missing, it points the user to a download
page.

For a **fully self-contained Windows build with a bundled Java runtime** (no
Java installation needed), run the *Windows build* GitHub Actions workflow
(`.github/workflows/windows-build.yml`, via the Actions tab or by pushing a
`v*` tag) and grab the `BlockForge-windows-standalone` artifact — unzip it and
run `BlockForge/BlockForge.exe`.

On **macOS** the JVM must start GLFW on the first thread:

```bash
java -XstartOnFirstThread -jar target/blockforge-1.0.0.jar
```

Or run directly through Maven:

```bash
mvn compile exec:java
```

## Controls

| Input | Action |
| --- | --- |
| Mouse | Look around |
| `W A S D` | Move |
| `Space` | Jump / fly up / swim |
| `Left Shift` | Fly down |
| `Left Ctrl` | Sprint |
| `F` | Toggle fly mode |
| Left click | Break block |
| Right click | Place block |
| Middle click | Pick targeted block into the hotbar |
| `1`–`9` / scroll | Select hotbar slot |
| `E` | Open / close the inventory |
| `Esc` | Pause menu (options, save & quit) |

In the inventory: scroll to browse all blocks, left-click to pick a block up,
click a hotbar slot to drop it there, right-click a block to send it straight
to the selected hotbar slot.

The window title shows FPS, position, the held block, and the registered block
count.

## Project layout

```
src/main/java/com/blockforge/
├── Main.java                 window, app state machine (menus vs in-game)
├── Game.java                 simulation, streaming, interaction, HUD,
│                             pause/options/inventory screens
├── Input.java                GLFW input state (keys, mouse, text input)
├── Settings.java             persisted options (~/.blockforge/options.txt)
├── audio/SoundEngine.java    OpenAL engine, waveform-synthesized sounds
├── gui/
│   ├── Gui.java              immediate-mode widgets (buttons, sliders, fields)
│   ├── TitleFlow.java        title / world list / create / rename screens
│   └── OptionsMenu.java      video, audio, graphics settings pages
├── save/WorldStorage.java    world folders, metadata, gzipped chunk files
├── player/Player.java        AABB physics & camera
├── world/
│   ├── Block.java            block type definition
│   ├── Blocks.java           registry of all 190+ blocks
│   ├── Chunk.java            16x128x16 block storage
│   ├── World.java            chunk streaming & block access
│   ├── TerrainGenerator.java biomes, caves, ores, trees
│   ├── Noise.java            gradient noise (2D/3D, fractal, ridged)
│   └── Raycast.java          voxel grid traversal
└── render/
    ├── Renderer.java         world passes, fog, day/night, selection box
    ├── ChunkMesher.java      chunk → vertex data
    ├── Mesh.java             VAO/VBO wrapper
    ├── Shader.java           GLSL program wrapper
    ├── Batch2D.java          dynamic 2D quad batch for the GUI
    ├── Font.java             procedural 5x7 bitmap font
    ├── TextureAtlas.java     runtime-built atlas
    └── Tiles.java            procedural tile painters (all in-game art)
```

## License

MIT — see [LICENSE](LICENSE). This is an original work; it is not affiliated
with, endorsed by, or derived from Mojang/Microsoft products.
