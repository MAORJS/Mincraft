package com.blockforge;

import static org.lwjgl.glfw.GLFW.*;

/** Keyboard and mouse state collected from GLFW callbacks. */
public final class Input {

    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keysPressed = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] mouse = new boolean[8];
    private final boolean[] mousePressed = new boolean[8];

    public double mouseDX, mouseDY;
    public double scrollY;

    private double lastX, lastY;
    private boolean firstMouse = true;

    public void install(long window) {
        glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            if (key < 0 || key > GLFW_KEY_LAST) return;
            if (action == GLFW_PRESS) {
                keys[key] = true;
                keysPressed[key] = true;
            } else if (action == GLFW_RELEASE) {
                keys[key] = false;
            }
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button < 0 || button >= mouse.length) return;
            if (action == GLFW_PRESS) {
                mouse[button] = true;
                mousePressed[button] = true;
            } else if (action == GLFW_RELEASE) {
                mouse[button] = false;
            }
        });
        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (firstMouse) {
                lastX = x;
                lastY = y;
                firstMouse = false;
            }
            mouseDX += x - lastX;
            mouseDY += y - lastY;
            lastX = x;
            lastY = y;
        });
        glfwSetScrollCallback(window, (w, dx, dy) -> scrollY += dy);
    }

    public boolean down(int key) {
        return keys[key];
    }

    /** True once per key press. */
    public boolean pressed(int key) {
        return keysPressed[key];
    }

    public boolean mouseDown(int button) {
        return mouse[button];
    }

    /** True once per click. */
    public boolean mouseClicked(int button) {
        return mousePressed[button];
    }

    /** Called at the end of each frame to reset one-shot events. */
    public void endFrame() {
        java.util.Arrays.fill(keysPressed, false);
        java.util.Arrays.fill(mousePressed, false);
        mouseDX = 0;
        mouseDY = 0;
        scrollY = 0;
    }

    /** Forget the last cursor position (after re-capturing the mouse). */
    public void resetMouse() {
        firstMouse = true;
    }
}
