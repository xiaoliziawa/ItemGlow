package com.lirxowo.itemglow.mixin.client;

import com.lirxowo.itemglow.client.config.ItemGlowConfig;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Shadow
    public abstract ItemStack getStack();

    @Shadow
    public abstract boolean cannotPickup();

    @Inject(method = "onPlayerCollision", at = @At("HEAD"))
    private void itemglow$onPickup(PlayerEntity player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.getWorld().isClient) {
            return;
        }
        if (!ItemGlowConfig.INSTANCE.enabled || !ItemGlowConfig.INSTANCE.pickupParticles) {
            return;
        }
        if (cannotPickup()) {
            return;
        }

        ItemStack stack = getStack();
        if (stack.isEmpty()) {
            return;
        }

        if (player.getInventory().getOccupiedSlotWithRoomForStack(stack) == -1
                && player.getInventory().getEmptySlot() == -1) {
            return;
        }

        ItemGlowConfig.ItemFilterRule rule = ItemGlowConfig.INSTANCE.findItemFilter(stack);
        int color = (rule != null) ? rule.outlineColor : ItemGlowConfig.INSTANCE.outlineColor;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        Vec3d pos = self.getPos();
        var random = self.getWorld().random;
        DustParticleEffect particle = new DustParticleEffect(new Vector3f(r, g, b), 1.0F);

        for (int i = 0; i < 10; i++) {
            double vx = (random.nextDouble() - 0.5) * 0.5;
            double vy = random.nextDouble() * 0.3 + 0.1;
            double vz = (random.nextDouble() - 0.5) * 0.5;
            self.getWorld().addParticle(particle, pos.x, pos.y + 0.25, pos.z, vx, vy, vz);
        }
    }
}
