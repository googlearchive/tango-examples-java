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

package com.projecttango.experiments.augmentedrealitysample;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.ar.TangoRajawaliView;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;
import com.projecttango.tangosupport.TangoSupport.IntersectionPointPlaneModelPair;

import org.rajawali3d.scene.ASceneFrameCallback;

/**
 * An example showing how to build a very simple augmented reality application
 * in Java. It uses Rajawali to do the rendering through the utility classes
 * <code>TangoRajawaliRenderer</code> and <code>TangoRajawaliView</code> from
 * TangoUtils.
 * It also uses the TangoSupportLibrary to do plane fitting using
 * the PointCloud data. Whenever the user clicks on the camera display, plane
 * detection will be done on the surface closest to the click location and a 3D
 * object will be placed in the scene anchored in that location.
 * <p/>
 * TangoRajawaliView is used in the same way as the TangoCameraPreview: We first
 * need initialize the TangoRajawaliView class with the activity's context and
 * connect to the camera we want by using connectToTangoCamera method. Once the
 * connection is established we need to update the view's texture by using the
 * onFrameAvailable callbacks.
 * <p/>
 * The TangoRajawaliRenderer class is used the same way as a RajawaliRenderer.
 * We need to create it with a reference to the activity's context and then pass
 * it to the view with the view's setSurfaceRenderer method. The implementation
 * of the 3D world is done by subclassing the Renderer, just like any other
 * Rajawali application.
 * The Rajawali Scene camera is updated via a Rajawali Scene Frame Callback
 * whenever a new RGB frame is rendered to the background texture, thus generating
 * the Augmented Reality effect.
 * <p/>
 * Note that it is important to include the KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION
 * configuration parameter in order to achieve best results synchronizing the
 * Rajawali virtual world with the RGB camera.
 */
public class AugmentedRealityActivity extends Activity implements View.OnTouchListener {
    private static final String TAG = AugmentedRealityActivity.class.getSimpleName();
    private TangoRajawaliView mGLView;
    private AugmentedRealityRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private DeviceExtrinsics mExtrinsics;
    private TangoPointCloudManager mPointCloudManager;
    private Tango mTango;
    private AtomicBoolean mIsConnected = new AtomicBoolean(false);
    private double mCameraPoseTimestamp = 0;

