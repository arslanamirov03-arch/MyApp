// Semi-Lagrangian advection of the velocity field + all body forces:
// vorticity confinement, thermal buoyancy, ambient curl-noise turbulence,
// the finger impulse and the explosion shock.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uVelocity;
uniform sampler2D uCurl;
uniform sampler2D uFields;
uniform sampler2D uNoise;

uniform vec2  uTexel;
uniform vec2  uAspect;
uniform float uDt;
uniform float uTime;

uniform float uVorticity;
uniform float uBuoyancy;
uniform float uSootWeight;
uniform float uDamping;
uniform float uNoiseAmp;
uniform float uNoiseScale;

uniform vec2  uTouch;
uniform vec2  uTouchVel;
uniform float uTouchRadius;
uniform float uTouchOn;

uniform float uBlast;
uniform vec2  uBlastPos;
uniform float uBlastRadius;

void main() {
    vec2 vel = DECV(texture(uVelocity, vUv));
    vec2 coord = vUv - uDt * vel * uTexel;
    vec2 v = DECV(texture(uVelocity, coord));

    // --- vorticity confinement: put back the small eddies advection eats ----
    float cC = DECS(texture(uCurl, vUv));
    float cL = abs(DECS(texture(uCurl, vUv - vec2(uTexel.x, 0.0))));
    float cR = abs(DECS(texture(uCurl, vUv + vec2(uTexel.x, 0.0))));
    float cB = abs(DECS(texture(uCurl, vUv - vec2(0.0, uTexel.y))));
    float cT = abs(DECS(texture(uCurl, vUv + vec2(0.0, uTexel.y))));
    vec2 g = vec2(cR - cL, cT - cB) * 0.5;
    g /= (length(g) + 1e-4);
    v += vec2(g.y, -g.x) * cC * uVorticity * uDt;

    // --- buoyancy: hot gas rises, heavy soot sinks -------------------------
    vec4 f = texture(uFields, vUv);
    float temp = f.r;
    float soot = f.b;
    v.y += (uBuoyancy * temp - uSootWeight * soot) * uDt;

    // --- ambient turbulence, two evolving scales ---------------------------
    vec2 ap = vUv * uAspect;
    vec2 t1 = curlNoiseTex(uNoise, ap * uNoiseScale + vec2(uTime * 0.05, -uTime * 0.24), 0.00390625);
    vec2 t2 = curlNoiseTex(uNoise, ap * uNoiseScale * 2.7 + vec2(-uTime * 0.09, -uTime * 0.55), 0.00390625);
    float mask = smoothstep(0.01, 0.30, temp) + 0.25 * smoothstep(0.01, 0.5, soot);
    v += (t1 + t2 * 0.55) * uNoiseAmp * mask * uDt;

    // --- finger drag impulse ----------------------------------------------
    vec2 d = (vUv - uTouch) * uAspect;
    float r2 = max(uTouchRadius * uTouchRadius, 1e-6);
    v += uTouchVel * exp(-dot(d, d) / r2) * uTouchOn * uDt;

    // --- explosion shock ring ---------------------------------------------
    if (uBlast > 0.001) {
        vec2 bd = (vUv - uBlastPos) * uAspect;
        float bl = length(bd) + 1e-5;
        float x = (bl - uBlastRadius) / 0.12;
        // ragged front: a perfectly circular piston reads as fake
        float rn = texture(uNoise, vUv * uAspect * 5.0 + vec2(0.37, 0.11)).g;
        v += (bd / bl) * exp(-x * x) * uBlast * mix(0.42, 1.58, rn) * uDt;
    }

    v *= exp(-uDamping * uDt);

    // free-slip container walls
    vec2 e = smoothstep(vec2(0.0), uTexel * 3.0, vUv) *
             smoothstep(vec2(0.0), uTexel * 3.0, vec2(1.0) - vUv);
    v *= min(e.x, e.y);

    fragColor = ENCV(v);
}
