package com.projecttango.jmotiontrackingsample.Renderables;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.projecttango.jmotiontrackingsample.MTGLRenderer;

import android.opengl.GLES20;
import android.opengl.Matrix;

public class CameraFrustum {
	
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
			+ "gl_FragColor = vec4(0.8,0.5,0.8,1);" + 
			"}";
	
	private float[] mTranslation = new float[3];
	private float[] mQuaternion = new float[4];
	private FloatBuffer mVertexBuffer, mColorBuffer;

	private float mVertices[] = {   
			0.0f, 0.0f, 0.0f,
		    -0.4f, 0.3f, -0.5f,

		    0.0f, 0.0f, 0.0f,
		    0.4f, 0.3f, -0.5f,

		    0.0f, 0.0f, 0.0f,
		    -0.4f, -0.3f, -0.5f,

		    0.0f, 0.0f, 0.0f,
		    0.4f, -0.3f, -0.5f,

		    -0.4f, 0.3f, -0.5f,
		    0.4f, 0.3f, -0.5f,

		    0.4f, 0.3f, -0.5f,
		    0.4f, -0.3f, -0.5f,

		    0.4f, -0.3f, -0.5f,
		    -0.4f, -0.3f, -0.5f,

		    -0.4f, -0.3f, -0.5f,
		    -0.4f, 0.3f, -0.5f};

	private float mColors[] = { 
			1.0f, 0.0f, 0.0f, 1.0f,
			1.0f, 0.0f, 0.0f, 1.0f,
			
			0.0f, 1.0f, 0.0f, 1.0f,
			0.0f, 1.0f, 0.0f, 1.0f,
			
			0.0f, 0.0f, 1.0f, 1.0f,
			0.0f, 0.0f, 1.0f, 1.0f,
			
			1.0f, 0.0f, 0.0f, 1.0f,
			1.0f, 0.0f, 0.0f, 1.0f,
			
			0.0f, 1.0f, 0.0f, 1.0f,
			0.0f, 1.0f, 0.0f, 1.0f,
			
			0.0f, 0.0f, 1.0f, 1.0f,
			0.0f, 0.0f, 1.0f, 1.0f,
			
			1.0f, 0.0f, 0.0f, 1.0f,
			1.0f, 0.0f, 0.0f, 1.0f,
			
			0.0f, 1.0f, 0.0f, 1.0f,
			0.0f, 1.0f, 0.0f, 1.0f};

	private float[] mModelMatrix = new float[16];
	private float[] mMvMatrix = new float[16];
	private float[] mMvpMatrix = new float[16];

	private final int mProgram;
	private int mPosHandle, mColorHandle;
	private int mMVPMatrixHandle;

	public CameraFrustum() {
		// Reset the model matrix to the identity
		Matrix.setIdentityM(mModelMatrix, 0);

		// Load the vertices into a vertex buffer
		ByteBuffer byteBuf = ByteBuffer.allocateDirect(mVertices.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		mVertexBuffer = byteBuf.asFloatBuffer();
		mVertexBuffer.put(mVertices);
		mVertexBuffer.position(0);

		// Load the colors into a color buffer
		ByteBuffer cByteBuff = ByteBuffer.allocateDirect(mColors.length * 4);
		cByteBuff.order(ByteOrder.nativeOrder());
		mColorBuffer = cByteBuff.asFloatBuffer();
		mColorBuffer.put(mColors);
		mColorBuffer.position(0);

		// Load the vertex and fragment shaders, then link the program
		int vertexShader = MTGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, sVertexShaderCode);
		int fragShader = MTGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, sFragmentShaderCode);
		mProgram = GLES20.glCreateProgram();
		GLES20.glAttachShader(mProgram, vertexShader);
		GLES20.glAttachShader(mProgram, fragShader);
		GLES20.glLinkProgram(mProgram);
	}

	/**
	 * Updates the model matrix (rotation and translation) of the CameraFrustum.
	 * @param translation a three-element array of translation data.
	 * @param quaternion a four-element array of rotation data.
	 */
	public void updateModelMatrix(float[] translation, float[] quaternion) {
		mTranslation = translation;
		mQuaternion = quaternion;
		
		float[] openglQuaternion = MathUtils.convertQuaternionToOpenGl(quaternion);
		float[] quaternionMatrix = new float[16];
		
		//quaternionMatrix = MathUtils.quaternionM(openglQuaternion);
		quaternionMatrix = MathUtils.quaternionM(quaternion);		
		Matrix.setIdentityM(mModelMatrix, 0);
		//Matrix.translateM(mModelMatrix, 0, translation[0], translation[2], -translation[1]);

		if (quaternionMatrix != null) {
			float[] mTempMatrix = new float[16];
			Matrix.setIdentityM(mTempMatrix, 0);
			
			Matrix.multiplyMM(mTempMatrix, 0, quaternionMatrix, 0, mModelMatrix, 0);
			mModelMatrix = mTempMatrix;
		}
	};

	public void updateViewMatrix(float[] viewMatrix) {
		Matrix.setLookAtM(viewMatrix, 0, 0, 5.0f, 5.0f, mTranslation[0], mTranslation[1], 
				mTranslation[2], 0, 1, 0);
	}

	/**
	 * Applies the view and projection matrices and draws the CameraFrustum.
	 * @param viewMatrix the view matrix to map from world space to camera space.
	 * @param projectionMatrix the projection matrix to map from camera space to screen space.
	 */
	public void draw(float[] viewMatrix, float[] projectionMatrix) {
		GLES20.glUseProgram(mProgram);
		// updateViewMatrix(viewMatrix);

		// Compose the model, view, and projection matrices into a single mvp matrix
		Matrix.setIdentityM(mMvMatrix, 0);
		Matrix.setIdentityM(mMvpMatrix, 0);
		Matrix.multiplyMM(mMvMatrix, 0, viewMatrix, 0, mModelMatrix, 0);
		Matrix.multiplyMM(mMvpMatrix, 0, projectionMatrix, 0, mMvMatrix, 0);

		// Load vertex attribute data
		mPosHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
		GLES20.glVertexAttribPointer(mPosHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, 
				mVertexBuffer);
		GLES20.glEnableVertexAttribArray(mPosHandle);

		// Load color attribute data
		mColorHandle = GLES20.glGetAttribLocation(mProgram, "aColor");
		GLES20.glVertexAttribPointer(mColorHandle, 4, GLES20.GL_FLOAT, false, 0, mColorBuffer);
		GLES20.glEnableVertexAttribArray(mColorHandle);

		// Draw the CameraFrustum
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
		GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMvpMatrix, 0);
		GLES20.glLineWidth(5);
		GLES20.glDrawArrays(GLES20.GL_LINES, 0, 16);
	}
	
}