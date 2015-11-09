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
package com.projecttango.rajawali.renderables.primitives;

import android.graphics.Color;
import android.opengl.GLES10;
import android.opengl.GLES20;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;

import java.nio.FloatBuffer;

/**
 * A Point primitive for Rajawali.
 * Intended to be contributed and PR'ed to Rajawali.
 */
public class Points extends Object3D {
    private int mMaxNumberOfVertices;

    public Points(int numberOfPoints) {
        super();
        mMaxNumberOfVertices = numberOfPoints;
        init(true);
        Material m = new Material();
        m.setColor(Color.GREEN);
        setMaterial(m);
    }

    // Initialize the buffers for Points primitive.
    // Since only vertex and index buffers are used, we only initialize them using setData call.
    protected void init(boolean createVBOs) {
        float[] vertices = new float[mMaxNumberOfVertices * 3];
        int[] indices = new int[mMaxNumberOfVertices];
        for(int i = 0; i < indices.length; ++i){
            indices[i] = i;
        }
        setData(vertices, GLES20.GL_STATIC_DRAW,
                null, GLES20.GL_STATIC_DRAW,
                null, GLES20.GL_STATIC_DRAW,
                null, GLES20.GL_STATIC_DRAW,
                indices, GLES20.GL_STATIC_DRAW,
                true);
    }

    // Update the geometry of the points once new Point Cloud Data is available.
    public void updatePoints(FloatBuffer pointCloudBuffer, int pointCount) {
        pointCloudBuffer.position(0);
        mGeometry.setNumIndices(pointCount);
        mGeometry.getVertices().position(0);
        mGeometry.changeBufferData(mGeometry.getVertexBufferInfo(), pointCloudBuffer, 0, pointCount * 3);
    }

    public void preRender() {
        super.preRender();
        setDrawingMode(GLES20.GL_POINTS);
        GLES10.glPointSize(5.0f);
    }
}
