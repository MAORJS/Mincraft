package com.blockforge;

import com.blockforge.audio.SoundEngine;
import com.blockforge.gui.Gui;
import com.blockforge.gui.OptionsMenu;
import com.blockforge.gui.TitleFlow;
import com.blockforge.render.TextureAtlas;
import com.blockforge.save.WorldStorage.WorldInfo;
import com.blockforge.world.Blocks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Platform;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Entry point and application state machine: title/menu screens when no world
 * is loaded, the Game itself once the player picks a world.
 *
 * On macOS run with the JVM flag -XstartOnFirstThread (GLFW requirement).
 */
public final class Main {

    private static long window;
    private static int windowedX = 80, windowedY = 60, windowedW = 1280, windowedH = 720;

    public static void main(String[] args) {
        if (Platform.get() == Platform.MACOSX) {
            System.out.println("Note: on macOS, launch with: java -XstartOnFirstThread -jar blockforge.jar");
        }

        Settings settings = Settings.load();

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("could not init GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(windowedW, windowedH, "BlockForge", NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("could not create window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(settings.vsync ? 1 : 0);
        GL.createCapabilities();

        Input input = new Input();
        input.install(window);
        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }

        // registries + procedural art, then one shared GL atlas for world & GUI
        Blocks.registerAll();
        com.blockforge.item.Items.registerAll();
        com.blockforge.entity.MobModels.registerAll();
        int atlasTexture = TextureAtlas.createGLTexture();
        System.out.println("BlockForge: " + Blocks.count() + " block types, "
                + com.blockforge.item.Items.count() + " items registered.");

        SoundEngine sound = new SoundEngine(settings);
        Gui gui = new Gui(atlasTexture, sound);
        OptionsMenu.WindowController winCtrl = new OptionsMenu.WindowController() {
            @Override
            public void applyVsync(boolean vsync) {
                glfwSwapInterval(vsync ? 1 : 0);
            }

            @Override
            public void applyFullscreen(boolean fullscreen) {
                setFullscreen(fullscreen);
            }
        };
        OptionsMenu optionsMenu = new OptionsMenu(settings, winCtrl, sound);
        TitleFlow title = new TitleFlow();

        if (settings.fullscreen) {
            setFullscreen(true);
        }

        Game game = null;

        int[] fbw = new int[1], fbh = new int[1];
        double lastTime = glfwGetTime();
        double fpsTimer = lastTime;
        int frames = 0;

        int[] winw = new int[1], winh = new int[1];

        while (!glfwWindowShouldClose(window)) {
            // clear last frame's one-shot events BEFORE polling, so the events
            // gathered here stay visible to this frame's update
            input.endFrame();
            glfwPollEvents();

            double now = glfwGetTime();
            double dt = now - lastTime;
            lastTime = now;

            glfwGetFramebufferSize(window, fbw, fbh);
            glfwGetWindowSize(window, winw, winh);
            int w = fbw[0], h = fbh[0];
            // cursor coords arrive in window space; the GUI works in
            // framebuffer pixels (they differ on HiDPI displays)
            if (winw[0] > 0 && winh[0] > 0) {
                input.setGuiScale(w / (float) winw[0], h / (float) winh[0]);
            }
            if (w > 0 && h > 0) {
                glViewport(0, 0, w, h);

                if (game != null) {
                    game.frame(dt, w, h);
                    if (game.quitToTitleRequested()) {
                        game.delete();
                        game = null;
                        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                        input.resetMouse();
                        title.reset();
                        glfwSetWindowTitle(window, "BlockForge");
                    }
                } else {
                    glClearColor(0.08f, 0.08f, 0.1f, 1f);
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    gui.begin(input, w, h, now);
                    title.frame(gui, input);
                    gui.end();

                    WorldInfo start = title.consumeStart();
                    if (start != null) {
                        game = new Game(window, input, gui, sound, settings, optionsMenu,
                                start, atlasTexture);
                    }
                    if (title.quitRequested()) {
                        glfwSetWindowShouldClose(window, true);
                    }
                }
            }

            glfwSwapBuffers(window);

            frames++;
            if (now - fpsTimer >= 0.5) {
                double fps = frames / (now - fpsTimer);
                frames = 0;
                fpsTimer = now;
                if (game != null) {
                    glfwSetWindowTitle(window, game.debugTitle(fps));
                }
            }
        }

        if (game != null) {
            game.saveAll();
            game.delete();
        }
        settings.save();
        gui.delete();
        sound.delete();
        glDeleteTextures(atlasTexture);
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }

    private static void setFullscreen(boolean fullscreen) {
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        if (mode == null) return;
        if (fullscreen) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            glfwGetWindowPos(window, x, y);
            glfwGetWindowSize(window, w, h);
            windowedX = x[0];
            windowedY = y[0];
            windowedW = w[0];
            windowedH = h[0];
            glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            glfwSetWindowMonitor(window, NULL, windowedX, windowedY, windowedW, windowedH, 0);
        }
    }
}
