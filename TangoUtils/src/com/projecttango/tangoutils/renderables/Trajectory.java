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
import android.util.Log;

/**
 * {@link Renderable} OpenGL object showing the Trajectory of the Project Tango
 * device in 3D space. Points are added when the trajectory is updated by
 * passing translation data obtained from Tango Pose Data.
 */
public class Trajectory extends Renderable {

    private static final int COORDS_PER_VERTEX = 3;
    private static final float MIN_DISTANCE_CHECK = 0.025f;

    /** Note: due to resetPath() logic, keep this as a multiple of 9 **/
    private static final int MAX_VERTICES = 9000;
    private static final int BYTES_PER_FLOAT = 4;
    private static int mTrajectoryCount = 0;

    private static final String TAG = Trajectory.class.getSimpleName();
    private String mVertexShaderCode = "uniform mat4 uMVPMatrix;"
            + "attribute vec4 vPosition;" + "uniform vec4 aColor;"
            + "varying vec4 vColor;" + "void main() {" + "gl_PointSize = 5.0;"
            + "vColor=aColor;" + "gl_Position = uMVPMatrix * vPosition;" + "}";
    private String mFragmentShaderCode = "precision mediump float;"
            + "varying vec4 vColor;" + "void main() {"
            + "gl_FragColor = vColor;" + "}";
    private FloatBuffer mVertexBuffer;
    private float[] mColor = { 0.22f, 0.28f, 0.67f, 1.0f };
    private final int mProgram;
    private int mPosHandle;
    private int mMVPMatrixHandle;
    private int mColorHandle;
    private int mLineWidth;

    public Trajectory(int lineWidth) {
        mLineWidth = lineWidth;
        // Reset the model matrix to the identity
        Matrix.setIdentityM(getModelMatrix(), 0);

        // Allocate a vertex buffer
        ByteBuffer vertexByteBuffer = ByteBuffer.allocateDirect(MAX_VERTICES
                * BYTES_PER_FLOAT);
        vertexByteBuffer.order(ByteOrder.nativeOrder());
        mVertexBuffer = vertexByteBuffer.asFloatBuffer();

        // Load the vertex and fragment shaders, then link the program
        int vertexShader = RenderUtils.loadShader(GLES20.GL_VERTEX_SHADER,
                mVertexShaderCode);
        int fragShader = RenderUtils.loadShader(GLES20.GL_FRAGMENT_SHADER,
                mFragmentShaderCode);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragShader);
        GLES20.glLinkProgram(mProgram);
    }

    // float[] color should contain only 4 elements.
    public Trajectory(int lineWidth, float[] color) {
        mLineWidth = lineWidth;
        mColor = color;
        // Reset the model matrix to the identity
        Matrix.setIdentityM(getModelMatrix(), 0);

        // Allocate a vertex buffer
        ByteBuffer vertexByteBuffer = ByteBuffer.allocateDirect(MAX_VERTICES
                * BYTES_PER_FLOAT);
        vertexByteBuffer.order(ByteOrder.nativeOrder());
        mVertexBuffer = vertexByteBuffer.asFloatBuffer();

        // Load the vertex and fragment shaders, then link the program
        int vertexShader = RenderUtils.loadShader(GLES20.GL_VERTEX_SHADER,
                mVertexShaderCode);
        int fragShader = RenderUtils.loadShader(GLES20.GL_FRAGMENT_SHADER,
                mFragmentShaderCode);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragShader);
        GLES20.glLinkProgram(mProgram);
    }

    public void updateTrajectory(float[] translation) {
        mVertexBuffer.position(mTrajectoryCount * 3);
        if (((mTrajectoryCount + 1) * 3) >= MAX_VERTICES) {
            Log.w(TAG, "Clearing float buffer");
            resetPath();
        }
        float dx = 0, dy = 0, dz = 0;
        try {
            dx = mVertexBuffer.get(mVertexBuffer.position() - 3)
                    - translation[0];
            dy = mVertexBuffer.get(mVertexBuffer.position() - 2)
                    - translation[2];
            dz = mVertexBuffer.get(mVertexBuffer.position() - 1)
                    - (-translation[1]);
        } catch (IndexOutOfBoundsException e) {
            mVertexBuffer.put(new float[] { translation[0], translation[2],
                    -translation[1] });
            mTrajectoryCount++;
        }
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > MIN_DISTANCE_CHECK) {
            mVertexBuffer.put(new float[] { translation[0], translation[2],
                    -translation[1] });
            mTrajectoryCount++;
        }
    }

    public void resetPath() {
        int currentPosition = mVertexBuffer.position();
        int pointsToGet = (MAX_VERTICES / 3);
        mVertexBuffer.position(currentPosition - pointsToGet);

        float[] tail = new float[pointsToGet];
        mVertexBuffer.get(tail, 0, pointsToGet);

        mVertexBuffer.clear();
        mVertexBuffer.put(tail);

        mTrajectoryCount = pointsToGet / 3;
    }

    public void clearPath() {
        ByteBuffer vertexByteBuffer = ByteBuffer.allocateDirect(MAX_VERTICES
                * BYTES_PER_FLOAT);
        vertexByteBuffer.order(ByteOrder.nativeOrder());
        mVertexBuffer = vertexByteBuffer.asFloatBuffer();
    }

    @Override
    public void draw(float[] viewMatrix, float[] projectionMatrix) {
        GLES20.glUseProgram(mProgram);
        mVertexBuffer.position(0);

        // Compose the model, view, and projection matrices into a single m-v-p
        // matrix
        updateMvpMatrix(viewMatrix, projectionMatrix);

        // Load vertex attribute data
        mPosHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        GLES20.glVertexAttribPointer(mPosHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, 0, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(mPosHandle);

        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, getMvpMatrix(), 0);

        mColorHandle = GLES20.glGetUniformLocation(mProgram, "aColor");
        GLES20.glUniform4f(mColorHandle, mColor[0], mColor[1], mColor[2],
                mColor[3]);
        GLES20.glLineWidth(mLineWidth);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, mTrajectoryCount);
    }

    public void setColor(float[] color) {
        mColor = color;
    }
}