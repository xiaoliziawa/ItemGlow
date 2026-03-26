package com.lirxowo.itemglow.client;

import com.lirxowo.itemglow.client.config.ItemGlowConfig;
import com.lirxowo.itemglow.client.config.ItemGlowConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ItemglowClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ItemGlowConfig.load();
        OutlineShaderRegistry.register();
        HeldItemOutlineRenderer.initialize();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("itemglow").executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.send(() -> client.setScreen(ItemGlowConfigScreen.create(client.currentScreen)));
                    return 1;
                }))
        );
    }
}
