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

package com.projecttango.examples.java.occlusion.opengl;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.projecttango.examples.java.occlusion.R;

/**
 * A sphere that is rendered in AR using OpenGL.
 */
public class OpenGlSphere {

    private OpenGlMesh mMesh;
    private int[] mTextures;
    private int mProgram;

    private float[] mModelMatrix = new float[16];

    private float mNearPlane;
    private float mFarPlane;
    private float mTextureWidth;
    private float mTextureHeight;

    public OpenGlSphere(float radius, int rows, int columns) {
        float[] vtmp = new float[rows * columns * 3];
        // Generate position grid.
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                float theta = i * (float) Math.PI / (rows - 1);
                float phi = j * 2 * (float) Math.PI / (columns - 1);
                float x = (float) (radius * Math.sin(theta) * Math.cos(phi));
                float y = (float) (radius * Math.cos(theta));
                float z = (float) -(radius * Math.sin(theta) * Math.sin(phi));
                int index = i * columns + j;
                vtmp[3 * index] = x;
                vtmp[3 * index + 1] = y;
                vtmp[3 * index + 2] = z;
            }
        }

        // Create texture grid.
        float[] ttmp = new float[rows * columns * 2];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                int index = i * columns + j;
                ttmp[index * 2] = (float) j / (columns - 1);
                ttmp[index * 2 + 1] = (float) i / (rows - 1);
            }
        }

        // Create indices.
        int numIndices = 2 * (rows - 1) * columns;
        short[] itmp = new short[numIndices];
        short index = 0;
        for (int i = 0; i < rows - 1; i++) {
            if ((i & 1) == 0) {
                for (int j = 0; j < columns; j++) {
                    itmp[index++] = (short) (i * columns + j);
                    itmp[index++] = (short) ((i + 1) * columns + j);
                }
            } else {
                for (int j = columns - 1; j >= 0; j--) {
                    itmp[index++] = (short) ((i + 1) * columns + j);
                    itmp[index++] = (short) (i * columns + j);
                }
            }
        }

        mMesh = new OpenGlMesh(vtmp, ttmp, itmp, GLES20.GL_TRIANGLE_STRIP);
    }

    public void setUpProgramAndBuffers(Bitmap texture, Context context) {
        mMesh.createVbos();
        createTexture(texture);
        mProgram = OpenGlHelper.createProgram(context, R.raw.sphere_vertex_shader, R.raw
                .sphere_fragment_shader);
    }

    private void createTexture(Bitmap texture) {
        mTextures = new int[1];
        GLES20.glGenTextures(1, mTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_NEAREST);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, texture, 0);
        texture.recycle();
    }

    public void drawSphere(float[] vpMatrix, int depthTexture) {
        GLES20.glUseProgram(mProgram);
        // Enable depth write for AR.
        int sph = GLES20.glGetAttribLocation(mProgram, "a_Position");
        int sth = GLES20.glGetAttribLocation(mProgram, "a_TexCoord");

        int um = GLES20.glGetUniformLocation(mProgram, "u_MvpMatrix");
        int ut = GLES20.glGetUniformLocation(mProgram, "u_Texture");
        int dh = GLES20.glGetUniformLocation(mProgram, "u_depthTexture");
        int un = GLES20.glGetUniformLocation(mProgram, "u_NearPlane");
        int uf = GLES20.glGetUniformLocation(mProgram, "u_FarPlane");
        int uw = GLES20.glGetUniformLocation(mProgram, "u_Width");
        int uh = GLES20.glGetUniformLocation(mProgram, "u_Height");

        float[] mvpMatrix = new float[16];
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, mModelMatrix, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
        GLES20.glUniform1i(ut, 0);
        GLES20.glUniformMatrix4fv(um, 1, false, mvpMatrix, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTexture);
        GLES20.glUniform1i(dh, 1);

        GLES20.glUniform1f(un, mNearPlane);
        GLES20.glUniform1f(uf, mFarPlane);

        GLES20.glUniform1f(uw, mTextureWidth);
        GLES20.glUniform1f(uh, mTextureHeight);

        mMesh.drawMesh(sph, sth);
    }

    public void setModelMatrix(float[] modelMatrix) {
        System.arraycopy(modelMatrix, 0, mModelMatrix, 0, 16);
    }

    public void configureCamera(float nearPlane, float farPlane){
        mNearPlane = nearPlane;
        mFarPlane = farPlane;
    }

    public void setDepthTextureSize(float width, float height){
        mTextureWidth = width;
        mTextureHeight = height;
    }

}
