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

package com.projecttango.experiments.floorplansample;

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
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.scene.ASceneFrameCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.ar.TangoRajawaliView;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;
import com.projecttango.tangosupport.TangoSupport.IntersectionPointPlaneModelPair;

/**
 * An example showing how to build a very simple application that allows the user to create a floor
 * plan in Java. It uses Rajawali to do the rendering through the utility classes
 * {@code TangoRajawaliRenderer} and {@code TangoRajawaliView} from java_examples_utils. It also
 * uses the TangoSupportLibrary to do plane fitting using the point cloud data.
 * When the user clicks on the display, plane detection is done on the surface at the location of
 * the click and a 3D object will be placed in the scene anchored at that location. A
 * {@code WallMeasurement} will be recorded for that plane.
 * <p/>
 * You need to take exactly one measurement per wall in clockwise order. As you take measurements,
 * the perimeter of the floor plan will be displayed as lines in AR. After you have taken all the
 * measurements you can press the 'Done' button and the final result will be drawn in 2D as seen
 * from above along with labels showing the sizes of the walls.
 * <p/>
 * You are going to be building an ADF as you take the measurements. After pressing the 'Done'
 * button the ADF will be saved and an optimization will be run on it. After that, all the recorded
 * measurements are re-queried and the floor plan will be rebuilt in order to have better precision.
 * <p/>
 * Note that it is important to include the KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION configuration
 * parameter in order to achieve the best results synchronizing the Rajawali virtual world with the
 * RGB camera.
 */
public class FloorplanActivity extends Activity implements View.OnTouchListener {
    private static final String TAG = FloorplanActivity.class.getSimpleName();
    // Record Device to Area Description as the main frame pair to be used for device pose
    // queries.
    private static final TangoCoordinateFramePair FRAME_PAIR = new TangoCoordinateFramePair(
            TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
            TangoPoseData.COORDINATE_FRAME_DEVICE);

