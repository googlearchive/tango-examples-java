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

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.tangosupport.TangoSupport;

/**
 * This is a simple example that shows how to use the Tango APIs to create an augmented reality
 * application. It display the Earth floating in space one meter in front of the device and the Moon
 * rotating around the Earth.
 * <p/>
 * This example render the TangoRGB camera into an OpenGL texture and get the device pose
 * information to update the scene camera accordingly.
 * It creates a standard Android {@code GLSurfaceView} with an OpenGL renderer and connects to
 * the Tango service with the appropriate configuration for Video rendering.
 * Each time a new RGB video frame is available through the Tango APIs, it is updated to the
 * OpenGL texture and the corresponding timestamp is printed on the logcat.
 * In addition, the device is queried for its pose at the exact time the RGB camera frame was
 * captured and the view matrix is updated to match it.
 * The intrinsics of the RGB camera are used to match the projection matrix of the AR camera.
 * <p/>
 * The OpenGL code necessary to do the Augmented Reality is in
 * {@code OpenGlAugmentedRealityRenderer}.
 * The OpenGL code necessary to understand how to render the specific texture format
 * produced by the Tango RGB camera is provided in {@code OpenGlCameraPreview}.
 * If you're looking for an example that uses a library instead of raw OpenGL, see
 * java_augmented_reality_example.
 */
public class OpenGlAugmentedRealityActivity extends Activity {
    private static final String TAG = OpenGlAugmentedRealityActivity.class.getSimpleName();
    private static final int INVALID_TEXTURE_ID = 0;

    private GLSurfaceView mSurfaceView;
    private OpenGlAugmentedRealityRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;

