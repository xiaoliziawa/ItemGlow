package com.lirxowo.itemglow.client;

import net.fabricmc.api.ClientModInitializer;

public class ItemglowClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OutlineShaderRegistry.register();
        HeldItemOutlineRenderer.initialize();
    }
}
