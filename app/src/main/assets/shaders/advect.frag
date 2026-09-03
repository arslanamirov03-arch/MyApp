// Plain semi-Lagrangian advection. Run forward then backward, it gives the two
// estimates the MacCormack correction in fields.frag needs.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uVelocity;
uniform sampler2D uSource;
uniform vec2 uTexel;
uniform float uDt;

void main() {
    vec2 vel = DECV(texture(uVelocity, vUv));
    fragColor = texture(uSource, vUv - uDt * vel * uTexel);
}
