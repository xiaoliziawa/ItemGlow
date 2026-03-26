package com.lirxowo.itemglow.client.config;

public enum OutlineStyleMode {
    SOLID,
    DASHED,
    DOUBLE,
    PIXEL;

    public String getTranslationKey() {
        return "itemglow.outline_style." + name().toLowerCase();
    }
}
