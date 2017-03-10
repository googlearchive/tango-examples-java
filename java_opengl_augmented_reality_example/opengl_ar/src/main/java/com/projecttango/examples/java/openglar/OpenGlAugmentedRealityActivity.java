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
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.tangosupport.TangoSupport;

/**
 * This is a simple example that shows how to use the Tango APIs to create an augmented reality
 * application. It displays the Earth floating in space one meter in front of the device and the
 * Moon rotating around the Earth.
 * <p/>
 * This example renders the TangoRGB camera into an OpenGL texture and gets the device pose
 * information to update the scene camera accordingly.
 * It creates a standard Android {@code GLSurfaceView} with an OpenGL renderer and connects to
 * the Tango Service with the appropriate configuration for video rendering.
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

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int CAMERA_PERMISSION_CODE = 0;

    private GLSurfaceView mSurfaceView;
    private OpenGlAugmentedRealityRenderer mRenderer;
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
    // Transform from Earth to Earth and Moon center. This will change over time as the Earth is
    // rotating.
    private float[] mEarthMoonCenterTEarth = new float[16];
    // Translation from the Moon to the Earth and Moon center. This is a fixed transform.
    private float[] mEarthMoonCenterTTranslation = new float[16];
    // Rotation from the Moon to the Earth and Moon center. This will change over time as the Moon
    // is rotating.
    private float[] mEarthMoonCenterTMoonRotation = new float[16];

    private int mDisplayRotation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);
        }

        setupRenderer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSurfaceView.onResume();

        // Set render mode to RENDERMODE_CONTINUOUSLY to force getting onDraw callbacks until
        // the Tango service is properly set-up and we start getting onFrameAvailable callbacks.
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        // Check and request camera permission at run time.
        if (checkAndRequestPermissions()) {
            bindTangoService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
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
               // re-connection in the OpenGL thread after resume.
               mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
               mTango.disconnect();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Initialize Tango Service as a normal Android Service.
     */
    private void bindTangoService() {
        // Initialize Tango Service as a normal Android Service. Since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time onResume gets called we
        // should create a new Tango object.
        mTango = new Tango(OpenGlAugmentedRealityActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready; this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there are no
            // UI thread changes involved.
            @Override
            public void run() {
                // Synchronize against disconnecting while the service is being used in the OpenGL
                // thread or in the UI thread.
                synchronized (OpenGlAugmentedRealityActivity.this) {
                    try {
                        TangoSupport.initialize();
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                        mIsConnected = true;
                        setDisplayRotation();
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                        showsToastAndFinishOnUiThread(R.string.exception_out_of_date);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_error);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_invalid);
                    }
                }
            }
        });
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service, plus color camera, low latency
        // IMU integration and drift correction.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of
        // virtual objects with the RGB image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        // Drift correction allows motion tracking to recover after it loses tracking.
        // The drift corrected pose is available through the frame pair with
        // base frame AREA_DESCRIPTION and target frame DEVICE.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);

        return config;
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the RGB camera.
     */
    private void startupTango() {
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
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                // We are not using onPointCloudAvailable for this app.
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
                    // This will result in a frame rate of approximately 30FPS, in synchrony with
                    // the RGB camera driver.
                    // If you need to render at a higher rate (i.e., if you want to render complex
                    // animations smoothly) you can use RENDERMODE_CONTINUOUSLY throughout the
                    // application lifecycle.
                    if (mSurfaceView.getRenderMode() != GLSurfaceView.RENDERMODE_WHEN_DIRTY) {
                        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                    }

                    // Mark a camera frame as available for rendering in the OpenGL thread.
                    mIsFrameAvailableTangoThread.set(true);
                    // Trigger an OpenGL render to update the OpenGL scene with the new RGB data.
                    mSurfaceView.requestRender();
                }
            }
        });
    }

    /**
     * Here is where you would set up your rendering logic. We're replacing it with a minimalistic,
     * dummy example, using a standard GLSurfaceView and a basic renderer, for illustration purposes
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

                        try {
                            // Synchronize against concurrently disconnecting the service triggered
                            // from the UI thread.
                            synchronized (OpenGlAugmentedRealityActivity.this) {
                                // We need to be careful not to run any Tango-dependent code in the
                                // OpenGL thread unless we know the Tango Service is properly
                                // set up and connected.
                                if (!mIsConnected) {
                                    return;
                                }

                                // Set up scene camera projection to match RGB camera intrinsics.
                                if (!mRenderer.isProjectionMatrixConfigured()) {
                                    TangoCameraIntrinsics intrinsics =
                                            TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                                    mDisplayRotation);
                                    mRenderer.setProjectionMatrix(
                                            projectionMatrixFromCameraIntrinsics(intrinsics));
                                }
                                // Connect the Tango SDK to the OpenGL texture ID where we are
                                // going to render the camera.
                                // NOTE: This must be done after both the texture is generated
                                // and the Tango Service is connected.
                                if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                                    mTango.connectTextureId(
                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                            mRenderer.getTextureId());
                                    mConnectedTextureIdGlThread = mRenderer.getTextureId();
                                    Log.d(TAG, "connected to texture id: " +
                                            mRenderer.getTextureId());
                                }
                                // If there is a new RGB camera frame available, update the texture
                                // and scene camera pose.
                                if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                                    // {@code mRgbTimestampGlThread} contains the exact timestamp at
                                    // which the rendered RGB frame was acquired.
                                    mRgbTimestampGlThread =
                                            mTango.updateTexture(TangoCameraIntrinsics.
                                                    TANGO_CAMERA_COLOR);

                                    // Get the transform from color camera to Start of Service
                                    // at the timestamp of the RGB image in OpenGL coordinates.
                                    //
                                    // When drift correction mode is enabled in config file, we need
                                    // to query the device with respect to Area Description pose in
                                    // order to use the drift-corrected pose.
                                    //
                                    // Note that if you don't want to use the drift corrected pose,
                                    // the normal device with respect to start of service pose is
                                    // still available.
                                    TangoSupport.TangoMatrixTransformData transform =
                                            TangoSupport.getMatrixTransformAtTime(
                                                    mRgbTimestampGlThread,
                                                    TangoPoseData
                                                            .COORDINATE_FRAME_AREA_DESCRIPTION,
                                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                    mDisplayRotation);
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
                                        // When the pose status is not valid, it indicates tracking
                                        // has been lost. In this case, we simply stop rendering.
                                        //
                                        // This is also the place to display UI to suggest that the
                                        // user walk to recover tracking.
                                        Log.w(TAG, "Could not get a valid transform at time " +
                                                mRgbTimestampGlThread);
                                    }
                                }
                            }
                            // Avoid crashing the application due to unhandled exceptions.
                        } catch (TangoErrorException e) {
                            Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
                        } catch (Throwable t) {
                            Log.e(TAG, "Exception on the OpenGL thread", t);
                        }
                    }
                });

        // Set the starting position and orientation of the Earth and Moon with respect to the
        // OpenGL frame.
        Matrix.setIdentityM(mOpenGLTEarthMoonCenter, 0);
        Matrix.translateM(mOpenGLTEarthMoonCenter, 0, 0, 0, -1f);
        Matrix.setIdentityM(mEarthMoonCenterTEarth, 0);
        Matrix.setIdentityM(mEarthMoonCenterTMoonRotation, 0);
        Matrix.setIdentityM(mEarthMoonCenterTTranslation, 0);
        Matrix.translateM(mEarthMoonCenterTTranslation, 0, 0.5f, 0, 0);

        mSurfaceView.setRenderer(mRenderer);
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the OpenGL scene.
     *
     * @param intrinsics camera instrinsics for computing the project matrix.
     */
    private static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics) {
        float cx = (float) intrinsics.cx;
        float cy = (float) intrinsics.cy;
        float width = (float) intrinsics.width;
        float height = (float) intrinsics.height;
        float fx = (float) intrinsics.fx;
        float fy = (float) intrinsics.fy;

        // Uses frustumM to create a projection matrix, taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = 0.1f;
        float far = 100;

        float xScale = near / fx;
        float yScale = near / fy;
        float xOffset = (cx - (width / 2.0f)) * xScale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        float yOffset = -(cy - (height / 2.0f)) * yScale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                xScale * (float) -width / 2.0f - xOffset,
                xScale * (float) width / 2.0f - xOffset,
                yScale * (float) -height / 2.0f - yOffset,
                yScale * (float) height / 2.0f - yOffset,
                near, far);
        return m;
    }

    /**
     * Set the color camera background texture rotation and save the camera to display rotation.
     */
    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        mDisplayRotation = display.getRotation();

        // We also need to update the camera texture UV coordinates. This must be run in the OpenGL
        // thread.
        mSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (mIsConnected) {
                    mRenderer.updateColorCameraTextureUv(mDisplayRotation);
                }
            }
        });
    }

    /**
     * Check to see that we have the necessary permissions for this app; ask for them if we don't.
     *
     * @return True if we have the necessary permissions, false if we don't.
     */
    private boolean checkAndRequestPermissions() {
        if (!hasCameraPermission()) {
            requestCameraPermission();
            return false;
        }
        return true;
    }

    /**
     * Check to see that we have the necessary permissions for this app.
     */
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request the necessary permissions for this app.
     */
    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION)) {
            showRequestPermissionRationale();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION},
                    CAMERA_PERMISSION_CODE);
        }
    }

    /**
     * If the user has declined the permission before, we have to explain that the app needs this
     * permission.
     */
    private void showRequestPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Java OpenGL Augmented Reality Example requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(OpenGlAugmentedRealityActivity.this,
                                new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Display toast on UI thread.
     *
     * @param resId The resource id of the string resource to use. Can be formatted text.
     */
    private void showsToastAndFinishOnUiThread(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(OpenGlAugmentedRealityActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    /**
     * Result for requesting camera permission.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (hasCameraPermission()) {
            bindTangoService();
        } else {
            Toast.makeText(this, "Java OpenGL Augmented Reality Example requires camera permission",
                    Toast.LENGTH_LONG).show();
        }
    }
}
