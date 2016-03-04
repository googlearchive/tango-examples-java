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

import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;

import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.renderer.RajawaliRenderer;

import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.renderables.Grid;

/**
 * This class implements the rendering logic for the Motion Tracking application using Rajawali.
 */
public class MotionTrackingRajawaliRenderer extends RajawaliRenderer {

    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;

    // Latest available device pose;
    private Pose mDevicePose = new Pose(Vector3.ZERO, Quaternion.getIdentity());
    private boolean mPoseUpdated = false;

    public MotionTrackingRajawaliRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {
        Grid grid = new Grid(100, 1, 1, 0xFFCCCCCC);
        grid.setPosition(0, -1.3f, 0);
        getCurrentScene().addChild(grid);

        getCurrentScene().setBackgroundColor(Color.WHITE);

        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);
    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        // Update the scene objects with the latest device position and orientation information.
        // Synchronize to avoid concurrent access from the Tango callback thread below.
        synchronized (this) {
            if (mPoseUpdated) {
                mPoseUpdated = false;

                // Update the scene camera position and orientation
                getCurrentCamera().setPosition(mDevicePose.getPosition());
                getCurrentCamera().setOrientation(mDevicePose.getOrientation());
            }
        }

        // Perform the actual OpenGL rendering of the updated objects
        super.onRender(ellapsedRealtime, deltaTime);
    }

    /**
     * Updates our information about the current device pose.
     * This is called from the Tango service thread through the callback API. Synchronize to avoid
     * concurrent access from the OpenGL thread above.
     */
    public synchronized void updateDevicePose(TangoPoseData tangoPoseData, int rotationIndex) {
        mDevicePose =
                ScenePoseCalculator.toOpenGLPoseWithScreenRotation(tangoPoseData, rotationIndex);
        mPoseUpdated = true;
    }

    @Override
    public void onOffsetsChanged(float v, float v1, float v2, float v3, int i, int i1) {
      // Unused, but needs to be declared to adhere to the IRajawaliSurfaceRenderer interface.
    }

    @Override
    public void onTouchEvent(MotionEvent motionEvent) {
      // Unused, but needs to be declared to adhere to the IRajawaliSurfaceRenderer interface.
    }
}
