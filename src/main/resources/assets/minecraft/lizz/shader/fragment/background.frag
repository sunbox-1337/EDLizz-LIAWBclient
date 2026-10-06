#ifdef GL_ES
precision mediump float;
#endif

uniform vec2 iResolution;
uniform float iTime;

mat2 m(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

float map(vec3 p) {
    p.xz *= m(iTime * 0.4);
    p.xy *= m(iTime * 0.1);
    vec3 q = p * 2.0 + iTime;
    return length(p + vec3(sin(iTime * 0.7))) * log(length(p) + 1.0)
           + sin(q.x + sin(q.z + sin(q.y))) * 0.5 - 1.0;
}

// 彩虹调色板：输入 0~1，输出彩虹色
vec3 palette(float t) {
    // 用余弦函数生成平滑彩虹色，t 在 0~1 之间循环
    return 0.5 + 0.5 * cos(6.28318 * (t + vec3(0.0, 0.33, 0.67)));
}

void main() {
    vec2 a = gl_FragCoord.xy / iResolution.y - vec2(0.9, 0.5);
    vec3 cl = vec3(0.0);
    float d = 2.5;

    for (int i = 0; i <= 5; i++) {
        vec3 p = vec3(0, 0, 4.0) + normalize(vec3(a, -1.0)) * d;
        float rz = map(p);
        float f = clamp((rz - map(p + 0.1)) * 0.5, -0.1, 1.0);

        // 光照强度（保持原逻辑的范围）
        float light = 0.2 + 0.8 * f;

        // 彩虹色相随时间来回变化
        float hue = iTime * 0.2 + f * 0.1;  // 时间驱动 + 光照微调
        vec3 l = palette(hue) * light * 2.0; // 调整亮度系数

        cl = cl * l + smoothstep(2.5, 0.0, rz) * 0.6 * l;
        d += min(rz, 1.0);
    }

    gl_FragColor = vec4(cl, 1.0);
}