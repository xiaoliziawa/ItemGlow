package com.lirxowo.itemglow.mixin.client;

import com.lirxowo.itemglow.client.HeldItemOutlineRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {
    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Shadow
    @Final
    protected T handler;

    @Inject(method = "render", at = @At("RETURN"))
    private void itemglow$renderInventoryOutline(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HeldItemOutlineRenderer.renderInventoryOutline(context, this.handler, this.x, this.y, delta);
    }
}
