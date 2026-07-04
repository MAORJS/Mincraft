package com.blockforge;

import com.blockforge.world.Blocks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Platform;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.glViewport;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Entry point: window creation and the main loop.
 *
 * On macOS run with the JVM flag -XstartOnFirstThread (GLFW requirement).
 */
public final class Main {

    public static void main(String[] args) {
        if (Platform.get() == Platform.MACOSX
                && System.getProperty("os.arch") != null
                && !"true".equalsIgnoreCase(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + ProcessHandle.current().pid()))) {
            System.out.println("Note: on macOS, launch with: java -XstartOnFirstThread -jar blockforge.jar");
        }

        long seed = args.length > 0 ? parseSeed(args[0]) : 1337L;

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

        long window = glfwCreateWindow(1280, 720, "BlockForge", NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("could not create window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // vsync
        GL.createCapabilities();

        Input input = new Input();
        input.install(window);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }

        // block + texture registration must happen after GL is current
        // (textures upload in the Renderer constructor inside Game)
        Blocks.registerAll();

        Game game = new Game(window, input, seed);

        int[] fbw = new int[1], fbh = new int[1];
        double lastTime = glfwGetTime();
        double fpsTimer = lastTime;
        int frames = 0;
        double fps = 0;

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            double dt = now - lastTime;
            lastTime = now;

            glfwGetFramebufferSize(window, fbw, fbh);
            if (fbw[0] > 0 && fbh[0] > 0) {
                glViewport(0, 0, fbw[0], fbh[0]);
                game.frame(dt, fbw[0], fbh[0]);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();

            frames++;
            if (now - fpsTimer >= 0.5) {
                fps = frames / (now - fpsTimer);
                frames = 0;
                fpsTimer = now;
                glfwSetWindowTitle(window, game.debugTitle(fps));
            }
        }

        game.delete();
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }

    private static long parseSeed(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }
}
