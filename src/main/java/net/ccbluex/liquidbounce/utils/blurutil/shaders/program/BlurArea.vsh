#version 120

varying vec2 texCoord;
varying vec2 oneTexel;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    texCoord = gl_MultiTexCoord0.xy;
    oneTexel = 1.0 / textureSize(DiffuseSampler, 0);
}