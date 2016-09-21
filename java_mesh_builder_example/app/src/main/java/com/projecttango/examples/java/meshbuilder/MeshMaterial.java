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

package com.projecttango.examples.java.meshbuilder;

import static android.opengl.GLES20.glUseProgram;

import android.opengl.GLES20;

/**
 * Represents a GPU material for the Mesh.
 */
public class MeshMaterial {

    private final String mVss =
            "attribute vec4 a_Position;\n" +
            "attribute vec4 a_Color;\n" +
            "\n" +
            "uniform mat4 u_mvp;\n" +
            "\n" +
            "varying vec4 vs_color;\n" +
            "void main() {\n" +
            "  gl_Position = u_mvp * a_Position;\n" +
            "  vs_color = a_Color;\n" +
            "}\n";

    private final String mFss =
            "precision mediump float;\n" +
            "\n" +
            "varying vec4 vs_color;\n" +
            "\n" +
            "void main() {\n" +
            "  gl_FragColor = vs_color;\n" +
            "}\n";

    private int mShaderProgram;
    private int mPosHandle;
    private int mAttributeColorHandle;

    private int mMVPMatrixHandle;
    private boolean mLoaded = false;

    public int getPosHandle() {
        return mPosHandle;
    }

    private void loadHandles() {
        mShaderProgram = OpenGlHelper.createProgram(mVss, mFss);
        glUseProgram(mShaderProgram);

        mPosHandle = GLES20.glGetAttribLocation(mShaderProgram, "a_Position");
        mAttributeColorHandle = GLES20.glGetAttribLocation(mShaderProgram, "a_Color");

        mMVPMatrixHandle = GLES20.glGetUniformLocation(mShaderProgram, "u_mvp");
        mLoaded = true;
    }

    public void start(float[] mvpMatrix) {
        if (!mLoaded) {
            loadHandles();
        }
        glUseProgram(mShaderProgram);
        // Send the MVP Matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
    }


    public void setupColors(MeshSegment mesh) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.colorBufferId);
        GLES20.glVertexAttribPointer(mAttributeColorHandle, 4,
                GLES20.GL_UNSIGNED_BYTE, true, 0, 0);
        GLES20.glEnableVertexAttribArray(mAttributeColorHandle);
    }

}
