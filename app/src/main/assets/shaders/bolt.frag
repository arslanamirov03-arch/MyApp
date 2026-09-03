in float vSide;
in float vBright;
out vec4 fragColor;

uniform float uBright;     // whole-bolt envelope (return strokes, flicker)
uniform float uCoreFrac;   // where the white channel ends, in side units
uniform vec3  uCoreColor;
uniform vec3  uMidColor;
uniform vec3  uHaloColor;

void main() {
    float d = abs(vSide);
    float c = d / max(uCoreFrac, 1e-3);

    // Three zones, the way a real channel photographs: a white-hot core only a
    // few pixels across, a vivid blue sheath of ionised air around it, and a
    // wide violet corona fading into the dark.
    float core = exp(-c * c * 3.2);
    float mid  = exp(-d * d * 5.5);
    float halo = exp(-d * d * 1.9);

    // The core has to stay narrow and only just clip: push it harder and the
    // blue sheath around it is tonemapped away and the whole bolt reads white.
    vec3 col = uHaloColor * halo * 0.60
             + uMidColor  * mid  * 2.40
             + uCoreColor * core * 3.20;
    fragColor = vec4(col * vBright * uBright, 1.0);
}
