in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uTex;
uniform vec2 uTexel;   // 1 / source size

void main() {
    vec3 s = texture(uTex, vUv + vec2(-1.0, -1.0) * uTexel).rgb
           + texture(uTex, vUv + vec2( 1.0, -1.0) * uTexel).rgb
           + texture(uTex, vUv + vec2(-1.0,  1.0) * uTexel).rgb
           + texture(uTex, vUv + vec2( 1.0,  1.0) * uTexel).rgb;
    fragColor = vec4(s * 0.25, 1.0);
}
