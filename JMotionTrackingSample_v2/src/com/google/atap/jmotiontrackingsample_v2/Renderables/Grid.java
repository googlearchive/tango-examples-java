package com.google.atap.jmotiontrackingsample_v2.Renderables;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.atap.jmotiontrackingsample_v2.MTGLRenderer;


public class Grid {

	private static final String vertexShaderCode = // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "void main() {" +
            // the matrix must be included as a modifier of gl_Position
            // Note that the uMVPMatrix factor *must be first* in order
            // for the matrix multiplication product to be correct.
            "  gl_Position = uMVPMatrix * vPosition;" +
            "}";
	private static final String fragmentShaderCode="precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() {" +
            "  gl_FragColor = vec4(0.8,0.8,0.8,1.0);" +
            "}";
	
	private static final int GRID_RANGE_M = 100;
	private static final int BYTES_PER_FLOAT = 4;
	private FloatBuffer mVertexBuffer;
	private final int mProgram;
	private float[] modelMatrix = new float[16];
	private float[] mvMatrix = new float[16];
	private float[] mvpMatrix = new float[16];
	private int mPosHandle;
	private int mMVPMatrixHandle;
	static final int COORDS_PER_VERTEX = 3;
	
	public Grid()
	{
		ByteBuffer vertexByteBuffer = ByteBuffer.allocateDirect((GRID_RANGE_M * 2 + 1) * 4 * 3 * BYTES_PER_FLOAT);
        vertexByteBuffer.order(ByteOrder.nativeOrder());
        mVertexBuffer = vertexByteBuffer.asFloatBuffer();
        for (int x = -GRID_RANGE_M; x <= GRID_RANGE_M; x++) {
            mVertexBuffer.put(new float[] {x, 0f, (float) -GRID_RANGE_M });
            mVertexBuffer.put(new float[] {x, 0f,(float) GRID_RANGE_M });
        }

        for (int z = -GRID_RANGE_M; z <= GRID_RANGE_M; z++) {
            mVertexBuffer.put(new float[] {(float) -GRID_RANGE_M, 0f, z});
            mVertexBuffer.put(new float[] {(float) GRID_RANGE_M, 0f, z  });
        }
        int vertexShader = MTGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER,vertexShaderCode);
		int fragShader = MTGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
		mProgram = GLES20.glCreateProgram();
		GLES20.glAttachShader(mProgram, vertexShader);
		GLES20.glAttachShader(mProgram, fragShader);
		GLES20.glLinkProgram(mProgram);
		
        Matrix.setIdentityM(modelMatrix, 0);
	}

	public void draw(float[] viewMatrix, float[] projectionMatrix) {

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
    	GLES20.glLineWidth(3);
    	GLES20.glDrawArrays(GLES20.GL_LINES, 0, (GRID_RANGE_M * 2 + 1) * 4);
	}

	
	
}
