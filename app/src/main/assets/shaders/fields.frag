// Transport + chemistry of the burning gas.
//   r = temperature   g = unburnt fuel   b = soot / smoke
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uVelocity;
uniform sampler2D uFields;
uniform sampler2D uNoise;

uniform vec2  uTexel;
uniform vec2  uAspect;
uniform float uDt;
uniform float uTime;

uniform float uTempDiss;
uniform float uFuelDiss;
uniform float uSootDiss;
uniform float uCooling;
uniform float uBurnRate;
uniform float uHeatRelease;
uniform float uSootYield;
uniform float uIgnition;

uniform vec2  uTouch;
uniform float uTouchOn;
uniform float uTouchRadius;
uniform float uInjectFuel;
uniform float uInjectHeat;

uniform float uBlastHeat;
uniform vec2  uBlastPos;
uniform float uBlastRadius;

void main() {
    vec2 vel = DECV(texture(uVelocity, vUv));
    vec4 f = texture(uFields, vUv - uDt * vel * uTexel);

    float temp = f.r;
    float fuel = f.g;
    float soot = f.b;

    // --- fuel injected under the finger ------------------------------------
    vec2 ap = vUv * uAspect;
    vec2 d = (vUv - uTouch) * uAspect;
    float r2 = max(uTouchRadius * uTouchRadius, 1e-6);
    float g = exp(-dot(d, d) / r2);
    // ragged, flickering nozzle instead of a perfect disc
    float nz = texture(uNoise, ap * 7.0 + vec2(uTime * 0.11, -uTime * 0.75)).b;
    float nz2 = texture(uNoise, ap * 17.0 + vec2(-uTime * 0.07, -uTime * 1.4)).g;
    g *= mix(0.45, 1.55, nz) * mix(0.7, 1.3, nz2);

    fuel += g * uInjectFuel * uTouchOn * uDt;
    temp += g * uInjectHeat * uTouchOn * uDt;

    // --- explosion: a lumpy fuel cloud that ignites -------------------------
    // Dumping temperature directly saturates the whole field into a flat white
    // sheet, so the blast mostly injects fuel through a gappy noise mask and
    // lets combustion carve the fireball out of it.
    if (uBlastHeat > 0.0) {
        vec2 bd = (vUv - uBlastPos) * uAspect;
        float br2 = max(uBlastRadius * uBlastRadius, 1e-6);
        float bnz = texture(uNoise, ap * 3.0 + vec2(0.13, 0.61)).r * 0.62
                  + texture(uNoise, ap * 7.5 + vec2(0.71, 0.29)).g * 0.38;
        float bg = exp(-dot(bd, bd) / br2) * smoothstep(0.18, 0.82, bnz);
        fuel += bg * uBlastHeat * uDt * 1.6;
        temp += bg * uBlastHeat * uDt * 0.30;
    }

    // --- combustion: first-order reaction, so rich pockets burn from outside in
    float ign = smoothstep(uIgnition, uIgnition + 0.06, temp);
    float burn = fuel * (1.0 - exp(-uBurnRate * uDt)) * ign;
    fuel -= burn;
    temp += burn * uHeatRelease;
    soot += burn * uSootYield;

    // --- radiative cooling + dissipation ------------------------------------
    temp -= uCooling * uDt * temp * temp;
    temp *= exp(-uTempDiss * uDt);
    fuel *= exp(-uFuelDiss * uDt);
    soot *= exp(-uSootDiss * uDt);

    fragColor = clamp(vec4(temp, fuel, soot, 0.0), 0.0, 1.0);
}
