// Each lightning segment is expanded on the CPU into a wide quad; aSide runs
// from -1 to +1 across it so the fragment shader can build a thin core inside a
// soft halo.
layout(location = 0) in vec2 aPos;     // normalised device coordinates
layout(location = 1) in vec2 aSideBright;  // x = side (-1..1), y = per-vertex gain

out float vSide;
out float vBright;

void main() {
    vSide = aSideBright.x;
    vBright = aSideBright.y;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
