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

import org.rajawali3d.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder that knows how to build a Floorplan given a list of WallMeasurements.
 */
public class PlanBuilder {

    /**
     * Creates a new Floorplan object beased on the measurements that we have so far.
     *
     * @param wallMeasurementList List of WallMeasurements to use as input to build the plan.
     *                            It must have only one measurement per wall.
     * @param closed              If true, close the floor plan and intersect the first and last
     *                            measurements. If false, continue the floor plan.
     */
    public static Floorplan buildPlan(List<WallMeasurement> wallMeasurementList, boolean closed) {
        List<float[]> planPoints = new ArrayList<float[]>();
        WallMeasurement lastWallMeasurement = null;
        // Intersect every measurement with the previous one and add the result to the plan.
        if (!wallMeasurementList.isEmpty()) {
            boolean first = true;
            float[] lastAddedPoint = null;
            for (WallMeasurement wallMeasurement : wallMeasurementList) {
                if (lastWallMeasurement != null) {
                    if (!first) {
                        planPoints.remove(lastAddedPoint);
                    }
                    planPoints.add(wallMeasurement.intersect(lastWallMeasurement));
                    first = false;
                }
                float[] openGlWall = wallMeasurement.getPlaneTransform();
                float[] measurementPoint = new float[]{openGlWall[12], openGlWall[13],
                        openGlWall[14]};
                planPoints.add(measurementPoint);
                lastWallMeasurement = wallMeasurement;
                lastAddedPoint = measurementPoint;
            }

            // If closing the floor plan, intersect the first and last measurements.
            if (closed) {
                planPoints.remove(lastAddedPoint);
                planPoints.add(lastWallMeasurement.intersect(wallMeasurementList.get(0)));
                planPoints.remove(planPoints.get(0));
            }
        }
        return new Floorplan(planPoints);
    }

}
