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

import android.opengl.GLES20;

import org.rajawali3d.Geometry3D;
import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.math.vector.Vector3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Rajawali object showing the Trajectory of the Project Tango
 * device in 3D space. Points are added when the trajectory is updated by
 * passing translation data obtained from Tango Pose Data.
 */
public class Trajectory extends Object3D {
    private static final int MAX_NUMBER_OF_VERTICES = 9000;
    private Vector3 mLastPoint = new Vector3();
    private FloatBuffer mVertexBuffer;
    private int mTrajectoryCount;

    public Trajectory(int color, float thickness) {
        super();
        init(true);
        Material m = new Material();
        m.setColor(color);
        setMaterial(m);
        mVertexBuffer = ByteBuffer
                .allocateDirect(MAX_NUMBER_OF_VERTICES * Geometry3D.FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

    }

    // Initialize the buffers for Trajectory primitive.
    // Since only vertex and Index buffers are used, we only initialize them using setData call.
    protected void init(boolean createVBOs) {
        float[] vertices = new float[MAX_NUMBER_OF_VERTICES * 3];
        int[] indices = new int[MAX_NUMBER_OF_VERTICES];
        for (int i = 0; i < indices.length; ++i) {
            indices[i] = i;
        }
        setData(vertices, GLES20.GL_STATIC_DRAW,
                null, GLES20.GL_STATIC_DRAW,
                null, GLES20.GL_STATIC_DRAW,
                null, GLES20.GL_STATIC_DRAW,
                indices, GLES20.GL_STATIC_DRAW,
                createVBOs);
    }

    // Update the geometry of the Trajectory once new vertex is available.
    public void addSegmentTo(Vector3 vertex) {
        mVertexBuffer.position(mTrajectoryCount * 3);
        mVertexBuffer.put((float) vertex.x);
        mVertexBuffer.put((float) vertex.y);
        mVertexBuffer.put((float) vertex.z);
        mTrajectoryCount++;
        mLastPoint = vertex.clone();
        mGeometry.setNumIndices(mTrajectoryCount);
        mGeometry.getVertices().position(0);
        mGeometry.changeBufferData(mGeometry.getVertexBufferInfo(), mVertexBuffer, 0,
                mTrajectoryCount * 3);
    }

    public void preRender() {
        super.preRender();
        setDrawingMode(GLES20.GL_LINE_STRIP);
    }

    public Vector3 getLastPoint() {
        return mLastPoint;
    }
}
