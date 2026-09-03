in float vSide;
in float vBright;
out vec4 fragColor;

uniform float uBright;     // whole-bolt envelope (return strokes, flicker)
uniform float uCoreFrac;   // where the white channel ends, in side units
uniform vec3  uCoreColor;
uniform vec3  uHaloColor;

void main() {
    float d = abs(vSide);
    float c = d / max(uCoreFrac, 1e-3);
    float core = exp(-c * c * 3.2);
    float halo = exp(-d * d * 2.6);

    vec3 col = uHaloColor * halo * 0.55 + uCoreColor * core * 7.0;
    fragColor = vec4(col * vBright * uBright, 1.0);
}
