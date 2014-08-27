package com.projecttango.tangoutils.renderables;

import android.opengl.Matrix;

public abstract class Renderable {

	private float[] mModelMatrix = new float[16];
	private float[] mMvMatrix = new float[16];
	private float[] mMvpMatrix = new float[16];
	
	/**
	 * Applies the view and projection matrices and draws the Renderable.
	 * @param viewMatrix the view matrix to map from world space to camera space.
	 * @param projectionMatrix the projection matrix to map from camera space to screen space.
	 */
	public abstract void draw(float[] viewMatrix, float[] projectionMatrix);
	
	public void updateMvpMatrix(float[] viewMatrix, float[] projectionMatrix) {
		// Compose the model, view, and projection matrices into a single mvp matrix
		Matrix.setIdentityM(mMvMatrix, 0);
		Matrix.setIdentityM(mMvpMatrix, 0);
		Matrix.multiplyMM(mMvMatrix, 0, viewMatrix, 0, mModelMatrix, 0);
		Matrix.multiplyMM(mMvpMatrix, 0, projectionMatrix, 0, mMvMatrix, 0);
	}
	
	public float[] getModelMatrix() {
		return mModelMatrix;
	}
	
	public float[] getMvMatrix() {
		return mMvMatrix;
	}
	
	public float[] getMvpMatrix() {
		return mMvpMatrix;
	}
}
