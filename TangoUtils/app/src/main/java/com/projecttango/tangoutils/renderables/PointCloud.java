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
import java.util.concurrent.atomic.AtomicBoolean;

import android.opengl.GLES20;
import android.opengl.Matrix;

/**
 * {@link Renderable} OpenGL showing a PointCloud obtained from Tango XyzIj
 * data. The point count can vary over as the information is updated.
 */
public class PointCloud extends Renderable {

    private static final int COORDS_PER_VERTEX = 3;

    private static final String sVertexShaderCode = "uniform mat4 uMVPMatrix;"
            + "attribute vec4 vPosition;" + "varying vec4 vColor;"
            + "void main() {" + "gl_PointSize = 5.0;"
            + "  gl_Position = uMVPMatrix * vPosition;"
            + "  vColor = vPosition;" + "}";
    private static final String sFragmentShaderCode = "precision mediump float;"
            + "varying vec4 vColor;"
            + "void main() {"
            + "  gl_FragColor = vec4(vColor);" + "}";

    private static final int BYTES_PER_FLOAT = 4;
    private static final int POINT_TO_XYZ = 3;

    int mVertexVBO; // VertexBufferObject.
    private AtomicBoolean mUpdateVBO = new AtomicBoolean();
    private volatile FloatBuffer mPointCloudBuffer;

    private final int mProgram;
    private int mPosHandle;
    private int mMVPMatrixHandle;
    private int mPointCount;
    private float mAverageZ;

    public PointCloud(int maxDepthPoints) {
        mAverageZ = 0;
        int vertexShader = RenderUtils.loadShader(GLES20.GL_VERTEX_SHADER,
                sVertexShaderCode);
        int fragShader = RenderUtils.loadShader(GLES20.GL_FRAGMENT_SHADER,
                sFragmentShaderCode);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragShader);
        GLES20.glLinkProgram(mProgram);
        Matrix.setIdentityM(getModelMatrix(), 0);

        final int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        mVertexVBO = buffers[0];        
    }

    public synchronized void UpdatePoints(FloatBuffer pointCloudFloatBuffer) {
        //save the reference in order to update this in the proper thread.
        mPointCloudBuffer = pointCloudFloatBuffer;

        //signal the update
        mUpdateVBO.set(true);
    }

    @Override
    public synchronized void draw(float[] viewMatrix, float[] projectionMatrix) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexVBO);
            
        if (mUpdateVBO.getAndSet(false)) {
            if (mPointCloudBuffer != null) {
                mPointCloudBuffer.position(0);
                // Pass the info to the VBO
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mPointCloudBuffer.capacity()
                        * BYTES_PER_FLOAT, mPointCloudBuffer, GLES20.GL_STATIC_DRAW);
                mPointCount = mPointCloudBuffer.capacity() / 3;
                float totalZ = 0;
                for (int i = 0; i < mPointCloudBuffer.capacity() - 3; i = i + 3) {
                    totalZ = totalZ + mPointCloudBuffer.get(i + 2);
                }
                if (mPointCount != 0)
                    mAverageZ = totalZ / mPointCount;
                // signal the update
                mUpdateVBO.set(true);
            }
            mPointCloudBuffer = null;
        }

        if (mPointCount > 0) {

            GLES20.glUseProgram(mProgram);
            updateMvpMatrix(viewMatrix, projectionMatrix);
            GLES20.glVertexAttribPointer(mPosHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0,
                    0);
            GLES20.glEnableVertexAttribArray(mPosHandle);
            GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, getMvpMatrix(), 0);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, mPointCount);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public float getAverageZ() {
        return mAverageZ;
    }

    public int getPointCount() {
        return mPointCount;
    }
}
