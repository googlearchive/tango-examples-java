package com.projecttango.tangoutils;

import android.opengl.Matrix;

public class ModelMatCalculator {
	
	float[] modelMatrix = new float[16];
	float[] conversionMatrix = new float[]
			{1.0f, 0.0f,0.0f,0.0f,
			 0.0f, 0.0f,-1.0f,0.0f,
			 0.0f, 1.0f,0.0f,0.0f,
			 0.0f, 0.0f,0.0f,1.0f};

	public ModelMatCalculator(){
		Matrix.setIdentityM(modelMatrix, 0);
	}
	
	/**
	 * Updates the model matrix (rotation and translation) of the CameraFrustum.
	 * @param translation a three-element array of translation data.
	 * @param quaternion a four-element array of rotation data.
	 */
	public  void UpdateModelMatrix(float[] translation,float[] quaternion){
		float[] quaternionMatrix = new float[16];
		quaternionMatrix = MathUtils.quaternionM(quaternion);	
		float[] transposedQuaternionMatrix = new float[16];
		Matrix.transposeM(transposedQuaternionMatrix, 0, quaternionMatrix, 0);		
		Matrix.multiplyMM(modelMatrix, 0,  conversionMatrix, 0 , transposedQuaternionMatrix, 0);	
		modelMatrix[12] = translation[0];
		modelMatrix[13] = translation[2];
		modelMatrix[14] = -translation[1];		
	}
	public  float[] getModelMatrix(){
		return modelMatrix;
	}
	
}
