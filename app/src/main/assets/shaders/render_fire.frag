// Shades the simulated gas at full screen resolution.
// The coarse grid is hidden by multi-octave curl-noise domain warping, which
// synthesises believable sub-grid filaments.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uFields;
uniform sampler2D uNoise;

uniform vec2  uAspect;
uniform float uTime;
uniform float uDetail;
uniform float uEmissive;
uniform float uSmokeDensity;
uniform float uIntensity;

void main() {
    vec2 ap = vUv * uAspect;
    float ts = uTime;

    // Flame structure is strongly anisotropic: features are several times taller
    // than they are wide and they stream upwards, so the detail lattice is
    // squashed in y and scrolled fast.
    vec2 sp = vec2(ap.x, ap.y * 0.30);

    vec2 w1 = curlNoiseTex(uNoise, sp *  4.5 + vec2( ts * 0.02, -ts * 0.55), 0.00390625);
    vec2 w2 = curlNoiseTex(uNoise, sp * 11.0 + vec2(-ts * 0.04, -ts * 1.10), 0.00390625);
    vec2 w3 = curlNoiseTex(uNoise, sp * 26.0 + vec2( ts * 0.03, -ts * 2.00), 0.00390625);
    vec2 warp = (w1 + w2 * 0.45 + w3 * 0.16) * uDetail;
    warp.y *= 2.4;

    vec4 f = texture(uFields, vUv + warp / uAspect);
    float T = f.r;
    float fuel = f.g;
    float soot = f.b;

    // fine filament breakup -- this is what turns a smooth blob into tongues
    float fine  = texture(uNoise, sp *  5.5 + vec2( ts * 0.03, -ts * 1.25)).g;
    float fine2 = texture(uNoise, sp * 13.0 + vec2(-ts * 0.02, -ts * 2.30)).b;
    float k = 0.78 * smoothstep(0.01, 0.30, T);
    T *= mix(1.0, 0.30 + 1.12 * fine + 0.38 * fine2, k);
    T = max(T, 0.0);

    // blackbody emission, Stefan-Boltzmann-ish falloff
    float kelvin = 850.0 + T * 1950.0 + uIntensity * 220.0;
    float emit = pow(T, 2.8) * uEmissive;
    // Sooty flames radiate noticeably warmer than an ideal blackbody, and the
    // tint also stops bright regions from tonemapping to a flat white.
    vec3 fire = blackbody(kelvin) * vec3(1.0, 0.80, 0.52) * emit;

    // rich, fuel-heavy roots of a flame burn blue
    float blue = smoothstep(0.30, 0.85, fuel) * smoothstep(0.30, 0.75, T);
    fire = mix(fire, vec3(0.30, 0.58, 1.00) * emit * 0.9, blue * 0.55);

    // soot: absorbs the light behind it, picks up a little from the flame
    float sm = 1.0 - exp(-soot * uSmokeDensity);
    vec3 smokeCol = mix(vec3(0.016, 0.015, 0.018), vec3(0.14, 0.070, 0.035),
                        smoothstep(0.04, 0.55, T));
    vec3 col = fire * (1.0 - 0.85 * sm) + smokeCol * sm * (0.16 + 0.55 * uIntensity);

    fragColor = vec4(col, 1.0);
}
