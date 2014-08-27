package com.projecttango.tangoutils.renderables;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


import android.opengl.GLES20;
import android.opengl.Matrix;

public class Trajectory extends Renderable {

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
	private float[] mVertices = new float[10000];
	
	private int mTrajectoryCount;
	private final int mProgram;
	private int mPosHandle;
	private int mMVPMatrixHandle;

	public Trajectory() {
		// Set model matrix to the identity
		Matrix.setIdentityM(getModelMatrix(), 0);
		
		mTrajectoryCount = 0;
		
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
		mVertexBuffer.put(translation[0]);
		mVertexBuffer.put(translation[1]);
		mVertexBuffer.put(translation[2]);
	}

	@Override
	public void draw(float[] viewMatrix, float[] projectionMatrix) {
		GLES20.glUseProgram(mProgram);
		// updateViewMatrix(viewMatrix);
		mVertexBuffer.position(0);
		
		// Compose the model, view, and projection matrices into a single m-v-p matrix
		updateMvpMatrix(viewMatrix, projectionMatrix);

		// Load vertex attribute data
		mPosHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
		GLES20.glVertexAttribPointer(mPosHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, 
				mVertexBuffer);
		GLES20.glEnableVertexAttribArray(mPosHandle);

		// Draw the Axis
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
		GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, getMvpMatrix(), 0);
		GLES20.glLineWidth(1);
		GLES20.glDrawArrays(GLES20.GL_LINES, 0, mTrajectoryCount);
		GLES20.glUseProgram(0);
	}
	
}