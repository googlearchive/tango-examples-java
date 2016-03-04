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

package com.projecttango.experiments.javapointcloud;

import com.google.atap.tango.ux.TangoUx;
import com.google.atap.tango.ux.TangoUx.StartParams;
import com.google.atap.tango.ux.TangoUxLayout;
import com.google.atap.tango.ux.UxExceptionEvent;
import com.google.atap.tango.ux.UxExceptionEventListener;
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
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.nio.FloatBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.tangosupport.TangoPointCloudManager;

/**
 * Main Activity class for the Point Cloud Sample. Handles the connection to the {@link Tango}
 * service and propagation of Tango XyzIj data to OpenGL and Layout views. OpenGL rendering logic is
 * delegated to the {@link PointCloudRajawaliRenderer} class.
 */
public class PointCloudActivity extends Activity implements OnClickListener {

    private static final String TAG = PointCloudActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;

    // Configure the Tango coordinate frame pair
    private static final ArrayList<TangoCoordinateFramePair> FRAME_PAIRS =
            new ArrayList<TangoCoordinateFramePair>();

    {
        FRAME_PAIRS.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
    }

    private Tango mTango;
    private TangoUx mTangoUx;
    private TangoPointCloudManager mPointCloudManager;
    private DeviceExtrinsics mExtrinsics;

    private PointCloudRajawaliRenderer mRenderer;

    private TextView mPointCountTextView;
    private TextView mAverageZTextView;

    private Button mFirstPersonButton;
    private Button mThirdPersonButton;
    private Button mTopDownButton;

    private double mXyIjPreviousTimeStamp;
    private AtomicBoolean mIsConnected = new AtomicBoolean(false);
    private TangoPoseData mPose;

    private static final DecimalFormat FORMAT_THREE_DECIMAL = new DecimalFormat("0.000");
    private static final double UPDATE_INTERVAL_MS = 100.0;

