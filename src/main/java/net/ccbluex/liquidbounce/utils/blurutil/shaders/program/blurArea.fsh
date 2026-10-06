#version 120

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform vec2 BlurDir;
uniform vec2 BlurXY;
uniform vec2 BlurCoord;
uniform float Radius;
uniform float ScaleFactor;

float SCurve (float x) {
    x = x * 2.0 - 1.0;
    return -x * abs(x) * 0.5 + x + 0.5;
}

vec4 BlurH (sampler2D source, vec2 size, vec2 uv, float radius) {
    if (uv.x / oneTexel.x >= BlurXY.x * ScaleFactor &&
        uv.y / oneTexel.y >= BlurXY.y * ScaleFactor &&
        uv.x / oneTexel.x <= (BlurCoord.x + BlurXY.x) * ScaleFactor &&
        uv.y / oneTexel.y <= (BlurCoord.y + BlurXY.y) * ScaleFactor) {
        vec4 A = vec4(0.0);
        vec4 C = vec4(0.0);
        float divisor = 0.0;
        float weight = 0.0;
        float radiusMultiplier = 1.0 / radius;
        float MAX_SAMPLES = 24.0;
        float stride = max(1.0, (radius * 2.0) / MAX_SAMPLES);
        for (float x = -radius; x <= radius; x += stride) {
            A = texture2D(source, uv + vec2(x * size) * BlurDir);
            weight = SCurve(1.0 - (abs(x) * radiusMultiplier));
            C += A * weight;
            divisor += weight;
        }
        return vec4(C.r / divisor, C.g / divisor, C.b / divisor, 1.0);
    }
    return texture2D(source, uv);
}

void main() {
    gl_FragColor = BlurH(DiffuseSampler, oneTexel, texCoord, Radius);
}