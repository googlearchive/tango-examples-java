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
package com.projecttango.examples.java.floorplan;

import android.opengl.Matrix;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector3;

import com.projecttango.rajawali.ScenePoseCalculator;

/**
 * Representation of wall as a measured plane.
 */
public class WallMeasurement {
    /**
     * The pose of the plane in OpenGl frame.
     */
    private float[] mOpenGlTPlaneTransform;
    /**
     * The pose of the depth camera when the measurement was taken in OpenGl frame.
     */
    private float[] mOpenGlTDepthTransform;
    /**
     * The mTimestamp of the measurement.
     */
    private double mTimestamp;

    public WallMeasurement(float[] openGlTPlaneTransform, float[] openGlTDepthTransform, double
            timestamp) {
        mOpenGlTPlaneTransform = openGlTPlaneTransform;
        mOpenGlTDepthTransform = openGlTDepthTransform;
        mTimestamp = timestamp;
    }

    /**
     * Update the plane pose of the measurement given an updated device pose at the timestamp of
     * the measurement.
     */
    public void update(float[] newOpenGlTDepthTransform) {
        float[] depthTOpenGl = new float[16];
        Matrix.invertM(depthTOpenGl, 0, mOpenGlTDepthTransform, 0);
        float[] newOpenGlTOldOpenGl = new float[16];
        Matrix.multiplyMM(newOpenGlTOldOpenGl, 0, newOpenGlTDepthTransform, 0, depthTOpenGl, 0);
        float[] newOpenGlTPlane = new float[16];
        Matrix.multiplyMM(newOpenGlTPlane, 0, newOpenGlTOldOpenGl, 0, mOpenGlTPlaneTransform, 0);
        mOpenGlTPlaneTransform = newOpenGlTPlane;
        mOpenGlTDepthTransform = newOpenGlTDepthTransform;
    }

    /**
     * Intersect this measurement with another WallMeasurement.
     *
     * @param otherWallMeasurement The other WallMeasurement to intersect with.
     * @return The point of intersection in world frame.
     */
    public float[] intersect(WallMeasurement otherWallMeasurement) {
        float[] openGlTPlane = getPlaneTransform();
        float[] openGlTOtherPlane = otherWallMeasurement.getPlaneTransform();
        // We will calculate the intersection in the frame of the first transformation.
        // Transform the second wall measurement to the first measurement frame
        float[] planeTOpenGl = new float[16];
        Matrix.invertM(planeTOpenGl, 0, openGlTPlane, 0);
        float[] firstPlaneTsecondPlane = new float[16];
        Matrix.multiplyMM(firstPlaneTsecondPlane, 0, planeTOpenGl, 0, openGlTOtherPlane, 0);

        // The translation of the second transform origin, in the first one's frame
        float[] wallPsecond = new float[]{firstPlaneTsecondPlane[12], firstPlaneTsecondPlane[13],
                firstPlaneTsecondPlane[14]};
        // The vector representing the X axis of the second transform, in the first's frame
        float[] wallTXsecond = new float[]{firstPlaneTsecondPlane[0], firstPlaneTsecondPlane[1],
                firstPlaneTsecondPlane[2]};

        float[] wallPintersection =
                new float[]{wallPsecond[0] - wallTXsecond[0] / wallTXsecond[2] * wallPsecond[2],
                        0, 0, 1};

        float[] worldPIntersection = new float[4];
        Matrix.multiplyMV(worldPIntersection, 0, openGlTPlane, 0, wallPintersection, 0);

        return worldPIntersection;
    }

    public float[] getPlaneTransform() {
        return mOpenGlTPlaneTransform;
    }

    public double getDepthTransformTimeStamp() {
        return mTimestamp;
    }

}
