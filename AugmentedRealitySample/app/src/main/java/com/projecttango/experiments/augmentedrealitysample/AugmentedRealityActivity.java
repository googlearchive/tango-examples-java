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

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
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
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.rajawali.ar.TangoRajawaliView;
import com.projecttango.tangosupport.TangoSupport;

/**
 * An example showing how to build a very simple augmented reality application in Java.
 * It uses Rajawali to do the rendering through the utility classes
 * <code>TangoRajawaliRenderer</code> and <code>TangoRajawaliView</code> from TangoUtils.
 * It also uses the TangoSupportLibrary to do plane fitting using the PointCloud data. Whenever the
 * user clicks on the camera display, plane detection will be done on the surface closest to the
 * click location and a 3D object will be placed in the scene anchored in that location.
 * <p/>
 * TangoRajawaliView is used in the same way as the TangoCamaraPreview: we first need initialize the
 * TangoRajawaliView class with the activity's context and connect to the camera we want by using
 * connectToTangoCamera method. Once the connection is established we need to update
 * the view's texture by using the onFrameAvailable callbacks.
 * <p/>
 * The TangoRajawaliRenderer class is used the same way as a RajawaliRenderer. We need to create it
 * with a reference to the activity's context and then pass it to the view with the view's
 * setSurfaceRenderer method.
 * The implementation of the 3D world is done by subclassing the Renderer, just like any other
 * Rajawali application.
 * <p/>
 * Note that it is important to include the KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION configuration
 * parameter in order to achieve best results synchronizing the Rajawali virtual world with
 * the RGB camera.
 */
public class AugmentedRealityActivity extends Activity implements View.OnTouchListener {
    private static final String TAG = "AugmentedRealityActiv";
    private TangoRajawaliView mGLView;
    private AugmentedRealityRenderer mRenderer;
    private PointCloudManager mPointCloudManager;
    private Tango mTango;
    private boolean mIsConnected;
    private boolean mIsPermissionGranted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGLView = new TangoRajawaliView(this);
        mRenderer = new AugmentedRealityRenderer(this);
        mGLView.setSurfaceRenderer(mRenderer);
        mGLView.setOnTouchListener(this);
        mTango = new Tango(this);
        startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING),
                Tango.TANGO_INTENT_ACTIVITYCODE);
        setContentView(mGLView);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Motion Tracking Permissions Required!",
                        Toast.LENGTH_SHORT).show();
                finish();
            } else {
                startAugmentedreality();
                mIsPermissionGranted = true;
            }
        }
    }

    // Augmented reality view and renderer
    private void startAugmentedreality() {
        if (!mIsConnected) {
            mIsConnected = true;
            // Connect to color camera
            mGLView.connectToTangoCamera(mTango, TangoCameraIntrinsics.TANGO_CAMERA_COLOR);

            // Use default configuration for Tango Service, plus low latency IMU integration.
            TangoConfig config = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
            // NOTE: low latency integration is necessary to achieve a precise alignment of
            // virtual objects with the RBG image and produce a good AR effect.
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
            config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
            mTango.connect(config);

            // No need to add any coordinate frame pairs since we are not using
            // pose data. So just initialize.
            ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
            mTango.connectListener(framePairs, new OnTangoUpdateListener() {
                @Override
                public void onPoseAvailable(TangoPoseData pose) {
                    // We are not using OnPoseAvailable for this app
                }

                @Override
                public void onFrameAvailable(int cameraId) {
                    // Check if the frame available is for the camera we want and
                    // update its frame on the view.
                    if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                        mGLView.onFrameAvailable();
                    }
                }

                @Override
                public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                    // Get the device pose at the time the point cloud was acquired
                    TangoCoordinateFramePair framePair = new TangoCoordinateFramePair(
                            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                            TangoPoseData.COORDINATE_FRAME_DEVICE);
                    TangoPoseData cloudPose = mTango.getPoseAtTime(xyzIj.timestamp, framePair);

                    // Save the cloud and point data for later use
                    mPointCloudManager.updateXyzIjData(xyzIj, cloudPose);
                }

                @Override
                public void onTangoEvent(TangoEvent event) {
                    // We are not using OnPoseAvailable for this app
                }
            });

            // Get extrinsics from device for use in transforms
            // This needs to be done after connecting Tango and listeners
            setupExtrinsics();

            // Set-up point cloud plane fitting library helper class
            mPointCloudManager = new PointCloudManager(mTango.getCameraIntrinsics(
                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR));

        }
    }

    /**
     * Calculates and stores the fixed transformations between the device and the various sensors
     * to be used later for transformations between frames.
     */
    private void setupExtrinsics() {
        // Create Camera to IMU Transform
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        TangoPoseData imuTrgbPose = mTango.getPoseAtTime(0.0, framePair);

        // Create Device to IMU Transform
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        TangoPoseData imuTdevicePose = mTango.getPoseAtTime(0.0, framePair);

        // Create Depth camera to IMU Transform
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH;
        TangoPoseData imuTdepthPose = mTango.getPoseAtTime(0.0, framePair);

        mRenderer.setupExtrinsics(imuTdevicePose, imuTrgbPose, imuTdepthPose);
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mIsConnected) {
            mGLView.disconnectCamera();
            mTango.disconnect();
            mIsConnected = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsConnected && mIsPermissionGranted) {
            startAugmentedreality();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            // Calculate click location in u,v (0;1) coordinates
            float u = motionEvent.getX() / view.getWidth();
            float v = motionEvent.getY() / view.getHeight();

            try {
                doFitPlane(u, v);
            } catch (Throwable t) {
                Log.e(TAG, "Exception measuring nomral", t);
            }
        }
        return true;
    }

    /**
     * Use the TangoSupport library with point cloud data to calculate the plane of
     * the world feature pointed at the location the camera is looking at and update the
     * renderer to show a 3D object in that location.
     */
    private void doFitPlane(float u, float v) {
        // Get the current device pose
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE);
        TangoPoseData devicePose = mTango.getPoseAtTime(0.0, framePair);

        // Perform plane fitting with the latest available point cloud data
        TangoSupport.IntersectionPointPlaneModelPair planeModel =
                mPointCloudManager.fitPlane(u, v, devicePose, mRenderer.getPoseCalculator());
        mRenderer.updateObjectPose(planeModel.intersectionPoint, planeModel.planeModel, devicePose);
    }
}
