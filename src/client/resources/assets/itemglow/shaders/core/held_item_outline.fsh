#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 ScreenSize;
uniform vec4 GlowColor;
uniform float OutlineWidth;
uniform float GlowStrength;

in vec2 texCoord;

out vec4 fragColor;

float sampleMask(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.0), vec2(1.0))).a;
}

void main() {
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    float center = sampleMask(texCoord);

    vec2 offsets[8] = vec2[](
        vec2(1.0, 0.0),
        vec2(-1.0, 0.0),
        vec2(0.0, 1.0),
        vec2(0.0, -1.0),
        vec2(0.7071, 0.7071),
        vec2(-0.7071, 0.7071),
        vec2(0.7071, -0.7071),
        vec2(-0.7071, -0.7071)
    );

    float innerEdge = 0.0;
    float outerEdge = 0.0;
    for (int i = 0; i < 8; i++) {
        innerEdge = max(innerEdge, sampleMask(texCoord + offsets[i] * texel * OutlineWidth));
        outerEdge = max(outerEdge, sampleMask(texCoord + offsets[i] * texel * OutlineWidth * 2.0));
    }

    float outline = max(innerEdge - center, 0.0);
    float halo = max(outerEdge - center, 0.0);
    float glow = clamp(outline * 1.35 + halo * 0.65, 0.0, 1.0) * GlowColor.a;

    if (glow <= 0.001) {
        discard;
    }

    vec3 rgb = GlowColor.rgb * glow * GlowStrength;
    fragColor = vec4(rgb, glow);
}
