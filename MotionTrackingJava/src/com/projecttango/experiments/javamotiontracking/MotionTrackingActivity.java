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

package com.projecttango.experiments.javamotiontracking;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Main Activity class for the Motion Tracking API Sample. Handles the connection to the Tango
 * service and propagation of Tango pose data to OpenGL and Layout views. OpenGL rendering logic is
 * delegated to the {@link MTGLRenderer} class.
 */
public class MotionTrackingActivity extends Activity implements View.OnClickListener {

    private static final String TAG = MotionTrackingActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;
    private Tango mTango;
    private TangoConfig mConfig;
    private TextView mDeltaTextView;
    private TextView mPoseCountTextView;
    private TextView mPoseTextView;
    private TextView mQuatTextView;
    private TextView mPoseStatusTextView;
    private TextView mTangoServiceVersionTextView;
    private TextView mApplicationVersionTextView;
    private TextView mTangoEventTextView;
    private Button mMotionResetButton;
    private float mPreviousTimeStamp;
    private int mPreviousPoseStatus;
    private int count;
    private float mDeltaTime;
    private boolean mIsAutoRecovery;
    private MTGLRenderer mRenderer;
    private GLSurfaceView mGLView;
    private boolean mIsProcessing = false;
    private TangoPoseData mPose;
    private static final int UPDATE_INTERVAL_MS = 100;
    public static Object sharedLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motion_tracking);
        Intent intent = getIntent();
        mIsAutoRecovery = intent.getBooleanExtra(StartActivity.KEY_MOTIONTRACKING_AUTORECOVER,
                false);
        // Text views for displaying translation and rotation data
        mPoseTextView = (TextView) findViewById(R.id.pose);
        mQuatTextView = (TextView) findViewById(R.id.quat);
        mPoseCountTextView = (TextView) findViewById(R.id.posecount);
        mDeltaTextView = (TextView) findViewById(R.id.deltatime);
        mTangoEventTextView = (TextView) findViewById(R.id.tangoevent);
        // Buttons for selecting camera view and Set up button click listeners
        findViewById(R.id.first_person_button).setOnClickListener(this);
        findViewById(R.id.third_person_button).setOnClickListener(this);
        findViewById(R.id.top_down_button).setOnClickListener(this);

        // Button to reset motion tracking
        mMotionResetButton = (Button) findViewById(R.id.resetmotion);

        // Text views for the status of the pose data and Tango library versions
        mPoseStatusTextView = (TextView) findViewById(R.id.status);
        mTangoServiceVersionTextView = (TextView) findViewById(R.id.version);
        mApplicationVersionTextView = (TextView) findViewById(R.id.appversion);

        // OpenGL view where all of the graphics are drawn
        mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

        // Set up button click listeners
        mMotionResetButton.setOnClickListener(this);

        // Configure OpenGL renderer
        mRenderer = new MTGLRenderer();
        mGLView.setEGLContextClientVersion(2);
        mGLView.setRenderer(mRenderer);

        // Instantiate the Tango service
        mTango = new Tango(this);
        // Create a new Tango Configuration and enable the MotionTrackingActivity API
        mConfig = new TangoConfig();
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);

        // The Auto-Recovery ToggleButton sets a boolean variable to determine
        // if the
        // Tango service should automatically attempt to recover when
        // / MotionTrackingActivity enters an invalid state.
        if (mIsAutoRecovery) {
            mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);
            Log.i(TAG, "Auto Reset On");
        } else {
            mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, false);
            Log.i(TAG, "Auto Reset Off");
        }

        PackageInfo packageInfo;
        try {
            packageInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            mApplicationVersionTextView.setText(packageInfo.versionName);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        // Display the library version for debug purposes
        mTangoServiceVersionTextView.setText(mConfig.getString("tango_service_library_version"));
        startUIThread();
    }

    /**
     * Set up the TangoConfig and the listeners for the Tango service, then begin using the Motion
     * Tracking API. This is called in response to the user clicking the 'Start' Button.
     */
    private void setTangoListeners() {
        // Lock configuration and connect to Tango
        // Select coordinate frame pair
        final ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        // Listen for new Tango data
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {

            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                //Make sure to have atomic access to Tango Pose Data so that
                //render loop doesn't interfere while Pose call back is updating
                // the data.
                synchronized (sharedLock) {
                    mPose = pose;
                    mDeltaTime = (float) (pose.timestamp - mPreviousTimeStamp) * SECS_TO_MILLISECS;
                    mPreviousTimeStamp = (float) pose.timestamp;
                    // Log whenever Motion Tracking enters an invalid state
                    if (!mIsAutoRecovery && (pose.statusCode == TangoPoseData.POSE_INVALID)) {
                        Log.w(TAG, "Invalid State");
                    }
                    if (mPreviousPoseStatus != pose.statusCode) {
                        count = 0;
                    }
                    count++;
                    mPreviousPoseStatus = pose.statusCode;
                    // Update the OpenGL renderable objects with the new Tango Pose
                    // data
                    float[] translation = pose.getTranslationAsFloats();
                    if(!mRenderer.isValid()){
                        return;
                    }
                    mRenderer.getTrajectory().updateTrajectory(translation);
                    mRenderer.getModelMatCalculator().updateModelMatrix(translation,
                            pose.getRotationAsFloats());
                    mRenderer.updateViewMatrix();
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData arg0) {
                // We are not using TangoXyzIjData for this application
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTangoEventTextView.setText(event.eventKey + ": " + event.eventValue);
                    }
                });
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application
            }
        });
    }

    private void motionReset() {
        mTango.resetMotionTracking();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            mTango.disconnect();
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }
    }

    protected void onResume() {
        super.onResume();
        try {
            setTangoListeners();
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
                    Toast.LENGTH_SHORT).show();
        }
        try {
            mTango.connect(mConfig);
        } catch (TangoOutOfDateException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoOutOfDateException,
                    Toast.LENGTH_SHORT).show();
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }
        try {
            setUpExtrinsics();
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.first_person_button:
            mRenderer.setFirstPersonView();
            break;
        case R.id.top_down_button:
            mRenderer.setTopDownView();
            break;
        case R.id.third_person_button:
            mRenderer.setThirdPersonView();
            break;
        case R.id.resetmotion:
            motionReset();
            break;
        default:
            Log.w(TAG, "Unknown button click");
            return;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mRenderer.onTouchEvent(event);
    }
    
    /**
     * Setup the extrinsics of the device.
     */
    private void setUpExtrinsics() {
        // Get device to imu matrix.
        TangoPoseData device2IMUPose = new TangoPoseData();
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        device2IMUPose = mTango.getPoseAtTime(0.0, framePair);
        mRenderer.getModelMatCalculator().SetDevice2IMUMatrix(
                device2IMUPose.getTranslationAsFloats(), device2IMUPose.getRotationAsFloats());

        // Get color camera to imu matrix.
        TangoPoseData color2IMUPose = new TangoPoseData();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        color2IMUPose = mTango.getPoseAtTime(0.0, framePair);

        mRenderer.getModelMatCalculator().SetColorCamera2IMUMatrix(
                color2IMUPose.getTranslationAsFloats(), color2IMUPose.getRotationAsFloats());
    }
    /**
     * Create a separate thread to update Log information on UI at the specified
     * interval of UPDATE_INTERVAL_MS. This function also makes sure to have access
     * to the mPose atomically.
     */
    private void startUIThread() {
        new Thread(new Runnable() {
            DecimalFormat threeDec = new DecimalFormat("00.000");
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(UPDATE_INTERVAL_MS);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    synchronized (sharedLock) {
                                        if (mPose == null) {
                                            return;
                                        }
                                       
                                        String translationString = "["
                                                + threeDec.format(mPose.translation[0]) + ", "
                                                + threeDec.format(mPose.translation[1]) + ", "
                                                + threeDec.format(mPose.translation[2]) + "] ";
                                        String quaternionString = "["
                                                + threeDec.format(mPose.rotation[0]) + ", "
                                                + threeDec.format(mPose.rotation[1]) + ", "
                                                + threeDec.format(mPose.rotation[2]) + ", "
                                                + threeDec.format(mPose.rotation[3]) + "] ";

                                        // Display pose data on screen in TextViews
                                        mPoseTextView.setText(translationString);
                                        mQuatTextView.setText(quaternionString);
                                        mPoseCountTextView.setText(Integer.toString(count));
                                        mDeltaTextView.setText(threeDec.format(mDeltaTime));
                                        if (mPose.statusCode == TangoPoseData.POSE_VALID) {
                                            mPoseStatusTextView.setText(R.string.pose_valid);
                                        } else if (mPose.statusCode == TangoPoseData.POSE_INVALID) {
                                            mPoseStatusTextView.setText(R.string.pose_invalid);
                                        } else if (mPose.statusCode == TangoPoseData.POSE_INITIALIZING) {
                                            mPoseStatusTextView.setText(R.string.pose_initializing);
                                        } else if (mPose.statusCode == TangoPoseData.POSE_UNKNOWN) {
                                            mPoseStatusTextView.setText(R.string.pose_unknown);
                                        }
                                    }
                                } catch (NullPointerException e) {
                                    e.printStackTrace();
                                }
                            }
                        });

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
}
