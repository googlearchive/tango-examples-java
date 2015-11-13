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

    public PointCloudManager(TangoCameraIntrinsics intrinsics) {
        mXyzIjData = new TangoXyzIjData();
        mTangoCameraIntrinsics = intrinsics;
    }

    /**
     * Update the current cloud data with the provided xyzIjData from a Tango callback.
     *
     * @param from          The point cloud data
     */
    public synchronized void updateXyzIjData(TangoXyzIjData from) {
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
     * @param timestampAtClickTime  Timestamp at the time this operation is requested
     * @return                      The point and plane model, in depth sensor frame at the time
     *                              the point cloud data was acquired and the timestamp when the
     *                              the point cloud data was acquired.
     */
    public synchronized FitPlaneResult fitPlane(float u, float v,
            double timestampAtClickTime) {

        // We need to calculate the transform between the color camera at the time the user clicked
        // and the depth camera at the time the depth cloud was acquired.
        TangoPoseData colorCameraTDepthCameraWithTime
                = TangoSupport.calculateRelativePose(timestampAtClickTime,
                TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                mXyzIjData.timestamp,
                TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH);

        return new FitPlaneResult(TangoSupport.fitPlaneModelNearClick(mXyzIjData, mTangoCameraIntrinsics,
                colorCameraTDepthCameraWithTime, u, v), mXyzIjData.timestamp);
    }

    /**
     * Internal class used to return the information about a plane fitting call.
     */
    public class FitPlaneResult {
        public final TangoSupport.IntersectionPointPlaneModelPair planeModelPair;
        public final double cloudTimestamp;

        public FitPlaneResult(TangoSupport.IntersectionPointPlaneModelPair planeModelPair,
                              double cloudTimestamp) {
            this.planeModelPair = planeModelPair;
            this.cloudTimestamp = cloudTimestamp;
        }
    }
}
