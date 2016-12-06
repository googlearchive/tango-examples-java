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

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.util.Map;

import com.projecttango.examples.java.occlusion.meshing.GridIndex;
import com.projecttango.examples.java.occlusion.meshing.MeshMaterial;
import com.projecttango.examples.java.occlusion.meshing.MeshSegment;

/**
 * This class renders the meshes as an OpenGL depth texture.
 */
public class DepthTexture {
    private static final String TAG = DepthTexture.class.getSimpleName();

    private int mTexWidth;
    private int mTexHeight;

    private int mDepthTexture;
    private int mFrameBuffer;

    private MeshMaterial mMeshMaterial;
    private float[] mModelMatrix = new float[16];

    public DepthTexture() {
        mMeshMaterial = new MeshMaterial();
    }

    public boolean createOrBindGpuTexture() {
        if (mDepthTexture != 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
            return false;
        } else {
            int[] tempFrameBuffers = new int[1];
            int[] tempTextures = new int[1];
            GLES20.glGenBuffers(1, tempFrameBuffers, 0);
            GLES20.glGenTextures(1, tempTextures, 0);
            mDepthTexture = tempTextures[0];
            mFrameBuffer = tempFrameBuffers[0];

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mDepthTexture);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_DEPTH_COMPONENT, mTexWidth,
                    mTexHeight, 0, GLES20.GL_DEPTH_COMPONENT, GLES20.GL_UNSIGNED_SHORT, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20
                    .GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20
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

    public void renderDepthTexture(Map<GridIndex, MeshSegment> meshMap, float[] vpMatrix) {
        createOrBindGpuTexture();
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        float[] mvpMatrix = new float[16];
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, mModelMatrix, 0);

        mMeshMaterial.start(mvpMatrix);
        for (MeshSegment mesh : meshMap.values()) {
            drawMesh(mesh);
        }

        int e = GLES20.glGetError();
        if (e != GLES20.GL_NO_ERROR) {
            Log.d(TAG, "Error rendering depth texture: " + e);
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glUseProgram(0);
    }

    /**
     * Reset depth texture to 0 so it is re-generated in the next render call.
     */
    public void resetDepthTexture() {
        mDepthTexture = 0;
        mMeshMaterial = new MeshMaterial();
    }

    public int getDepthTextureId() {
        return mDepthTexture;
    }

    public void setTextureSize(int width, int height) {
        mTexWidth = width;
        mTexHeight = height;
        mDepthTexture = 0;
    }

    private void drawMesh(MeshSegment mesh) {
        // Send the vertices.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vertexBufferId);
        GLES20.glVertexAttribPointer(mMeshMaterial.getPosHandle(), 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(mMeshMaterial.getPosHandle());
        // Draw the mesh
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBufferId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.numFaces * 3, GLES20.GL_UNSIGNED_INT, 0);
    }

    public void setModelMatrix(float[] modelMatrix) {
        System.arraycopy(modelMatrix, 0, mModelMatrix, 0, 16);
    }
}
