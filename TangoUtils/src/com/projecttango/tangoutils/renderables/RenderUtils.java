package com.projecttango.tangoutils.renderables;

import android.opengl.GLES20;

public class RenderUtils {
	
	/**
	 * Creates a vertex or fragment shader.
	 * @param type one of GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
	 * @param shaderCode GLSL code for the shader as a String
	 * @return a compiled shader.
	 */
	public static int loadShader(int type, String shaderCode) {
		// Create a shader of the correct type
		int shader = GLES20.glCreateShader(type);

		// Compile the shader from source code
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);

		return shader;
	}
	
}
