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


package com.projecttango.examples.java.occlusion.meshing;

import com.google.atap.tango.mesh.TangoMesh;
import com.google.atap.tango.reconstruction.Tango3dReconstruction;
import com.google.atap.tango.reconstruction.Tango3dReconstructionConfig;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

/**
 * Uses the Tango Service data to build 3D meshes. Provides higher level functionality built on top
 * of the {@code Tango3dReconstruction}. Given a point cloud it will report a callback with the
 * generated meshes. No color is needed in this example.
 * It abstracts all the needed thread management and pose requesting logic.
 */
public class TangoMesher extends Tango.OnTangoUpdateListener {

    private static final String TAG = TangoMesher.class.getSimpleName();
    private final TangoPointCloudManager mPointCloudBuffer;

    private Tango3dReconstruction mTango3dReconstruction = null;
    private OnTangoMeshesAvailableListener mCallback = null;
    private HandlerThread mHandlerThread = null;
    private volatile Handler mHandler = null;

    private volatile boolean mIsReconstructionActive = false;

    private Runnable mRunnableCallback = null;

    /**
     * Callback for when meshes are available.
     */
    public interface OnTangoMeshesAvailableListener {
        void onMeshesAvailable(TangoMesh[] meshVector);
    }

    public TangoMesher(OnTangoMeshesAvailableListener callback) {
        mCallback = callback;

        Tango3dReconstructionConfig config = new Tango3dReconstructionConfig();
        config.putBoolean("generate_color", false);
        mTango3dReconstruction = new Tango3dReconstruction(config);
        mPointCloudBuffer = new TangoPointCloudManager();

        mHandlerThread = new HandlerThread("mesherCallback");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        if (callback != null) {
            /**
             * This runnable processes the saved point clouds and meshes and triggers the
             * onMeshesAvailable callback with the generated {@code TangoMesh} instances.
             */
            mRunnableCallback = new Runnable() {
                @Override
                public void run() {
                    // Synchronize access to mTango3dReconstruction. This runs in TangoMesher
                    // thread.
                    synchronized (TangoMesher.this) {
                        if (!mIsReconstructionActive) {
                            return;
                        }

                        if (mPointCloudBuffer.getLatestPointCloud() == null) {
                            return;
                        }

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


                        List<int[]> updatedIndices =
                                mTango3dReconstruction.update(cloudData, depthPose,
                                        null, null);

                        if (updatedIndices != null) {
                            int indexCount = updatedIndices.size();
                            List<TangoMesh> meshes = new ArrayList<TangoMesh>(indexCount);
                            for (int i = 0; i < indexCount; ++i) {
                                Log.d(TAG, "Extracting mesh");
                                TangoMesh mesh = mTango3dReconstruction.extractMeshSegment(
                                        updatedIndices.get(i));
                                if (mesh.numVertices > 0 && mesh.numFaces > 0) {
                                    meshes.add(mesh);
                                }
                            }
                            TangoMesh[] meshArray = new TangoMesh[meshes.size()];
                            meshes.toArray(meshArray);
                            mCallback.onMeshesAvailable(meshArray);
                        }
                    }
                }
            };
        }
    }

    /**
     * Synchronize access to mTango3dReconstruction. This runs in UI thread.
     */
    public synchronized void release() {
        mIsReconstructionActive = false;
        mTango3dReconstruction.release();
    }

    public void startSceneReconstruction() {
        mIsReconstructionActive = true;
    }

    public void stopSceneReconstruction() {
        mIsReconstructionActive = false;
    }

    /**
     * Synchronize access to mTango3dReconstruction. This runs in UI thread.
     */
    public synchronized void resetSceneReconstruction() {
        mTango3dReconstruction.clear();
    }

    /**
     * Synchronize access to mTango3dReconstruction. This runs in UI thread.
     */
    public synchronized void setColorCameraCalibration(TangoCameraIntrinsics calibration) {
        mTango3dReconstruction.setColorCameraCalibration(calibration);
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
        // Do nothing.
    }

    /**
     * Receives the depth point cloud. This method retrieves and stores the depth camera pose
     * and point cloud to later use when updating the {@code Tango3dReconstruction}.
     *
     * @param tangoPointCloudData the depth point cloud.
     */
    @Override
    public void onPointCloudAvailable(final TangoPointCloudData tangoPointCloudData) {
        if (!mIsReconstructionActive || tangoPointCloudData == null ||
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