    private double mXyzIjTimeToNextUpdate = UPDATE_INTERVAL_MS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jpoint_cloud);

        mRenderer = setupGLViewAndRenderer();
        mTango = new Tango(this);
        mPointCloudManager = new TangoPointCloudManager();
        mTangoUx = setupTangoUxAndLayout();

        setupTextViewsAndButtons();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mIsConnected.compareAndSet(true, false)) {
            mTangoUx.stop();
            mRenderer.getCurrentScene().clearFrameCallbacks();
            mTango.disconnect();
            mPose = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mIsConnected.compareAndSet(false, true)) {
            mTangoUx.start(new StartParams());
            try {
                connectTango();
                connectRenderer();
            } catch (TangoOutOfDateException outDateEx) {
                if (mTangoUx != null) {
                    mTangoUx.showTangoOutOfDate();
                }
            } catch (TangoErrorException e) {
                Toast.makeText(this, R.string.exception_tango_error, Toast.LENGTH_SHORT).show();
            } catch (SecurityException e) {
                Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
                        Toast.LENGTH_SHORT).show();
            }
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
            case R.id.third_person_button:
                mRenderer.setThirdPersonView();
                break;
            case R.id.top_down_button:
                mRenderer.setTopDownView();
                break;
            default:
                Log.w(TAG, "Unrecognized button click.");
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mRenderer.onTouchEvent(event);
        return true;
    }

    private void connectTango() {
        // Use the default configuration plus add depth sensing
        TangoConfig config = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        mTango.connect(config);

        // Listen for new Tango data
        mTango.connectListener(FRAME_PAIRS, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                // Passing in the pose data to UX library produce exceptions.
                if (mTangoUx != null) {
                    mTangoUx.updatePoseStatus(pose.statusCode);
                }

                // Update our copy of the latest pose
                // Synchronize against concurrent use in the render loop.
                synchronized (this) {
                    mPose = pose;
                }
            }

            @Override
            public void onXyzIjAvailable(final TangoXyzIjData xyzIj) {
                if (mTangoUx != null) {
                    mTangoUx.updateXyzCount(xyzIj.xyzCount);
                }
                mPointCloudManager.updateXyzIj(xyzIj);

                final double currentTimeStamp = xyzIj.timestamp;
                final double pointCloudFrameDelta = (currentTimeStamp - mXyIjPreviousTimeStamp)
                        * SECS_TO_MILLISECS;
                mXyIjPreviousTimeStamp = currentTimeStamp;
                final double averageDepth = getAveragedDepth(xyzIj.xyz);

                mXyzIjTimeToNextUpdate -= pointCloudFrameDelta;

                if (mXyzIjTimeToNextUpdate < 0.0) {
                    mXyzIjTimeToNextUpdate = UPDATE_INTERVAL_MS;
                    final String pointCountString = Integer.toString(xyzIj.xyzCount);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mPointCountTextView.setText(pointCountString);
                            mAverageZTextView.setText(FORMAT_THREE_DECIMAL.format(averageDepth));
                        }
                    });
                }
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                if (mTangoUx != null) {
                    mTangoUx.updateTangoEvent(event);
                }
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });

        // After connecting the Tang service, query and store the camera extrinsics information
        mExtrinsics = setupExtrinsics();
    }

    public void connectRenderer() {
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This will be executed on each cycle before rendering, called from the
                // OpenGL rendering thread

                // NOTE: Sometimes a pre-frame call will already be scheduled by the time the Tango
                // service is disconnected, so we need to check for service connection here just
                // in case. This avoid crashes when pausing the application.
                if (!mIsConnected.get()) {
                    return;
                }

                // Update point cloud data
                TangoXyzIjData pointCloud = mPointCloudManager.getLatestXyzIj();
                if (pointCloud != null) {
                    TangoPoseData pointCloudPose =
                            mTango.getPoseAtTime(pointCloud.timestamp, FRAME_PAIRS.get(0));
                    mRenderer.updatePointCloud(pointCloud, pointCloudPose, mExtrinsics);
                }

                // Update current device pose
                synchronized (this) {
                    if (mPose != null) {
                        mRenderer.updateDevicePose(mPose, mExtrinsics);
                    }
                }
            }

            @Override
            public boolean callPreFrame() {
                return true;
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }
        });
    }

    private DeviceExtrinsics setupExtrinsics() {
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        TangoPoseData imuTColorCameraPose = mTango.getPoseAtTime(0.0, framePair);

        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH;
        TangoPoseData imuTDepthCameraPose = mTango.getPoseAtTime(0.0, framePair);

        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        TangoPoseData imuTDevicePose = mTango.getPoseAtTime(0.0, framePair);

        return new DeviceExtrinsics(imuTDevicePose, imuTColorCameraPose, imuTDepthCameraPose);
    }

    /*
   * This is an advanced way of using UX exceptions. In most cases developers can just use the in
   * built exception notifications using the Ux Exception layout. In case a developer doesn't want
   * to use the default Ux Exception notifications, he can set the UxException listener as shown
   * below.
   * In this example we are just logging all the ux exceptions to logcat, but in a real app,
   * developers should use these exceptions to contextually notify the user and help direct the
   * user in using the device in a way Tango service expects it.
   */
    private UxExceptionEventListener mUxExceptionListener = new UxExceptionEventListener() {

        @Override
        public void onUxExceptionEvent(UxExceptionEvent uxExceptionEvent) {
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_LYING_ON_SURFACE) {
                Log.i(TAG, "Device lying on surface ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_DEPTH_POINTS) {
                Log.i(TAG, "Very few depth points in mPoint cloud ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_FEATURES) {
                Log.i(TAG, "Invalid poses in MotionTracking ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_INCOMPATIBLE_VM) {
                Log.i(TAG, "Device not running on ART");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOTION_TRACK_INVALID) {
                Log.i(TAG, "Invalid poses in MotionTracking ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOVING_TOO_FAST) {
                Log.i(TAG, "Invalid poses in MotionTracking ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_OVER_EXPOSED) {
                Log.i(TAG, "Camera Over Exposed");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_TANGO_SERVICE_NOT_RESPONDING) {
                Log.i(TAG, "TangoService is not responding ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_UNDER_EXPOSED) {
                Log.i(TAG, "Camera Under Exposed ");
            }

        }
    };

    /**
     * Sets Text views to display statistics of Poses being received. This also sets the buttons
     * used in the UI.
     */
    private void setupTextViewsAndButtons() {
        mPointCountTextView = (TextView) findViewById(R.id.point_count_textview);
        mAverageZTextView = (TextView) findViewById(R.id.average_z_textview);

        mFirstPersonButton = (Button) findViewById(R.id.first_person_button);
        mFirstPersonButton.setOnClickListener(this);
        mThirdPersonButton = (Button) findViewById(R.id.third_person_button);
        mThirdPersonButton.setOnClickListener(this);
        mTopDownButton = (Button) findViewById(R.id.top_down_button);
        mTopDownButton.setOnClickListener(this);
    }

    /**
     * Sets Rajawalisurface view and its renderer. This is ideally called only once in onCreate.
     */
    private PointCloudRajawaliRenderer setupGLViewAndRenderer() {
        PointCloudRajawaliRenderer renderer = new PointCloudRajawaliRenderer(this);
        RajawaliSurfaceView glView = (RajawaliSurfaceView) findViewById(R.id.gl_surface_view);
        glView.setEGLContextClientVersion(2);
        glView.setSurfaceRenderer(renderer);
        return renderer;
    }

    /**
     * Sets up TangoUX layout and sets its listener.
     */
    private TangoUx setupTangoUxAndLayout() {
        TangoUxLayout uxLayout = (TangoUxLayout) findViewById(R.id.layout_tango);
        TangoUx tangoUx = new TangoUx(this);
        tangoUx.setLayout(uxLayout);
        tangoUx.setUxExceptionEventListener(mUxExceptionListener);
        return tangoUx;
    }

    /**
     * Calculates the average depth from a point cloud buffer.
     *
     * @param pointCloudBuffer
     * @return Average depth.
     */
    private float getAveragedDepth(FloatBuffer pointCloudBuffer) {
        int pointCount = pointCloudBuffer.capacity() / 3;
        float totalZ = 0;
        float averageZ = 0;
        for (int i = 0; i < pointCloudBuffer.capacity() - 3; i = i + 3) {
            totalZ = totalZ + pointCloudBuffer.get(i + 2);
        }
        if (pointCount != 0) {
            averageZ = totalZ / pointCount;
        }
        return averageZ;
    }
}
