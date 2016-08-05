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
package com.projecttango.examples.java.openglar;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Mesh class that knows how to generate its VBOs and indices to be drawn in OpenGL.
 */
public class OpenGlMesh {

    private FloatBuffer mVertex;
    private FloatBuffer mTexCoord;
    private ShortBuffer mIndices;

    private int mNumVertices;
    private int mNumIndices;
    private int mVertexCoordNumber;
    private int mTexCoordNumber;
    private int[] mVbos;

    /**
     * Create an OpenGL mesh.
     *
     * @param vertices          Array of vertex positions.
     * @param vertexCoordNumber Number of coordinates per vertex position.
     * @param texCoords         Array of texture coordinates.
     * @param texCoordNumber    Number of coordinates per texcoord.
     * @param indices           Array of indices.
     */
    public OpenGlMesh(float[] vertices, int vertexCoordNumber, float[]
            texCoords, int texCoordNumber, short[] indices) {
        mNumVertices = vertices.length / vertexCoordNumber;
        mVertexCoordNumber = vertexCoordNumber;
        mTexCoordNumber = texCoordNumber;

        mVertex = ByteBuffer.allocateDirect(Float.SIZE / 8 * vertices.length).order(
                ByteOrder.nativeOrder()).asFloatBuffer();
        mVertex.put(vertices);
        mVertex.position(0);

        mTexCoord = ByteBuffer.allocateDirect(Float.SIZE / 8 * texCoords.length).order(
                ByteOrder.nativeOrder()).asFloatBuffer();
        mTexCoord.put(texCoords);
        mTexCoord.position(0);


        mIndices = ByteBuffer.allocateDirect(Short.SIZE / 8 * indices.length).order(
                ByteOrder.nativeOrder()).asShortBuffer();
        mNumIndices = indices.length;
        mIndices.put(indices);
        mIndices.position(0);
    }

    public void createVbos() {
        mVbos = new int[3];
        // Generate 3 buffers. Vertex buffer, texture buffer and index buffer.
        GLES20.glGenBuffers(3, mVbos, 0);
        // Bind to vertex buffer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbos[0]);
        // Populate it.
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mNumVertices * mVertexCoordNumber * Float
                        .SIZE / 8, mVertex, GLES20.GL_STATIC_DRAW); // vertices of floats.

        // Bind to texcoord buffer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbos[1]);
        // Populate it.
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mNumVertices * mTexCoordNumber * Float.SIZE / 8,
                mTexCoord, GLES20.GL_STATIC_DRAW); // texcoord of floats.

        // Bind to indices buffer
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mVbos[2]);
        // Populate it.
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, mNumIndices * Short.SIZE / 8,
                mIndices, GLES20.GL_STATIC_DRAW); // Indices

        // Unbind buffer.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void drawMesh(int positionh, int textureh) {
        GLES20.glEnableVertexAttribArray(positionh);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbos[0]);
        GLES20.glVertexAttribPointer(positionh, mVertexCoordNumber, GLES20.GL_FLOAT, false, Float
                .SIZE / 8 * mVertexCoordNumber, 0);

        GLES20.glEnableVertexAttribArray(textureh);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbos[1]);
        GLES20.glVertexAttribPointer(textureh, mTexCoordNumber, GLES20.GL_FLOAT, false, Float
                .SIZE / 8 * mTexCoordNumber, 0);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mVbos[2]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, mNumIndices, GLES20.GL_UNSIGNED_SHORT, 0);

        // Unbind.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }
}
