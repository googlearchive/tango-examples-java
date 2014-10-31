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

package com.projecttango.tangoutils.renderables;

import android.opengl.GLES20;

/**
 * Static functions used by Renderer classes in Tango Java samples.
 */
public class RenderUtils {

    /**
     * Creates a vertex or fragment shader.
     * 
     * @param type
     *            one of GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
     * @param shaderCode
     *            GLSL code for the shader as a String
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
