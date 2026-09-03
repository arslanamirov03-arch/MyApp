in float vLife;
in float vSeed;
out vec4 fragColor;

uniform float uIntensity;

void main() {
    vec2 c = gl_PointCoord * 2.0 - 1.0;
    float d2 = dot(c, c);
    if (d2 > 1.0) discard;

    float a = exp(-d2 * 3.2);
    float heat = clamp(vLife, 0.0, 1.0);
    heat *= mix(0.7, 1.25, fract(vSeed * 3.77));

    vec3 col = blackbody(950.0 + heat * 1750.0);
    fragColor = vec4(col * a * (0.10 + 0.95 * heat * heat) * (0.45 + 0.9 * uIntensity), 1.0);
}
