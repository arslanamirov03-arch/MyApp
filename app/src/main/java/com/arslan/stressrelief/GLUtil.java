package com.arslan.stressrelief;

import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/** Shader loading / compiling helpers. */
final class GLUtil {

    static final String TAG = "StressFire";

    private static String common;

    private GLUtil() {
    }

    static String readAsset(Context ctx, String path) {
        AssetManager am = ctx.getAssets();
        try (InputStream in = am.open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), Charset.forName("UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException("cannot read asset " + path, e);
        }
    }

    private static String common(Context ctx) {
        if (common == null) {
            common = readAsset(ctx, "shaders/common.glsl");
        }
        return common;
    }

    private static int compile(int type, String src, String label) {
        int id = GLES30.glCreateShader(type);
        GLES30.glShaderSource(id, src);
        GLES30.glCompileShader(id);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(id);
            Log.e(TAG, "shader compile failed (" + label + "):\n" + log);
            GLES30.glDeleteShader(id);
            throw new RuntimeException("shader compile failed: " + label + "\n" + log);
        }
        return id;
    }

    /**
     * Builds a program from two asset files. Every stage gets the version line,
     * the caller supplied defines and {@code common.glsl} prepended.
     */
    static int program(Context ctx, String vertAsset, String fragAsset, String defines) {
        String head = "#version 300 es\n" + (defines == null ? "" : defines) + common(ctx) + "\n";
        int vs = compile(GLES30.GL_VERTEX_SHADER, head + readAsset(ctx, vertAsset), vertAsset);
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, head + readAsset(ctx, fragAsset), fragAsset);

        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, vs);
        GLES30.glAttachShader(p, fs);
        GLES30.glLinkProgram(p);

        int[] ok = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(p);
            Log.e(TAG, "link failed (" + fragAsset + "):\n" + log);
            throw new RuntimeException("program link failed: " + fragAsset + "\n" + log);
        }
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);
        return p;
    }

    static void bindTex(int program, String name, int unit, int texture) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, name), unit);
    }

    static void f1(int p, String n, float v) {
        GLES30.glUniform1f(GLES30.glGetUniformLocation(p, n), v);
    }

    static void f2(int p, String n, float a, float b) {
        GLES30.glUniform2f(GLES30.glGetUniformLocation(p, n), a, b);
    }

    static void i1(int p, String n, int v) {
        GLES30.glUniform1i(GLES30.glGetUniformLocation(p, n), v);
    }

    static void checkGl(String where) {
        int e = GLES30.glGetError();
        if (e != GLES30.GL_NO_ERROR) {
            Log.w(TAG, "GL error 0x" + Integer.toHexString(e) + " at " + where);
        }
    }
}
