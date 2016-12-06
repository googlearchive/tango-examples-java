#version 300 es
precision mediump float;
uniform float u_NearPlane;
uniform float u_FarPlane;
uniform float u_Width;
uniform float u_Height;
uniform sampler2D u_Texture;
uniform sampler2D u_depthTexture;
in vec2 v_TexCoord;
out vec4 gl_FragColor;

float linearDepth(float depth){
    float f= u_FarPlane;
    float n= u_NearPlane;
    float z = (2.0 * n *f) / ((f + n) - depth * (f - n));
    return z;
}

float depthSample(sampler2D depthTexture, vec2 coords, float shiftX, float shiftY, float weight,
                    float currentZ){
    float nonlinearZ = texture(depthTexture, coords + vec2(shiftX * 0.01, shiftY * 0.01)).r * 2.0 - 1.0;
    float linearZ = linearDepth(nonlinearZ);
    if(linearZ < currentZ){
        return weight;
    } else {
        return 0.0;
    }
}

float occCertainty(sampler2D depthTexture, vec2 coords, float currentZ){
    float shift[5] = float[5](0.0, 1.0, 2.0, 3.0, 4.0);
    float weight[5] = float[5](0.36, 0.075, 0.05, 0.025, 0.01);
    float certainty = 0.0;
    certainty += depthSample(u_depthTexture, coords, shift[0], shift[0],weight[0], currentZ);
    for (int i = 1; i <= 4; i++){
        certainty += depthSample(u_depthTexture, coords, shift[i], shift[i], weight[i], currentZ);
        certainty += depthSample(u_depthTexture, coords, -shift[i], shift[i], weight[i], currentZ);
        certainty += depthSample(u_depthTexture, coords, shift[i], -shift[i], weight[i], currentZ);
        certainty += depthSample(u_depthTexture, coords, -shift[i], -shift[i], weight[i], currentZ);
    }
    return certainty;
}

void main() {
    float _u = gl_FragCoord.x / u_Width;
    float _v = gl_FragCoord.y / u_Height;
    vec2 coords = vec2(_u, _v);
    float currentZ = linearDepth(gl_FragCoord.z * 2.0 - 1.0);
    float certainty = occCertainty(u_depthTexture, coords, currentZ);
    vec4 textureColor = texture(u_Texture,v_TexCoord);
    gl_FragColor = vec4(textureColor.rgb, textureColor.a * (1.0 - certainty));
}