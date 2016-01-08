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
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.TouchViewHandler;
import com.projecttango.rajawali.renderables.FrustumAxes;
import com.projecttango.rajawali.renderables.Grid;
import com.projecttango.rajawali.renderables.PointCloud;

import org.rajawali3d.renderer.RajawaliRenderer;

/**
 * Renderer for Point Cloud data.
 */
public class PointCloudRajawaliRenderer extends RajawaliRenderer {

    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;
    private static final int MAX_NUMBER_OF_POINTS = 60000;

    private TouchViewHandler mTouchViewHandler;
    private DeviceExtrinsics mDeviceExtrinsics;

    // Objects rendered in the scene
    private PointCloud mPointCloud;
    private FrustumAxes mFrustumAxes;
    private Grid mGrid;

    public PointCloudRajawaliRenderer(Context context) {
        super(context);
        mTouchViewHandler = new TouchViewHandler(mContext, getCurrentCamera());
    }

    @Override
    protected void initScene() {
        mGrid = new Grid(100, 1, 1, 0xFFCCCCCC);
        mGrid.setPosition(0, -1.3f, 0);
        getCurrentScene().addChild(mGrid);

        mFrustumAxes = new FrustumAxes(3);
        getCurrentScene().addChild(mFrustumAxes);

        mPointCloud = new PointCloud(MAX_NUMBER_OF_POINTS);
        getCurrentScene().addChild(mPointCloud);
        getCurrentScene().setBackgroundColor(Color.WHITE);
        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);
        getCurrentCamera().setFieldOfView(37.5);
    }

    /**
     * Updates the rendered point cloud. For this, we need the point cloud data and the device pose
     * at the time the cloud data was acquired.
     * NOTE: This needs to be called from the OpenGL rendering thread.
     */
    public void updatePointCloud(TangoXyzIjData xyzIjData, TangoPoseData devicePose) {
        if (mDeviceExtrinsics != null) {
            Pose pointCloudPose =
                    ScenePoseCalculator.toDepthCameraOpenGlPose(devicePose, mDeviceExtrinsics);
            mPointCloud.updateCloud(xyzIjData.xyzCount, xyzIjData.xyz);
            mPointCloud.setPosition(pointCloudPose.getPosition());
            mPointCloud.setOrientation(pointCloudPose.getOrientation());
        }
    }

    /**
     * Updates our information about the current device pose.
     * NOTE: This needs to be called from the OpenGL rendering thread.
     */
    public void updateDevicePose(TangoPoseData tangoPoseData) {
        if (mDeviceExtrinsics != null) {
            Pose cameraPose =
                    ScenePoseCalculator.toOpenGlCameraPose(tangoPoseData, mDeviceExtrinsics);
            mFrustumAxes.setPosition(cameraPose.getPosition());
            mFrustumAxes.setOrientation(cameraPose.getOrientation());
            mTouchViewHandler.updateCamera(cameraPose.getPosition(), cameraPose.getOrientation());
        }
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
        mDeviceExtrinsics =
                new DeviceExtrinsics(imuTDevicePose, imuTColorCameraPose, imuTDepthCameraPose);
    }
}
