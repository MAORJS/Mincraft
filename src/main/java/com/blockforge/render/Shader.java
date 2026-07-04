package com.blockforge.render;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/** Minimal GLSL program wrapper with uniform helpers. */
public final class Shader {

    private final int program;
    private final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);

    public Shader(String vertexSrc, String fragmentSrc) {
        int vs = compile(GL_VERTEX_SHADER, vertexSrc);
        int fs = compile(GL_FRAGMENT_SHADER, fragmentSrc);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("shader link failed: " + glGetProgramInfoLog(program));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int shader = glCreateShader(type);
        glShaderSource(shader, src);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("shader compile failed: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    public void use() {
        glUseProgram(program);
    }

    private int loc(String name) {
        return glGetUniformLocation(program, name);
    }

    public void setMat4(String name, Matrix4f mat) {
        mat.get(matBuf);
        glUniformMatrix4fv(loc(name), false, matBuf);
    }

    public void setVec3(String name, float x, float y, float z) {
        glUniform3f(loc(name), x, y, z);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        glUniform4f(loc(name), x, y, z, w);
    }

    public void setFloat(String name, float v) {
        glUniform1f(loc(name), v);
    }

    public void setInt(String name, int v) {
        glUniform1i(loc(name), v);
    }

    public void delete() {
        glDeleteProgram(program);
    }
}
