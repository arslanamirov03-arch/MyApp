// ---------------------------------------------------------------------------
// Common prelude injected into every program (after #version and #defines).
// ---------------------------------------------------------------------------
precision highp float;
precision highp int;
precision highp sampler2D;

#if LOWPREC == 1
// Fallback path for GPUs without renderable float targets: signed quantities are
// stored biased into an RGBA8 target.
#define VSCALE 220.0
#define DECV(t) ((((t).xy) * 2.0 - 1.0) * VSCALE)
#define ENCV(v) vec4((v) / VSCALE * 0.5 + 0.5, 0.0, 1.0)
#define DECS(t) ((((t).x) * 2.0 - 1.0) * VSCALE)
#define ENCS(s) vec4((s) / VSCALE * 0.5 + 0.5, 0.0, 0.0, 1.0)
#else
#define DECV(t) ((t).xy)
#define ENCV(v) vec4((v), 0.0, 1.0)
#define DECS(t) ((t).x)
#define ENCS(s) vec4((s), 0.0, 0.0, 1.0)
#endif

// Divergence-free 2D turbulence from the curl of a scalar noise potential.
// The potential lives in the red channel of a tiling fBm texture.
vec2 curlNoiseTex(sampler2D nz, vec2 p, float e) {
    float n1 = texture(nz, p + vec2(0.0, e)).r;
    float n2 = texture(nz, p - vec2(0.0, e)).r;
    float n3 = texture(nz, p + vec2(e, 0.0)).r;
    float n4 = texture(nz, p - vec2(e, 0.0)).r;
    return vec2(n1 - n2, n4 - n3);
}

// Planck blackbody radiator colour, Kelvin -> linear-ish RGB.
// (Tanner Helland / Neil Bartlett approximation.)
vec3 blackbody(float kelvin) {
    float k = clamp(kelvin, 1000.0, 20000.0) / 100.0;
    float r, g, b;
    if (k <= 66.0) {
        r = 255.0;
        g = 99.4708025861 * log(k) - 161.1195681661;
    } else {
        r = 329.698727446 * pow(k - 60.0, -0.1332047592);
        g = 288.1221695283 * pow(k - 60.0, -0.0755148492);
    }
    if (k >= 66.0) {
        b = 255.0;
    } else if (k <= 19.0) {
        b = 0.0;
    } else {
        b = 138.5177312231 * log(k - 10.0) - 305.0447927307;
    }
    return clamp(vec3(r, g, b) / 255.0, 0.0, 1.0);
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}
