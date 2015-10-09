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
package com.projecttango.rajawali.renderables;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;

import java.util.Stack;

/**
 * Rajawali object showing the Trajectory of the Project Tango
 * device in 3D space. Points are added when the trajectory is updated by
 * passing translation data obtained from Tango Pose Data.
 */
public class Trajectory extends Object3D {
    private Vector3 mLastPoint = new Vector3();
    private Material mTrajectorymaterial;
    private float mThickness = 2f;

    public Trajectory(int color) {
        mTrajectorymaterial = new Material();
        mTrajectorymaterial.setColor(color);
    }

    public void addSegmentTo(Vector3 newPoint) {
        Stack<Vector3> points = new Stack<Vector3>();
        points.add(mLastPoint);
        points.add(newPoint);
        Line3D line = new Line3D(points, mThickness);
        line.setMaterial(mTrajectorymaterial);
        addChild(line);
        mLastPoint = newPoint;
    }

    public Vector3 getLastPoint() {
        return mLastPoint;
    }
}
