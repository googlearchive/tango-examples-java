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

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.tangoutils.TangoPoseUtilities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.os.SystemClock;

import org.rajawali3d.surface.IRajawaliSurface;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main Activity class for the Motion Tracking API Sample. Handles the connection to the Tango
 * service and propagation of Tango pose data to OpenGL and Layout views. OpenGL rendering logic is
 * delegated to the {@link MotionTrackingRajawaliRenderer} class.
 */
public class MotionTrackingActivity extends Activity implements View.OnClickListener {

    private static final String TAG = MotionTrackingActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;
    private static final int UPDATE_INTERVAL_MS = 100;

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
    private double mPreviousSyncedTimestamp;
    private int mPreviousPoseStatus;
    private int mCount;
    private float mDeltaTime;
    private long mSystemTime=0;
    private long mSystemTimePre=0;
    private float[] mOtherPosition = {0,0,0};
    private boolean mIsFirstUpdate = true;
    private boolean mIsAutoRecovery;
    private MotionTrackingRajawaliRenderer mRenderer;
    private TangoPoseData mPose;
    public static Object mUiThreadLock = new Object();

    private static final String FIREBASE_URL = "https://flickering-torch-1816.firebaseio.com/users";
    private Firebase mFirebaseRef;
    private Firebase mOtherRef;
    private ValueEventListener mConnectedListener;
    private String mUserId;

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
        startUIThread();
        firebaseSetup();
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

    private void firebaseSetup() {
        SharedPreferences prefs = getApplication().getSharedPreferences("DataPrefs", 0);
        mUserId = prefs.getString("userId", null);
        if (mUserId == null) {
            Random r = new Random();
            mUserId = Integer.toString(r.nextInt(100000));
            prefs.edit().putString("userId", mUserId).commit();
        }

        mFirebaseRef = new Firebase(FIREBASE_URL).child(mUserId);
        if(mUserId.equals("42641")) {
            mOtherRef = new Firebase(FIREBASE_URL).child("57770");
        } else {
            mOtherRef = new Firebase(FIREBASE_URL).child("42641");
        }
        mOtherRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if(snapshot.getValue()==null) return;
                if(mIsFirstUpdate) {
                    if(SystemClock.elapsedRealtime()-mSystemTimePre<100) {
                        String[] data = ((String) snapshot.getValue()).split(",");
                        mSystemTime = (long)(Float.parseFloat(data[0]) * 1000.f);
                        mIsFirstUpdate = false;
                    }
                    mSystemTimePre = SystemClock.elapsedRealtime();
                }
                if(!mIsFirstUpdate){
                    String[] data = ((String) snapshot.getValue()).split(",");
                    if (mRenderer != null) {
                        mRenderer.updateOtherPosition(
                                (long)(Float.parseFloat(data[0]) * 1000.f) - mSystemTime,
                                Float.parseFloat(data[1]),
                                Float.parseFloat(data[2]),
                                Float.parseFloat(data[3]));
                    }
                }
            }
            @Override
            public void onCancelled(FirebaseError firebaseError) {
                System.out.println("The read failed: " + firebaseError.getMessage());
            }
        });

        mConnectedListener = mFirebaseRef.getRoot().child(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean connected = (Boolean) dataSnapshot.getValue();
                if (connected) {
                    Toast.makeText(MotionTrackingActivity.this, "Connected to Firebase", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MotionTrackingActivity.this, "Disconnected from Firebase", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
            }
        });
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
                // Update the OpenGL renderable objects with the new Tango Pose data.
                // Note that locking for thread safe access with the OpenGL loop is done entirely
                // in the renderer.
                mRenderer.updateDevicePose(pose);

                // Make sure to have atomic access to Tango Pose Data so that the UI
                // the UI loop doesn't interfere while Pose call back is updating the data
                synchronized (mUiThreadLock) {
                    mPose = pose;

                    //Now lets log some interesting statistics of Motion Tracking like
                    // Delta Time between two Poses, number of poses since the initialization state.
                    mDeltaTime = (float) (pose.timestamp - mPreviousTimeStamp) * SECS_TO_MILLISECS;
                    mPreviousTimeStamp = (float) pose.timestamp;
                    // Log whenever Motion Tracking enters an invalid state
                    if (!mIsAutoRecovery && (pose.statusCode == TangoPoseData.POSE_INVALID)) {
                        Log.w(TAG, "Invalid State");
                    }
                    if (mPreviousPoseStatus != pose.statusCode) {
                        mCount = 0;
                    }
                    mCount++;
                    mPreviousPoseStatus = pose.statusCode;

                    if (pose.timestamp - mPreviousSyncedTimestamp > 0.05) {
                        float[] data = pose.getTranslationAsFloats();
                        String send =
                                String.format("%.3f", pose.timestamp) + "," +
                                String.format("%.3f", data[0]) + "," +
                                String.format("%.3f", data[1]) + "," +
                                String.format("%.3f", data[2]);
                        mFirebaseRef.setValue(send);
                        mPreviousSyncedTimestamp = pose.timestamp;
                    }
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
        mFirebaseRef.getRoot().child(".info/connected").removeEventListener(mConnectedListener);
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
                                    synchronized (mUiThreadLock) {
                                        if (mPose == null) {
                                            return;
                                        }
                                        String translationString =
                                                TangoPoseUtilities.getTranslationString(mPose, threeDec);
                                        String quaternionString =
                                                TangoPoseUtilities.getQuaternionString(mPose, threeDec);
                                        String status = TangoPoseUtilities.getStatusString(mPose);
                                        // Display pose data on screen in TextViews
                                        mPoseTextView.setText(translationString);
                                        mQuatTextView.setText(quaternionString);
                                        mPoseCountTextView.setText(Integer.toString(mCount));
                                        mDeltaTextView.setText(threeDec.format(mDeltaTime));
                                        mPoseStatusTextView.setText(status);
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