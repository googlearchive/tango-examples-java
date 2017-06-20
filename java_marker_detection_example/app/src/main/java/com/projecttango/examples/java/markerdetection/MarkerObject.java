/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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
package com.projecttango.examples.java.markerdetection;

import com.google.tango.markers.TangoMarkers;

import android.graphics.Color;

import org.rajawali3d.materials.Material;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.scene.Scene;

import java.util.Stack;
/**
 * Rajawali object which represents a marker.
 */
public class MarkerObject {
    // 3D object for bounding box of the marker.
    private MutableLine3D mRect;

    // 3D object for three axes of the marker local frame.
    private MutableLine3D mAxisX;
    private MutableLine3D mAxisY;
    private MutableLine3D mAxisZ;

    // If the object is visible.
    private boolean mIsVisible;

    /**
     * Construct marker object.
     */
    public MarkerObject() {
        // Line width for drawing the axes and bounding rectangle.
        final float axisLineWidth = 10.f;
        final float rectLineWidth = 5.f;

        // An array that contains two dummy points to form one line segment.
        Stack<Vector3> points = new Stack<Vector3>();
        points.add(new Vector3());
        points.add(new Vector3());

        // A generic matrial to make Rajawali happy.
        Material material = new Material();

        // X axis
        mAxisX = new MutableLine3D(points, axisLineWidth, Color.RED);
        mAxisX.setMaterial(material);

        // Y axis
        mAxisY = new MutableLine3D(points, axisLineWidth, Color.GREEN);
        mAxisY.setMaterial(material);

        // Z axis
        mAxisZ = new MutableLine3D(points, axisLineWidth, Color.BLUE);
        mAxisZ.setMaterial(material);

        // Rectangle for the bounding box.
        // Add 6 more(8 in total) points to form 4 line segments.
        points.add(new Vector3());
        points.add(new Vector3());
        points.add(new Vector3());
        points.add(new Vector3());
        points.add(new Vector3());
        points.add(new Vector3());
        mRect = new MutableLine3D(points, rectLineWidth, Color.CYAN);
        mRect.setMaterial(material);

        // Hide the object until it is detected.
        setVisible(false);
    }

    /**
     * Add all 3D objects of a marker as children of the input scene.
     */
    public void addToScene(Scene scene) {
        scene.addChild(mAxisX);
        scene.addChild(mAxisY);
        scene.addChild(mAxisZ);
        scene.addChild(mRect);
    }

    /**
     * Update the geometry of the marker.
     */
    public void updateGeometry(TangoMarkers.Marker marker) {
        // Create marker center and four corners.
        Vector3 center =
                new Vector3(marker.translation[0], marker.translation[1], marker.translation[2]);
        Vector3 cornerBottomLeft =
                new Vector3(marker.corners3d[0][0], marker.corners3d[0][1], marker.corners3d[0][2]);
        Vector3 cornerBottomRight =
                new Vector3(marker.corners3d[1][0], marker.corners3d[1][1], marker.corners3d[1][2]);
        Vector3 cornerTopRight =
                new Vector3(marker.corners3d[2][0], marker.corners3d[2][1], marker.corners3d[2][2]);
        Vector3 cornerTopLeft =
                new Vector3(marker.corners3d[3][0], marker.corners3d[3][1], marker.corners3d[3][2]);

        // Create a quaternion from marker orientation.
        Quaternion q = new Quaternion(marker.orientation[3], marker.orientation[0],
                marker.orientation[1], marker.orientation[2]);

        // Calculate marker size in meters, assuming square-shape markers.
        double markerSize = cornerTopLeft.distanceTo(cornerTopRight);

        Stack<Vector3> points = new Stack<Vector3>();

        // X axis
        Vector3 xAxis = q.multiply(new Vector3(markerSize / 3, 0, 0));
        points.add(center);
        points.add(Vector3.addAndCreate(center, xAxis));
        mAxisX.updateVertices(points);

        // Y axis
        points.clear();
        Vector3 yAxis = q.multiply(new Vector3(0, markerSize / 3, 0));
        points.add(center);
        points.add(Vector3.addAndCreate(center, yAxis));
        mAxisY.updateVertices(points);

        // Z axis
        points.clear();
        Vector3 zAxis = q.multiply(new Vector3(0, 0, markerSize / 3));
        points.add(center);
        points.add(Vector3.addAndCreate(center, zAxis));
        mAxisZ.updateVertices(points);

        // Rect
        points.clear();
        points.add(cornerBottomLeft);
        points.add(cornerBottomRight);
        points.add(cornerBottomRight);
        points.add(cornerTopRight);
        points.add(cornerTopRight);
        points.add(cornerTopLeft);
        points.add(cornerTopLeft);
        points.add(cornerBottomLeft);
        mRect.updateVertices(points);

        // Make it visible
        if (!mIsVisible) {
            setVisible(true);
        }
    }

    /**
     * Set visibility of the marker object.
     */
    public void setVisible(boolean visible) {
        mRect.setVisible(visible);
        mAxisX.setVisible(visible);
        mAxisY.setVisible(visible);
        mAxisZ.setVisible(visible);
        mIsVisible = visible;
    }
}
