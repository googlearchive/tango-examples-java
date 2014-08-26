package com.projecttango.jmotiontrackingsample;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.projecttango.jmotiontrackingsample.Renderables.Axis;
import com.projecttango.jmotiontrackingsample.Renderables.CameraFrustum;
import com.projecttango.jmotiontrackingsample.Renderables.Grid;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

public class MTGLRenderer implements GLSurfaceView.Renderer {
	
	private static final float CAMERA_FOV = 45f;
	private static final float CAMERA_NEAR = 1f;
	private static final float CAMERA_FAR = 200f;
	private static final int MATRIX_4X4 = 16;
	
	private CameraFrustum mCameraFrustum;
	private Axis mAxis;
	private Grid mFloorGrid;
	private float[] mViewMatrix = new float[16];

	private float mCameraAspect;
	private float[] mProjectionMatrix = new float[MATRIX_4X4];

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		// Set background color and enable depth testing
		GLES20.glClearColor(1f, 1f, 1f, 1.0f);
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
		
		mCameraFrustum = new CameraFrustum();
		mFloorGrid = new Grid();
		mAxis = new Axis();
		
		// Construct the initial view matrix
		Matrix.setIdentityM(mViewMatrix, 0);
		Matrix.setLookAtM(mViewMatrix, 0, 0f, 5f, 0f, 0f, 0f, 0f, 0f, 0f, -1f);
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		GLES20.glViewport(0, 0, width, height);
		mCameraAspect = (float) width / height;
		Matrix.perspectiveM(mProjectionMatrix, 0, CAMERA_FOV, mCameraAspect, CAMERA_NEAR, 
				CAMERA_FAR);
	}

	@Override
	public void onDrawFrame(GL10 gl) {
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
		mFloorGrid.draw(mViewMatrix, mProjectionMatrix);
		mAxis.draw(mViewMatrix, mProjectionMatrix);
		mCameraFrustum.draw(mViewMatrix, mProjectionMatrix);
	}

	/**
	 * Creates a vertex or fragment shader.
	 * @param type one of GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
	 * @param shaderCode GLSL code for the shader as a String
	 * @return a compiled shader.
	 */
	public static int loadShader(int type, String shaderCode) {
		// Create a shader of the correct type
		int shader = GLES20.glCreateShader(type);

		// Compile the shader from source code
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);

		return shader;
	}
	
	public CameraFrustum getCameraFrustum() {
		return mCameraFrustum;
	}

	public Axis getAxis() {
		return mAxis;
	}

}
