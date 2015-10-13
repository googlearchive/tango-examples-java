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
package com.projecttango.experiments.augmentedrealitysample;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.rajawali.ScenePoseCalcuator;
import com.projecttango.tangosupport.TangoSupport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This helper class keeps a copy of the point cloud data received in callbacks for use with the
 * plane fitting function.
 * It is implemented to be thread safe so that the caller (the Activity) doesn't need to worry
 * about locking between the Tango callback and UI threads.
 */
public class PointCloudManager {
    private static final String TAG = "PointCloudManager";

    private final TangoCameraIntrinsics mTangoCameraIntrinsics;
    private final TangoXyzIjData mXyzIjData;
    private TangoPoseData mDevicePoseAtCloudTime;

    public PointCloudManager(TangoCameraIntrinsics intrinsics) {
        mXyzIjData = new TangoXyzIjData();
        mTangoCameraIntrinsics = intrinsics;
    }

    /**
     * Update the current cloud data with the provided xyzIjData from a Tango callback.
     *
     * @param from          The point cloud data
     * @param xyzIjPose     The device pose with respect to start of service at the time
     *                      the point cloud was acquired
     */
    public synchronized void updateXyzIjData(TangoXyzIjData from, TangoPoseData xyzIjPose) {
        mDevicePoseAtCloudTime = xyzIjPose;

        if (mXyzIjData.xyz == null || mXyzIjData.xyz.capacity() < from.xyzCount * 3) {
            mXyzIjData.xyz = ByteBuffer.allocateDirect(from.xyzCount * 3 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        } else {
            mXyzIjData.xyz.rewind();
        }

        mXyzIjData.xyzCount = from.xyzCount;
        mXyzIjData.timestamp = from.timestamp;

        from.xyz.rewind();
        mXyzIjData.xyz.put(from.xyz);
        mXyzIjData.xyz.rewind();
        from.xyz.rewind();
    }

    /**
     * Calculate the plane that best fits the current point cloud at the provided u,v coordinates
     * in the 2D projection of the point cloud data (i.e.: point cloud image).
     *
     * @param u                     u (horizontal) component of the click location
     * @param v                     v (vertical) component of the click location
     * @param devicePoseAtClickTime Device pose at the time this operation is requested
     * @param poseCalcuator         ScenePoseCalculator helper instance to calculate transforms
     * @return                      The point and plane model, in depth sensor frame
     */
    public synchronized TangoSupport.IntersectionPointPlaneModelPair fitPlane(float u, float v,
            TangoPoseData devicePoseAtClickTime, ScenePoseCalcuator poseCalcuator) {

        // We need to calculate the transform between the color camera at the time the user clicked
        // and the depth camera at the time the depth cloud was acquired.
        // This operation is currently implemented in the provided ScenePoseCalculator helper
        // class. In the future, the support library will provide a method for this calculation.
        TangoPoseData colorCameraTDepthCameraWithTime
                = poseCalcuator.calculateColorCameraTDepthWithTime(devicePoseAtClickTime, mDevicePoseAtCloudTime);

        return TangoSupport.fitPlaneModelNearClick(mXyzIjData, mTangoCameraIntrinsics,
                colorCameraTDepthCameraWithTime, u, v);
    }
}
