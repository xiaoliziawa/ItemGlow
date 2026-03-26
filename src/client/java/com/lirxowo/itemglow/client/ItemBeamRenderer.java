package com.lirxowo.itemglow.client;

import com.lirxowo.itemglow.client.config.AnimationEngine;
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

    private static final int SEGMENTS = 12;
    private static final float RADIUS = 0.12F;
    private static final float ITEM_CENTER_Y_OFFSET = 0.25F;

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

        float height = config.beamHeight;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR
        );

        float[] sinTable = new float[SEGMENTS + 1];
        float[] cosTable = new float[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = (float) (i * 2.0 * Math.PI / SEGMENTS);
            sinTable[i] = MathHelper.sin(angle);
            cosTable[i] = MathHelper.cos(angle);
        }

        for (ItemEntity entity : entities) {
            double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - cameraPos.x;
            double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - cameraPos.y;
            double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - cameraPos.z;

            // Match the vanilla bob animation: sin((age + tickDelta) / 10 + uniqueOffset) * 0.1 + 0.1
            float bobOffset = MathHelper.sin((entity.getItemAge() + tickDelta) / 10.0F + entity.uniqueOffset) * 0.1F + 0.1F;

            ItemGlowConfig.ItemFilterRule rule = config.findItemFilter(entity.getStack());
            int colorInt = (rule != null) ? rule.outlineColor : config.outlineColor;
            float r = ColorHelper.Argb.getRed(colorInt) / 255.0F;
            float g = ColorHelper.Argb.getGreen(colorInt) / 255.0F;
            float b = ColorHelper.Argb.getBlue(colorInt) / 255.0F;

            float[] animColor = AnimationEngine.getColorForBase(config, r, g, b, 1.0F);
            r = animColor[0];
            g = animColor[1];
            b = animColor[2];

            float fx = (float) x;
            float baseY = (float) y + bobOffset + ITEM_CENTER_Y_OFFSET;
            float fz = (float) z;
            float topY = baseY + height;

            int bottomColor = colorArgb(0.45F, r, g, b);
            int midColor = colorArgb(0.25F, r, g, b);
            int topColor = colorArgb(0.0F, r, g, b);

            float midY = baseY + height * 0.3F;

            for (int i = 0; i < SEGMENTS; i++) {
                float x0 = fx + cosTable[i] * RADIUS;
                float z0 = fz + sinTable[i] * RADIUS;
                float x1 = fx + cosTable[i + 1] * RADIUS;
                float z1 = fz + sinTable[i + 1] * RADIUS;

                // Bottom segment: baseY -> midY (brighter)
                builder.vertex(x0, baseY, z0).color(bottomColor);
                builder.vertex(x1, baseY, z1).color(bottomColor);
                builder.vertex(x1, midY, z1).color(midColor);
                builder.vertex(x0, midY, z0).color(midColor);

                // Top segment: midY -> topY (fades out)
                builder.vertex(x0, midY, z0).color(midColor);
                builder.vertex(x1, midY, z1).color(midColor);
                builder.vertex(x1, topY, z1).color(topColor);
                builder.vertex(x0, topY, z0).color(topColor);
            }
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static int colorArgb(float alpha, float r, float g, float b) {
        return ColorHelper.Argb.getArgb(
                Math.round(alpha * 255.0F),
                Math.round(r * 255.0F),
                Math.round(g * 255.0F),
                Math.round(b * 255.0F)
        );
    }
}
