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

package com.projecttango.examples.java.pointtopoint;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

/**
 * An example showing how to build a very simple point to point measurement app
 * in Java. It uses the TangoSupportLibrary to do depth calculations using
 * the PointCloud data. Whenever the user clicks on the camera display, a point
 * is recorded from the PointCloud data closest to the point of the touch.
 * consecutive touches are used as the two points for a distance measurement.
 * <p/>
 * Note that it is important to include the KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION
 * configuration parameter in order to achieve best results synchronizing the
 * Rajawali virtual world with the RGB camera.
 * <p/>
 * For more details on the augmented reality effects, including color camera texture rendering,
 * see java_augmented_reality_example or java_hello_video_example.
 */
public class PointToPointActivity extends Activity implements View.OnTouchListener {
    private static final String TAG = PointToPointActivity.class.getSimpleName();

    // The interval at which we'll update our UI debug text in milliseconds.
    // This is the rate at which we query for distance data.
    private static final int UPDATE_UI_INTERVAL_MS = 100;

    public static final TangoCoordinateFramePair FRAME_PAIR = new TangoCoordinateFramePair(
            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
            TangoPoseData.COORDINATE_FRAME_DEVICE);
    private static final int INVALID_TEXTURE_ID = 0;

    private RajawaliSurfaceView mSurfaceView;
    private PointToPointRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private TangoPointCloudManager mPointCloudManager;
    private Tango mTango;
    private boolean mIsConnected = false;
    private double mCameraPoseTimestamp = 0;
    private TextView mDistanceMeasure;

    // Texture rendering related fields
    // NOTE: Naming indicates which thread is in charge of updating this variable
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    private float[][] mLinePoints = new float[2][3];
    private boolean mPointSwitch = true;

