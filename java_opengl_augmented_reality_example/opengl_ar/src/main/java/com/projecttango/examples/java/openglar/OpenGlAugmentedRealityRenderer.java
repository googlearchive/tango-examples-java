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
package com.projecttango.examples.java.openglar;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.projecttango.tangosupport.TangoSupport;

/**
 * An OpenGL renderer that renders the Tango RGB camera texture on a full-screen background
 * and two spheres representing the earth and the moon in Augmented Reality.
 */
public class OpenGlAugmentedRealityRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = OpenGlAugmentedRealityRenderer.class.getSimpleName();

    /**
     * A small callback to allow the caller to introduce application-specific code to be executed
     * in the OpenGL thread.
     */
    public interface RenderCallback {
        void preRender();
    }

    private RenderCallback mRenderCallback;
    private OpenGlCameraPreview mOpenGlCameraPreview;
    private OpenGlSphere mEarthSphere;
    private OpenGlSphere mMoonSphere;
    private Context mContext;

    public OpenGlAugmentedRealityRenderer(Context context, RenderCallback callback) {
        mContext = context;
        mRenderCallback = callback;
        mOpenGlCameraPreview = new OpenGlCameraPreview();
        mEarthSphere = new OpenGlSphere(0.15f, 20, 20);
        mMoonSphere = new OpenGlSphere(0.05f, 10, 10);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        // Enable depth test to discard fragments that are behind of another fragment.
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        // Enable face culling to discard back facing triangles.
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
        mOpenGlCameraPreview.setUpProgramAndBuffers();
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap earthBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable
                .earth, options);
        mEarthSphere.setUpProgramAndBuffers(earthBitmap);
        Bitmap moonBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable
                .moon, options);
        mMoonSphere.setUpProgramAndBuffers(moonBitmap);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
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
        GLES20.glCullFace(GLES20.GL_BACK);
        mEarthSphere.drawSphere();
        mMoonSphere.drawSphere();
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
     * Create the Projection matrix matching the Tango RGB camera in order to be able to do
     * Augmented Reality.
     */
    public void setProjectionMatrix(TangoCameraIntrinsics intrinsics) {
        float[] projectionMatrix = new float[16];
        double vFov = 2 * Math.atan(intrinsics.height / (2 * intrinsics.fy));
        Matrix.perspectiveM(projectionMatrix, 0, (float) Math.toDegrees(vFov), (float)
                intrinsics.width / intrinsics.height, 0.1f, 1000);
        mEarthSphere.setProjectionMatrix(projectionMatrix);
        mMoonSphere.setProjectionMatrix(projectionMatrix);
    }

    /**
     * Update the View matrix matching the pose of the Tango RGB camera.
     *
     * @param ssTcamera The transform from RGB camera to Start of Service.
     */
    public void updateViewMatrix(float[] ssTcamera) {
        float[] viewMatrix = new float[16];
        Matrix.invertM(viewMatrix, 0, ssTcamera, 0);
        mEarthSphere.setViewMatrix(viewMatrix);
        mMoonSphere.setViewMatrix(viewMatrix);
    }

    public void setMoonTransform(float[] worldTMoon) {
        mMoonSphere.setModelMatrix(worldTMoon);
    }

    public void setEarthTransform(float[] worldTEarth) {
        mEarthSphere.setModelMatrix(worldTEarth);
    }

}
