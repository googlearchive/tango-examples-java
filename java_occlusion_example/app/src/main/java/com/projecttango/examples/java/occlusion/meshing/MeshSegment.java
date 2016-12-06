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

package com.projecttango.examples.java.occlusion.meshing;

import com.google.atap.tango.mesh.TangoMesh;

import android.opengl.GLES20;

/**
 * Tango Mesh Data Representation.
 */
public class MeshSegment {

    // The service will always pass 4 bytes floats.
    private static final int SIZE_OF_FLOAT = 4;
    // The service will always pass 4 bytes integers.
    private static final int SIZE_OF_INT = 4;

    public int vertexBufferId;
    public int indexBufferId;
    public int numFaces;

    /**
     * Generates an internal Mesh representation. It also allocates the needed hardware buffers to
     * store the data.
     */
    public MeshSegment() {
        loadBuffers();
    }

    private void loadBuffers() {
        final int buffers[] = new int[3];
        GLES20.glGenBuffers(2, buffers, 0);
        this.vertexBufferId = buffers[0];
        this.indexBufferId = buffers[1];
    }

    /**
     * Updates the mesh buffers with the new data.
     *
     * @param tangoMesh
     */
    public void update(TangoMesh tangoMesh) {

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, this.vertexBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                tangoMesh.vertices.capacity() * SIZE_OF_FLOAT,
                tangoMesh.vertices, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, this.indexBufferId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                tangoMesh.faces.capacity() * SIZE_OF_INT,
                tangoMesh.faces, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        this.numFaces = tangoMesh.numFaces;
    }
}
