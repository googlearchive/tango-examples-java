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

package com.projecttango.experiments.javaarealearning;

import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.app.FragmentManager;
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
 * Main Activity class for the Area Learning API Sample. Handles the connection to the Tango service
 * and propagation of Tango pose data to OpenGL and Layout views. OpenGL rendering logic is
 * delegated to the {@link AreaLearningRajawaliRenderer} class.
 */
public class AreaLearningActivity extends Activity implements View.OnClickListener,
        SetADFNameDialog.CallbackListener, SaveAdfTask.SaveAdfListener {

    private static final String TAG = AreaLearningActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;
    private Tango mTango;
    private TangoConfig mConfig;
    private TextView mTangoEventTextView;
    private TextView mStart2DeviceTranslationTextView;
    private TextView mAdf2DeviceTranslationTextView;
    private TextView mAdf2StartTranslationTextView;
    private TextView mStart2DeviceQuatTextView;
    private TextView mAdf2DeviceQuatTextView;
    private TextView mAdf2StartQuatTextView;
    private TextView mTangoServiceVersionTextView;
    private TextView mApplicationVersionTextView;
    private TextView mUUIDTextView;
    private TextView mStart2DevicePoseStatusTextView;
    private TextView mAdf2DevicePoseStatusTextView;
    private TextView mAdf2StartPoseStatusTextView;
    private TextView mStart2DevicePoseCountTextView;
    private TextView mAdf2DevicePoseCountTextView;
    private TextView mAdf2StartPoseCountTextView;
    private TextView mStart2DevicePoseDeltaTextView;
    private TextView mAdf2DevicePoseDeltaTextView;
    private TextView mAdf2StartPoseDeltaTextView;

    private Button mSaveAdfButton;
    private Button mFirstPersonButton;
    private Button mThirdPersonButton;
    private Button mTopDownButton;

    private int mStart2DevicePoseCount;
    private int mAdf2DevicePoseCount;
    private int mAdf2StartPoseCount;
    private int mStart2DevicePreviousPoseStatus;
    private int mAdf2DevicePreviousPoseStatus;
    private int mAdf2StartPreviousPoseStatus;

    private double mStart2DevicePoseDelta;
    private double mAdf2DevicePoseDelta;
    private double mAdf2StartPoseDelta;
    private double mStart2DevicePreviousPoseTimeStamp;
    private double mAdf2DevicePreviousPoseTimeStamp;
    private double mAdf2StartPreviousPoseTimeStamp;

    private double mPreviousPoseTimeStamp;
    private double mTimeToNextUpdate = UPDATE_INTERVAL_MS;

    private boolean mIsRelocalized;
    private boolean mIsLearningMode;
    private boolean mIsConstantSpaceRelocalize;

    private AreaLearningRajawaliRenderer mRenderer;
    private RajawaliSurfaceView mGLView;

    // Long-running task to save the ADF.
    private SaveAdfTask mSaveAdfTask;

    private TangoPoseData[] mPoses;
    private static final double UPDATE_INTERVAL_MS = 100.0;
    private static final DecimalFormat FORMAT_THREE_DECIMAL = new DecimalFormat("00.000");

    private final Object mSharedLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_area_learning);
        Intent intent = getIntent();
        mIsLearningMode = intent.getBooleanExtra(ALStartActivity.USE_AREA_LEARNING, false);
        mIsConstantSpaceRelocalize = intent.getBooleanExtra(ALStartActivity.LOAD_ADF, false);

        // Instantiate the Tango service
        mTango = new Tango(this);
        mIsRelocalized = false;
        mConfig = setTangoConfig(mTango, mIsLearningMode, mIsConstantSpaceRelocalize);
        setupTextViewsAndButtons(mConfig, mTango, mIsLearningMode, mIsConstantSpaceRelocalize);

        // Configure OpenGL renderer
        mRenderer = setupGLViewAndRenderer();

    }

    /**
     * Initializes pose data we keep track of. To be done
     */
    private void initializePoseData() {
        mPoses = new TangoPoseData[3];
        mStart2DevicePoseCount = 0;
        mAdf2DevicePoseCount = 0;
        mAdf2StartPoseCount = 0;
    }

    /**
     * Implements SetADFNameDialog.CallbackListener.
     */
    @Override
    public void onAdfNameOk(String name, String uuid) {
        saveAdf(name);
    }

    /**
     * Implements SetADFNameDialog.CallbackListener.
     */
    @Override
    public void onAdfNameCancelled() {
        // Continue running.
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            mTango.disconnect();
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.tango_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reset pose data and start counting from resume.
        initializePoseData();

        // Clear the relocalization state: we don't know where the device has been since our app was paused.
        mIsRelocalized = false;

        // Re-attach listeners.
        try {
            setUpTangoListeners();
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.tango_error, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(getApplicationContext(), R.string.no_permissions, Toast.LENGTH_SHORT).show();
        }

        // Connect to the tango service (start receiving pose updates).
        try {
            mTango.connect(mConfig);
        } catch (TangoOutOfDateException e) {
            Toast.makeText(getApplicationContext(), R.string.tango_out_of_date_exception, Toast.LENGTH_SHORT).show();
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.tango_error, Toast.LENGTH_SHORT).show();
        } catch (TangoInvalidException e) {
            Toast.makeText(getApplicationContext(), R.string.tango_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Listens for click events from any button in the view.
     */
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
            case R.id.saveAdf:
                // Query the user for an ADF name and save if OK was clicked.
                showSetADFNameDialog();
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
     * Sets Rajawalisurface view and its renderer. This is ideally called only once in onCreate.
     */
    private AreaLearningRajawaliRenderer setupGLViewAndRenderer(){
        // Configure OpenGL renderer
        AreaLearningRajawaliRenderer renderer = new AreaLearningRajawaliRenderer(this);
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
    private void setupTextViewsAndButtons(TangoConfig config, Tango tango, boolean isLearningMode, boolean isLoadAdf){
        mTangoEventTextView = (TextView) findViewById(R.id.tangoevent);

        mAdf2DeviceTranslationTextView = (TextView) findViewById(R.id.adf2devicePose);
        mStart2DeviceTranslationTextView = (TextView) findViewById(R.id.start2devicePose);
        mAdf2StartTranslationTextView = (TextView) findViewById(R.id.adf2startPose);
        mAdf2DeviceQuatTextView = (TextView) findViewById(R.id.adf2deviceQuat);
        mStart2DeviceQuatTextView = (TextView) findViewById(R.id.start2deviceQuat);
        mAdf2StartQuatTextView = (TextView) findViewById(R.id.adf2startQuat);

        mAdf2DevicePoseStatusTextView = (TextView) findViewById(R.id.adf2deviceStatus);
        mStart2DevicePoseStatusTextView = (TextView) findViewById(R.id.start2deviceStatus);
        mAdf2StartPoseStatusTextView = (TextView) findViewById(R.id.adf2startStatus);

        mAdf2DevicePoseCountTextView = (TextView) findViewById(R.id.adf2devicePosecount);
        mStart2DevicePoseCountTextView = (TextView) findViewById(R.id.start2devicePosecount);
        mAdf2StartPoseCountTextView = (TextView) findViewById(R.id.adf2startPosecount);

        mAdf2DevicePoseDeltaTextView = (TextView) findViewById(R.id.adf2deviceDeltatime);
        mStart2DevicePoseDeltaTextView = (TextView) findViewById(R.id.start2deviceDeltatime);
        mAdf2StartPoseDeltaTextView = (TextView) findViewById(R.id.adf2startDeltatime);

        mFirstPersonButton = (Button) findViewById(R.id.first_person_button);
        mThirdPersonButton = (Button) findViewById(R.id.third_person_button);
        mTopDownButton = (Button) findViewById(R.id.top_down_button);

        mTangoServiceVersionTextView = (TextView) findViewById(R.id.version);
        mApplicationVersionTextView = (TextView) findViewById(R.id.appversion);
        mGLView = (RajawaliSurfaceView) findViewById(R.id.gl_surface_view);

        mSaveAdfButton = (Button) findViewById(R.id.saveAdf);
        mUUIDTextView = (TextView) findViewById(R.id.uuid);

        // Set up button click listeners and button state.
        mFirstPersonButton.setOnClickListener(this);
        mThirdPersonButton.setOnClickListener(this);
        mTopDownButton.setOnClickListener(this);
        if (isLearningMode) {
            // Disable save ADF button until Tango relocalizes to the current ADF.
            mSaveAdfButton.setEnabled(false);
            mSaveAdfButton.setOnClickListener(this);
        } else {
            // Hide to save ADF button if leanring mode is off.
            mSaveAdfButton.setVisibility(View.GONE);
        }


        if(isLoadAdf){
            ArrayList<String> fullUUIDList = new ArrayList<String>();
            // Returns a list of ADFs with their UUIDs
            fullUUIDList = tango.listAreaDescriptions();
            if (fullUUIDList.size() == 0) {
                mUUIDTextView.setText(R.string.no_uuid);
            } else {
                mUUIDTextView.setText(getString(R.string.number_of_adfs) + fullUUIDList.size()
                        + getString(R.string.latest_adf_is)
                        + fullUUIDList.get(fullUUIDList.size() - 1));
            }
        }

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
    private TangoConfig setTangoConfig(Tango tango, boolean isLearningMode, boolean isLoadAdf) {
        TangoConfig config = new TangoConfig();
        config = tango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        // Check if learning mode
        if (isLearningMode) {
            // Set learning mode to config.
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);

        }
        // Check for Load ADF/Constant Space relocalization mode
        if (isLoadAdf) {
            ArrayList<String> fullUUIDList = new ArrayList<String>();
            // Returns a list of ADFs with their UUIDs
            fullUUIDList = tango.listAreaDescriptions();
            // Load the latest ADF if ADFs are found.
            if (fullUUIDList.size() > 0) {
                config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION,
                        fullUUIDList.get(fullUUIDList.size() - 1));
            }
        }
        return config;
    }

    /**
     * Set up the callback listeners for the Tango service, then begin using the Motion
     * Tracking API. This is called in response to the user clicking the 'Start' Button.
     */
    private void setUpTangoListeners() {

        // Set Tango Listeners for Poses Device wrt Start of Service, Device wrt
        // ADF and Start of Service wrt ADF
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzij) {
                // Not using XyzIj data for this sample
            }

            // Listen to Tango Events
            @Override
            public void onTangoEvent(final TangoEvent event) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Update the debug UI with information about this event.
                        mTangoEventTextView.setText(event.eventKey + ": " + event.eventValue);

                        // When saving an ADF, update the progress bar UI.
                        if (event.eventType == TangoEvent.EVENT_AREA_LEARNING &&
                                TangoEvent.KEY_AREA_DESCRIPTION_SAVE_PROGRESS.equals(event.eventKey)) {
                            int progressPercent = (int)(Double.parseDouble(event.eventValue) * 100);
                            if (mSaveAdfTask != null) {
                                mSaveAdfTask.publishProgress(progressPercent);
                            }
                        }
                    }
                });
            }

            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                boolean updateRenderer = false;
                // Make sure to have atomic access to Tango Data so that
                // UI loop doesn't interfere while Pose call back is updating
                // the data.
                synchronized (mSharedLock) {
                    // Check for Device wrt ADF pose, Device wrt Start of Service pose,
                    // Start of Service wrt ADF pose(This pose determines if device
                    // the is relocalized or not).
                    if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                        mPoses[0] = pose;
                        if (mAdf2DevicePreviousPoseStatus != pose.statusCode) {
                            // Set the count to zero when status code changes.
                            mAdf2DevicePoseCount = 0;
                        }
                        mAdf2DevicePreviousPoseStatus = pose.statusCode;
                        mAdf2DevicePoseCount++;
                        // Calculate time difference between current and last available Device wrt
                        // ADF pose.
                        mAdf2DevicePoseDelta = (pose.timestamp - mAdf2DevicePreviousPoseTimeStamp)
                                * SECS_TO_MILLISECS;
                        mAdf2DevicePreviousPoseTimeStamp = pose.timestamp;
                        if (mIsRelocalized) {
                            updateRenderer = true;
                        }
                    } else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                        mPoses[1] = pose;
                        if (mStart2DevicePreviousPoseStatus != pose.statusCode) {
                            // Set the count to zero when status code changes.
                            mStart2DevicePoseCount = 0;
                        }
                        mStart2DevicePreviousPoseStatus = pose.statusCode;
                        mStart2DevicePoseCount++;
                        // Calculate time difference between current and last available Device wrt
                        // SS pose.
                        mStart2DevicePoseDelta = (pose.timestamp - mStart2DevicePreviousPoseTimeStamp)
                                * SECS_TO_MILLISECS;
                        mStart2DevicePreviousPoseTimeStamp = pose.timestamp;
                        if (!mIsRelocalized) {
                            updateRenderer = true;
                        }

                    } else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
                        mPoses[2] = pose;
                        if (mAdf2StartPreviousPoseStatus != pose.statusCode) {
                            // Set the count to zero when status code changes.
                            mAdf2StartPoseCount = 0;
                        }
                        mAdf2StartPreviousPoseStatus = pose.statusCode;
                        mAdf2StartPoseCount++;
                        // Calculate time difference between current and last available SS wrt ADF
                        // pose.
                        mAdf2StartPoseDelta = (pose.timestamp - mAdf2StartPreviousPoseTimeStamp)
                                * SECS_TO_MILLISECS;
                        mAdf2StartPreviousPoseTimeStamp = pose.timestamp;
                        if (pose.statusCode == TangoPoseData.POSE_VALID) {
                            mIsRelocalized = true;
                            // Set the color to green
                        } else {
                            mIsRelocalized = false;
                            // Set the color blue
                        }
                    }
                }

                final double deltaTime = (pose.timestamp - mPreviousPoseTimeStamp) * SECS_TO_MILLISECS;
                mPreviousPoseTimeStamp = pose.timestamp;
                mTimeToNextUpdate -= deltaTime;

                if (mTimeToNextUpdate < 0.0) {
                    mTimeToNextUpdate = UPDATE_INTERVAL_MS;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mSharedLock) {
                                updateTextViews();
                            }
                        }
                    });
                }

                if (updateRenderer) {
                    mRenderer.updateDevicePose(pose, mIsRelocalized);
                }
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }

    /**
     * Save the current Area Description File.
     * Performs saving on a background thread and displays a progress dialog.
     */
    private void saveAdf(String adfName) {
        mSaveAdfTask = new SaveAdfTask(this, this, mTango, adfName);
        mSaveAdfTask.execute();
    }

    /**
     * Handles failed save from mSaveAdfTask.
     */
    @Override
    public void onSaveAdfFailed(String adfName) {
        String toastMessage = String.format(
            getResources().getString(R.string.save_adf_failed_toast_format),
            adfName);
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        mSaveAdfTask = null;
    }

    /**
     * Handles successful save from mSaveAdfTask.
     */
    @Override
    public void onSaveAdfSuccess(String adfName, String adfUuid) {
        String toastMessage = String.format(
            getResources().getString(R.string.save_adf_success_toast_format),
            adfName, adfUuid);
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        mSaveAdfTask = null;
        finish();
    }

    /**
     * Shows a dialog for setting the ADF name.
     */
    private void showSetADFNameDialog() {
        Bundle bundle = new Bundle();
        bundle.putString("name", "New ADF");
        bundle.putString("id", ""); // UUID is generated after the ADF is saved.

        FragmentManager manager = getFragmentManager();
        SetADFNameDialog setADFNameDialog = new SetADFNameDialog();
        setADFNameDialog.setArguments(bundle);
        setADFNameDialog.show(manager, "ADFNameDialog");
    }

    /**
     * Updates the text view in UI screen with the Pose. Each pose is associated with Target and
     * Base Frame. We need to check for that pair and update our views accordingly.
     */
    private void updateTextViews() {
        // Allow clicking of the save button only when Tango is localized to the current ADF.
        mSaveAdfButton.setEnabled(mIsRelocalized);

        if (mPoses[0] != null && mPoses[0].baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                && mPoses[0].targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
            mAdf2DeviceTranslationTextView.setText(TangoPoseUtilities.getTranslationString(mPoses[0], FORMAT_THREE_DECIMAL));
            mAdf2DeviceQuatTextView.setText(TangoPoseUtilities.getQuaternionString(mPoses[0], FORMAT_THREE_DECIMAL));
            mAdf2DevicePoseStatusTextView.setText(TangoPoseUtilities.getStatusString(mPoses[0]));
            mAdf2DevicePoseCountTextView.setText(Integer.toString(mAdf2DevicePoseCount));
            mAdf2DevicePoseDeltaTextView.setText(FORMAT_THREE_DECIMAL.format(mAdf2DevicePoseDelta));
        }

        if (mPoses[1] != null && mPoses[1].baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE
                && mPoses[1].targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
            mStart2DeviceTranslationTextView.setText(TangoPoseUtilities.getTranslationString(mPoses[1], FORMAT_THREE_DECIMAL));
            mStart2DeviceQuatTextView.setText(TangoPoseUtilities.getQuaternionString(mPoses[1], FORMAT_THREE_DECIMAL));
            mStart2DevicePoseStatusTextView.setText(TangoPoseUtilities.getStatusString(mPoses[1]));
            mStart2DevicePoseCountTextView.setText(Integer.toString(mStart2DevicePoseCount));
            mStart2DevicePoseDeltaTextView.setText(FORMAT_THREE_DECIMAL.format(mStart2DevicePoseDelta));
        }

        if (mPoses[2] != null && mPoses[2].baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                && mPoses[2].targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
            mAdf2StartTranslationTextView.setText(TangoPoseUtilities.getTranslationString(mPoses[2], FORMAT_THREE_DECIMAL));
            mAdf2StartQuatTextView.setText(TangoPoseUtilities.getQuaternionString(mPoses[2], FORMAT_THREE_DECIMAL));
            mAdf2StartPoseStatusTextView.setText(TangoPoseUtilities.getStatusString(mPoses[2]));
            mAdf2StartPoseCountTextView.setText(Integer.toString(mAdf2StartPoseCount));
            mAdf2StartPoseDeltaTextView.setText(FORMAT_THREE_DECIMAL.format(mAdf2StartPoseDelta));
        }
    }
}
