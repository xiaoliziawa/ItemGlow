package com.lirxowo.itemglow.mixin.client;

import com.lirxowo.itemglow.client.HeldItemOutlineRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD")
    )
    private void itemglow$captureItemEntity(
            ItemEntity entity, float yaw, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, CallbackInfo ci
    ) {
        HeldItemOutlineRenderer.captureItemEntity(entity, light);
    }
}
