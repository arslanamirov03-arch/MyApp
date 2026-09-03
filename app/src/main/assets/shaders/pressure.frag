// One Jacobi relaxation step of the pressure Poisson equation.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uPressure;
uniform sampler2D uDivergence;
uniform vec2 uTexel;

void main() {
    float l = DECS(texture(uPressure, vUv - vec2(uTexel.x, 0.0)));
    float r = DECS(texture(uPressure, vUv + vec2(uTexel.x, 0.0)));
    float b = DECS(texture(uPressure, vUv - vec2(0.0, uTexel.y)));
    float t = DECS(texture(uPressure, vUv + vec2(0.0, uTexel.y)));
    float div = DECS(texture(uDivergence, vUv));
    fragColor = ENCS((l + r + b + t - div) * 0.25);
}
