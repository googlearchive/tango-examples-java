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

import com.google.atap.tango.mesh.TangoMesh;

import android.annotation.SuppressLint;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 *
 */
public class MeshBuilderRenderer implements GLSurfaceView.Renderer {

    /**
     * A small callback to allow the caller to introduce application-specific code to be executed
     * in the OpenGL thread.
     */
    public interface RenderCallback {
        void preRender();
    }

    @SuppressLint("UseSparseArrays")
    private final HashMap<GridIndex, MeshSegment> mMeshMap = new HashMap<GridIndex, MeshSegment>();

    private RenderCallback mRenderCallback;
    private boolean mSceneCameraConfigured = false;

    private MeshMaterial mMeshMaterial;
    private float[] mModelMatrix = new float[16];
    private float[] mViewMatrix = new float[16];
    private float[] mProjectionMatrix = new float[16];
    private float[] mMVPMatrix = new float[16];

    public MeshBuilderRenderer(RenderCallback callback) {
        mRenderCallback = callback;
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        // Enable depth test to discard fragments that are behind of another fragment.
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        // Enable face culling to discard back facing triangles.
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glDepthMask(true);
        mMeshMaterial = new MeshMaterial();
        mModelMatrix = new float[16];
        Matrix.setIdentityM(mModelMatrix, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        updateProjectionMatrix(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Call application-specific code that needs to run on the OpenGL thread.
        mRenderCallback.preRender();

        updateMVPMatrix();

        mMeshMaterial.start(mMVPMatrix);
        for (MeshSegment mesh : mMeshMap.values()) {
            drawMesh(mesh);
        }
    }


    private void drawMesh(MeshSegment mesh) {
        // Handle the colors.
        mMeshMaterial.setupColors(mesh);

        // Send the vertices.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vertexBufferId);
        GLES20.glVertexAttribPointer(mMeshMaterial.getPosHandle(), 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(mMeshMaterial.getPosHandle());
        // Draw the mesh
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBufferId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.numFaces * 3, GLES20.GL_UNSIGNED_INT, 0);
    }

    /**
     * Update the View matrix matching the pose of the Tango RGB camera.
     *
     * @param ssTcamera The transform from RGB camera to Start of Service.
     */
    public void updateViewMatrix(float[] ssTcamera) {
        float[] viewMatrix = new float[16];
        Matrix.invertM(viewMatrix, 0, ssTcamera, 0);
        mViewMatrix = viewMatrix;
    }

    /**
     * Composes the model, view and projection matrices into a single MVP matrix.
     */
    private void updateMVPMatrix() {
        float[] modelViewMatrix = new float[16];
        Matrix.setIdentityM(modelViewMatrix, 0);
        Matrix.multiplyMM(modelViewMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, modelViewMatrix, 0);
    }

    private void updateProjectionMatrix(int width, int height) {
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, (float) width / height, 0.1f, 100f);
    }

    public boolean isSceneCameraConfigured() {
        return mSceneCameraConfigured;
    }

    public void updateMesh(TangoMesh tangoMesh) {
        GridIndex key = new GridIndex(tangoMesh.index);
        if (!mMeshMap.containsKey(key)) {
            mMeshMap.put(key, new MeshSegment());
        }
        MeshSegment mesh = mMeshMap.get(key);
        mesh.update(tangoMesh);
        mMeshMap.put(key, mesh);
    }

    public void clearMeshes() {
        mMeshMap.clear();
    }
}
