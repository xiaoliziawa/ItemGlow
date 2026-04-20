package com.lirxowo.itemglow.client;

import com.lirxowo.itemglow.client.config.AnimationEngine;
import com.lirxowo.itemglow.client.config.BeamStyleMode;
import com.lirxowo.itemglow.client.config.ItemGlowConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class ItemBeamRenderer {

    private static final int SEGMENTS = 16;
    private static final int HEIGHT_SLICES = 7;
    private static final float ITEM_CENTER_Y_OFFSET = 0.25F;
    private static final float MIN_RADIUS = 0.01F;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private ItemBeamRenderer() {
    }

    public static void renderBeams(
            java.util.List<ItemEntity> entities,
            float tickDelta,
            Vec3d cameraPos
    ) {
        ItemGlowConfig config = ItemGlowConfig.INSTANCE;
        if (!config.itemBeam || entities.isEmpty()) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR
        );

        for (ItemEntity entity : entities) {
            double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - cameraPos.x;
            double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - cameraPos.y;
            double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - cameraPos.z;

            float bobOffset = MathHelper.sin((entity.getItemAge() + tickDelta) / 10.0F + entity.uniqueOffset) * 0.1F + 0.1F;

            ItemGlowConfig.ItemFilterRule rule = config.findItemFilter(entity.getStack());
            int colorInt = (rule != null) ? rule.outlineColor : config.outlineColor;
            float r = ColorHelper.Argb.getRed(colorInt) / 255.0F;
            float g = ColorHelper.Argb.getGreen(colorInt) / 255.0F;
            float b = ColorHelper.Argb.getBlue(colorInt) / 255.0F;

            float[] animColor = AnimationEngine.getColorForBase(config, r, g, b, 1.0F);
            float fx = (float) x;
            float fz = (float) z;
            float baseY = (float) y + bobOffset + ITEM_CENTER_Y_OFFSET;
            float animationTime = (entity.getItemAge() + tickDelta + entity.uniqueOffset * 10.0F) * 0.12F;

            renderBeam(
                    builder,
                    fx,
                    baseY,
                    fz,
                    config.beamHeight,
                    config.beamWidth,
                    config.beamAlpha,
                    config.beamStyle,
                    animationTime,
                    animColor[0],
                    animColor[1],
                    animColor[2]
            );
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void renderBeam(
            BufferBuilder builder,
            float centerX,
            float baseY,
            float centerZ,
            float height,
            float width,
            float maxAlpha,
            BeamStyleMode style,
            float animationTime,
            float red,
            float green,
            float blue
    ) {
        BeamLayerSample[] samples = new BeamLayerSample[HEIGHT_SLICES + 1];
        for (int layer = 0; layer <= HEIGHT_SLICES; layer++) {
            float progress = layer / (float) HEIGHT_SLICES;
            samples[layer] = sampleLayer(style, progress, width, maxAlpha, animationTime);
        }

        for (int layer = 0; layer < HEIGHT_SLICES; layer++) {
            BeamLayerSample lower = samples[layer];
            BeamLayerSample upper = samples[layer + 1];
            if (lower.alpha <= 0.0F && upper.alpha <= 0.0F) {
                continue;
            }

            float y0 = baseY + height * lower.progress;
            float y1 = baseY + height * upper.progress;
            int color0 = colorArgb(lower.alpha, red, green, blue);
            int color1 = colorArgb(upper.alpha, red, green, blue);

            for (int segment = 0; segment < SEGMENTS; segment++) {
                float angle0 = TWO_PI * segment / SEGMENTS;
                float angle1 = TWO_PI * (segment + 1) / SEGMENTS;

                float x00 = centerX + MathHelper.cos(angle0 + lower.rotation) * lower.radius;
                float z00 = centerZ + MathHelper.sin(angle0 + lower.rotation) * lower.radius;
                float x01 = centerX + MathHelper.cos(angle1 + lower.rotation) * lower.radius;
                float z01 = centerZ + MathHelper.sin(angle1 + lower.rotation) * lower.radius;

                float x10 = centerX + MathHelper.cos(angle0 + upper.rotation) * upper.radius;
                float z10 = centerZ + MathHelper.sin(angle0 + upper.rotation) * upper.radius;
                float x11 = centerX + MathHelper.cos(angle1 + upper.rotation) * upper.radius;
                float z11 = centerZ + MathHelper.sin(angle1 + upper.rotation) * upper.radius;

                builder.vertex(x00, y0, z00).color(color0);
                builder.vertex(x01, y0, z01).color(color0);
                builder.vertex(x11, y1, z11).color(color1);
                builder.vertex(x10, y1, z10).color(color1);
            }
        }
    }

    private static BeamLayerSample sampleLayer(
            BeamStyleMode style,
            float progress,
            float width,
            float maxAlpha,
            float animationTime
    ) {
        float radiusMultiplier = 1.0F;
        float alphaMultiplier = 1.0F - progress;
        float rotation = 0.0F;

        switch (style) {
            case TAPERED -> {
                radiusMultiplier = MathHelper.lerp(progress, 1.0F, 0.18F);
                alphaMultiplier = 1.0F - progress * progress;
            }
            case TWISTED -> {
                radiusMultiplier = MathHelper.lerp(progress, 1.0F, 0.58F);
                alphaMultiplier = (1.0F - progress) * 0.95F;
                rotation = animationTime * 0.55F + progress * 3.6F;
            }
            case PULSE -> {
                float wave = MathHelper.sin(progress * 8.0F - animationTime * 2.4F);
                radiusMultiplier = (0.78F + (wave * 0.16F)) * MathHelper.lerp(progress, 1.0F, 0.42F);
                alphaMultiplier = (1.0F - progress) * (0.82F + (wave + 1.0F) * 0.09F);
            }
            case STRAIGHT -> {
            }
        }

        float radius = Math.max(width * Math.max(radiusMultiplier, 0.15F), MIN_RADIUS);
        float alpha = MathHelper.clamp(maxAlpha * Math.max(alphaMultiplier, 0.0F), 0.0F, 1.0F);
        return new BeamLayerSample(progress, radius, alpha, rotation);
    }

    private static int colorArgb(float alpha, float r, float g, float b) {
        return ColorHelper.Argb.getArgb(
                Math.round(alpha * 255.0F),
                Math.round(r * 255.0F),
                Math.round(g * 255.0F),
                Math.round(b * 255.0F)
        );
    }

    private static final class BeamLayerSample {
        private final float progress;
        private final float radius;
        private final float alpha;
        private final float rotation;

        private BeamLayerSample(float progress, float radius, float alpha, float rotation) {
            this.progress = progress;
            this.radius = radius;
            this.alpha = alpha;
            this.rotation = rotation;
        }
    }
}
