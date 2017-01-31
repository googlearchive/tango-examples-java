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
package com.projecttango.examples.java.floorplanreconstruction;

import com.google.atap.tango.reconstruction.Tango3dReconstruction;
import com.google.atap.tango.reconstruction.Tango3dReconstructionConfig;
import com.google.atap.tango.reconstruction.TangoPolygon;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.List;

import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

/**
 * Uses the tango service data to build a floor plan 2D. Provides higher level functionality
 * built on top of the {@code Tango3dReconstruction}.
 * Given a point cloud it will report a callback with the floorplan polygons.
 * It abstracts all the needed thread management and pose requesting logic.
 */
public class TangoFloorplanner extends Tango.OnTangoUpdateListener {

    private static final String TAG = TangoFloorplanner.class.getSimpleName();
    private final TangoPointCloudManager mPointCloudBuffer;

    private Tango3dReconstruction mTango3dReconstruction = null;
    private OnFloorplanAvailableListener mCallback = null;
    private HandlerThread mHandlerThread = null;
    private volatile Handler mHandler = null;

    private volatile boolean mIsFloorplanningActive = false;

    private Runnable mRunnableCallback = null;

   /**
     * Callback for when meshes are available.
     */
    public interface OnFloorplanAvailableListener {
        void onFloorplanAvailable(List<TangoPolygon> polygons);
    }

    public TangoFloorplanner(OnFloorplanAvailableListener callback) {
        mCallback = callback;
        Tango3dReconstructionConfig config = new Tango3dReconstructionConfig();
        // Configure the 3D reconstruction library to work in "floorplan" mode.
        config.putBoolean("use_floorplan", true);
        config.putBoolean("generate_color", false);
        // Simplify the detected countours by allowing a maximum error of 5cm
        config.putDouble("floorplan_max_error", 0.05);
        mTango3dReconstruction = new Tango3dReconstruction(config);
        mPointCloudBuffer = new TangoPointCloudManager();

        mHandlerThread = new HandlerThread("mesherCallback");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        if (callback != null) {
            /**
             * This runnable processes the saved point clouds and meshes and triggers the
             * onFloorplanAvailable callback with the generated {@code TangoPolygon} instances.
             */
            mRunnableCallback = new Runnable() {
                @Override
                public void run() {
                    // Synchronize access to mTango3dReconstruction. This runs in TangoFloorplanner
                    // thread.
                    synchronized (TangoFloorplanner.this) {
                        if (!mIsFloorplanningActive) {
                            return;
                        }

                        if (mPointCloudBuffer.getLatestPointCloud() == null) {
                            return;
                        }

                        // Get the latest point cloud data.
                        TangoPointCloudData cloudData = mPointCloudBuffer.getLatestPointCloud();
                        TangoPoseData depthPose = TangoSupport.getPoseAtTime(cloudData.timestamp,
                                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                TangoSupport.ROTATION_IGNORED);
                        if (depthPose.statusCode != TangoPoseData.POSE_VALID) {
                            Log.e(TAG, "couldn't extract a valid depth pose");
                            return;
                        }

                        // Update the mesh and floorplan representation.
                        mTango3dReconstruction.updateFloorplan(cloudData, depthPose);

                        // Extract the full set of floorplan polygons.
                        List<TangoPolygon> polygons = mTango3dReconstruction.extractFloorplan();

                        // Provide the new floorplan polygons to the app via callback.
                        mCallback.onFloorplanAvailable(polygons);
                    }
                }
            };
        }
    }

    /**
     * Synchronize access to mTango3dReconstruction. This runs in UI thread.
     */
    public synchronized void release() {
        mIsFloorplanningActive = false;
        mTango3dReconstruction.release();
    }

    public void startFloorplanning() {
        mIsFloorplanningActive = true;
    }

    public void stopFloorplanning() {
        mIsFloorplanningActive = false;
    }

    /**
     * Synchronize access to mTango3dReconstruction. This runs in UI thread.
     */
    public synchronized void resetFloorplan() {
        mTango3dReconstruction.clear();
    }

    /**
     * Synchronize access to mTango3dReconstruction. This runs in UI thread.
     */
    public synchronized void setDepthCameraCalibration(TangoCameraIntrinsics calibration) {
        mTango3dReconstruction.setDepthCameraCalibration(calibration);
    }

    @Override
    public void onPoseAvailable(TangoPoseData var1) {

    }

    @Override
    public void onXyzIjAvailable(final TangoXyzIjData var1) {
        // do nothing.
    }

    /**
     * Receives the depth point cloud. This method retrieves and stores the depth camera pose
     * and point cloud to later use it when updating the {@code Tango3dReconstruction}.
     *
     * @param tangoPointCloudData the depth point cloud.
     */
    @Override
    public void onPointCloudAvailable(final TangoPointCloudData tangoPointCloudData) {
        if (!mIsFloorplanningActive || tangoPointCloudData == null ||
                tangoPointCloudData.points == null) {
            return;
        }
        mPointCloudBuffer.updatePointCloud(tangoPointCloudData);
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(mRunnableCallback);
    }

    @Override
    public void onFrameAvailable(int var1) {

    }

    @Override
    public void onTangoEvent(TangoEvent var1) {

    }
}
