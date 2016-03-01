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

import com.google.atap.tangoservice.TangoPoseData;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector3;

import com.projecttango.rajawali.ScenePoseCalculator;

/**
 * Representation of wall as a measured plane.
 */
public class WallMeasurement {
    /**
     * The pose of the plane in ADF frame.
     */
    private TangoPoseData mAdfTPlanePose;
    /**
     * The pose of the device when the measurement was taken in ADF frame.
     */
    private TangoPoseData mAdfTDevicePose;

    public WallMeasurement(TangoPoseData adfTPlanePose, TangoPoseData adfTDevicePose) {
        mAdfTPlanePose = adfTPlanePose;
        mAdfTDevicePose = adfTDevicePose;
    }

    /**
     * Update the plane pose of the measurement given an updated device pose at the timestamp of the
     * measurement.
     */
    public void update(TangoPoseData newAdfTDevicePose) {
        Matrix4 worldTDevice = ScenePoseCalculator.tangoPoseToMatrix(mAdfTDevicePose);
        Matrix4 newWorldTDevice = ScenePoseCalculator.tangoPoseToMatrix(newAdfTDevicePose);
        Matrix4 worldTPlane = ScenePoseCalculator.tangoPoseToMatrix(mAdfTPlanePose);
        Matrix4 newWorldTPlane = newWorldTDevice.multiply(
                worldTDevice.inverse().multiply(worldTPlane));

        mAdfTDevicePose = newAdfTDevicePose;
        mAdfTPlanePose = ScenePoseCalculator.matrixToTangoPose(newWorldTPlane);
        mAdfTPlanePose.baseFrame = TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION;
        // NOTE: We need to set the target frame to COORDINATE_FRAME_DEVICE because that is the
        // default target frame to place objects in the OpenGL world with
        // TangoSupport.getPoseInEngineFrame.
        mAdfTPlanePose.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
    }

    /**
     * Intersect this measurement with another WallMeasurement.
     *
     * @param otherWallMeasurement The other WallMeasurement to intersect with.
     * @return The point of intersection in world frame.
     */
    public Vector3 intersect(WallMeasurement otherWallMeasurement) {
        Matrix4 worldTPlane = ScenePoseCalculator.tangoPoseToMatrix(getPlanePose());
        Matrix4 worldTOtherPlane =
                ScenePoseCalculator.tangoPoseToMatrix(otherWallMeasurement.getPlanePose());
        // We will calculate the intersection in the frame of the first transformation.
        // Transform the second wall measurement to the first measurement frame
        Matrix4 firstPlaneTsecondPlane = worldTPlane.clone().inverse().multiply(worldTOtherPlane);

        // The translation of the second transform origin, in the first one's frame
        Vector3 wallPsecond = firstPlaneTsecondPlane.getTranslation();
        // The vector representing the X axis of the second transform, in the first's frame
        double[] matrixValues = firstPlaneTsecondPlane.getDoubleValues();
        Vector3 wallTXsecond = new Vector3(matrixValues[0], matrixValues[1], matrixValues[2]);

        Vector3 wallPintersection =
                new Vector3(wallPsecond.x - wallTXsecond.x / wallTXsecond.z * wallPsecond.z, 0, 0);
        Matrix4 wallTintersection = new Matrix4();
        wallTintersection.setToTranslation(wallPintersection);

        return worldTPlane.multiply(wallTintersection).getTranslation();
    }

    public TangoPoseData getPlanePose() {
        return mAdfTPlanePose;
    }

    public double getDevicePoseTimeStamp() {
        return mAdfTDevicePose.timestamp;
    }

}
