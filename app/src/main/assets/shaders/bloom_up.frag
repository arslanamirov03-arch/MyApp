// 3x3 tent upsample, blended additively onto the next-larger mip.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uTex;
uniform vec2 uTexel;   // 1 / source size
uniform float uRadius;

void main() {
    vec2 o = uTexel * uRadius;
    vec3 s = texture(uTex, vUv + vec2(-o.x, -o.y)).rgb
           + texture(uTex, vUv + vec2( 0.0, -o.y)).rgb * 2.0
           + texture(uTex, vUv + vec2( o.x, -o.y)).rgb
           + texture(uTex, vUv + vec2(-o.x,  0.0)).rgb * 2.0
           + texture(uTex, vUv).rgb * 4.0
           + texture(uTex, vUv + vec2( o.x,  0.0)).rgb * 2.0
           + texture(uTex, vUv + vec2(-o.x,  o.y)).rgb
           + texture(uTex, vUv + vec2( 0.0,  o.y)).rgb * 2.0
           + texture(uTex, vUv + vec2( o.x,  o.y)).rgb;
    fragColor = vec4(s * (1.0 / 16.0), 1.0);
}
