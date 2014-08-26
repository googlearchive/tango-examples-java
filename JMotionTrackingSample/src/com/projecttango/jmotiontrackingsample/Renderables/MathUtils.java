package com.projecttango.jmotiontrackingsample.Renderables;

public class MathUtils {
	
	public static float[] convertQuaternionToOpenGl(float[] quaternion) {
		double[] xAxis = {-1, 0, 0};
		float[] rotation_offsetX = rotateQuaternionWithAngleAxis(quaternion, 3.141517f/2f, xAxis);
		float[] openglQuaternion = {rotation_offsetX[3],rotation_offsetX[0],rotation_offsetX[2],-rotation_offsetX[1]};
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
	
	public static float[] rotateQuaternionWithAngleAxis(float[] quaternion, double angleInRadians, 
			double[] axisVector) {
		
		float norm = (float) Math.sqrt(Math.pow(axisVector[0], 2) + Math.pow(axisVector[1], 2) + Math.pow(axisVector[2], 2));
		float sin_half_angle = (float) Math.sin(angleInRadians / 2.0f);
        float x = (float) (sin_half_angle * axisVector[0] / norm);
        float y = (float) (sin_half_angle * axisVector[1] / norm);
        float z = (float) (sin_half_angle * axisVector[2] / norm);
        float w = (float)Math.cos(angleInRadians / 2.0f);
        float[] rotatedQuaternion = {0.7071067811865476f, 0 ,0.7071067811865476f, 0};
        float[] multiQuaternion = multiplyQuarternions(rotatedQuaternion, quaternion);
        
        return multiQuaternion;
	}
	
	public static float[] multiplyQuarternions(float[] a,float[] b) {
		float[] multipliedQuaternion = new float[4];
		multipliedQuaternion[3] = a[0]*b[0] - a[1]*b[1] - a[2]*b[2] - a[3]*b[3];
		multipliedQuaternion[0] = a[0]*b[1] + a[1]*b[0] + a[2]*b[3] - a[3]*b[2];
		multipliedQuaternion[1] = a[0]*b[2] - a[1]*b[3] + a[2]*b[0] + a[3]*b[1];
		multipliedQuaternion[2] = a[0]*b[3] + a[1]*b[2] - a[2]*b[1] + a[3]*b[3];
		
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
