/*
 * Copyright 2014 Google Inc. All Rights Reserved.
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

package com.projecttango.tangoutils.renderables;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

public class Trajectory {

	private static final int COORDS_PER_VERTEX = 3;
	
	private static final String sVertexShaderCode =
			"uniform mat4 uMVPMatrix;"
			+ "attribute vec4 vPosition;"
			+ "attribute vec4 aColor;"
			+ "varying vec4 vColor;"
			+ "void main() {"+
			"  vColor=aColor;" 
			+ "gl_Position = uMVPMatrix * vPosition;"
			+ "}";

	private static final String sFragmentShaderCode = 
			"precision mediump float;"
			+ "varying vec4 vColor;" 
			+ "void main() {"
			+ "gl_FragColor = {0.8f,0.8f,0.8f,1.0f};" + 
			"}";
	
	private FloatBuffer mVertexBuffer;
	private float[] mVertices = new float[10000]; // max number of points trajectory can have.

	private float[] mModelMatrix = new float[16];
	private float[] mMvMatrix = new float[16];
	private float[] mMvpMatrix = new float[16];
	
	public int mTrajectoryCount;
	private final int mProgram;
	private int mPosHandle;
	private int mMVPMatrixHandle;

	public Trajectory() {
		// Set model matrix to the identity
		Matrix.setIdentityM(mModelMatrix, 0);
		
		mTrajectoryCount =0;
		
		// Put vertices into a vertex buffer
		ByteBuffer byteBuf = ByteBuffer.allocateDirect(mVertices.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		mVertexBuffer = byteBuf.asFloatBuffer();
		mVertexBuffer.put(mVertices);
		mVertexBuffer.position(0);


		// Load the vertex and fragment shaders, then link the program
		int vertexShader = RenderUtils.loadShader(GLES20.GL_VERTEX_SHADER, sVertexShaderCode);
		int fragShader = RenderUtils.loadShader(GLES20.GL_FRAGMENT_SHADER, sFragmentShaderCode);
		mProgram = GLES20.glCreateProgram();
		GLES20.glAttachShader(mProgram, vertexShader);
		GLES20.glAttachShader(mProgram, fragShader);
		GLES20.glLinkProgram(mProgram);
		
	}
	
	public void updateTrajectory(float[] translation){
		mTrajectoryCount++;
		Log.e("Count is :","" + mTrajectoryCount);
		mVertexBuffer.put(translation[0]);
		mVertexBuffer.put(translation[1]);
		mVertexBuffer.put(translation[2]);
	}

	/**
	 * Applies the view and projection matrices and draws the Axis.
	 * @param viewMatrix the view matrix to map from world space to camera space.
	 * @param projectionMatrix the projection matrix to map from camera space to screen space.
	 */
	public void draw(float[] viewMatrix, float[] projectionMatrix) {

		GLES20.glUseProgram(mProgram);
		//updateViewMatrix(viewMatrix);
		mVertexBuffer.position(0);
		// Compose the model, view, and projection matrices into a single m-v-p matrix
		Matrix.setIdentityM(mMvMatrix, 0);
		Matrix.setIdentityM(mMvpMatrix, 0);
		Matrix.multiplyMM(mMvMatrix, 0, viewMatrix, 0, mModelMatrix, 0);
		Matrix.multiplyMM(mMvpMatrix, 0, projectionMatrix, 0, mMvMatrix, 0);

		// Load vertex attribute data
		mPosHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
		GLES20.glVertexAttribPointer(mPosHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, 
				mVertexBuffer);
		GLES20.glEnableVertexAttribArray(mPosHandle);

		// Draw the Axis
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
		GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMvpMatrix, 0);
		GLES20.glLineWidth(1);
		GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, mTrajectoryCount);
		GLES20.glUseProgram(0);
	}
	
}