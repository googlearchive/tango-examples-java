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

import com.google.atap.tangoservice.TangoPointCloudData;

import android.opengl.GLES20;
import android.util.Log;

/**
 * This class renders the point cloud as an OpenGL depth texture.
 */
public class DepthTexture {
    private static final String TAG = DepthTexture.class.getSimpleName();

    private final String mVss =
            "precision mediump float;\n" +
            "attribute vec4 a_vertex;\n" +
            "\n" +
            "uniform mat4 u_mvp;\n" +
            "uniform float u_pointsize;\n" +
            "\n" +
            "void main() {\n" +
            "  gl_PointSize = u_pointsize;\n" +
            "  gl_Position = u_mvp * a_vertex;\n" +
            "}\n";
    private final String mFss =
            "precision mediump float;\n" +
            "void main() {\n" +
            "  gl_FragColor = vec4(1.0,1.0,1.0,1.0);\n" +
            "}\n";

    // Window size for splatter upsample.
    private static final int POINT_SIZE = 15;

    private int mTexWidth;
    private int mTexHeight;

    private int mDepthTexture;
    private int mFrameBuffer;
    private int mVertexBuffer;
    private int mProgram;
    private int mMvpHandle;
    private int mVertexHandle;

    public boolean createOrBindGPUTexture() {
        if (mDepthTexture != 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
            GLES20.glViewport(0, 0, mTexWidth, mTexHeight);
            return false;
        } else {
            int[] tempFrameBuffers = new int[1];
            int[] tempTextures = new int[1];
            GLES20.glGenBuffers(1, tempFrameBuffers, 0);
            GLES20.glGenTextures(1, tempTextures, 0);
            mDepthTexture = tempTextures[0];
            mFrameBuffer = tempFrameBuffers[0];
            mProgram = OpenGlHelper.createProgram(mVss, mFss);

            GLES20.glUseProgram(mProgram);
            mMvpHandle = GLES20.glGetUniformLocation(mProgram, "u_mvp");
            // Assume these are constant for the life the program
            int pointSizeHandle =
                    GLES20.glGetUniformLocation(mProgram, "u_pointsize");
            GLES20.glUniform1f(pointSizeHandle, POINT_SIZE);
            mVertexHandle = GLES20.glGetAttribLocation(mProgram, "a_vertex");

            // Generate Vertex buffer.
            int[] tempVertexBuffers = new int[1];
            GLES20.glGenBuffers(1, tempVertexBuffers, 0);
            mVertexBuffer = tempVertexBuffers[0];

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mDepthTexture);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_DEPTH_COMPONENT, mTexWidth,
                    mTexHeight, 0, GLES20.GL_DEPTH_COMPONENT, GLES20.GL_UNSIGNED_SHORT, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20
                    .GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20
                    .GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20
                    .GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20
                    .GL_NEAREST);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                    GLES20.GL_TEXTURE_2D,
                    mDepthTexture, 0);

            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20
                    .GL_FRAMEBUFFER_COMPLETE) {
                Log.d(TAG, "Frame buffer could not be completed");
            }

            return true;
        }
    }

    public void renderDepthTexture(TangoPointCloudData pointCloud, float[] mvpMatrix) {
        if (pointCloud != null && mvpMatrix != null) {
            createOrBindGPUTexture();

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            GLES20.glUseProgram(mProgram);

            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBuffer);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                    pointCloud.numPoints * 4 * Float.SIZE / 8,
                    pointCloud.points, GLES20.GL_STATIC_DRAW);

            GLES20.glUniformMatrix4fv(mMvpHandle, 1, false, mvpMatrix, 0);

            GLES20.glEnableVertexAttribArray(mVertexHandle);
            GLES20.glVertexAttribPointer(mVertexHandle, 4, GLES20.GL_FLOAT, false, Float
                    .SIZE / 8 * 4, 0);

            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pointCloud.numPoints);
            GLES20.glDisableVertexAttribArray(mVertexHandle);

            int e = GLES20.glGetError();
            if (e != GLES20.GL_NO_ERROR) {
                Log.d(TAG, "Error rendering depth texture");
            }

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glUseProgram(0);
        }
    }

    /**
     * Reset depth texture to 0 so it is re-generated in the next render call.
     */
    public void resetDepthTexture() {
        mDepthTexture = 0;
    }

    public int getDepthTextureId() {
        return mDepthTexture;
    }

    public void setTextureSize(int width, int height) {
        mTexWidth = width;
        mTexHeight = height;
    }

}
