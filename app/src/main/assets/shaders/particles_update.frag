// Ember state texture: rg = position (uv), b = life, a = seed.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uState;
uniform sampler2D uVelocity;
uniform sampler2D uFields;

uniform vec2  uTexel;
uniform vec2  uAspect;
uniform float uDt;
uniform float uTime;

uniform vec2  uSpawn;
uniform float uSpawnRadius;
uniform float uSpawnRate;
uniform float uIntensity;

void main() {
    vec4 s = texture(uState, vUv);
    vec2 pos = s.xy;
    float life = s.z;
    float seed = s.w;

    if (life <= 0.0) {
        float rnd = hash11(seed * 71.13 + vUv.x * 311.7 + vUv.y * 97.31 + floor(uTime * 60.0) * 0.0173);
        if (rnd < uSpawnRate * uDt) {
            float a = hash11(seed * 3.19 + uTime * 1.7) * 6.28318530718;
            float rr = sqrt(hash11(seed * 7.71 + uTime * 2.3)) * uSpawnRadius;
            pos = uSpawn + vec2(cos(a), sin(a)) * rr / uAspect;
            life = 0.45 + 0.55 * hash11(seed * 5.13 + uTime * 0.77);
            seed = fract(seed + 0.61803398875);
        }
    } else {
        vec2 v = DECV(texture(uVelocity, pos));
        pos += v * uTexel * uDt;

        float w = hash11(seed * 13.37);
        // embers are lighter than the gas: extra lift plus a lateral wobble
        pos.y += (0.06 + 0.26 * w) * uDt * (0.35 + uIntensity);
        pos.x += sin(uTime * (1.7 + 4.3 * w) + seed * 21.0) * 0.055 * uDt;

        life -= uDt * (0.16 + 0.30 * w);
        if (pos.y > 1.03 || pos.y < -0.03 || pos.x < -0.05 || pos.x > 1.05) {
            life = 0.0;
        }
    }

    fragColor = vec4(pos, life, seed);
}
