/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.projecttango.examples.java.hellovideo;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A simple OpenGL renderer that renders the Tango RGB camera texture on a full-screen background.
 */
public class HelloVideoRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = HelloVideoRenderer.class.getSimpleName();

    private final String vss =
            "attribute vec2 vPosition;\n" +
            "attribute vec2 vTexCoord;\n" +
            "varying vec2 texCoord;\n" +
            "void main() {\n" +
            "  texCoord = vTexCoord;\n" +
            "  gl_Position = vec4(vPosition.x, vPosition.y, 0.0, 1.0);\n" +
            "}";

    private final String fss =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "varying vec2 texCoord;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture,texCoord);\n" +
            "}";

    /**
     * A small callback to allow the caller to introduce application-specific code to be executed
     * in the OpenGL thread.
     */
    public interface RenderCallback {
        void preRender();
    }

    private FloatBuffer mVertex;
    private FloatBuffer mTexCoord;
    private ShortBuffer mIndices;
    private int[] mVbos;
    private int[] mTextures = new int[1];
    private int mProgram;
    private RenderCallback mRenderCallback;

    public HelloVideoRenderer(RenderCallback callback) {
        mRenderCallback = callback;
        mTextures[0] = 0;
        // Vertex positions.
        float[] vtmp = { 1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f };
        // Vertex texture coords.
        float[] ttmp = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
        // Indices.
        short[] itmp = {0, 1, 2, 3};
        mVertex = ByteBuffer.allocateDirect(vtmp.length * Float.SIZE / 8).order(
                ByteOrder.nativeOrder()).asFloatBuffer();
        mVertex.put(vtmp);
        mVertex.position(0);
        mTexCoord = ByteBuffer.allocateDirect(ttmp.length * Float.SIZE / 8).order(
                ByteOrder.nativeOrder()).asFloatBuffer();
        mTexCoord.put(ttmp);
        mTexCoord.position(0);
        mIndices = ByteBuffer.allocateDirect(itmp.length * Short.SIZE / 8).order(
                ByteOrder.nativeOrder()).asShortBuffer();
        mIndices.put(itmp);
        mIndices.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        createTextures();
        createCameraVbos();
        GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
        mProgram = getProgram(vss, fss);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Call application-specific code that needs to run on the OpenGL thread
        mRenderCallback.preRender();

        GLES20.glUseProgram(mProgram);

        // Don't write depth buffer because we want to draw the camera as background
        GLES20.glDepthMask(false);

        int ph = GLES20.glGetAttribLocation(mProgram, "vPosition");
        int tch = GLES20.glGetAttribLocation(mProgram, "vTexCoord");
        int th = GLES20.glGetUniformLocation(mProgram, "sTexture");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextures[0]);
        GLES20.glUniform1i(th, 0);

        GLES20.glEnableVertexAttribArray(ph);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbos[0]);
        GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);

        GLES20.glEnableVertexAttribArray(tch);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbos[1]);
        GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mVbos[2]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 4, GLES20.GL_UNSIGNED_SHORT, 0);

        // Unbind.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        // Enable depth write again for any additional rendering on top of the camera surface
        GLES20.glDepthMask(true);
    }

    private void createTextures() {
        mTextures = new int[1];
        GLES20.glGenTextures(1, mTextures, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextures[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
    }

    /**
     * Creates and populeates vertex buffer objects for rendering the camera.
     */
    private void createCameraVbos() {
        mVbos = new int[3];
        // Generate 3 buffers. Vertex buffer, texture buffer and index buffer.
        GLES20.glGenBuffers(3, mVbos, 0);
        // Bind to vertex buffer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbos[0]);
        // Populate it.
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVertex.capacity() * Float.SIZE / 8,
                mVertex, GLES20.GL_STATIC_DRAW); // 4 2D vertex of floats.

        // Bind to texture buffer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbos[1]);
        // Populate it.
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mTexCoord.capacity() * Float.SIZE / 8,
                mTexCoord, GLES20.GL_STATIC_DRAW); // 4 2D texture coords of floats.

        // Bind to indices buffer
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mVbos[2]);
        // Populate it.
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, mIndices.capacity() * Short.SIZE / 8,
                mIndices, GLES20.GL_STATIC_DRAW); // 4 short indices

        // Unbind buffer.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private int getProgram(String vShaderSrc, String fShaderSrc) {
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            return 0;
        }
        int vShader = loadShader(GLES20.GL_VERTEX_SHADER, vShaderSrc);
        int fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fShaderSrc);
        GLES20.glAttachShader(program, vShader);
        GLES20.glAttachShader(program, fShader);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            Log.e(TAG, "Could not link program");
            Log.v(TAG, "Could not link program:" +
                    GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private int loadShader(int type, String shaderSrc) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderSrc);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader");
            Log.v(TAG, "Could not compile shader:" +
                    GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    public int getTextureId() {
        return mTextures[0];
    }
}