    public static final TangoCoordinateFramePair FRAME_PAIR = new TangoCoordinateFramePair(
            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
            TangoPoseData.COORDINATE_FRAME_DEVICE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGLView = new TangoRajawaliView(this);
        mRenderer = new AugmentedRealityRenderer(this);
        mGLView.setSurfaceRenderer(mRenderer);
        mGLView.setOnTouchListener(this);
        mTango = new Tango(this);
        mPointCloudManager = new TangoPointCloudManager();
        setContentView(mGLView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mIsConnected.compareAndSet(true, false)) {
            mRenderer.getCurrentScene().clearFrameCallbacks();
            mGLView.disconnectCamera();
            mTango.disconnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mIsConnected.compareAndSet(false, true)) {
            try {
                connectTango();
                connectRenderer();
            } catch (TangoOutOfDateException e) {
                Toast.makeText(getApplicationContext(),
                        R.string.TangoOutOfDateException,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Configures the Tango service and connect it to callbacks.
     */
    private void connectTango() {
        // Use default configuration for Tango Service, plus low latency
        // IMU integration.
        TangoConfig config = mTango.getConfig(
                TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a
        // precise alignment of virtual objects with the RBG image and
        // produce a good AR effect.
        config.putBoolean(
                TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        mTango.connect(config);

        // No need to add any coordinate frame pairs since we are not
        // using pose data. So just initialize.
        ArrayList<TangoCoordinateFramePair> framePairs =
                new ArrayList<TangoCoordinateFramePair>();
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // We are not using OnPoseAvailable for this app.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Check if the frame available is for the camera we
                // want and update its frame on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    mGLView.onFrameAvailable();
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // Save the cloud and point data for later use.
                mPointCloudManager.updateXyzIj(xyzIj);
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using OnPoseAvailable for this app.
            }
        });

        // Get extrinsics from device for use in transforms. This needs
        // to be done after connecting Tango and listeners.
        mExtrinsics = setupExtrinsics(mTango);
        mIntrinsics = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
    }

    /**
     * Connects the view and renderer to the color camara and callbacks.
     */
    private void connectRenderer() {
        // Connect to color camera.
        mGLView.connectToTangoCamera(mTango, TangoCameraIntrinsics.TANGO_CAMERA_COLOR);

        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                if (!mIsConnected.get()) {
                    return;
                }
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks had a chance to run and before scene objects are rendered
                // into the scene.

                // Note that the TangoRajwaliRenderer will update the RGB frame to the background
                // texture and update the RGB timestamp before this callback is executed.

                // If a new RGB frame has been rendered, update the camera pose to match.
                // NOTE: This doesn't need to be synchronized since the renderer provided timestamp
                // is also set in this same OpenGL thread.
                double rgbTimestamp = mRenderer.getTimestamp();
                if (rgbTimestamp > mCameraPoseTimestamp) {
                    // Calculate the device pose at the camera frame update time.
                    TangoPoseData lastFramePose = mTango.getPoseAtTime(rgbTimestamp, FRAME_PAIR);
                    if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                        // Update the camera pose from the renderer
                        mRenderer.updateRenderCameraPose(lastFramePose, mExtrinsics);
                        mCameraPoseTimestamp = lastFramePose.timestamp;
                    } else {
                        Log.w(TAG, "Unable to get device pose at time: " + rgbTimestamp);
                    }
                }
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }

            @Override
            public boolean callPreFrame() {
                return true;
            }
        });
    }

    /**
     * Calculates and stores the fixed transformations between the device and
     * the various sensors to be used later for transformations between frames.
     */
    private static DeviceExtrinsics setupExtrinsics(Tango tango) {
        // Create camera to IMU transform.
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        TangoPoseData imuTrgbPose = tango.getPoseAtTime(0.0, framePair);

        // Create device to IMU transform.
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        TangoPoseData imuTdevicePose = tango.getPoseAtTime(0.0, framePair);

        // Create depth camera to IMU transform.
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH;
        TangoPoseData imuTdepthPose = tango.getPoseAtTime(0.0, framePair);

        return new DeviceExtrinsics(imuTdevicePose, imuTrgbPose, imuTdepthPose);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            // Calculate click location in u,v (0;1) coordinates.
            float u = motionEvent.getX() / view.getWidth();
            float v = motionEvent.getY() / view.getHeight();

            try {
                // Fit a plane on the clicked point using the latest poiont cloud data
                TangoPoseData planeFitPose = doFitPlane(u, v, mRenderer.getTimestamp());

                if (planeFitPose != null) {
                    // Update the position of the rendered cube to the pose of the detected plane
                    // This update is made thread safe by the renderer
                    mRenderer.updateObjectPose(planeFitPose);
                }

            } catch (TangoException t) {
                Toast.makeText(getApplicationContext(),
                               R.string.failed_measurement,
                               Toast.LENGTH_SHORT).show();
                Log.e(TAG, getString(R.string.failed_measurement), t);
            } catch (SecurityException t) {
                Toast.makeText(getApplicationContext(),
                               R.string.failed_permissions,
                               Toast.LENGTH_SHORT).show();
                Log.e(TAG, getString(R.string.failed_permissions), t);
            }
        }
        return true;
    }

    /**
     * Use the TangoSupport library with point cloud data to calculate the plane
     * of the world feature pointed at the location the camera is looking.
     * It returns the pose of the fitted plane in a TangoPoseData structure.
     */
    private TangoPoseData doFitPlane(float u, float v, double rgbTimestamp) {
        TangoXyzIjData xyzIj = mPointCloudManager.getLatestXyzIj();

        if (xyzIj == null) {
            return null;
        }

        // We need to calculate the transform between the color camera at the
        // time the user clicked and the depth camera at the time the depth
        // cloud was acquired.
        TangoPoseData colorTdepthPose = TangoSupport.calculateRelativePose(
                rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                xyzIj.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH);

        // Perform plane fitting with the latest available point cloud data.
        IntersectionPointPlaneModelPair intersectionPointPlaneModelPair =
                TangoSupport.fitPlaneModelNearClick(xyzIj, mIntrinsics,
                        colorTdepthPose, u, v);

        // Get the device pose at the time the plane data was acquired.
        TangoPoseData devicePose =
                mTango.getPoseAtTime(xyzIj.timestamp, FRAME_PAIR);

        // Update the AR object location.
        TangoPoseData planeFitPose = ScenePoseCalculator.planeFitToTangoWorldPose(
                intersectionPointPlaneModelPair.intersectionPoint,
                intersectionPointPlaneModelPair.planeModel, devicePose, mExtrinsics);

        return planeFitPose;
    }
}
