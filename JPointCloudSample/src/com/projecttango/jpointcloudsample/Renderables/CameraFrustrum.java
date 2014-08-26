package com.projecttango.jpointcloudsample.Renderables;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.opengles.GL10;

import com.projectango.jpointcloudsample.PCRenderer;

import android.opengl.GLES20;
import android.opengl.Matrix;

public class CameraFrustrum {
		
		private float[] mTranslation = new float[3];
		private float[] mQuaternion = new float[4];
		
	 	private FloatBuffer mVertexBuffer;
	    private final String vertexShaderCode =
	            // This matrix member variable provides a hook to manipulate
	            // the coordinates of the objects that use this vertex shader
	            "uniform mat4 uMVPMatrix;" +
	            "attribute vec4 vPosition;" +
	            "void main() {" +
	            // the matrix must be included as a modifier of gl_Position
	            // Note that the uMVPMatrix factor *must be first* in order
	            // for the matrix multiplication product to be correct.
	            "  gl_Position = uMVPMatrix * vPosition;" +
	            "}";

		 private final String fragmentShaderCode =
		            "precision mediump float;" +
		            "uniform vec4 vColor;" +
		            "void main() {" +
		            "  gl_FragColor = vec4(0.3,0.5,0.0,1.0);" +
		            "}";
	    
	        
	    private float vertices[] = {
	    		0.25f, -0.15f, -0.5f, // TR
                0.0f, 0.0f, 0.0f, // S

                0.25f, -0.15f, -0.5f, // TR
                -0.25f, -0.15f, -0.5f, // TL

                0.25f, -0.15f, -0.5f, // TR
                0.25f, 0.15f, -0.5f, // BR

                0.0f, 0.0f, 0.0f, // S
                -0.25f, -0.15f, -0.5f, // TL

                -0.25f, -0.15f, -0.5f, // TL
                -0.25f, 0.15f, -0.5f, // BL

                0.25f, 0.15f, -0.5f, // BR
                -0.25f, 0.15f, -0.5f, // BL

                -0.25f, 0.15f, -0.5f, // BL
                0.0f, 0.0f, 0.0f, // S

                0.0f, 0.0f, 0.0f, // S
                0.25f, 0.15f, -0.5f, // BR
	                                };
	    private float colors[] = {
	    		0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f,
                0.4f, 0.4f, 0.4f, 1.0f
	                            };   
	    
	    private float[] modelMatrix = new float[16];
	    private float[] mvMatrix = new float[16];
	    private float[] mvpMatrix = new float[16];
	    
	    
	    private final int mProgram;
		private int mPosHandle;
		static final int COORDS_PER_VERTEX = 3;
		private int mMVPMatrixHandle;
	                
	    public CameraFrustrum() {
	    		Matrix.setIdentityM(modelMatrix, 0);
	            ByteBuffer byteBuf = ByteBuffer.allocateDirect(vertices.length * 4);
	            byteBuf.order(ByteOrder.nativeOrder());
	            mVertexBuffer = byteBuf.asFloatBuffer();
	            mVertexBuffer.put(vertices);
	            mVertexBuffer.position(0);
	              
	            int vertexShader = PCRenderer.loadShader(GLES20.GL_VERTEX_SHADER,vertexShaderCode);
	    		int fragShader = PCRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
	    		mProgram = GLES20.glCreateProgram();
	    		GLES20.glAttachShader(mProgram, vertexShader);
	    		GLES20.glAttachShader(mProgram, fragShader);
	    		GLES20.glLinkProgram(mProgram);
	    }
	    
	    public void updateModelMatrix(float[] translation, float[] quaternion ) {
	        
	    	mTranslation = translation;
	        mQuaternion = quaternion;
	    	float[] quaternionMatrix = new float[16];
	    	quaternionMatrix = quaternionM(quaternion);
	    	
	        Matrix.setIdentityM(modelMatrix, 0);
	        
	        //Matrix.rotateM(modelMatrix, 0, 90, 1f, 0f, 0f);
	        //Matrix.rotateM(modelMatrix, 0, 90, 0f, 1f, 0f);
	        Matrix.translateM(modelMatrix, 0, translation[0], translation[2], -translation[1]);
	        
	        float[] mTempMatrix = new float[16];
	        Matrix.setIdentityM(mTempMatrix, 0);
	        
	        if (quaternionMatrix != null) {
	            Matrix.multiplyMM(mTempMatrix, 0, modelMatrix, 0, quaternionMatrix, 0);
	           // Matrix.rotateM(mTempMatrix, 0, 90, 0, 0, 1);
	            System.arraycopy(mTempMatrix, 0, modelMatrix, 0, 16);
	        }
	    };
	    
	    public void updateViewMatrix(float[] viewMatrix) {
	    	Matrix.setLookAtM(viewMatrix, 0, mTranslation[0], mTranslation[2]+2, -mTranslation[1]+2, mTranslation[0], mTranslation[2], -mTranslation[1], 0, 1, 0);
		}
	  

	    public void draw(float[] viewMatrix, float[] projectionMatrix) {             
	    		
	    	
	    	GLES20.glUseProgram(mProgram);
	    	updateViewMatrix(viewMatrix);
	    	
	    	Matrix.setIdentityM(mvMatrix, 0);    
	    	Matrix.setIdentityM(mvpMatrix,0);   
	    	Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0);    
	    	Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0);
	    	
	    	mPosHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");	
	    	GLES20.glVertexAttribPointer(mPosHandle, COORDS_PER_VERTEX,GLES20.GL_FLOAT, false,0, mVertexBuffer);		
	    	GLES20.glEnableVertexAttribArray(mPosHandle);	
	    	mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");	
	    	GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);	
	    	GLES20.glLineWidth(3);
	    	GLES20.glDrawArrays(GLES20.GL_LINES, 0, 16);

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

		 public static void normalizeVector(float[] v) {

		        float mag2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2] + v[3] * v[3];
		        if (Math.abs(mag2) > 0.00001f && Math.abs(mag2 - 1.0f) > 0.00001f) {
		            float mag = (float) Math.sqrt(mag2);
		            v[0] /= mag;
		            v[1] /= mag;
		            v[2] /= mag;
		            v[3] /= mag;
		        }
		    }
}