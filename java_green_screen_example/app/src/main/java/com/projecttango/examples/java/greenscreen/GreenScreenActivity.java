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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.media.MediaActionSound;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

/**
 * This is an example that shows how to use the Tango APIs to get a Green Screen (Chroma key)
 * effect. It synchronizes RGB and depth information, displaying the RGB information of every object
 * that is nearer than a given threshold. It displays a background image for the space that is
 * further than the threshold.
 * It also has the capability of taking screenshots and storing them in the Android gallery.
 * <p/>
 * This example renders the point cloud of the depth camera into an OpenGL depth texture, and
 * renders the TangoRGB camera into an OpenGL texture.
 * It creates a standard Android {@code GLSurfaceView} with an OpenGL renderer and connects to
 * the Tango service with the appropriate configuration for Video rendering.
 * Each time a new RGB video frame is available through the Tango APIs, it is updated to the
 * OpenGL texture and the corresponding timestamp is printed on the logcat.
 * It uses Tango Support library to synchronize the point cloud at the timestamp it was acquired to
 * the RGB camera at the timestamp it was updated. This is done through the
 * {@code CalculateRelativePose} method.
 * <p/>
 * The OpenGL code necessary to do the rendering is is {@code GreenScreenRenderer}.
 * The OpenGL code necessary to understand how to render the point cloud to a depth texture is
 * provided in {@code DepthTexture}.
 * The OpenGL code necessary to understand how to render the specific texture format
 * produced by the Tango RGB camera filtered by the depth texture is provided in
 * {@code GreenScreen}.
 */
public class GreenScreenActivity extends AppCompatActivity {
    private static final String TAG = GreenScreenActivity.class.getSimpleName();
    private static final int INVALID_TEXTURE_ID = 0;
    // For all current Tango devices, color camera is in the camera id 0.
    private static final int COLOR_CAMERA_ID = 0;

    private SeekBar mDepthSeekbar;
    private FrameLayout mPanelFlash;
    private GLSurfaceView mSurfaceView;
    private GreenScreenRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private TangoPointCloudManager mPointCloudManager;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;

