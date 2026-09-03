// Storm backdrop: a churning cloud deck across the top of the screen, lit from
// inside by the live bolts, plus the ambient glow that builds as the storm does.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uNoise;

uniform vec2  uAspect;
uniform float uTime;
uniform float uIntensity;
uniform float uAmbient;     // sky glow from the storm as a whole
uniform float uCloudBase;   // how far down the deck reaches

// xy = position in uv, z = power. Unused slots carry z = 0.
uniform vec3 uLights[6];

void main() {
    vec2 ap = vUv * uAspect;

    // --- cloud deck ---------------------------------------------------------
    float deck = smoothstep(uCloudBase - 0.32, 1.05, vUv.y);
    float n1 = texture(uNoise, ap * 1.30 + vec2(uTime * 0.011, uTime * 0.004)).r;
    float n2 = texture(uNoise, ap * 3.10 + vec2(-uTime * 0.019, uTime * 0.008)).g;
    float n3 = texture(uNoise, ap * 7.40 + vec2(uTime * 0.027, -uTime * 0.012)).b;
    float n = n1 * 0.55 + n2 * 0.30 + n3 * 0.15;
    float dens = smoothstep(0.30, 0.86, n) * deck;

    // --- light reaching the cloud from the bolts ------------------------------
    float lit = uAmbient;
    float ground = 0.0;
    float spot = 0.0;
    // clouds are lit by the discharges themselves, so the sum is capped rather
    // than allowed to wash the frame out when several fire at once
    for (int i = 0; i < 6; i++) {
        vec2 d = (vUv - uLights[i].xy) * uAspect;
        float r2 = dot(d, d);
        lit += uLights[i].z * 0.42 / (1.0 + r2 * 34.0);
        ground += uLights[i].z * 0.5 / (1.0 + r2 * 120.0);
        spot += uLights[i].z / (1.0 + r2 * 1400.0);
    }

    vec3 deepCloud = vec3(0.030, 0.045, 0.085);
    vec3 litCloud = vec3(0.42, 0.58, 0.98);
    lit = min(lit, 1.35);
    vec3 col = mix(deepCloud, litCloud, dens) * dens * lit;

    // faint glow where the bolts touch down, and a hint of wash over the frame
    col += vec3(0.30, 0.48, 1.00) * min(ground, 1.6) * 0.045;
    // the air right at the contact point glows hard
    col += vec3(0.62, 0.80, 1.00) * min(spot, 2.4) * 0.32;
    col += vec3(0.04, 0.07, 0.16) * uAmbient * (0.20 + 0.80 * uIntensity);

    fragColor = vec4(col, 1.0);
}
