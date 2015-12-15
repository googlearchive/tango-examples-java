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
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.projecttango.tangoutils.TangoPoseUtilities;

import org.rajawali3d.surface.IRajawaliSurface;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Main Activity class for the Motion Tracking API Sample. Handles the connection to the Tango
 * service and propagation of Tango pose data to OpenGL and Layout views. OpenGL rendering logic is
 * delegated to the {@link MotionTrackingRajawaliRenderer} class.
 */
public class MotionTrackingActivity extends Activity implements View.OnClickListener {

    private static final String TAG = MotionTrackingActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;
    private static final double UPDATE_INTERVAL_MS = 100.0f;
    private static final DecimalFormat FORMAT_THREE_DECIMAL = new DecimalFormat("0.000");

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
    private boolean mIsAutoRecovery;
    private MotionTrackingRajawaliRenderer mRenderer;

    // State tracking variables for pose callback handler.  If these are
    // referenced from the main thread, they need to be synchronized with the
    // pose handler callback.
    private double mPreviousTimeStamp = 0.0;
    private int mPreviousPoseStatus = TangoPoseData.POSE_INVALID;
    private int mCount = 0;
    private double mTimeToNextUpdate = UPDATE_INTERVAL_MS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motion_tracking);
        Intent intent = getIntent();
        mIsAutoRecovery = intent.getBooleanExtra(StartActivity.KEY_MOTIONTRACKING_AUTORECOVER,
                false);
        // Instantiate the Tango service
        mTango = new Tango(this);
        mConfig = setupTangoConfig(mTango, mIsAutoRecovery);
        setupTextViewsAndButtons(mConfig);
        mRenderer = setupGLViewAndRenderer();
    }

    /**
     * Sets Rajawalisurface view and its renderer. This is ideally called only once in onCreate.
     */
    private MotionTrackingRajawaliRenderer setupGLViewAndRenderer(){

        // Configure OpenGL renderer
        MotionTrackingRajawaliRenderer renderer = new MotionTrackingRajawaliRenderer(this);
        // OpenGL view where all of the graphics are drawn
        RajawaliSurfaceView glView = (RajawaliSurfaceView) findViewById(R.id.gl_surface_view);
        glView.setEGLContextClientVersion(2);
        glView.setRenderMode(IRajawaliSurface.RENDERMODE_CONTINUOUSLY);
        glView.setSurfaceRenderer(renderer);
        return renderer;

    }

    /**
     * Sets Texts views to display statistics of Poses being received. This also sets the buttons
     * used in the UI. Please note that this needs to be called after TangoService and Config
     * objects are initialized since we use them for the SDK related stuff like version number
     * etc.
     */
    private void setupTextViewsAndButtons(TangoConfig config){
        // Text views for displaying translation and rotation data
        mPoseTextView = (TextView) findViewById(R.id.pose);
        mQuatTextView = (TextView) findViewById(R.id.quat);
        mPoseCountTextView = (TextView) findViewById(R.id.posecount);
        mDeltaTextView = (TextView) findViewById(R.id.deltatime);
        mTangoEventTextView = (TextView) findViewById(R.id.tangoevent);

        // Text views for the status of the pose data and Tango library versions
        mPoseStatusTextView = (TextView) findViewById(R.id.status);
        mTangoServiceVersionTextView = (TextView) findViewById(R.id.version);
        mApplicationVersionTextView = (TextView) findViewById(R.id.appversion);

        // Buttons for selecting camera view and Set up button click listeners
        findViewById(R.id.first_person_button).setOnClickListener(this);
        findViewById(R.id.third_person_button).setOnClickListener(this);
        findViewById(R.id.top_down_button).setOnClickListener(this);

        // Button to reset motion tracking
        mMotionResetButton = (Button) findViewById(R.id.resetmotion);
        // Set up button click listeners
        mMotionResetButton.setOnClickListener(this);

        // Display the library version for debug purposes
        mTangoServiceVersionTextView.setText(config.getString("tango_service_library_version"));
        PackageInfo packageInfo;
        try {
            packageInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            mApplicationVersionTextView.setText(packageInfo.versionName);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango, boolean isAutoRecovery){
        // Create a new Tango Configuration and enable the MotionTrackingActivity API
        TangoConfig config = new TangoConfig();
        config = tango.getConfig(config.CONFIG_TYPE_CURRENT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);

        // The Auto-Recovery ToggleButton sets a boolean variable to determine
        // if the
        // Tango service should automatically attempt to recover when
        // / MotionTrackingActivity enters an invalid state.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, isAutoRecovery);
        Log.i(TAG, "Auto Reset: " + mIsAutoRecovery);
        return  config;
    }

    /**
     * Set up the callback listeners for the Tango service, then begin using the Motion
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
                // Update the OpenGL renderable objects with the new Tango Pose
                // data.  Note that locking for thread safe access with the
                // OpenGL loop is done entirely in the renderer.
                mRenderer.updateDevicePose(pose);

                // Now lets log some interesting statistics of Motion Tracking
                // like Delta Time between two Poses, number of poses since the
                // initialization state.
                final double deltaTime = (pose.timestamp - mPreviousTimeStamp)
                    * SECS_TO_MILLISECS;

                mPreviousTimeStamp = pose.timestamp;
                // Log whenever Motion Tracking enters an invalid state
                if (!mIsAutoRecovery && (pose.statusCode == TangoPoseData.POSE_INVALID)) {
                    Log.w(TAG, "Invalid State");
                }
                if (mPreviousPoseStatus != pose.statusCode) {
                    mCount = 0;
                }
                mCount++;
                final int count = mCount;
                mPreviousPoseStatus = pose.statusCode;

                // Throttle updates to the UI based on UPDATE_INTERVAL_MS.
                mTimeToNextUpdate -= deltaTime;
                boolean updateUI = false;
                if(mTimeToNextUpdate < 0.0) {
                    mTimeToNextUpdate = UPDATE_INTERVAL_MS;
                    updateUI = true;
                }

                // If the pose is not valid, we may not get another callback so make sure to update
                // the UI during this call
                if (pose.statusCode != TangoPoseData.POSE_VALID) {
                    updateUI = true;
                }

                if(updateUI) {
                    final String translationString =
                        TangoPoseUtilities.getTranslationString(pose, FORMAT_THREE_DECIMAL);
                    final String quaternionString =
                        TangoPoseUtilities.getQuaternionString(pose, FORMAT_THREE_DECIMAL);
                    final String status = TangoPoseUtilities.getStatusString(pose);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Display pose data on screen in TextViews.
                            mPoseTextView.setText(translationString);
                            mQuatTextView.setText(quaternionString);
                            mPoseCountTextView.setText(Integer.toString(count));
                            mDeltaTextView.setText(FORMAT_THREE_DECIMAL.format(deltaTime));
                            mPoseStatusTextView.setText(status);
                        }
                    });
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

    /**
     * Reset motion tracking to last known valid pose. Once this function is called,
     * Motion Tracking restarts as such we may get initializing poses again. Developer should make
     * sure that user gets enough feed back in that case.
     */
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

    @Override
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
        mRenderer.onTouchEvent(event);
        return true;
    }
}
