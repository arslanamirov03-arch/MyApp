package com.arslan.stressrelief;

import android.content.Context;
import android.opengl.GLES30;

import java.util.HashMap;

/** A linked program with cached uniform locations. */
final class Prog {

    final int id;
    private final HashMap<String, Integer> loc = new HashMap<>();

    Prog(Context ctx, String vert, String frag, String defines) {
        id = GLUtil.program(ctx, vert, frag, defines);
    }

    private int u(String name) {
        Integer i = loc.get(name);
        if (i == null) {
            i = GLES30.glGetUniformLocation(id, name);
            loc.put(name, i);
        }
        return i;
    }

    Prog use() {
        GLES30.glUseProgram(id);
        return this;
    }

    Prog f(String n, float v) {
        GLES30.glUniform1f(u(n), v);
        return this;
    }

    Prog f2(String n, float a, float b) {
        GLES30.glUniform2f(u(n), a, b);
        return this;
    }

    Prog i(String n, int v) {
        GLES30.glUniform1i(u(n), v);
        return this;
    }

    Prog tex(String n, int unit, int texture) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glUniform1i(u(n), unit);
        return this;
    }
}
