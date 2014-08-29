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

public class MathUtils {
	
	public static float[] convertQuaternionToOpenGl(float[] quaternion) {
		float[] xAxis = {-1f, 0f, 0f};
		float[] rotation_offsetX = rotateQuaternionWithAngleAxis(quaternion, 3.141517f/2f, xAxis);
		float[] openglQuaternion = {rotation_offsetX[2],rotation_offsetX[3],rotation_offsetX[1],-rotation_offsetX[0]};
		return openglQuaternion;		
	}
	
	public static float[] invertQuaternion(float[] quaternion) {	
		float sqNorm = (float) (Math.pow(quaternion[0], 2) + Math.pow(quaternion[1], 2) + 
				Math.pow(quaternion[2], 2)+ Math.pow(quaternion[3], 2));
		float[] inversedQ = new float[4];
		inversedQ[0] = -quaternion[0] / sqNorm;
		inversedQ[1] = -quaternion[1] / sqNorm;
		inversedQ[2] = -quaternion[2] / sqNorm;
		inversedQ[3] = quaternion[3] / sqNorm;
		return inversedQ;
	}
	
	public static double[] quaternionToEulerAngle(float[] quaternion){
		double test = quaternion[0]*quaternion[1] + quaternion[2]*quaternion[3];
		double roll,pitch,yaw;
		if (test > 0.499) { // singularity at north pole
			pitch = 2 * Math.atan2(quaternion[0], quaternion[3]);
			yaw = Math.PI/2;
			roll = 0;
			return new double[]{roll,pitch,yaw};
		}
		if (test < -0.499) { // singularity at south pole
			pitch = -2 * Math.atan2(quaternion[0], quaternion[3]);
			yaw = - Math.PI/2;
			roll = 0;
			return new double[]{roll,pitch,yaw};
		}
	    double sqx = quaternion[0]*quaternion[0];
	    double sqy = quaternion[1]*quaternion[1];
	    double sqz = quaternion[2]*quaternion[2];
	    pitch = Math.atan2(2*quaternion[1]*quaternion[3]-2*quaternion[0]*quaternion[2] , 1 - 2*sqy - 2*sqz);
		yaw = Math.asin(2*test);
		roll = Math.atan2(2*quaternion[0]*quaternion[3]-2*quaternion[1]*quaternion[2] , 1 - 2*sqx - 2*sqz);
		return new double[]{roll,pitch,yaw};
	}
	
	public static float[] rotateQuaternionWithAngleAxis(float[] quaternion, float angleInRadians, 
			float[] axisVector) {
		
		float norm = (float) Math.sqrt(Math.pow(axisVector[0], 2) + Math.pow(axisVector[1], 2) + Math.pow(axisVector[2], 2));
		float sin_half_angle = (float) Math.sin(angleInRadians / 2.0f);
        float x = (float) (sin_half_angle * axisVector[0] / norm);
        float y = (float) (sin_half_angle * axisVector[1] / norm);
        float z = (float) (sin_half_angle * axisVector[2] / norm);
        float w = (float)Math.cos(angleInRadians / 2.0f);
        float[] rotatedQuaternion = {x,y,z, w};
        float[] multiQuaternion = multiplyQuarternions(quaternion, rotatedQuaternion);
        
        return multiQuaternion;
	}
	
	public static float[] multiplyQuarternions(float[] a,float[] b) {
		float[] multipliedQuaternion = new float[4];
		int w =3;int x=0;int y=1;int z=2;
		multipliedQuaternion[w] = a[w]*b[w] - a[x]*b[x] - a[y]*b[y] - a[z]*b[z];
		multipliedQuaternion[x] = a[w]*b[x] + a[x]*b[w] + a[y]*b[z] - a[z]*b[y];
		multipliedQuaternion[y] = a[w]*b[y] - a[x]*b[z] + a[y]*b[w] + a[z]*b[x];
		multipliedQuaternion[z] = a[w]*b[z] + a[x]*b[y] - a[y]*b[x] + a[z]*b[w];
		return multipliedQuaternion;
	}
	
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