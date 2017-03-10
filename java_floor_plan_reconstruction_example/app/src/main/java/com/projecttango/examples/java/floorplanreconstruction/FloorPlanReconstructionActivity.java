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

package com.projecttango.examples.java.floorplanreconstruction;

import com.google.atap.tango.reconstruction.TangoPolygon;
import com.google.atap.tangoservice.Tango;
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
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.projecttango.tangosupport.TangoSupport;

/**
 * An example showing how to use the 3D reconstruction floor planning features to create a
 * floor plan in Java.
 *
 * This sample uses the APIs that extract a set of simplified 2D polygons and renders them on a
 * SurfaceView. The device orientation is used to automatically translate and rotate the map.
 *
 * Rendering is done in a simplistic way, using the canvas API over a SurfaceView.
 */
public class FloorPlanReconstructionActivity extends Activity implements FloorplanView
        .DrawingCallback {
    private static final String TAG = FloorPlanReconstructionActivity.class.getSimpleName();

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int CAMERA_PERMISSION_CODE = 0;

    private TangoFloorplanner mTangoFloorplanner;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;
    private boolean mIsPaused;
    private Button mPauseButton;
    private FloorplanView mFloorplanView;
    private TextView mAreaText;

    private int mDisplayRotation = 0;

    private float mMinAreaSpace = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TypedValue typedValue = new TypedValue();
        getResources().getValue(R.dimen.min_area_space, typedValue, true);
        mMinAreaSpace = typedValue.getFloat();

        mPauseButton = (Button) findViewById(R.id.pause_button);
        mFloorplanView = (FloorplanView) findViewById(R.id.floorplan);
        mFloorplanView.registerCallback(this);
        mAreaText = (TextView) findViewById(R.id.area_text);

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
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check and request camera permission at run time.
        if (checkAndRequestPermissions()) {
            bindTangoService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        synchronized (this) {
            try {
                mTangoFloorplanner.stopFloorplanning();
                mTango.disconnect();
                mTangoFloorplanner.resetFloorplan();
                mTangoFloorplanner.release();
                mIsConnected = false;
                mIsPaused = true;
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Initialize Tango Service as a normal Android Service.
     */
    private void bindTangoService() {
        // Initialize Tango Service as a normal Android Service.
        // Since we call mTango.disconnect() in onPause, this will unbind Tango Service,
        // so every time onResume gets called we should create a new Tango object.
        mTango = new Tango(FloorPlanReconstructionActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready,
            // this Runnable will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only
            // when there are no UI thread changes involved.
            @Override
            public void run() {
                synchronized (FloorPlanReconstructionActivity.this) {
                    try {
                        TangoSupport.initialize();
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                        mIsConnected = true;
                        mIsPaused = false;
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
     * Sets up the Tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service, plus color camera, low latency
        // IMU integration, depth, smooth pose and dataset recording.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of virtual
        // objects with the RGB image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        return config;
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the point cloud.
     */
    private void startupTango() {
        mTangoFloorplanner = new TangoFloorplanner(new TangoFloorplanner
                .OnFloorplanAvailableListener() {
            @Override
            public void onFloorplanAvailable(List<TangoPolygon> polygons) {
                mFloorplanView.setFloorplan(polygons);
                calculateAndUpdateArea(polygons);
            }
        });
        // Set camera intrinsics to TangoFloorplanner.
        mTangoFloorplanner.setDepthCameraCalibration(mTango.getCameraIntrinsics
                (TangoCameraIntrinsics.TANGO_CAMERA_DEPTH));

        mTangoFloorplanner.startFloorplanning();

        // Connect listeners to Tango Service and forward point cloud and camera information to
        // TangoFloorplanner.
        List<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData tangoPoseData) {
                // We are not using onPoseAvailable for this app.
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData tangoXyzIjData) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onFrameAvailable(int i) {
                // We are not using onFrameAvailable for this app.
            }

            @Override
            public void onTangoEvent(TangoEvent tangoEvent) {
                // We are not using onTangoEvent for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData tangoPointCloudData) {
                mTangoFloorplanner.onPointCloudAvailable(tangoPointCloudData);
            }
        });
    }

    /**
     * Method called each time right before the floorplan is drawn. It allows use of the Tango
     * Service to get the device position and orientation.
     */
    @Override
    public void onPreDrawing() {
        try {
            // Synchronize against disconnecting while using the service.
            synchronized (FloorPlanReconstructionActivity.this) {
                // Don't execute any Tango API actions if we're not connected to
                // the service.
                if (!mIsConnected) {
                    return;
                }

                // Calculate the device pose in OpenGL engine (Y+ up).
                TangoPoseData devicePose = TangoSupport.getPoseAtTime(0.0,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_DEVICE,
                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                        mDisplayRotation);

                if (devicePose.statusCode == TangoPoseData.POSE_VALID) {
                    // Extract position and rotation around Z.
                    float[] devicePosition = devicePose.getTranslationAsFloats();
                    float[] deviceOrientation = devicePose.getRotationAsFloats();
                    float yawRadians = yRotationFromQuaternion(deviceOrientation[0],
                            deviceOrientation[1], deviceOrientation[2],
                            deviceOrientation[3]);

                    mFloorplanView.updateCameraMatrix(devicePosition[0], -devicePosition[2],
                            yawRadians);
                } else {
                    Log.w(TAG, "Can't get last device pose");
                }
            }
        } catch (TangoErrorException e) {
            Log.e(TAG, "Tango error while querying device pose.", e);
        } catch (TangoInvalidException e) {
            Log.e(TAG, "Tango exception while querying device pose.", e);
        }
    }

    /**
     * Calculates the rotation around Y (yaw) from the given quaternion.
     */
    private static float yRotationFromQuaternion(float x, float y, float z, float w) {
        return (float) Math.atan2(2 * (w * y - x * z), w * (w + x) - y * (z + y));
    }

    /**
     * Calculate the total explored space area and update the text field with that information.
     */
    private void calculateAndUpdateArea(List<TangoPolygon> polygons) {
        double area = 0;
        for (TangoPolygon polygon: polygons) {
            if (polygon.layer == TangoPolygon.TANGO_3DR_LAYER_SPACE) {
                // If there is more than one free space polygon, only count those
                // that have an area larger than two square meters to suppress unconnected
                // areas (which might occur in front of windows).
                if (area == 0 || (polygon.area > mMinAreaSpace || polygon.area < 0)) {
                    area += polygon.area;
                }
            }
        }
        final String areaText = String.format("%.2f", area);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAreaText.setText(areaText);
            }
        });
    }

    public void onPauseButtonClick(View v) {
        if (mIsPaused) {
            mTangoFloorplanner.startFloorplanning();
            mPauseButton.setText("Pause");
        } else {
            mTangoFloorplanner.stopFloorplanning();
            mPauseButton.setText("Resume");
        }
        mIsPaused = !mIsPaused;
    }

    public void onClearButtonClicked(View v) {
        mTangoFloorplanner.resetFloorplan();
    }

    /**
     * Set the display rotation.
     */
    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        mDisplayRotation = display.getRotation();
    }

    /**
     * Check to see if we have the necessary permissions for this app; ask for them if we don't.
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
     * Check to see if we have the necessary permissions for this app.
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
                .setMessage("Java Floorplan Reconstruction Example requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(FloorPlanReconstructionActivity.this,
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
                Toast.makeText(FloorPlanReconstructionActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (hasCameraPermission()) {
            bindTangoService();
        } else {
            Toast.makeText(this, "Java Floorplan Reconstruction Example requires camera permission",
                    Toast.LENGTH_LONG).show();
        }
    }
}
