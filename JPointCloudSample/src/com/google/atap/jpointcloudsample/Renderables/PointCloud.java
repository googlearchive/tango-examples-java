package com.google.atap.jpointcloudsample.Renderables;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.atap.jpointcloudsample.PCRenderer;

public class PointCloud {
	
	private static final String vertexShaderCode = // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
             "varying vec4 vColor;"+
            "void main() {" +
            // the matrix must be included as a modifier of gl_Position
            // Note that the uMVPMatrix factor *must be first* in order
            // for the matrix multiplication product to be correct.
            "gl_PointSize = 2.0;"+
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  vColor = vPosition;" +
            "}";
	private static final String fragmentShaderCode="precision mediump float;" +
            "varying vec4 vColor;" +
            "void main() {" +
            "  gl_FragColor = vec4(vColor);" +
            "}";
	
	private static final int BYTES_PER_FLOAT = 4;
	private FloatBuffer mVertexBuffer;
	
	private final int mProgram;
	private float[] modelMatrix = new float[16];
	private float[] mvMatrix = new float[16];
	private float[] mvpMatrix = new float[16];
	private int mPosHandle;
	private int mMVPMatrixHandle;
	static final int COORDS_PER_VERTEX = 3;
	public int mPointCount;
	
	public PointCloud()
	{
		   	int vertexShader = PCRenderer.loadShader(GLES20.GL_VERTEX_SHADER,vertexShaderCode);
			int fragShader = PCRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
			mProgram = GLES20.glCreateProgram();
			GLES20.glAttachShader(mProgram, vertexShader);
			GLES20.glAttachShader(mProgram, fragShader);
			GLES20.glLinkProgram(mProgram);
	        Matrix.setIdentityM(modelMatrix, 0);
	}
	
	public void UpdatePoints(byte[] byteArray)
	{
		FloatBuffer mPointCloudFloatBuffer;
		ByteBuffer mVertexByteBuffer;
		mPointCloudFloatBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.nativeOrder()).asFloatBuffer(); 
		mPointCount = mPointCloudFloatBuffer.capacity()/3;
		mVertexByteBuffer = ByteBuffer.allocateDirect(mPointCloudFloatBuffer.capacity()*BYTES_PER_FLOAT);
		mVertexByteBuffer.order(ByteOrder.nativeOrder());
		mVertexBuffer = mVertexByteBuffer.asFloatBuffer();
		mVertexBuffer.clear();
		mVertexBuffer.position(0);
		
		for(int i=0; i< mPointCloudFloatBuffer.capacity();i=i+3){
			if(i+3 < mPointCloudFloatBuffer.capacity())
			{
				mVertexBuffer.put(-mPointCloudFloatBuffer.get(i));
				mVertexBuffer.put(-mPointCloudFloatBuffer.get(i+1));
				mVertexBuffer.put(mPointCloudFloatBuffer.get(i+2));
			}
		}
		
	}
	
	public void draw(float[] viewMatrix,float[] projectionMatrix)
	{
		if(mPointCount>0){
		GLES20.glUseProgram(mProgram);
		mVertexBuffer.position(0);
		Matrix.setIdentityM(mvMatrix, 0);
		Matrix.setIdentityM(mvpMatrix, 0);
	
		Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0);
		Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0);
		mPosHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");	
	    GLES20.glVertexAttribPointer(mPosHandle, COORDS_PER_VERTEX,GLES20.GL_FLOAT, false,0, mVertexBuffer);		
	    GLES20.glEnableVertexAttribArray(mPosHandle);	
	    mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");	
	    GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);	
	    GLES20.glDrawArrays(GLES20.GL_POINTS, 0, mPointCount);   
		}
	}
	
}
