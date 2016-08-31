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
package com.projecttango.examples.java.pointcloud.rajawali;

import android.opengl.GLES10;
import android.opengl.GLES20;

import org.rajawali3d.Object3D;

import java.nio.FloatBuffer;

/**
 * A Point primitive for Rajawali.
 * Intended to be contributed and PR'ed to Rajawali.
 */
public class Points extends Object3D {
    private static final int BYTES_PER_FLOAT = 4;

    private int mMaxNumberOfVertices;
    // Float values per point to expect in points FloatBuffer. XYZ format = 3, XYZC format = 4.
    protected int mFloatsPerPoint = 3;
    // Float values per color = 4 (RGBA).
    protected int mFloatsPerColor = 4;

    public Points(int numberOfPoints, int floatsPerPoint, boolean isCreateColors) {
        super();
        mMaxNumberOfVertices = numberOfPoints;
        mFloatsPerPoint = floatsPerPoint;
        init(true, isCreateColors);
    }

    // Initialize the buffers for Points primitive.
    // Since only vertex, index and color buffers are used,
    // we only initialize them using setData call.
    protected void init(boolean createVBOs, boolean createColors) {
        float[] vertices = new float[mMaxNumberOfVertices * mFloatsPerPoint];
        int[] indices = new int[mMaxNumberOfVertices];
        for (int i = 0; i < indices.length; ++i) {
            indices[i] = i;
        }
        float[] colors = null;
        if (createColors) {
            colors = new float[mMaxNumberOfVertices * mFloatsPerColor];
        }
        mGeometry.getVertexBufferInfo().stride = mFloatsPerPoint * BYTES_PER_FLOAT;
        setData(vertices, null, null, colors, indices, true);
    }

    /**
     * Update the geometry of the points based on the provided points float buffer.
     */
    public void updatePoints(int pointCount, FloatBuffer pointCloudBuffer) {
        mGeometry.setNumIndices(pointCount);
        mGeometry.setVertices(pointCloudBuffer);
        mGeometry.changeBufferData(mGeometry.getVertexBufferInfo(), mGeometry.getVertices(), 0,
                pointCount * mFloatsPerPoint);
    }

    /**
     * Update the geometry of the points based on the provided points float buffer and corresponding
     * colors based on the provided float array.
     */
    public void updatePoints(int pointCount, FloatBuffer points, float[] colors) {
        if (pointCount > mMaxNumberOfVertices) {
            throw new RuntimeException(
                    String.format("pointClount = %d exceeds maximum number of points = %d",
                            pointCount, mMaxNumberOfVertices));
        }
        mGeometry.setNumIndices(pointCount);
        mGeometry.setVertices(points);
        mGeometry.changeBufferData(mGeometry.getVertexBufferInfo(), mGeometry.getVertices(), 0,
                pointCount * mFloatsPerPoint);
        mGeometry.setColors(colors);
        mGeometry.changeBufferData(mGeometry.getColorBufferInfo(), mGeometry.getColors(), 0,
                pointCount * mFloatsPerColor);
    }

    @Override
    public void preRender() {
        super.preRender();
        setDrawingMode(GLES20.GL_POINTS);
        GLES10.glPointSize(5.0f);
    }
}
