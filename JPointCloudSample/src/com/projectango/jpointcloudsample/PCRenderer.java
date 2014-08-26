package com.projectango.jpointcloudsample;

import static android.opengl.GLES20.GL_DEPTH_TEST;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glViewport;
import static android.opengl.Matrix.setLookAtM;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.projecttango.jpointcloudsample.Renderables.CameraFrustrum;
import com.projecttango.jpointcloudsample.Renderables.Grid;
import com.projecttango.jpointcloudsample.Renderables.PointCloud;


import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

public class PCRenderer implements GLSurfaceView.Renderer {
	
	private float[] mViewMatrix = new float[16];
    private final float[] mMVPMatrix = new float[16];
    private final float[] mRotationMatrix = new float[16];
    private static final float CAMERA_FOV = 45f, CAMERA_NEAR = 1f, CAMERA_FAR = 200f;
    private static final int MATRIX_4X4 = 16;

    // Camera settings
    private float mCameraAspect;
    private float[] mProjectionMatrix = new float[MATRIX_4X4];
	public CameraFrustrum cameraFrustrum;
	private Grid mFloorGrid;
	public PointCloud mPointCloud;
	
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		// TODO Auto-generated method stub
		 glClearColor(1f, 1f, 1f, 1.0f);
	     glEnable(GL_DEPTH_TEST);
	     cameraFrustrum = new CameraFrustrum();
	     mFloorGrid = new Grid();
	     mPointCloud = new PointCloud();
	     Matrix.setIdentityM(mViewMatrix, 0);
	     setLookAtM(mViewMatrix, 0, 0f,  3f, -3f, 0f, 0f, 0f, 0f, 1f, 0f);
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		// TODO Auto-generated method stub
		glViewport(0, 0, width, height);
        mCameraAspect = (float) width / height;
        Matrix.perspectiveM(mProjectionMatrix, 0, CAMERA_FOV, mCameraAspect, CAMERA_NEAR, CAMERA_FAR);
	}

	@Override
	public void onDrawFrame(GL10 gl) {
		// TODO Auto-generated method stub
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT); 	
		mFloorGrid.draw(mViewMatrix, mProjectionMatrix);
		//cameraFrustrum.draw(mViewMatrix, mProjectionMatrix);
		mPointCloud.draw(mViewMatrix,mProjectionMatrix);
	}
	
    public static int loadShader(int type, String shaderCode){

        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }
}