    // Handles the debug text UI update loop.
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (RajawaliSurfaceView) findViewById(R.id.ar_view);
        mRenderer = new PointToPointRenderer(this);
        mSurfaceView.setSurfaceRenderer(mRenderer);
        mSurfaceView.setOnTouchListener(this);
        mPointCloudManager = new TangoPointCloudManager();
        mDistanceMeasure = (TextView) findViewById(R.id.distance_textview);
        mLinePoints[0] = null;
        mLinePoints[1] = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearLine();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        // NOTE: DO NOT lock against this same object in the Tango callback thread. Tango.disconnect
        // will block here until all Tango callback calls are finished. If you lock against this
        // object in a Tango callback thread it will cause a deadlock.
        synchronized (this) {
            if (mIsConnected) {
                mRenderer.getCurrentScene().clearFrameCallbacks();
                mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                // We need to invalidate the connected texture ID so that we cause a re-connection
                // in the OpenGL thread after resume
                mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
                mTango.disconnect();
                mIsConnected = false;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        synchronized (this) {
            if (!mIsConnected) {
                // Initialize Tango Service as a normal Android Service, since we call
                // mTango.disconnect() in onPause, this will unbind Tango Service, so
                // everytime when onResume get called, we should create a new Tango object.
                mTango = new Tango(PointToPointActivity.this, new Runnable() {
                    // Pass in a Runnable to be called from UI thread when Tango is ready,
                    // this Runnable will be running on a new thread.
                    // When Tango is ready, we can call Tango functions safely here only
                    // when there is no UI thread changes involved.
                    @Override
                    public void run() {
                        try {
                            TangoSupport.initialize();
                            connectTango();
                            connectRenderer();
                            mIsConnected = true;
                        } catch (TangoOutOfDateException e) {
                            Log.e(TAG, getString(R.string.tango_out_of_date_exception), e);
                        }
                    }
                });
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
                // Check if the frame available is for the camera we want and update its frame
                // on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Mark a camera frame is available for rendering in the OpenGL thread
                    mIsFrameAvailableTangoThread.set(true);
                    mSurfaceView.requestRender();
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

        mIntrinsics = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
    }

    /**
     * Connects the view and renderer to the color camara and callbacks.
     */
    private void connectRenderer() {
        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks had a chance to run and before scene objects are rendered
                // into the scene.

                // Prevent concurrent access to {@code mIsFrameAvailableTangoThread} from the Tango
                // callback thread and service disconnection from an onPause event.
                synchronized (PointToPointActivity.this) {
                    // Don't execute any tango API actions if we're not connected to the service
                    if (!mIsConnected) {
                        return;
                    }

                    // Set-up scene camera projection to match RGB camera intrinsics
                    if (!mRenderer.isSceneCameraConfigured()) {
                        mRenderer.setProjectionMatrix(mIntrinsics);
                    }

                    // Connect the camera texture to the OpenGL Texture if necessary
                    // NOTE: When the OpenGL context is recycled, Rajawali may re-generate the
                    // texture with a different ID.
                    if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                        mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                mRenderer.getTextureId());
                        mConnectedTextureIdGlThread = mRenderer.getTextureId();
                        Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                    }

                    // If there is a new RGB camera frame available, update the texture with it
                    if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                        mRgbTimestampGlThread =
                                mTango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                    }

                    // If a new RGB frame has been rendered, update the camera pose to match.
                    if (mRgbTimestampGlThread > mCameraPoseTimestamp) {
                        // Calculate the camera color pose at the camera frame update time in
                        // OpenGL engine.
                        TangoPoseData lastFramePose = TangoSupport.getPoseAtTime(
                                mRgbTimestampGlThread,
                                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL, 0);
                        if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                            // Update the camera pose from the renderer
                            mRenderer.updateRenderCameraPose(lastFramePose);
                            mCameraPoseTimestamp = lastFramePose.timestamp;
                        } else {
                            Log.w(TAG, "Can't get device pose at time: " +
                                    mRgbTimestampGlThread);
                        }
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

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            // Calculate click location in u,v (0;1) coordinates.
            float u = motionEvent.getX() / view.getWidth();
            float v = motionEvent.getY() / view.getHeight();

            try {
                // Place point near the clicked point using the latest point cloud data
                // Synchronize against concurrent access to the RGB timestamp in the OpenGL thread
                // and a possible service disconnection due to an onPause event.
                float[] rgbPoint;
                synchronized (this) {
                    rgbPoint = getDepthAtTouchPosition(u, v, mRgbTimestampGlThread);
                }
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
    private float[] getDepthAtTouchPosition(float u, float v, double rgbTimestamp) {
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

        // Get the transform from depth camera to OpenGL world at the timestamp of the cloud.
        TangoSupport.TangoMatrixTransformData transform =
                TangoSupport.getMatrixTransformAtTime(xyzIj.timestamp,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO);
        if (transform.statusCode == TangoPoseData.POSE_VALID) {
            float[] dephtPoint = new float[]{point[0], point[1], point[2], 1};
            float[] openGlPoint = new float[4];
            Matrix.multiplyMV(openGlPoint, 0, transform.matrix, 0, dephtPoint, 0);
            return openGlPoint;
        } else {
            Log.w(TAG, "Could not get depth camera transform at time " + xyzIj.timestamp);
        }
        return null;
    }

    /**
     * Update the oldest line endpoint to the value passed into this function.
     * This will also flag the line for update on the next render pass.
     */
    private synchronized void updateLine(float[] worldPoint) {
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
            points.push(new Vector3(mLinePoints[0][0], mLinePoints[0][1], mLinePoints[0][2]));
            points.push(new Vector3(mLinePoints[1][0], mLinePoints[1][1], mLinePoints[1][2]));
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
        float[] p1 = mLinePoints[0];
        float[] p2 = mLinePoints[1];
        double separation = Math.sqrt(
                Math.pow(p1[0] - p2[0], 2) +
                        Math.pow(p1[1] - p2[1], 2) +
                        Math.pow(p1[2] - p2[2], 2));
        return String.format("%.2f", separation) + " meters";
    }

    // Debug text UI update loop, updating at 10Hz.
    private Runnable mUpdateUiLoopRunnable = new Runnable() {
        public void run() {
            updateUi();
            mHandler.postDelayed(this, UPDATE_UI_INTERVAL_MS);
        }
    };

    private synchronized void updateUi() {
        try {
            mDistanceMeasure.setText(getPointSeparation());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