    // Texture rendering related fields.
    // NOTE: Naming indicates which thread is in charge of updating this variable
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    // Transform from the Earth and Moon center to OpenGL frame. This is a fixed transform.
    private float[] mOpenGLTEarthMoonCenter = new float[16];
    // Transform from Earth to Earth and Moon center. This will change over time, as the Earth is
    // rotating.
    private float[] mEarthMoonCenterTEarth = new float[16];
    // Translation from the Moon to the Earth and Moon center. This is a fixed transform.
    private float[] mEarthMoonCenterTTranslation = new float[16];
    // Rotation from the Moon to the Earth and Moon center. This will change over time, as the Moon
    // is rotating.
    private float[] mEarthMoonCenterTMoonRotation = new float[16];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
        setupRenderer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSurfaceView.onResume();
        // Set render mode to RENDERMODE_CONTINUOUSLY to force getting onDraw callbacks until the
        // Tango service is properly set-up and we start getting onFrameAvailable callbacks.
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Initialize Tango Service as a normal Android Service, since we call
        // mTango.disconnect() in onPause, this will unbind Tango Service, so
        // everytime when onResume get called, we should create a new Tango object.
        mTango = new Tango(OpenGlAugmentedRealityActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready,
            // this Runnable will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only
            // when there is no UI thread changes involved.
            @Override
            public void run() {
                synchronized (OpenGlAugmentedRealityActivity.this) {
                    TangoSupport.initialize();
                    mConfig = setupTangoConfig(mTango);

                    try {
                        setTangoListeners();
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    } catch (SecurityException e) {
                        Log.e(TAG, getString(R.string.permission_camera), e);
                    }
                    try {
                        mTango.connect(mConfig);
                        mIsConnected = true;
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (OpenGlAugmentedRealityActivity.this) {
                            mIntrinsics = mTango.getCameraIntrinsics(
                                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        }
                    }
                });
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        mSurfaceView.onPause();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        // NOTE: DO NOT lock against this same object in the Tango callback thread. Tango.disconnect
        // will block here until all Tango callback calls are finished. If you lock against this
        // object in a Tango callback thread it will cause a deadlock.
        synchronized (this) {
            try {
                mIsConnected = false;
                mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                // We need to invalidate the connected texture ID so that we cause a
                // re-connection in the OpenGL thread after resume
                mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
                mTango.disconnect();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service, plus color camera and
        // low latency IMU integration.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of
        // virtual objects with the RBG image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        return config;
    }

    /**
     * Set up the callback listeners for the Tango service, then begin using the Motion
     * Tracking API. This is called in response to the user clicking the 'Start' Button.
     */
    private void setTangoListeners() {
        // No need to add any coordinate frame pairs since we aren't using pose data from callbacks.
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();

        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // We are not using onPoseAvailable for this app.
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using onTangoEvent for this app.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Check if the frame available is for the camera we want and update its frame
                // on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Now that we are receiving onFrameAvailable callbacks, we can switch
                    // to RENDERMODE_WHEN_DIRTY to drive the render loop from this callback.
                    // This will result on a frame rate of  approximately 30FPS, in synchrony with
                    // the RGB camera driver.
                    // If you need to render at a higher rate (i.e.: if you want to render complex
                    // animations smoothly) you  can use RENDERMODE_CONTINUOUSLY throughout the
                    // application lifecycle.
                    if (mSurfaceView.getRenderMode() != GLSurfaceView.RENDERMODE_WHEN_DIRTY) {
                        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                    }

                    // Mark a camera frame is available for rendering in the OpenGL thread.
                    mIsFrameAvailableTangoThread.set(true);
                    // Trigger an OpenGL render to update the OpenGL scene with the new RGB data.
                    mSurfaceView.requestRender();
                }
            }
        });
    }

    /**
     * Here is where you would set-up your rendering logic. We're replacing it with a minimalistic,
     * dummy example using a standard GLSurfaceView and a basic renderer, for illustration purposes
     * only.
     */
    private void setupRenderer() {
        mSurfaceView.setEGLContextClientVersion(2);
        mRenderer = new OpenGlAugmentedRealityRenderer(this,
                new OpenGlAugmentedRealityRenderer.RenderCallback() {
                    private double lastRenderedTimeStamp;

                    @Override
                    public void preRender() {
                        // This is the work that you would do on your main OpenGL render thread.

                        // We need to be careful to not run any Tango-dependent code in the
                        // OpenGL thread unless we know the Tango service to be properly set-up
                        // and connected.
                        if (!mIsConnected) {
                            return;
                        }

                        // Synchronize against concurrently disconnecting the service triggered
                        // from the UI thread.
                        synchronized (OpenGlAugmentedRealityActivity.this) {
                            // Connect the Tango SDK to the OpenGL texture ID where we are
                            // going to render the camera.
                            // NOTE: This must be done after both the texture is generated
                            // and the Tango service is connected.
                            if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                                mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                        mRenderer.getTextureId());
                                mConnectedTextureIdGlThread = mRenderer.getTextureId();
                                Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                                mRenderer.setProjectionMatrix(mIntrinsics);
                            }
                            // If there is a new RGB camera frame available, update the texture and
                            // scene camera pose.
                            if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                                // {@code mRgbTimestampGlThread} contains the exact timestamp at
                                // which the rendered RGB frame was acquired.
                                mRgbTimestampGlThread =
                                        mTango.updateTexture(TangoCameraIntrinsics.
                                                TANGO_CAMERA_COLOR);

                                // Get the transform from color camera to Start of Service
                                // at the timestamp of the RGB image in OpenGL coordinates.
                                TangoSupport.TangoMatrixTransformData transform =
                                        TangoSupport.getMatrixTransformAtTime(mRgbTimestampGlThread,
                                                TangoPoseData
                                                        .COORDINATE_FRAME_START_OF_SERVICE,
                                                TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                                TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL);
                                if (transform.statusCode == TangoPoseData.POSE_VALID) {

                                    mRenderer.updateViewMatrix(transform.matrix);
                                    double deltaTime = mRgbTimestampGlThread
                                            - lastRenderedTimeStamp;
                                    lastRenderedTimeStamp = mRgbTimestampGlThread;

                                    // Set the earth rotation around itself.
                                    float[] openGlTEarth = new float[16];
                                    Matrix.rotateM(mEarthMoonCenterTEarth, 0, (float)
                                            deltaTime * 360 / 10, 0, 1, 0);
                                    Matrix.multiplyMM(openGlTEarth, 0, mOpenGLTEarthMoonCenter,
                                            0, mEarthMoonCenterTEarth, 0);

                                    // Set moon rotation around the earth and moon center.
                                    float[] openGlTMoon = new float[16];
                                    Matrix.rotateM(mEarthMoonCenterTMoonRotation, 0, (float)
                                            deltaTime * 360 / 50, 0, 1, 0);
                                    float[] mEarthTMoon = new float[16];
                                    Matrix.multiplyMM(mEarthTMoon, 0,
                                            mEarthMoonCenterTMoonRotation, 0,
                                            mEarthMoonCenterTTranslation, 0);
                                    Matrix.multiplyMM(openGlTMoon, 0,
                                            mOpenGLTEarthMoonCenter,
                                            0, mEarthTMoon, 0);

                                    mRenderer.setEarthTransform(openGlTEarth);
                                    mRenderer.setMoonTransform(openGlTMoon);
                                } else {
                                    Log.w(TAG, "Could not get a valid transform at time " +
                                            mRgbTimestampGlThread);
                                }
                            }
                        }
                    }
                });

        // Set the starting position and orientation of the Earth and Moon respect the OpenGL frame.
        Matrix.setIdentityM(mOpenGLTEarthMoonCenter, 0);
        Matrix.translateM(mOpenGLTEarthMoonCenter, 0, 0, 0, -1f);
        Matrix.setIdentityM(mEarthMoonCenterTEarth, 0);
        Matrix.setIdentityM(mEarthMoonCenterTMoonRotation, 0);
        Matrix.setIdentityM(mEarthMoonCenterTTranslation, 0);
        Matrix.translateM(mEarthMoonCenterTTranslation, 0, 0.5f, 0, 0);

        mSurfaceView.setRenderer(mRenderer);
    }
}
