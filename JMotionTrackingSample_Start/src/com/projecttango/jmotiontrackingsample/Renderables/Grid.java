package com.projecttango.jmotiontrackingsample.Renderables;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.projecttango.jmotiontrackingsample.MTGLRenderer;

public class Grid {

	private static final int COORDS_PER_VERTEX = 3;
	private static final int GRID_RANGE_M = 100;
	private static final int BYTES_PER_FLOAT = 4;
	
	private static final String sVertexShaderCode = 
			"uniform mat4 uMVPMatrix;" 
			+"attribute vec4 vPosition;" 
			+"void main() {" 
			+"gl_Position = uMVPMatrix * vPosition;" 
			+"}";
	private static final String sFragmentShaderCode = 
			"precision mediump float;"
			+ "uniform vec4 vColor;" 
			+ "void main() {"
			+ " gl_FragColor = vec4(0.8,0.8,0.8,1.0);" 
			+ "}";

	private FloatBuffer mVertexBuffer;
	private final int mProgram;
	private float[] mModelMatrix = new float[16];
	private float[] mMvMatrix = new float[16];
	private float[] mMvpMatrix = new float[16];
	private int mPosHandle;
	private int mMVPMatrixHandle;

	public Grid() {
		// Reset the model matrix to the identity
		Matrix.setIdentityM(mModelMatrix, 0);
		
		// Allocate a vertex buffer
		ByteBuffer vertexByteBuffer = ByteBuffer.allocateDirect(
				(GRID_RANGE_M * 2 + 1) * 4 * 3 * BYTES_PER_FLOAT);
		vertexByteBuffer.order(ByteOrder.nativeOrder());
		mVertexBuffer = vertexByteBuffer.asFloatBuffer();
		
		// Load the vertices for the z-axis grid lines into the vertex buffer
		for (int x = -GRID_RANGE_M; x <= GRID_RANGE_M; x++) {
			mVertexBuffer.put(new float[] { x, 0f, (float) -GRID_RANGE_M });
			mVertexBuffer.put(new float[] { x, 0f, (float) GRID_RANGE_M });
		}

		// Load the vertices for the x-axis grid lines into the vertex buffer
		for (int z = -GRID_RANGE_M; z <= GRID_RANGE_M; z++) {
			mVertexBuffer.put(new float[] { (float) -GRID_RANGE_M, 0f, z });
			mVertexBuffer.put(new float[] { (float) GRID_RANGE_M, 0f, z });
		}
		
		// Load the vertex and fragment shaders, then link the program
		int vertexShader = MTGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, sVertexShaderCode);
		int fragShader = MTGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, sFragmentShaderCode);
		mProgram = GLES20.glCreateProgram();
		GLES20.glAttachShader(mProgram, vertexShader);
		GLES20.glAttachShader(mProgram, fragShader);
		GLES20.glLinkProgram(mProgram);
	}

	/**
	 * Applies the view and projection matrices and draws the Grid.
	 * @param viewMatrix the view matrix to map from world space to camera space.
	 * @param projectionMatrix the projection matrix to map from camera space to screen space.
	 */
	public void draw(float[] viewMatrix, float[] projectionMatrix) {
		GLES20.glUseProgram(mProgram);
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
		
		// Draw the Grid
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
		GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMvpMatrix, 0);
		GLES20.glLineWidth(3);
		GLES20.glDrawArrays(GLES20.GL_LINES, 0, (GRID_RANGE_M * 2 + 1) * 4);
	}

}
