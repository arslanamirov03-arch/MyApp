// Subtract the pressure gradient -> divergence-free velocity field.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uPressure;
uniform sampler2D uVelocity;
uniform vec2 uTexel;

void main() {
    float l = DECS(texture(uPressure, vUv - vec2(uTexel.x, 0.0)));
    float r = DECS(texture(uPressure, vUv + vec2(uTexel.x, 0.0)));
    float b = DECS(texture(uPressure, vUv - vec2(0.0, uTexel.y)));
    float t = DECS(texture(uPressure, vUv + vec2(0.0, uTexel.y)));
    vec2 v = DECV(texture(uVelocity, vUv));
    v -= vec2(r - l, t - b) * 0.5;

    vec2 e = smoothstep(vec2(0.0), uTexel * 3.0, vUv) *
             smoothstep(vec2(0.0), uTexel * 3.0, vec2(1.0) - vUv);
    v *= min(e.x, e.y);

    fragColor = ENCV(v);
}
