uniform sampler2D uState;
uniform int   uTexSize;
uniform float uPointScale;

out float vLife;
out float vSeed;

void main() {
    int id = gl_VertexID;
    ivec2 tc = ivec2(id % uTexSize, id / uTexSize);
    vec4 s = texelFetch(uState, tc, 0);

    vLife = s.z;
    vSeed = s.w;

    if (s.z <= 0.0) {
        gl_Position = vec4(4.0, 4.0, 4.0, 1.0);
        gl_PointSize = 1.0;
        return;
    }

    gl_Position = vec4(s.xy * 2.0 - 1.0, 0.0, 1.0);
    float f = fract(s.w * 7.31);
    gl_PointSize = max(uPointScale * (0.30 + 0.85 * f) * clamp(s.z, 0.15, 1.0), 1.0);
}
