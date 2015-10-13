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

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalcuator;
import com.projecttango.rajawali.TouchViewHandler;
import com.projecttango.rajawali.renderables.FrustumAxes;
import com.projecttango.rajawali.renderables.Grid;
import com.projecttango.rajawali.renderables.primitives.Points;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.renderer.RajawaliRenderer;

/**
 * Renderer for Point Cloud data. This is also a thread safe class as in when Renderloop and
 * OnposeAvailable callbacks are synchronized with each other.
 */
public class PointCloudRajawaliRenderer extends RajawaliRenderer {

    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;
    private static final int MAX_NUMBER_OF_POINTS = 60000;
    private FrustumAxes mFrustumAxes;
    private Grid mGrid;

    private TouchViewHandler mTouchViewHandler;
    private ScenePoseCalcuator mScenePoseCalcuator;
    private Pose mCameraPose;
    private Pose mPointCloudPose;

    // Latest available device pose
    private Points mPoints;
    private PointCloudManager mPointCloudManager;

    public PointCloudRajawaliRenderer(Context context, PointCloudManager pointCloudManager) {
        super(context);
        mTouchViewHandler = new TouchViewHandler(mContext, getCurrentCamera());
        mScenePoseCalcuator = new ScenePoseCalcuator();
        mPointCloudManager = pointCloudManager;
    }

    @Override
    protected void initScene() {
        mGrid = new Grid(100, 1, 1, 0xFFCCCCCC);
        mGrid.setPosition(0, -1.3f, 0);
        getCurrentScene().addChild(mGrid);

        mFrustumAxes = new FrustumAxes(3);
        getCurrentScene().addChild(mFrustumAxes);

        mPoints = new Points(MAX_NUMBER_OF_POINTS);
        getCurrentScene().addChild(mPoints);
        getCurrentScene().setBackgroundColor(Color.WHITE);
        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);
        getCurrentCamera().setFieldOfView(37.5);
    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        super.onRender(ellapsedRealtime, deltaTime);
        PointCloudManager.PointCloudData renderPointCloudData
                = mPointCloudManager.updateAndGetLatestPointCloudRenderBuffer();
        mPoints.updatePoints(renderPointCloudData.floatBuffer, renderPointCloudData.pointCount);
        if(mCameraPose==null || mPointCloudPose == null){
            return;
        }
        // Update the scene objects with the latest device position and orientation information.
        // Synchronize to avoid concurrent access from the Tango callback thread below.
        synchronized (this) {
            mFrustumAxes.setPosition(mCameraPose.getPosition());
            mFrustumAxes.setOrientation(mCameraPose.getOrientation());

            mPoints.setPosition(mPointCloudPose.getPosition());
            mPoints.setOrientation(mPointCloudPose.getOrientation());
            mTouchViewHandler.updateCamera(mCameraPose.getPosition(), mCameraPose.getOrientation());
        }
    }

    /**
     * Updates our information about the current device pose.
     * This is called from the Tango service thread through the callback API. Synchronize to avoid
     * concurrent access from the OpenGL thread above.
     */
    public synchronized void updateDevicePose(TangoPoseData tangoPoseData) {
        mCameraPose = mScenePoseCalcuator.toOpenGLCameraPose(tangoPoseData);
    }

    public synchronized void updatePointCloudPose(TangoPoseData pointCloudPose) {
        mPointCloudPose = mScenePoseCalcuator.toOpenGLPointCloudPose(pointCloudPose);
    }

    @Override
    public void onOffsetsChanged(float v, float v1, float v2, float v3, int i, int i1) {
    }

    @Override
    public void onTouchEvent(MotionEvent motionEvent) {
        mTouchViewHandler.onTouchEvent(motionEvent);
    }

    public void setFirstPersonView() {
        mTouchViewHandler.setFirstPersonView();
    }

    public void setTopDownView() {
        mTouchViewHandler.setTopDownView();
    }

    public void setThirdPersonView() {
        mTouchViewHandler.setThirdPersonView();
    }

    /**
     * Sets the extrinsics between different sensors of a Tango Device
     *
     * @param imuTDevicePose : Pose transformation between Device and IMU sensor.
     * @param imuTColorCameraPose : Pose transformation between Color camera and IMU sensor.
     * @param imuTDepthCameraPose : Pose transformation between Depth camera and IMU sensor.
     */
    public void setupExtrinsics(TangoPoseData imuTDevicePose, TangoPoseData imuTColorCameraPose,
                                TangoPoseData imuTDepthCameraPose) {
        mScenePoseCalcuator.setupExtrinsics(imuTDevicePose, imuTColorCameraPose, imuTDepthCameraPose);
    }

}
