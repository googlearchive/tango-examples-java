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

package com.projecttango.examples.java.greenscreen;

import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;

/**
 * A preview of the RGB camera rendered as background filtered by a depth texture using OpenGL.
 * The fragments that are further than a given threshold are discarded and rendered as green.
 */
public class GreenScreen {

    private final String mVss =
            "attribute vec2 a_position;\n" +
            "attribute vec2 a_texCoord;\n" +
            "attribute vec2 a_cameraTexCoord;\n" +
            "varying vec2 v_texCoord;\n" +
            "varying vec2 v_cameraTexCoord;\n" +
            "void main() {\n" +
            "  v_texCoord = a_texCoord;\n" +
            "  v_cameraTexCoord = a_cameraTexCoord;\n" +
            "  gl_Position = vec4(a_position.x, a_position.y, 0.0, 1.0);\n" +
            "}";

    private final String mFss =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform float u_depthThreshold;\n" +
            "uniform samplerExternalOES u_cameraTexture;\n" +
            "uniform sampler2D u_backgroundTexture;\n" +
            "uniform sampler2D u_depthTexture;\n" +
            "varying vec2 v_texCoord;\n" +
            "varying vec2 v_cameraTexCoord;\n" +
            "float linearDepth(float depth){\n" +
            "  float f=100.0;\n" +
            "  float n=0.1;\n" +
            "  float z = (2.0 * n *f) / ((f + n) - depth * (f - n));\n" +
            "  return z;\n" +
            "}\n" +
            "void main() {\n" +
            "  float nonlinearZ = texture2D(u_depthTexture, v_cameraTexCoord).r * 2.0 - 1.0;\n" +
            "  float linearZ = linearDepth(nonlinearZ);\n" +
            "  if(linearZ < u_depthThreshold){\n" +
            "    gl_FragColor = texture2D(u_cameraTexture, v_cameraTexCoord);\n" +
            "  } else {\n" +
            "    gl_FragColor = texture2D(u_backgroundTexture, v_texCoord);\n" +
            "  }\n" +
            "}";

    private final float[] textureCoords0 =
            new float[]{1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f};
    private final float[] textureCoords270 =
            new float[]{1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F};
    private final float[] textureCoords180 =
            new float[]{0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
    private final float[] textureCoords90 =
            new float[]{0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F};

    private OpenGlMesh mMesh;
    private int mColorCameraTexture;
    private int mProgram;
    private int mDepthTexture = 0;
    private int mBackgroundTexture;
    private float mDepthThreshold = 2f;

    public GreenScreen() {
        mColorCameraTexture = 0;
        // Vertices positions.
        float[] vtmp = {1.0f, -1.0f, 0f, -1.0f, -1.0f, 0f, 1.0f, 1.0f, 0f, -1.0f, 1.0f, 0f};
        // Vertices texture coords.
        float[] ttmp = {1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f};
        // Vertices camera texture coords.
        float[] cttmp = new float[]{1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f};
        // Indices.
        short[] itmp = {0, 2, 1, 3};
        mMesh = new OpenGlMesh(vtmp, ttmp, cttmp, itmp, GLES20.GL_TRIANGLE_STRIP);
    }

    public void updateTextureUv(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_90:
                mMesh.setCameraTextureCoords(textureCoords90);
                break;
            case Surface.ROTATION_180:
                mMesh.setCameraTextureCoords(textureCoords180);
                break;
            case Surface.ROTATION_270:
                mMesh.setCameraTextureCoords(textureCoords270);
                break;
            default:
                mMesh.setCameraTextureCoords(textureCoords0);
                break;
        }
    }

    public void setUpProgramAndBuffers(Bitmap texture) {
        createColorCameraTexture();
        createBackgroundTexture(texture);
        mMesh.createVbos();
        mProgram = OpenGlHelper.createProgram(mVss, mFss);
    }

    private void createColorCameraTexture() {
        int[] tempTextures = new int[1];
        GLES20.glGenTextures(1, tempTextures, 0);
        mColorCameraTexture = tempTextures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mColorCameraTexture);
    }

    private void createBackgroundTexture(Bitmap texture) {
        int[] tempTextures = new int[1];
        GLES20.glGenTextures(1, tempTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tempTextures[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, texture, 0);
        texture.recycle();
        mBackgroundTexture = tempTextures[0];
    }

    public void drawAsBackground() {
        GLES20.glUseProgram(mProgram);

        int ph = GLES20.glGetAttribLocation(mProgram, "a_position");
        int tch = GLES20.glGetAttribLocation(mProgram, "a_texCoord");
        int ctch = GLES20.glGetAttribLocation(mProgram, "a_cameraTexCoord");
        int th = GLES20.glGetUniformLocation(mProgram, "u_cameraTexture");
        int bh = GLES20.glGetUniformLocation(mProgram, "u_backgroundTexture");
        int dh = GLES20.glGetUniformLocation(mProgram, "u_depthTexture");
        int thresholdh = GLES20.glGetUniformLocation(mProgram, "u_depthThreshold");
        GLES20.glUniform1f(thresholdh, mDepthThreshold * 5);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mColorCameraTexture);
        GLES20.glUniform1i(th, 0);

        // Bind depth texture to texture unit 1.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mDepthTexture);
        GLES20.glUniform1i(dh, 1);

        // Bind background texture to texture unit 2.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBackgroundTexture);
        GLES20.glUniform1i(bh, 2);

        mMesh.drawMesh(ph, tch, ctch);
    }

    public void setDepthTexture(int depthTexture) {
        mDepthTexture = depthTexture;
    }

    public int getTextureId() {
        return mColorCameraTexture;
    }

    public void setDepthThreshold(float depthThreshold) {
        mDepthThreshold = depthThreshold;
    }
}
