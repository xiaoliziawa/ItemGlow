package com.lirxowo.itemglow.client.config;

public final class AnimationEngine {
    private static long startTimeMs = System.currentTimeMillis();

    private AnimationEngine() {
    }

    private static float time() {
        return (System.currentTimeMillis() - startTimeMs) / 1000.0F;
    }

    public static float[] getColor(ItemGlowConfig config) {
        return getColorForBase(config, config.getRed(), config.getGreen(), config.getBlue(), config.getAlpha());
    }

    public static float[] getColorForBase(ItemGlowConfig config, float r, float g, float b, float a) {
        float t = time() * config.animationSpeed;

        switch (config.animationMode) {
            case BREATHING -> {
                float breath = (float) (Math.sin(t * 2.0) * 0.35 + 0.65);
                a *= breath;
            }
            case RAINBOW -> {
                float hue = (t * 0.5F) % 1.0F;
                float[] rgb = hsvToRgb(hue, 0.9F, 1.0F);
                r = rgb[0];
                g = rgb[1];
                b = rgb[2];
            }
            case FLICKER -> {
                float flicker = Math.sin(t * 12.0F) > 0 ? 1.0F : 0.2F;
                a *= flicker;
            }
            case COLOR_SHIFT -> {
                float shift = (float) (Math.sin(t * 1.5) * 0.5 + 0.5);
                float compR = 1.0F - r;
                float compG = 1.0F - g;
                float compB = 1.0F - b;
                r = r + (compR - r) * shift;
                g = g + (compG - g) * shift;
                b = b + (compB - b) * shift;
            }
            case WAVE -> {
                float wave = (float) (Math.sin(t * 3.0) * 0.3 + 0.7);
                r *= wave;
                g *= wave;
                b *= wave;
            }
            default -> {
            }
        }

        return new float[]{r, g, b, a};
    }

    public static float getOutlineWidth(ItemGlowConfig config) {
        float base = config.outlineWidth;
        if (config.animationMode == AnimationMode.PULSE) {
            float t = time() * config.animationSpeed;
            float pulse = (float) (Math.sin(t * 3.0) * 0.25 + 1.25);
            return base * pulse;
        }
        return base;
    }

    public static float getGlowStrength(ItemGlowConfig config) {
        float base = config.glowStrength;
        if (config.animationMode == AnimationMode.WAVE) {
            float t = time() * config.animationSpeed;
            float wave = (float) (Math.sin(t * 3.0) * 0.5 + 1.0);
            return base * wave;
        }
        return base;
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6.0F) % 6;
        float f = h * 6.0F - i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        return switch (i) {
            case 0 -> new float[]{v, t, p};
            case 1 -> new float[]{q, v, p};
            case 2 -> new float[]{p, v, t};
            case 3 -> new float[]{p, q, v};
            case 4 -> new float[]{t, p, v};
            default -> new float[]{v, p, q};
        };
    }
}
