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

package com.projecttango.experiments.pointtopointsample;

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

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.scene.ASceneFrameCallback;

import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.ar.TangoRajawaliView;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;
import com.projecttango.tangosupport.TangoSupport.IntersectionPointPlaneModelPair;

/**
 * An example showing how to build a very simple point to point measurement app
 * in Java. It uses Rajawali to do the rendering through the utility classes
 * {@code TangoRajawaliRenderer} and {@code TangoRajawaliView} from
 * TangoUtils.
 * It also uses the TangoSupportLibrary to do depth calculations using
 * the PointCloud data. Whenever the user clicks on the camera display, a point
 * is recorded from the PointCloud data closest to the point of the touch. 
 * consecutive touches are used as the two points for a distance measurement.
 * <p/>
 * See the Augmented Reality sample for details on how the TangoRajawaliView
 * and TangoRajawaliRenderer are used to render 3D objects with an appropriate
 * augmented reality effect.
 * <p/>
 * Note that it is important to include the KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION
 * configuration parameter in order to achieve best results synchronizing the
 * Rajawali virtual world with the RGB camera.
 */
public class PointToPointActivity extends Activity implements View.OnTouchListener {
    private static final String TAG = PointToPointActivity.class.getSimpleName();

    // The interval at which we'll update our UI debug text in milliseconds.
    // This is the rate at which we query for distance data.
    private static final int UPDATE_UI_INTERVAL_MS = 100;

    public static final TangoCoordinateFramePair FRAME_PAIR = new TangoCoordinateFramePair(
            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
            TangoPoseData.COORDINATE_FRAME_DEVICE);

    private TangoRajawaliView mGLView;
    private PointToPointRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private DeviceExtrinsics mExtrinsics;
    private TangoPointCloudManager mPointCloudManager;
    private Tango mTango;
    private AtomicBoolean mIsConnected = new AtomicBoolean(false);
    private double mCameraPoseTimestamp = 0;
    private TextView mDistanceMeasure;

    private Vector3[] mLinePoints = new Vector3[2];
    private boolean mPointSwitch = true;

    // Handles the debug text UI update loop.
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mGLView = (TangoRajawaliView) findViewById(R.id.ar_view);
        mRenderer = new PointToPointRenderer(this);
        mGLView.setSurfaceRenderer(mRenderer);
        mGLView.setOnTouchListener(this);
        mTango = new Tango(this);
        mPointCloudManager = new TangoPointCloudManager();
        mDistanceMeasure = (TextView) findViewById(R.id.distance_textview);
        mLinePoints[0] = null;
        mLinePoints[1] = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearLine();
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
                        R.string.tango_out_of_date_exception,
                        Toast.LENGTH_SHORT).show();
            }
        }
        mHandler.post(mUpdateUiLoopRunnable);
    }

    /**
     * Configures the Tango service and connects it to callbacks.
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
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
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
                // Place point near the clicked point using the latest point cloud data
                Vector3 rgbPoint = getDepthAtTouchPosition(u, v, 
                                        mRenderer.getTimestamp());
                if (rgbPoint != null) {
                    // Update a line endpoint to the touch location.
                    // This update is made thread safe by the renderer
                    updateLine(rgbPoint);
                    mRenderer.setLine(generateEndpoints());
                } else {
                    Log.w(TAG, "Point was null.");
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
     * Use the TangoSupport library with point cloud data to calculate the depth 
     * of the point closest to where the user touches the screen. It returns a 
     * Vector3 in openGL world space.
     */
    private Vector3 getDepthAtTouchPosition(float u, float v, double rgbTimestamp) {
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

        float[] point = TangoSupport.getDepthAtPointNearestNeighbor(xyzIj, mIntrinsics,
                colorTdepthPose, u, v);
        if (point == null) {
            return null;
        }

        return ScenePoseCalculator.getPointInEngineFrame(
                    new Vector3(point[0], point[1], point[2]),
                    ScenePoseCalculator.matrixToTangoPose(mExtrinsics.getDeviceTDepthCamera()), 
                    mTango.getPoseAtTime(rgbTimestamp, FRAME_PAIR));
    }

    /**
     * Update the oldest line endpoint to the value passed into this function.
     * This will also flag the line for update on the next render pass.
     */
    private synchronized void updateLine(Vector3 worldPoint) {
        if (mPointSwitch) {
            mPointSwitch = !mPointSwitch;
            mLinePoints[0] = worldPoint;
            return;
        }
        mPointSwitch = !mPointSwitch;
        mLinePoints[1] = worldPoint;
    }

    /**
     * Return the endpoints of the line as a Stack of Vector3s. Returns
     * null if the line is not visible.
     */
    private synchronized Stack<Vector3> generateEndpoints() {

        // Place the line based on the two points.
        if (mLinePoints[0] != null && mLinePoints[1] != null) {
            Stack<Vector3> points = new Stack<Vector3>();
            points.push(mLinePoints[0]);
            points.push(mLinePoints[1]);
            return points;
        }
        return null;
    }
    
    /*
     * Remove all the points from the Scene.
     */
    private synchronized void clearLine() {
        mLinePoints[0] = null;
        mLinePoints[1] = null;
        mPointSwitch = true;
        mRenderer.setLine(null);
    }

    /**
     * Produces the String for the line length base on
     * endpoint locations.
     */
    private synchronized String getPointSeparation() {
        if (mLinePoints[0] == null || mLinePoints[1] == null) {
            return "Null";
        }
        Vector3 p1 = mLinePoints[0];
        Vector3 p2 = mLinePoints[1];
        double separation = Math.sqrt(
                                Math.pow(p1.x - p2.x, 2) + 
                                Math.pow(p1.y - p2.y, 2) + 
                                Math.pow(p1.z - p2.z, 2));
        return String.format("%.2f", separation) + " meters";
    }

    // Debug text UI update loop, updating at 10Hz.
    private Runnable mUpdateUiLoopRunnable = new Runnable() {
        public void run() {
            updateUi();
            mHandler.postDelayed(this, UPDATE_UI_INTERVAL_MS);
        }
    };

    private synchronized void updateUi(){
        try {
            mDistanceMeasure.setText(getPointSeparation());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
