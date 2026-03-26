package com.lirxowo.itemglow.mixin.client;

import com.lirxowo.itemglow.client.HeldItemOutlineRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Camera camera;

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void itemglow$beginHeldItemOutline(Camera camera, float tickDelta, Matrix4f positionMatrix, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginFrame();
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void itemglow$renderHeldItemOutline(Camera camera, float tickDelta, Matrix4f positionMatrix, CallbackInfo ci) {
        HeldItemOutlineRenderer.renderAfterHand(tickDelta, positionMatrix);
    }

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void itemglow$renderWorldItemOutlines(RenderTickCounter tickCounter, CallbackInfo ci) {
        HeldItemOutlineRenderer.renderWorldItemOutlines(tickCounter.getTickDelta(false), this.camera);
    }
}