    private TangoRajawaliView mGLView;
    private FloorplanRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private DeviceExtrinsics mExtrinsics;
    private TangoPointCloudManager mPointCloudManager;
    private Tango mTango;
    private AtomicBoolean mIsConnected = new AtomicBoolean(false);
    private double mCameraPoseTimestamp = 0;
    private List<WallMeasurement> mWallMeasurementList;
    private Floorplan mFloorplan;
    private FinishPlanTask mFinishPlanTask;
    private Button mDoneButton;
    private ViewGroup mProgressGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGLView = (TangoRajawaliView) findViewById(R.id.ar_view);
        mRenderer = new FloorplanRenderer(this);
        mGLView.setSurfaceRenderer(mRenderer);
        mGLView.setOnTouchListener(this);
        // Set ZOrderOnTop to false so the other views don't get hidden by the SurfaceView.
        mGLView.setZOrderOnTop(false);
        mProgressGroup = (ViewGroup) findViewById(R.id.progress_group);
        mTango = new Tango(this);
        mPointCloudManager = new TangoPointCloudManager();
        mWallMeasurementList = new ArrayList<WallMeasurement>();
        mDoneButton = (Button) findViewById(R.id.done_button);
        mDoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishPlan();
            }
        });
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
        // Check if it has permissions.
        // Area learning permissions are needed in order to save the adf.
        if (Tango.hasPermission(this, Tango.PERMISSIONTYPE_ADF_LOAD_SAVE)) {
            connectAndStart();
        } else {
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                    Tango.TANGO_INTENT_ACTIVITYCODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Area Learning Permissions Required!",
                        Toast.LENGTH_SHORT).show();
                finish();
            } else {
                connectAndStart();
            }
        }
    }

    /**
     * Connect to Tango service and connect the camera to the renderer.
     */
    private void connectAndStart() {
        if (mIsConnected.compareAndSet(false, true)) {
            try {
                connectTango();
                connectRenderer();
            } catch (TangoOutOfDateException e) {
                Toast.makeText(getApplicationContext(),
                        R.string.exception_out_of_date,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Configure the Tango service and connect it to callbacks.
     */
    private void connectTango() {
        // Use default configuration for Tango Service, plus low latency
        // IMU integration and area learning.
        TangoConfig config = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of virtual
        // objects with the RBG image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        // NOTE: Area learning is necessary to achieve better precision is pose estimation
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        mTango.connect(config);

        // No need to add any coordinate frame pairs since we are not
        // using pose data. So just initialize.
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
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
                // We are not using OnTangoEvent for this app.
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
                        mRenderer.updateRenderCameraPose(lastFramePose);
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

    /**
     * This method handles when the user clicks the screen. It will try to fit a plane to the
     * clicked point using depth data. The floor plan will be rebuilt and the result will be shown
     * in AR.
     */
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            // Calculate click location in u,v (0;1) coordinates.
            float u = motionEvent.getX() / view.getWidth();
            float v = motionEvent.getY() / view.getHeight();

            try {
                // Take a wall measurement by fitting a plane on the clicked point using the latest
                // point cloud data.
                WallMeasurement wallMeasurement = doWallMeasurement(u, v, mRenderer.getTimestamp());

                // If the measurement was successful add it and run the floor plan building
                // algorithm.
                if (wallMeasurement != null) {
                    mWallMeasurementList.add(wallMeasurement);
                    mRenderer.addWallMeasurement(wallMeasurement);
                    buildPlan(false);
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
     * Use the TangoSupport library and point cloud data to calculate the plane at the specified
     * location in the color camera frame.
     * It returns the pose of the fitted plane in a TangoPoseData structure.
     */
    private WallMeasurement doWallMeasurement(float u, float v, double rgbTimestamp) {
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
        TangoPoseData planeFitPose = calculatePlanePose(
                intersectionPointPlaneModelPair.intersectionPoint,
                intersectionPointPlaneModelPair.planeModel, devicePose);

        return new WallMeasurement(planeFitPose, devicePose);
    }

    /**
     * Calculate the pose of the plane based on the position and normal orientation of the plane
     * and align it with gravity.
     */
    private TangoPoseData calculatePlanePose(double[] point, double normal[],
                                             TangoPoseData devicePose) {
        Matrix4 adfTdevice = ScenePoseCalculator.tangoPoseToMatrix(devicePose);
        // Vector aligned to gravity.
        Vector3 depthUp = ScenePoseCalculator.TANGO_WORLD_UP.clone();
        adfTdevice.clone().multiply(mExtrinsics.getDeviceTDepthCamera()).inverse().
                rotateVector(depthUp);
        // Create the plane matrix transform in depth frame from a point, the plane normal and the
        // up vector.
        Matrix4 depthTplane = ScenePoseCalculator.matrixFromPointNormalUp(point, normal, depthUp);
        // Plane matrix transform in adf frame.
        Matrix4 adfTplane =
                adfTdevice.multiply(mExtrinsics.getDeviceTDepthCamera()).multiply(depthTplane);

        TangoPoseData planeFitPose = ScenePoseCalculator.matrixToTangoPose(adfTplane);
        planeFitPose.baseFrame = TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION;
        // NOTE: We need to set the target frame to COORDINATE_FRAME_DEVICE because that is the
        // default target frame to place objects in the OpenGL world with
        // TangoSupport.getPoseInEngineFrame.
        planeFitPose.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;

        return planeFitPose;
    }

    /**
     * Builds the plan from the set of measurements and updates the rendering in AR.
     *
     * @param closed If true, close the floor plan; if false, continue the floor plan.
     */
    public void buildPlan(boolean closed) {
        mFloorplan = PlanBuilder.buildPlan(mWallMeasurementList, closed);
        mRenderer.updatePlan(mFloorplan);
    }

    /**
     * Updates every saved measurement. It re-queries the device pose at the time the measurement
     * was taken.
     */
    public void updateMeasurements() {
        for (WallMeasurement wallMeasurement : mWallMeasurementList) {
            // We need to re query the device pose when the measurements were taken.
            TangoPoseData newDevicePose = mTango.getPoseAtTime(
                    wallMeasurement.getDevicePoseTimeStamp(), FRAME_PAIR);
            wallMeasurement.update(newDevicePose);
            mRenderer.addWallMeasurement(wallMeasurement);
        }
    }

    /**
     * Finish plan, save the adf, and show the final result.
     * Executed as an AsyncTask because saving the adf could be an expensive operation.
     */
    private void finishPlan() {
        if (mFinishPlanTask != null) {
            Log.w(TAG, "Finish task already executing");
            return;
        }

        mFinishPlanTask = new FinishPlanTask();
        mFinishPlanTask.execute();
    }

    /**
     * Finish plan AsyncTask.
     * Shows a spinner while it's saving the adf and updating the measurements.
     * Draws the final result on a canvas and shows it.
     */
    private class FinishPlanTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected void onPreExecute() {
            mProgressGroup.setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Save and optimize ADF.
            mTango.saveAreaDescription();
            // Update poses after optimization and re build plan.
            mRenderer.removeMeasurements();
            updateMeasurements();
            buildPlan(true);

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            mProgressGroup.setVisibility(View.GONE);
            // Draw final result on Canvas.
            PlanView planView = new PlanView(FloorplanActivity.this);
            planView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            setContentView(planView);
            planView.invalidate();
            mFinishPlanTask = null;
        }
    }

    /**
     * Custom View that draws the plan in 2D.
     */
    private class PlanView extends View {

        private Paint mPaint;

        public PlanView(Context context) {
            super(context);
            mPaint = new Paint();
            mPaint.setStrokeWidth(10);
            mPaint.setTextSize(50);
        }

        @Override
        public void onDraw(Canvas canvas) {
            mFloorplan.drawOnCanvas(canvas, mPaint);
        }
    }
}
