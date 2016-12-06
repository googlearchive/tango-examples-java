#version 300 es
in vec3 a_Position;
in vec2 a_TexCoord;
uniform mat4 u_MvpMatrix;
out vec2 v_TexCoord;

void main() {
    v_TexCoord = a_TexCoord;
    gl_Position = u_MvpMatrix * vec4(a_Position.x, a_Position.y, a_Position.z, 1.0);
}