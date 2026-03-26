package com.lirxowo.itemglow.client.config;

public enum AnimationMode {
    NONE,
    BREATHING,
    RAINBOW,
    FLICKER,
    PULSE,
    WAVE,
    COLOR_SHIFT;

    public String getTranslationKey() {
        return "itemglow.animation." + name().toLowerCase();
    }
}
