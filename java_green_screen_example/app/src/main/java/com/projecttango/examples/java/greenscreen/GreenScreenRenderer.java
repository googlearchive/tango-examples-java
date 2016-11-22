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

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPointCloudData;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * An OpenGL renderer that renders the Tango RGB camera texture and a depth texture to filter it.
 * The objects that are nearer than a threshold are rendered as the Tango RGB camera texture, and
 * those who are further away are renderer as a background image.
 */
public class GreenScreenRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = GreenScreenRenderer.class.getSimpleName();

    /**
     * A small callback to allow the caller to introduce application-specific code to be executed
     * in the OpenGL thread.
     */
    public interface RenderCallback {
        void preRender();

        void onScreenshotTaken(Bitmap screenshot);
    }

    private RenderCallback mRenderCallback;
    private GreenScreen mGreenScreen;
    private DepthTexture mDepthTexture;
    private Context mContext;

    private TangoPointCloudData mPointCloud;
    private float[] mProjectionMatrix = new float[16];
    private float[] mModelMatrix = new float[16];

    private boolean mTakeScreenshot;
    private int mDefaultViewportWidth;
    private int mDefaultViewportHeight;

    public GreenScreenRenderer(Context context, RenderCallback callback) {
        mContext = context;
        mRenderCallback = callback;
        mGreenScreen = new GreenScreen();
        mDepthTexture = new DepthTexture();
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        // Enable depth test to discard fragments that are behind of another fragment.
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        // Enable face culling to discard back facing triangles.
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap backgroundBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable
                .background, options);
        mGreenScreen.setUpProgramAndBuffers(backgroundBitmap);
        mDepthTexture.resetDepthTexture();
    }

    /**
     * Update background texture's UV coordinates when device orientation is changed. i.e change
     * between landscape and portrait mode.
     */
    public void updateColorCameraTextureUv(int rotation) {
        mGreenScreen.updateTextureUv(rotation);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mDefaultViewportWidth = width;
        mDefaultViewportHeight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Call application-specific code that needs to run on the OpenGL thread.
        mRenderCallback.preRender();
        float[] mMV = new float[16];
        float[] mMVP = new float[16];
        float[] viewMatrix = new float[16];
        Matrix.setIdentityM(viewMatrix, 0);
        // Invert Z component to get negative values. We are not inverting Y because we want a
        // texture that matches the Y component of the RGB camera.
        viewMatrix[10] = -1;
        Matrix.multiplyMM(mMV, 0, viewMatrix, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mMVP, 0, mProjectionMatrix, 0, mMV, 0);

        mDepthTexture.renderDepthTexture(mPointCloud, mMVP);
        mGreenScreen.setDepthTexture(mDepthTexture.getDepthTextureId());

        GLES20.glViewport(0, 0, mDefaultViewportWidth, mDefaultViewportHeight);
        // Don't write depth buffer because we want to draw the camera as background.
        GLES20.glDepthMask(false);
        mGreenScreen.drawAsBackground();
        // Enable depth buffer again for AR.
        GLES20.glDepthMask(true);
        GLES20.glCullFace(GLES20.GL_BACK);

        // Take screenshot if needed.
        if (mTakeScreenshot) {
            mTakeScreenshot = false;
            Bitmap screenshot = createBitmapFromGLSurface(0, 0, mDefaultViewportWidth,
                    mDefaultViewportHeight);
            mRenderCallback.onScreenshotTaken(screenshot);
        }
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public int getTextureId() {
        return mGreenScreen == null ? -1 : mGreenScreen.getTextureId();
    }

    /**
     * Set the Projection matrix matching the Tango RGB camera in order to be able to do
     * Augmented Reality.
     */
    public void setProjectionMatrix(float[] matrixFloats) {
        mProjectionMatrix = matrixFloats;
    }

    public void setCameraIntrinsics(TangoCameraIntrinsics intrinsics) {
        mDepthTexture.setTextureSize(intrinsics.width, intrinsics.height);
    }

    public void updateModelMatrix(float[] colorTdepth) {
        mModelMatrix = colorTdepth;
    }

    public void updatePointCloud(TangoPointCloudData pointCloud) {
        mPointCloud = pointCloud;
    }

    public void setDepthThreshold(float depthThreshold) {
        mGreenScreen.setDepthThreshold(depthThreshold);
    }

    /**
     * This method sets a flag for saving the view in the next frame to a screenshot.
     */
    public void takeScreenshot() {
        mTakeScreenshot = true;
    }

    /**
     * Read pixels from buffer to make a bitmap.
     *
     * @throws RuntimeException if there is a GLException.
     * @trhows OutOfMemoryError
     */
    private Bitmap createBitmapFromGLSurface(int x, int y, int w, int h) throws OutOfMemoryError {
        int bitmapBuffer[] = new int[w * h];
        int bitmapSource[] = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);

        try {
            GLES20.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            // Transformation needed from RGBA to ARGB due to different formats used by OpenGL
            // and Android.
            int offset1, offset2;
            for (int i = 0; i < h; i++) {
                offset1 = i * w;
                offset2 = (h - i - 1) * w;
                for (int j = 0; j < w; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int green = texturePixel & 0x0000ff00;
                    int blue = (texturePixel >> 16) & 0xff;
                    int alpha = texturePixel & 0xff000000;
                    int pixel = alpha | red | green | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            Log.e(TAG, "Error while creating bitmap from GLSurface.");
            throw new RuntimeException(e);
        }
        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);
    }
}
