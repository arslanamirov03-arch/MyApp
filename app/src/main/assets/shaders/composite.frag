// Camera shake, blast shockwave, chromatic aberration, bloom, ACES, grain.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform sampler2D uNoise;

uniform vec2  uResolution;
uniform vec2  uAspect;
uniform float uTime;

uniform vec2  uShakeOffset;
uniform float uShakeRot;
uniform float uZoom;

uniform float uFlash;
uniform float uIntensity;
uniform float uBloomAmount;
uniform float uExposure;
uniform float uVignette;

uniform float uShockT;      // < 0 when no shockwave is running
uniform vec2  uShockPos;

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec2 p = vUv - 0.5;
    float s = sin(uShakeRot);
    float c = cos(uShakeRot);
    p = mat2(c, -s, s, c) * p * (1.0 - uZoom);
    vec2 uv = p + 0.5 + uShakeOffset;

    if (uShockT >= 0.0) {
        vec2 d = (uv - uShockPos) * uAspect;
        float dist = length(d) + 1e-5;
        float x = (dist - uShockT * 1.7) / 0.10;
        float w = exp(-x * x) * (1.0 - uShockT);
        uv += (d / dist) * w * 0.06 / uAspect;
    }

    float ca = 0.0010 + 0.0055 * uIntensity + 0.020 * uFlash;
    vec2 dir = uv - 0.5;
    vec3 col;
    col.r = texture(uScene, uv + dir * ca).r;
    col.g = texture(uScene, uv).g;
    col.b = texture(uScene, uv - dir * ca).b;

    col += texture(uBloom, uv).rgb * uBloomAmount;
    col += vec3(1.0, 0.94, 0.84) * uFlash;

    col = aces(col * uExposure);

    col *= clamp(1.0 - uVignette * dot(p, p) * 1.7, 0.0, 1.0);

    float grain = texture(uNoise, vUv * uResolution * 0.00390625
                          + vec2(fract(uTime * 7.13), fract(uTime * 5.71))).a;
    col += (grain - 0.5) * 0.020;

    fragColor = vec4(max(col, 0.0), 1.0);
}
