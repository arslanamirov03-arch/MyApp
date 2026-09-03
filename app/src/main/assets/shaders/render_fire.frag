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

// Colour grade: the blackbody ramp is shared, but a phosphorus flash and a
// campfire sit at very different points on it.
uniform vec3  uTint;
uniform float uKelvinBase;
uniform float uKelvinSpan;
// 0.3 stretches detail into flame tongues; near 1.0 keeps it round for a fireball
uniform float uAniso;
// Multiplies the smoke's own colour. A campfire's smoke should stay near black;
// a mushroom cloud is the whole point of the shot and has to be visible.
uniform float uSmokeGlow;

// glowing bed of embers the flames sit on
uniform vec2  uTouch;
uniform float uCoal;
uniform float uCoalRadius;
uniform float uCoalFlat;

void main() {
    vec2 ap = vUv * uAspect;
    float ts = uTime;

    // Flame structure is strongly anisotropic: features are several times taller
    // than they are wide and they stream upwards, so the detail lattice is
    // squashed in y and scrolled fast.
    vec2 sp = vec2(ap.x, ap.y * uAniso);

    vec2 w1 = curlNoiseTex(uNoise, sp *  4.5 + vec2( ts * 0.02, -ts * 0.55), 0.00390625);
    vec2 w2 = curlNoiseTex(uNoise, sp * 11.0 + vec2(-ts * 0.04, -ts * 1.10), 0.00390625);
    vec2 w3 = curlNoiseTex(uNoise, sp * 26.0 + vec2( ts * 0.03, -ts * 2.00), 0.00390625);
    vec2 warp = (w1 + w2 * 0.45 + w3 * 0.16) * uDetail;
    warp.y *= mix(2.4, 1.0, clamp((uAniso - 0.30) / 0.70, 0.0, 1.0));

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
    float kelvin = uKelvinBase + T * uKelvinSpan + uIntensity * 220.0;
    float emit = pow(T, 2.8) * uEmissive;
    // Sooty flames radiate noticeably warmer than an ideal blackbody, and the
    // tint also stops bright regions from tonemapping to a flat white.
    vec3 fire = blackbody(kelvin) * uTint * emit;

    // Rich, fuel-heavy roots burn blue -- but only right down at the fuel bed;
    // letting it reach up the tongues turns them a washed-out grey.
    float nearBase = exp(-pow(max(vUv.y - uTouch.y, 0.0) / 0.085, 2.0));
    float blue = smoothstep(0.35, 0.90, fuel) * smoothstep(0.30, 0.75, T) * nearBase;
    fire = mix(fire, vec3(0.35, 0.60, 1.00) * emit * 0.85, blue * 0.42);

    // soot: absorbs the light behind it, picks up a little from the flame
    float sm = 1.0 - exp(-soot * uSmokeDensity);
    vec3 smokeCol = mix(vec3(0.016, 0.015, 0.018), vec3(0.14, 0.070, 0.035),
                        smoothstep(0.04, 0.55, T));
    vec3 col = fire * (1.0 - 0.85 * sm)
            + smokeCol * sm * (0.16 + 0.55 * uIntensity) * uSmokeGlow;

    // --- bed of embers ------------------------------------------------------
    // Individual coals, each glowing at its own temperature and breathing at
    // its own rate. This is most of what makes the scene read as a campfire
    // rather than a flame floating in the dark.
    if (uCoal > 0.001) {
        vec2 cd = (vUv - uTouch) * uAspect;
        cd.y *= uCoalFlat;
        float bed = exp(-dot(cd, cd) / max(uCoalRadius * uCoalRadius, 1e-6));

        float grain = texture(uNoise, ap * 13.0 + vec2(0.31, 0.77)).g;
        float lump  = texture(uNoise, ap *  5.0 + vec2(ts * 0.015, ts * 0.010)).r;
        float glow = smoothstep(0.26, 0.88, grain * 0.55 + lump * 0.62);
        float breathe = 0.62 + 0.38 * sin(ts * (1.4 + grain * 6.0) + lump * 34.0);

        float c = bed * glow * breathe * uCoal;
        vec3 coalCol = blackbody(1000.0 + grain * 800.0 + lump * 420.0)
                     * vec3(1.0, 0.70, 0.38);
        col += coalCol * c * (1.0 - 0.55 * sm) * 1.7;
    }

    fragColor = vec4(col, 1.0);
}