    // Texture rendering related fields.
    // NOTE: Naming indicates which thread is in charge of updating this variable.
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    private int mColorCameraToDisplayAndroidRotation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
        mDepthSeekbar = (SeekBar) findViewById(R.id.depth_seekbar);
        mDepthSeekbar.setOnSeekBarChangeListener(new DepthSeekbarListener());
        mPanelFlash = (FrameLayout) findViewById(R.id.panel_flash);
        mPointCloudManager = new TangoPointCloudManager();
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setAndroidOrientation();
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
    protected void onResume() {
        super.onResume();
        mSurfaceView.onResume();

        setAndroidOrientation();

        // Set render mode to RENDERMODE_CONTINUOUSLY to force getting onDraw callbacks until the
        // Tango service is properly set-up and we start getting onFrameAvailable callbacks.
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Initialize Tango Service as a normal Android Service, since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time when onResume gets called, we
        // should create a new Tango object.
        mTango = new Tango(GreenScreenActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready, this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there is no UI
            // thread changes involved.
            @Override
            public void run() {
                synchronized (GreenScreenActivity.this) {
                    try {
                        TangoSupport.initialize();
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                        mIsConnected = true;
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                    }
                }
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
        // Use default configuration for Tango Service (motion tracking), plus low latency
        // IMU integration, color camera, depth and drift correction.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of
        // virtual objects with the RBG image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        // Drift correction allows motion tracking to recover after it loses tracking.
        // The drift corrected pose is is available through the frame pair with
        // base frame AREA_DESCRIPTION and target frame DEVICE.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);

        return config;
    }

    /**
     * Set up the callback listeners for the Tango service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the RGB camera and the Point Cloud.
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
                // Save the cloud and point data for later use.
                mPointCloudManager.updatePointCloud(pointCloud);
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

        // Obtain the intrinsic parameters of the color camera.
        mIntrinsics = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
    }

    /**
     * Here is where you would set-up your rendering logic. We're replacing it with a minimalistic,
     * dummy example using a standard GLSurfaceView and a basic renderer, for illustration purposes
     * only.
     */
    private void setupRenderer() {
        mSurfaceView.setEGLContextClientVersion(2);
        mRenderer = new GreenScreenRenderer(this,
                new GreenScreenRenderer.RenderCallback() {

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
                        synchronized (GreenScreenActivity.this) {
                            // Connect the Tango SDK to the OpenGL texture ID where we are
                            // going to render the camera.
                            // NOTE: This must be done after both the texture is generated
                            // and the Tango service is connected.
                            if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                                mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                        mRenderer.getTextureId());
                                mConnectedTextureIdGlThread = mRenderer.getTextureId();
                                Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                                // Set-up scene camera projection to match RGB camera intrinsics.
                                mRenderer.setProjectionMatrix(
                                        projectionMatrixFromCameraIntrinsics(mIntrinsics));
                                mRenderer.setCameraIntrinsics(mIntrinsics);
                            }
                            // If there is a new RGB camera frame available, update the texture and
                            // scene camera pose.
                            if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                                double depthTimestamp = 0;
                                TangoPointCloudData pointCloud =
                                        mPointCloudManager.getLatestPointCloud();
                                if (pointCloud != null) {
                                    mRenderer.updatePointCloud(pointCloud);
                                    depthTimestamp = pointCloud.timestamp;
                                }
                                try {
                                    // {@code mRgbTimestampGlThread} contains the exact timestamp at
                                    // which the rendered RGB frame was acquired.
                                    mRgbTimestampGlThread =
                                            mTango.updateTexture(TangoCameraIntrinsics.
                                                    TANGO_CAMERA_COLOR);

                                    // In the following code, we define t0 as the depth timestamp
                                    // and t1 as the color camera timestamp.

                                    // Calculate the relative pose from color camera frame at
                                    // timestamp color_timestamp t1 and depth.
                                    TangoPoseData poseColort1Tdeptht0;
                                    poseColort1Tdeptht0 = TangoSupport.calculateRelativePose(
                                            mRgbTimestampGlThread,
                                            TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                            depthTimestamp,
                                            TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH);
                                    if (poseColort1Tdeptht0.statusCode == TangoPoseData
                                            .POSE_VALID) {
                                        float[] colort1Tdeptht0 = poseToMatrix(poseColort1Tdeptht0);
                                        mRenderer.updateModelMatrix(colort1Tdeptht0);
                                    } else {
                                        Log.w(TAG, "Could not get relative pose from camera depth" +
                                                " " +
                                                "at " + depthTimestamp + " to camera color at " +
                                                mRgbTimestampGlThread);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Exception on the OpenGL thread", e);
                                }
                            }
                        }
                    }

                    /**
                     * This method is called by the renderer when the screenshot has been taken.
                     */
                    @Override
                    public void onScreenshotTaken(final Bitmap screenshotBitmap) {
                        // Give immediate feedback to the user.
                        MediaActionSound sound = new MediaActionSound();
                        sound.play(MediaActionSound.SHUTTER_CLICK);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mPanelFlash.setVisibility(View.VISIBLE);
                                // Run a fade in and out animation of a white screen.
                                ObjectAnimator fadeIn =
                                        ObjectAnimator.ofFloat(mPanelFlash, View.ALPHA, 0, 1);
                                fadeIn.setDuration(100);
                                fadeIn.setInterpolator(new DecelerateInterpolator());
                                ObjectAnimator fadeOut =
                                        ObjectAnimator.ofFloat(mPanelFlash, View.ALPHA, 1, 0);
                                fadeOut.setInterpolator(new AccelerateInterpolator());
                                fadeOut.setDuration(100);

                                AnimatorSet animation = new AnimatorSet();
                                animation.playSequentially(fadeIn, fadeOut);
                                animation.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        mPanelFlash.setVisibility(View.GONE);
                                    }
                                });
                                animation.start();
                            }
                        });
                        // Save bitmap to gallery in background.
                        new BitmapSaverTask(screenshotBitmap).execute();
                    }
                });

        mSurfaceView.setRenderer(mRenderer);
    }

    private static int getColorCameraToDisplayAndroidRotation(int displayRotation,
                                                              int cameraRotation) {
        int cameraRotationNormalized = 0;
        switch (cameraRotation) {
            case 90:
                cameraRotationNormalized = 1;
                break;
            case 180:
                cameraRotationNormalized = 2;
                break;
            case 270:
                cameraRotationNormalized = 3;
                break;
            default:
                cameraRotationNormalized = 0;
                break;
        }
        int ret = displayRotation - cameraRotationNormalized;
        if (ret < 0) {
            ret += 4;
        }
        return ret;
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the OpenGL scene.
     */
    private static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics) {
        // Uses frustumM to create a projection matrix taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = 0.1f;
        float far = 100;

        float xScale = near / (float) intrinsics.fx;
        float yScale = near / (float) intrinsics.fy;
        float xOffset = (float) (intrinsics.cx - (intrinsics.width / 2.0)) * xScale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        float yOffset = (float) -(intrinsics.cy - (intrinsics.height / 2.0)) * yScale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                xScale * (float) -intrinsics.width / 2.0f - xOffset,
                xScale * (float) intrinsics.width / 2.0f - xOffset,
                yScale * (float) -intrinsics.height / 2.0f - yOffset,
                yScale * (float) intrinsics.height / 2.0f - yOffset,
                near, far);
        return m;
    }

    /**
     * Set the color camera background texture rotation and save the camera to display rotation.
     */
    private void setAndroidOrientation() {
        Display display = getWindowManager().getDefaultDisplay();
        Camera.CameraInfo colorCameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(COLOR_CAMERA_ID, colorCameraInfo);

        mColorCameraToDisplayAndroidRotation =
                getColorCameraToDisplayAndroidRotation(display.getRotation(),
                        colorCameraInfo.orientation);
        // Run this in the OpenGL thread.
        mSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.updateColorCameraTextureUv(mColorCameraToDisplayAndroidRotation);
            }
        });
    }

    /*
     * Translate a Pose(translation and orientation) to a matrix transform.
     */
    private static float[] poseToMatrix(TangoPoseData pose) {
        float[] rot = pose.getRotationAsFloats();
        float[] translation = pose.getTranslationAsFloats();

        float[] transform = new float[16];
        transform[0] = 1 - 2 * rot[1] * rot[1] - 2 * rot[2] * rot[2];
        transform[1] = 2 * rot[0] * rot[1] + 2 * rot[2] * rot[3];
        transform[2] = 2 * rot[0] * rot[2] - 2 * rot[1] * rot[3];
        transform[3] = 0;
        transform[4] = 2 * rot[0] * rot[1] - 2 * rot[2] * rot[3];
        transform[5] = 1 - 2 * rot[0] * rot[0] - 2 * rot[2] * rot[2];
        transform[6] = 2 * rot[1] * rot[2] + 2 * rot[0] * rot[3];
        transform[7] = 0;
        transform[8] = 2 * rot[0] * rot[2] + 2 * rot[1] * rot[3];
        transform[9] = 2 * rot[1] * rot[2] - 2 * rot[0] * rot[3];
        transform[10] = 1 - 2 * rot[0] * rot[0] - 2 * rot[1] * rot[1];
        transform[11] = 0;
        transform[12] = translation[0];
        transform[13] = translation[1];
        transform[14] = translation[2];
        transform[15] = 1;

        return transform;
    }

    public void takeScreenshot(View view) {
        mRenderer.takeScreenshot();
    }

    private class DepthSeekbarListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
            mRenderer.setDepthThreshold((float) progress / (float) seekBar.getMax());
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    /**
     * Internal AsyncTask that saves a given Bitmap.
     */
    private class BitmapSaverTask extends AsyncTask<Void, Void, Boolean> {
        private Bitmap mBitmap;

        BitmapSaverTask(Bitmap bitmap) {
            mBitmap = bitmap;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                File bitmapFile = getNewFileForBitmap();
                saveBitmap(bitmapFile, mBitmap);
                addScreenshotToGallery(bitmapFile);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result != null) {
                Toast.makeText(GreenScreenActivity.this, "Screenshot saved to gallery",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(GreenScreenActivity.this, "Screenshot could not be saved",
                        Toast.LENGTH_LONG).show();
            }
        }

        /**
         * returns a new bitmap File. This is a random generated filename.
         *
         * @return File
         * @throws IOException
         */
        private File getNewFileForBitmap() throws IOException {
            File bmpFile = null;
            Random rn = new Random();
            boolean validPath = false;
            while (!validPath) {
                String fileId = String.valueOf(rn.nextInt());
                bmpFile = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "GreenScreen" + fileId + ".png");
                Log.d(TAG, "Screenshot directory " + bmpFile.getAbsolutePath());
                validPath = !bmpFile.exists();
            }
            bmpFile.createNewFile();
            return bmpFile;
        }

        /**
         * Saves the given bitmap to disk (as a PNG file).
         *
         * @param localFile
         * @param bmp
         * @throws IOException
         */
        private void saveBitmap(File localFile, Bitmap bmp) throws IOException {
            FileOutputStream fos = new FileOutputStream(localFile);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        }

        /**
         * Adds a file to the Android gallery.
         */
        private void addScreenshotToGallery(File localFile) throws IOException {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.MediaColumns.DATA, localFile.getCanonicalPath());
            GreenScreenActivity.this.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        }
    }
}


