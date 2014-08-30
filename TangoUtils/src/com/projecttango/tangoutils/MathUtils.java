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

/**
 * Utility class for Matrix math computations needed for Tango Java samples that are not provided
 * by OpenGL.
 */
public class MathUtils {
	
	/**
	 * Converts an (x, y, z, w) quaternion tuple into a 4x4 orthogonal
	 * rotation matrix.
	 * @param quaternion a 4-element quaternion tuple.
	 * @return a 4x4 row-major rotation matrix.
	 */
	public static float[] quaternionM(float[] quaternion) {
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
		matrix[1] = 2f * (xy - wz);
		matrix[2] = 2f * (xz + wy);
		matrix[3] = 0f;

		matrix[4] = 2f * (xy + wz);
		matrix[5] = 1f - 2f * (x2 + z2);
		matrix[6] = 2f * (yz - wx);
		matrix[7] = 0f;

		matrix[8] = 2f * (xz - wy);
		matrix[9] = 2f * (yz + wx);
		matrix[10] = 1f - 2f * (x2 + y2);
		matrix[11] = 0f;

		matrix[12] = 0f;
		matrix[13] = 0f;
		matrix[14] = 0f;
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