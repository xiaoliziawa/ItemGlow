#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 ScreenSize;
uniform vec4 GlowColor;
uniform vec4 GlowColor2;
uniform float OutlineWidth;
uniform float GlowStrength;
uniform int OutlineMode;
uniform int GradientEnabled;

in vec2 texCoord;

out vec4 fragColor;

float sampleMask(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.0), vec2(1.0))).a;
}

float sampleMaskPixelated(vec2 uv, vec2 pixelSize) {
    vec2 snapped = floor(uv / pixelSize) * pixelSize + pixelSize * 0.5;
    return texture(DiffuseSampler, clamp(snapped, vec2(0.0), vec2(1.0))).a;
}

void main() {
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));

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

    float glow = 0.0;

    if (OutlineMode == 3) {
        // PIXEL: snap to 4px grid before sampling
        vec2 pixelSize = vec2(4.0) / ScreenSize;
        float center = sampleMaskPixelated(texCoord, pixelSize);
        float innerEdge = 0.0;
        float outerEdge = 0.0;
        for (int i = 0; i < 8; i++) {
            innerEdge = max(innerEdge, sampleMaskPixelated(texCoord + offsets[i] * texel * OutlineWidth, pixelSize));
            outerEdge = max(outerEdge, sampleMaskPixelated(texCoord + offsets[i] * texel * OutlineWidth * 2.0, pixelSize));
        }
        float outline = max(innerEdge - center, 0.0);
        float halo = max(outerEdge - center, 0.0);
        glow = clamp(outline * 1.35 + halo * 0.65, 0.0, 1.0) * GlowColor.a;
    } else {
        float center = sampleMask(texCoord);
        float innerEdge = 0.0;
        float outerEdge = 0.0;
        for (int i = 0; i < 8; i++) {
            innerEdge = max(innerEdge, sampleMask(texCoord + offsets[i] * texel * OutlineWidth));
            outerEdge = max(outerEdge, sampleMask(texCoord + offsets[i] * texel * OutlineWidth * 2.0));
        }

        if (OutlineMode == 2) {
            // DOUBLE: two separate edge bands
            float farEdge = 0.0;
            for (int i = 0; i < 8; i++) {
                farEdge = max(farEdge, sampleMask(texCoord + offsets[i] * texel * OutlineWidth * 3.5));
            }
            float band1 = max(innerEdge - center, 0.0);
            float band2 = max(farEdge - outerEdge, 0.0);
            glow = clamp(band1 * 1.2 + band2 * 0.9, 0.0, 1.0) * GlowColor.a;
        } else {
            // SOLID or DASHED
            float outline = max(innerEdge - center, 0.0);
            float halo = max(outerEdge - center, 0.0);
            glow = clamp(outline * 1.35 + halo * 0.65, 0.0, 1.0) * GlowColor.a;
        }

        if (OutlineMode == 1) {
            // DASHED: create dash pattern along edge direction
            float dashPattern = step(0.5, fract(dot(texCoord * ScreenSize, vec2(0.7071, 0.7071)) / 8.0));
            glow *= dashPattern;
        }
    }

    if (glow <= 0.001) {
        discard;
    }

    // Apply gradient if enabled
    vec4 effectiveColor = GlowColor;
    if (GradientEnabled != 0) {
        effectiveColor = mix(GlowColor, GlowColor2, texCoord.y);
    }

    vec3 rgb = effectiveColor.rgb * glow * GlowStrength;
    fragColor = vec4(rgb, glow);
}
