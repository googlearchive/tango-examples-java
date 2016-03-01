/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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
package com.projecttango.rajawali;

import com.google.atap.tangoservice.TangoPoseData;

import org.rajawali3d.math.Matrix4;

/**
 * Class used to hold device extrinsics information in a way that is easy to use to perform
 * transformations with the ScenePoseCalculator.
 */
public class DeviceExtrinsics {
    // Transformation from the position of the depth camera to the device frame.
    private Matrix4 mDeviceTDepthCamera;

    // Transformation from the position of the color Camera to the device frame.
    private Matrix4 mDeviceTColorCamera;

    public DeviceExtrinsics(TangoPoseData imuTDevicePose, TangoPoseData imuTColorCameraPose,
                            TangoPoseData imuTDepthCameraPose) {
        Matrix4 deviceTImu = ScenePoseCalculator.tangoPoseToMatrix(imuTDevicePose).inverse();
        Matrix4 imuTColorCamera = ScenePoseCalculator.tangoPoseToMatrix(imuTColorCameraPose);
        Matrix4 imuTDepthCamera = ScenePoseCalculator.tangoPoseToMatrix(imuTDepthCameraPose);
        mDeviceTDepthCamera = deviceTImu.clone().multiply(imuTDepthCamera);
        mDeviceTColorCamera = deviceTImu.multiply(imuTColorCamera);
    }

    public Matrix4 getDeviceTColorCamera() {
        return mDeviceTColorCamera;
    }

    public Matrix4 getDeviceTDepthCamera() {
        return mDeviceTDepthCamera;
    }
}
