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
package com.projecttango.examples.java.occlusion;

import com.google.atap.tango.mesh.TangoMesh;
import com.google.atap.tangoservice.TangoCameraIntrinsics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.projecttango.examples.java.occlusion.meshing.GridIndex;
import com.projecttango.examples.java.occlusion.meshing.MeshSegment;
import com.projecttango.examples.java.occlusion.opengl.DepthTexture;
import com.projecttango.examples.java.occlusion.opengl.OpenGlCameraPreview;
import com.projecttango.examples.java.occlusion.opengl.OpenGlSphere;

/**
 * An OpenGL renderer that renders the Tango RGB camera texture as a background and an earth sphere.
 * It also renders a depth texture to occlude the sphere if it is behind an object.
 */
public class OcclusionRenderer implements GLSurfaceView.Renderer {

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
    private DepthTexture mDepthTexture;
    private OpenGlCameraPreview mOpenGlCameraPreview;
    private OpenGlSphere mOpenGlSphere;
    private Context mContext;
    private boolean mProjectionMatrixConfigured;

    private float[] mViewMatrix = new float[16];
    private float[] mProjectionMatrix = new float[16];
    private float[] mVPMatrix = new float[16];

    public OcclusionRenderer(Context context, RenderCallback callback) {
        mContext = context;
        mRenderCallback = callback;
        mOpenGlCameraPreview = new OpenGlCameraPreview();
        mOpenGlSphere = new OpenGlSphere(0.1f, 20, 20);

        float[] worldTsphere = new float[16];
        Matrix.setIdentityM(worldTsphere, 0);
        Matrix.translateM(worldTsphere, 0, 0, 0, -2);
        mOpenGlSphere.setModelMatrix(worldTsphere);

        mDepthTexture = new DepthTexture();
        float[] worldTmesh = new float[16];
        Matrix.setIdentityM(worldTmesh, 0);
        Matrix.rotateM(worldTmesh, 0, -90, 1, 0, 0);
        mDepthTexture.setModelMatrix(worldTmesh);
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
        mOpenGlCameraPreview.setUpProgramAndBuffers();
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap earthBitmap = android.graphics.BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.earth, options);
        mOpenGlSphere.setUpProgramAndBuffers(earthBitmap, mContext);
        mDepthTexture.resetDepthTexture();
        mMeshMap.clear();
    }

    /**
     * Update background texture's UV coordinates when device orientation is changed. i.e change
     * between landscape and portrait mode.
     */
    public void updateColorCameraTextureUv(int rotation) {
        mOpenGlCameraPreview.updateTextureUv(rotation);
        mProjectionMatrixConfigured = false;
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mDepthTexture.setTextureSize(width, height);
        mOpenGlSphere.setDepthTextureSize(width, height);
        mProjectionMatrixConfigured = false;
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Call application-specific code that needs to run on the OpenGL thread.
        mRenderCallback.preRender();

        // Don't write depth buffer because we want to draw the camera as background.
        GLES20.glDepthMask(false);
        mOpenGlCameraPreview.drawAsBackground();
        // Enable depth buffer again for AR.
        GLES20.glDepthMask(true);

        updateVPMatrix();

        // Render depth texture.
        mDepthTexture.renderDepthTexture(mMeshMap, mVPMatrix);
        int depthTexture = mDepthTexture.getDepthTextureId();

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Render objects.
        mOpenGlSphere.drawSphere(mVPMatrix, depthTexture);
    }

    /**
     * Set the Projection matrix matching the Tango RGB camera in order to be able to do
     * Augmented Reality.
     */
    public void setProjectionMatrix(float[] matrixFloats, float nearPlane, float farPlane) {
        mProjectionMatrix = matrixFloats;
        mOpenGlSphere.configureCamera(nearPlane, farPlane);
        mProjectionMatrixConfigured = true;
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
     * Composes the view and projection matrices into a single VP matrix.
     */
    private void updateVPMatrix() {
        Matrix.setIdentityM(mVPMatrix, 0);
        Matrix.multiplyMM(mVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
    }

    /**
     * Update the mesh segments given a new TangoMesh.
     */
    public void updateMesh(TangoMesh tangoMesh) {
        GridIndex key = new GridIndex(tangoMesh.index);
        if (!mMeshMap.containsKey(key)) {
            mMeshMap.put(key, new MeshSegment());
        }
        MeshSegment mesh = mMeshMap.get(key);
        mesh.update(tangoMesh);
        mMeshMap.put(key, mesh);
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public int getTextureId() {
        return mOpenGlCameraPreview == null ? -1 : mOpenGlCameraPreview.getTextureId();
    }

    /**
     * Updates the earth model matrix.
     */
    public void updateEarthTransform(float[] openGlTearth) {
        mOpenGlSphere.setModelMatrix(openGlTearth);
    }

    public boolean isProjectionMatrixConfigured() {
        return mProjectionMatrixConfigured;
    }
}
