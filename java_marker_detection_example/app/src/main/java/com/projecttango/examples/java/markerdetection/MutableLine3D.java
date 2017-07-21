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

import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;

import java.util.Stack;

/**
 * This extension class of Line3D allows change of vertices on-the-fly.
 */
public class MutableLine3D extends Line3D {
    /**
     * Creates a line primitive with a single color.
     *
     * @param points vertices of the line segments. Each pair forms a segment.
     * @param thickness thickness of the line in the same unit of points.
     * @param color color of the line.
     */
    public MutableLine3D(Stack<Vector3> points, float thickness, int color) {
        super(points, thickness, color);
    }

    /**
     * Update the vertex coordinates of the object.
     */
    public void updateVertices(Stack<Vector3> points) {
        int numVertices = points.size();
        float[] vertices = new float[numVertices * 3];
        for (int i = 0; i < numVertices; i++) {
            Vector3 point = points.get(i);
            int index = i * 3;
            vertices[index] = (float) point.x;
            vertices[index + 1] = (float) point.y;
            vertices[index + 2] = (float) point.z;
        }

        // Override existing vertices with new coordinates.
        mGeometry.setVertices(vertices, true);

        // Update OpenGL vertex buffer.
        mGeometry.createBuffers();
    }
}
