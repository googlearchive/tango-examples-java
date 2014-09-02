package com.projecttango.tangoutils;

import android.opengl.Matrix;

public class Renderer {
	
	public static final int FIRST_PERSON = 0;
	public static final int TOP_DOWN = 1;
	public static final int THIRD_PERSON = 2;
	
	private static final int MATRIX_4X4 = 16;
	
	private ModelMatCalculator mModelMatCalculator = new ModelMatCalculator();
 	private int viewId = 2;
	private float[] mViewMatrix = new float[MATRIX_4X4];

	/**
	 * Update the view matrix of the Renderer to follow the position of the device in the current
	 * perspective.
	 */
	public void updateViewMatrix() {
		float[] devicePosition = mModelMatCalculator.getTranslation();
		
		switch(viewId){
		case FIRST_PERSON:
			float[] invertModelMat = new float[MATRIX_4X4];
			Matrix.setIdentityM(invertModelMat, 0);
			
			float[] temporaryMatrix = new float[MATRIX_4X4];
			Matrix.setIdentityM(temporaryMatrix, 0);
			
			Matrix.setIdentityM(mViewMatrix, 0);
			Matrix.invertM(invertModelMat, 0, mModelMatCalculator.getModelMatrix(), 0);
			Matrix.multiplyMM(temporaryMatrix, 0, mViewMatrix, 0, invertModelMat, 0);
			System.arraycopy(temporaryMatrix, 0, mViewMatrix, 0, 16);
			break;
		case THIRD_PERSON:
			Matrix.setIdentityM(mViewMatrix, 0);
			Matrix.setLookAtM(mViewMatrix, 0, devicePosition[0], 5.0f + devicePosition[1], 
					5.0f + devicePosition[2], devicePosition[0], devicePosition[1], 
					devicePosition[2], 0f, 1f, 0f);
			break;
		case TOP_DOWN:
			Matrix.setIdentityM(mViewMatrix, 0);
			Matrix.setLookAtM(mViewMatrix, 0, devicePosition[0], 5.0f + devicePosition[1], 
					devicePosition[2], devicePosition[0], devicePosition[1], 
					devicePosition[2], 0f, 0f, -1f);
			break;
		default:
			viewId = THIRD_PERSON;
			return;
		}	
	}
	
	public void setFirstPersonView(){
		viewId = FIRST_PERSON;
	}
	
	public void setThirdPersonView(){
		viewId = THIRD_PERSON;
	}
	
	public void setTopDownView(){
		viewId = TOP_DOWN;
	}
	
	public void resetModelMatCalculator() {
		mModelMatCalculator = new ModelMatCalculator();
	}
	
	public ModelMatCalculator getModelMatCalculator() {
		return mModelMatCalculator;
	}
	
	public float[] getViewMatrix() {
		return mViewMatrix;
	}
}
