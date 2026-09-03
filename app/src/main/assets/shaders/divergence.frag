in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uVelocity;
uniform vec2 uTexel;

void main() {
    float l = DECV(texture(uVelocity, vUv - vec2(uTexel.x, 0.0))).x;
    float r = DECV(texture(uVelocity, vUv + vec2(uTexel.x, 0.0))).x;
    float b = DECV(texture(uVelocity, vUv - vec2(0.0, uTexel.y))).y;
    float t = DECV(texture(uVelocity, vUv + vec2(0.0, uTexel.y))).y;
    fragColor = ENCS(0.5 * ((r - l) + (t - b)));
}
