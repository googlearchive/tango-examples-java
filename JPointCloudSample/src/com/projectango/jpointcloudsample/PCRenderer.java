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

package com.projectango.jpointcloudsample;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.projecttango.tangoutils.renderables.CameraFrustum;
import com.projecttango.tangoutils.renderables.Grid;
import com.projecttango.tangoutils.renderables.PointCloud;


import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

public class PCRenderer implements GLSurfaceView.Renderer {
	
    private static final float CAMERA_FOV = 45f;
    private static final float CAMERA_NEAR = 1f;
    private static final float CAMERA_FAR = 200f;
    private static final int MATRIX_4X4 = 16;
	
	private float[] mViewMatrix = new float[MATRIX_4X4];
    private float mCameraAspect;
    private float[] mProjectionMatrix = new float[MATRIX_4X4];
	private CameraFrustum mCameraFrustum;
	private Grid mFloorGrid;
	private PointCloud mPointCloud;
	
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		GLES20.glClearColor(1f, 1f, 1f, 1.0f);
	    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
	    mCameraFrustum = new CameraFrustum();
	    mFloorGrid = new Grid();
	    mPointCloud = new PointCloud();
	    Matrix.setIdentityM(mViewMatrix, 0);
	    Matrix.setLookAtM(mViewMatrix, 0, 0f,  0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f);
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		GLES20.glViewport(0, 0, width, height);
        mCameraAspect = (float) width / height;
        Matrix.perspectiveM(mProjectionMatrix, 0, CAMERA_FOV, mCameraAspect, CAMERA_NEAR, CAMERA_FAR);
	}

	@Override
	public void onDrawFrame(GL10 gl) {
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT); 	
		mFloorGrid.draw(mViewMatrix, mProjectionMatrix);
		mPointCloud.draw(mViewMatrix,mProjectionMatrix);
	}

	public PointCloud getPointCloud() {
		return mPointCloud;
	}
	
	public CameraFrustum getCameraFrustum() {
		return mCameraFrustum;
	}
	
	public void setFirstPersonView(){
		Matrix.setIdentityM(mViewMatrix, 0);
		Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f);
	}
	
	public void setThirdPersonView(){
		Matrix.setIdentityM(mViewMatrix, 0);
		Matrix.setLookAtM(mViewMatrix, 0, 2f, 2f, 2f, 0f, 0f, 0f, 0f, 1f, 0f);
	}
	
	public void setTopDownView(){
		Matrix.setIdentityM(mViewMatrix, 0);
		Matrix.setLookAtM(mViewMatrix, 0, 0f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, -2f);
	}
}
