/*
 * Copyright 2014 Google Inc. All Rights Reserved.
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

package com.projecttango.tangoutils.renderables;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.opengl.GLES20;
import android.opengl.Matrix;

/**
 * {@link Renderable} OpenGL object representing XYZ axes in 3D space. X is Red,
 * Y is Green, and Z is Blue.
 */
public class CameraFrustumAndAxis extends Renderable {

    private static final int COORDS_PER_VERTEX = 3;

    private static final String sVertexShaderCode = "uniform mat4 uMVPMatrix;"
            + "attribute vec4 vPosition;" + "attribute vec4 aColor;"
            + "varying vec4 vColor;" + "void main() {" + "  vColor=aColor;"
            + "gl_Position = uMVPMatrix * vPosition;" + "}";

    private static final String sFragmentShaderCode = "precision mediump float;"
            + "varying vec4 vColor;"
            + "void main() {"
            + "gl_FragColor = vColor;" + "}";
    private FloatBuffer mVertexBuffer, mColorBuffer;

    private float mVertices[] = { 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,

    0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,

    0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f,

    0.0f, 0.0f, 0.0f, -0.4f, 0.3f, -0.5f,

    0.0f, 0.0f, 0.0f, 0.4f, 0.3f, -0.5f,

    0.0f, 0.0f, 0.0f, -0.4f, -0.3f, -0.5f,

    0.0f, 0.0f, 0.0f, 0.4f, -0.3f, -0.5f,

    -0.4f, 0.3f, -0.5f, 0.4f, 0.3f, -0.5f,

    0.4f, 0.3f, -0.5f, 0.4f, -0.3f, -0.5f,

    0.4f, -0.3f, -0.5f, -0.4f, -0.3f, -0.5f,

    -0.4f, -0.3f, -0.5f, -0.4f, 0.3f, -0.5f };

    private float mColors[] = { 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f,

    0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f,

    0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f,

    0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,

    0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,

    0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,

    0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,

    0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,

    0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,

    0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,

    0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, };

    private final int mProgram;
    private int mPosHandle, mColorHandle;
    private int mMVPMatrixHandle;

    public CameraFrustumAndAxis() {
        // Set model matrix to the identity
        Matrix.setIdentityM(getModelMatrix(), 0);

        // Put vertices into a vertex buffer
        ByteBuffer byteBuf = ByteBuffer.allocateDirect(mVertices.length * 4);
        byteBuf.order(ByteOrder.nativeOrder());
        mVertexBuffer = byteBuf.asFloatBuffer();
        mVertexBuffer.put(mVertices);
        mVertexBuffer.position(0);

        // Put colors into a color buffer
        ByteBuffer cByteBuff = ByteBuffer.allocateDirect(mColors.length * 4);
        cByteBuff.order(ByteOrder.nativeOrder());
        mColorBuffer = cByteBuff.asFloatBuffer();
        mColorBuffer.put(mColors);
        mColorBuffer.position(0);

        // Load the vertex and fragment shaders, then link the program
        int vertexShader = RenderUtils.loadShader(GLES20.GL_VERTEX_SHADER,
                sVertexShaderCode);
        int fragShader = RenderUtils.loadShader(GLES20.GL_FRAGMENT_SHADER,
                sFragmentShaderCode);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragShader);
        GLES20.glLinkProgram(mProgram);
    }

    @Override
    public synchronized void draw(float[] viewMatrix, float[] projectionMatrix) {
        GLES20.glUseProgram(mProgram);

        // Compose the model, view, and projection matrices into a single m-v-p
        // matrix
        updateMvpMatrix(viewMatrix, projectionMatrix);

        // Load vertex attribute data
        mPosHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        GLES20.glVertexAttribPointer(mPosHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, 0, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(mPosHandle);

        // Load color attribute data
        mColorHandle = GLES20.glGetAttribLocation(mProgram, "aColor");
        GLES20.glVertexAttribPointer(mColorHandle, 4, GLES20.GL_FLOAT, false,
                0, mColorBuffer);
        GLES20.glEnableVertexAttribArray(mColorHandle);

        // Draw the CameraFrustumAndAxis
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, getMvpMatrix(), 0);
        GLES20.glLineWidth(3);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, mVertices.length / 3);

    }

}