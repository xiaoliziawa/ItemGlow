package com.lirxowo.itemglow.client.config;

public enum BeamStyleMode {
    STRAIGHT,
    TAPERED,
    TWISTED,
    PULSE;

    public String getTranslationKey() {
        return "itemglow.beam_style." + name().toLowerCase();
    }
}
