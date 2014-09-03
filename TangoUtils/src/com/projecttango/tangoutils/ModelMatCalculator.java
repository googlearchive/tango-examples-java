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

package com.projecttango.tangoutils;

import android.opengl.Matrix;

/**
 * Utility class to manage the calculation of a Model Matrix from the translation and quaternion
 * arrays obtained from an {@link TangoPose} object.  Delegates some mathematical computations to
 * the {@link MathUtils}.
 */
public class ModelMatCalculator {

	private static float[] conversionMatrix = new float[]{
			1.0f, 0.0f, 0.0f, 0.0f,
			0.0f, 0.0f,-1.0f, 0.0f,
			0.0f, 1.0f, 0.0f, 0.0f,
			0.0f, 0.0f, 0.0f, 1.0f};
	
	private float[] modelMatrix = new float[16];
	private float[] mTranslation = new float[3];
	private float[] mQuaternion = new float[4];
	
	public ModelMatCalculator() {
		Matrix.setIdentityM(modelMatrix, 0);
	}
	
	/**
	 * Updates the model matrix (rotation and translation).
	 * @param translation a three-element array of translation data.
	 * @param quaternion a four-element array of rotation data.
	 */
	public void updateModelMatrix(float[] translation, float[] quaternion){
		
		float[] quaternionMatrix = new float[16];
		quaternionMatrix = quaternionMatrixOpenGL(quaternion);	
				
		Matrix.multiplyMM(modelMatrix, 0,  conversionMatrix, 0 , quaternionMatrix, 0);	
		modelMatrix[12] = translation[0];
		modelMatrix[13] = translation[2];
		modelMatrix[14] = -1f * translation[1];		
		
	}
	
	public float[] getModelMatrix(){
		return modelMatrix;
	}
	
	public float[] getTranslation(){
		return new float[]{modelMatrix[12], modelMatrix[13], modelMatrix[14]};
	}
	
	/**
	 * A function to convert a quaternion to quaternion Matrix. Please note that Opengl.Matrix is 
	 * Column Major and so we construct the matrix in Column Major Format.
	 *		 - -		- -
	 * 		| 0  4  8  12 |
	 * 		| 1  5  9  13 |
	 * 		| 2  6  10 14 |
	 * 		| 3  7  11 15 |
	 * 		- -   		- -
	 * 
	 * @param quaternion Input quaternion with float[4]
	 * @return Quaternion Matrix of float[16]
	 */
	public static float[] quaternionMatrixOpenGL(float[] quaternion) {
		float[] matrix = new float[16];
		normalizeVector(quaternion);

		float x = quaternion[0];
		float y = quaternion[1];
		float z = quaternion[2];
		float w = quaternion[3];

		float x2 = x * x;
		float y2 = y * y;
		float z2 = z * z;
		float xy = x * y;
		float xz = x * z;
		float yz = y * z;
		float wx = w * x;
		float wy = w * y;
		float wz = w * z;

		matrix[0] = 1f - 2f * (y2 + z2);
		matrix[4] = 2f * (xy - wz);
		matrix[8] = 2f * (xz + wy);
		matrix[12] = 0f;

		matrix[1] = 2f * (xy + wz);
		matrix[5] = 1f - 2f * (x2 + z2);
		matrix[9] = 2f * (yz - wx);
		matrix[13] = 0f;

		matrix[2] = 2f * (xz - wy);
		matrix[6] = 2f * (yz + wx);
		matrix[10] = 1f - 2f * (x2 + y2);
		matrix[14] = 0f;

		matrix[3] = 0f;
		matrix[7] = 0f;
		matrix[11] = 0f;
		matrix[15] = 1f;
		
		return matrix;
	}
	
	/**
	 * Creates a unit vector in the direction of an arbitrary vector.  The original vector is
	 * modified in place.
	 * @param v the vector to normalize
	 */
	public static void normalizeVector(float[] v) {
		float mag2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2] + v[3] * v[3];
		if (Math.abs(mag2) > 0.00001f && Math.abs(mag2 - 1.0f) > 0.00001f) {
			float mag = (float) Math.sqrt(mag2);
			v[0] = v[0] / mag;
			v[1] = v[1] / mag;
			v[2] = v[2] / mag;
			v[3] = v[3] / mag;
		}
	}
}
