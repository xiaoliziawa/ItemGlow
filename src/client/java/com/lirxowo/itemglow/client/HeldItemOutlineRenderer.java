package com.lirxowo.itemglow.client;

import com.lirxowo.itemglow.client.config.AnimationEngine;
import com.lirxowo.itemglow.client.config.ItemGlowConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Arm;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL30.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_NEAREST;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glClear;

public final class HeldItemOutlineRenderer {
    private static final int HOTBAR_SIZE = 9;
    private static final OutlineStyle GLOBAL_STYLE = new OutlineStyle(true, 0);

    private static SimpleFramebuffer maskTarget;

    private static final List<CapturedItemRender> CAPTURED_RENDERS = new ArrayList<>();
    private static final List<CapturedItemRender> CAPTURED_WORLD_ITEM_RENDERS = new ArrayList<>();
    private static final List<CapturedItemEntity> CAPTURED_ITEM_ENTITIES = new ArrayList<>();

    private static boolean initialized;
    private static boolean renderingMask;

    private static Matrix4f capturedHandProjection;
    private static Matrix4f capturedHandModelView;
    private static Matrix4f capturedWorldProjection;
    private static Matrix4f capturedWorldModelView;

    private HeldItemOutlineRenderer() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        HudRenderCallback.EVENT.register(HeldItemOutlineRenderer::renderHotbarOutline);
    }

    private static boolean isReady() {
        return initialized && OutlineShaderRegistry.getShader() != null;
    }

    private static void ensureMaskTarget(Framebuffer mainTarget) {
        if (maskTarget == null) {
            maskTarget = new SimpleFramebuffer(mainTarget.textureWidth, mainTarget.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
        } else if (maskTarget.textureWidth != mainTarget.textureWidth || maskTarget.textureHeight != mainTarget.textureHeight) {
            maskTarget.resize(mainTarget.textureWidth, mainTarget.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
        }
    }

    private static void beginMaskPass() {
        MinecraftClient client = MinecraftClient.getInstance();
        ensureMaskTarget(client.getFramebuffer());
        maskTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        maskTarget.clear(MinecraftClient.IS_SYSTEM_MAC);
        maskTarget.beginWrite(true);
    }

    private static void copyDepthFromMain() {
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        if (main == null || maskTarget == null) {
            return;
        }

        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, maskTarget.fbo);
        GlStateManager._glBlitFrameBuffer(
                0, 0, main.textureWidth, main.textureHeight,
                0, 0, maskTarget.textureWidth, maskTarget.textureHeight,
                GL_DEPTH_BUFFER_BIT, GL_NEAREST
        );
        maskTarget.beginWrite(false);
    }

    private static void composite(OutlineStyle style) {
        ShaderProgram shader = OutlineShaderRegistry.getShader();
        if (shader == null || maskTarget == null) {
            return;
        }

        ItemGlowConfig config = ItemGlowConfig.INSTANCE;
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer mainTarget = client.getFramebuffer();

        mainTarget.beginWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        shader.addSampler("DiffuseSampler", maskTarget.getColorAttachment());
        shader.addSampler("DepthSampler", maskTarget.getDepthAttachment());

        GlUniform screenSize = shader.getUniform("ScreenSize");
        if (screenSize != null) {
            screenSize.set((float) mainTarget.textureWidth, (float) mainTarget.textureHeight);
        }

        float[] baseColor = unpackColor(style.color);
        float[] color = style.useGlobalColor
                ? AnimationEngine.getColor(config)
                : AnimationEngine.getColorForBase(config, baseColor[0], baseColor[1], baseColor[2], baseColor[3]);
        GlUniform glowColor = shader.getUniform("GlowColor");
        if (glowColor != null) {
            glowColor.set(color[0], color[1], color[2], color[3]);
        }

        GlUniform outlineWidth = shader.getUniform("OutlineWidth");
        if (outlineWidth != null) {
            outlineWidth.set(AnimationEngine.getOutlineWidth(config));
        }

        GlUniform glowStrength = shader.getUniform("GlowStrength");
        if (glowStrength != null) {
            glowStrength.set(AnimationEngine.getGlowStrength(config));
        }

        GlUniform outlineMode = shader.getUniform("OutlineMode");
        if (outlineMode != null) {
            outlineMode.set(config.outlineStyle.ordinal());
        }

        GlUniform gradientEnabled = shader.getUniform("GradientEnabled");
        if (gradientEnabled != null) {
            gradientEnabled.set(config.gradientEnabled ? 1 : 0);
        }

        GlUniform glowColor2 = shader.getUniform("GlowColor2");
        if (glowColor2 != null) {
            if (config.gradientEnabled) {
                float[] gc2 = unpackColor(config.gradientColor);
                float[] animColor2 = AnimationEngine.getColorForBase(config, gc2[0], gc2[1], gc2[2], gc2[3]);
                glowColor2.set(animColor2[0], animColor2[1], animColor2[2], animColor2[3]);
            } else {
                glowColor2.set(color[0], color[1], color[2], color[3]);
            }
        }

        drawFullscreenQuad(shader);

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static float[] unpackColor(int color) {
        return new float[] {
                ColorHelper.Argb.getRed(color) / 255.0F,
                ColorHelper.Argb.getGreen(color) / 255.0F,
                ColorHelper.Argb.getBlue(color) / 255.0F,
                ColorHelper.Argb.getAlpha(color) / 255.0F
        };
    }

    private static void drawFullscreenQuad(ShaderProgram shader) {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        builder.vertex(-1.0F, -1.0F, 0.0F).texture(0.0F, 0.0F);
        builder.vertex(1.0F, -1.0F, 0.0F).texture(1.0F, 0.0F);
        builder.vertex(1.0F, 1.0F, 0.0F).texture(1.0F, 1.0F);
        builder.vertex(-1.0F, 1.0F, 0.0F).texture(0.0F, 1.0F);
        RenderSystem.setShader(() -> shader);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public static void beginFrame() {
        if (!initialized) {
            return;
        }
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }

        if (!IrisCompat.isShaderPackInUse()) {
            CAPTURED_RENDERS.clear();
            capturedHandProjection = null;
            capturedHandModelView = null;
        }

        CAPTURED_WORLD_ITEM_RENDERS.clear();
        CAPTURED_ITEM_ENTITIES.clear();
        capturedWorldProjection = null;
        capturedWorldModelView = null;
    }

    public static void capture(
            LivingEntity entity,
            ItemStack stack,
            ModelTransformationMode renderMode,
            boolean leftHanded,
            MatrixStack matrices,
            int light,
            HeldItemRenderer heldItemRenderer
    ) {
        if (!initialized || !shouldOutline(stack) || renderingMask) {
            return;
        }
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }

        if (renderMode.isFirstPerson() && capturedHandProjection == null) {
            capturedHandProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            capturedHandModelView = new Matrix4f(RenderSystem.getModelViewStack());
        }
        if (!renderMode.isFirstPerson() && capturedWorldProjection == null) {
            capturedWorldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            capturedWorldModelView = new Matrix4f(RenderSystem.getModelViewStack());
        }

        MatrixStack copiedMatrices = new MatrixStack();
        copiedMatrices.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
        copiedMatrices.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());

        CapturedItemRender captured = new CapturedItemRender(
                entity,
                stack,
                renderMode,
                leftHanded,
                copiedMatrices,
                light,
                heldItemRenderer,
                resolveOutlineStyle(stack)
        );

        if (renderMode.isFirstPerson()) {
            CAPTURED_RENDERS.add(captured);
        } else {
            CAPTURED_WORLD_ITEM_RENDERS.add(captured);
        }
    }

    public static void captureItemEntity(ItemEntity entity, int light) {
        if (!initialized || !shouldOutline(entity.getStack()) || renderingMask) {
            return;
        }
        if (entity.isRemoved() || entity.getStack().isEmpty()) {
            return;
        }
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }

        if (capturedWorldProjection == null) {
            capturedWorldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            capturedWorldModelView = new Matrix4f(RenderSystem.getModelViewStack());
        }

        CAPTURED_ITEM_ENTITIES.add(new CapturedItemEntity(entity, light, resolveOutlineStyle(entity.getStack())));
    }

    public static void renderAfterHand(float tickDelta, Matrix4f positionMatrix) {
        if (CAPTURED_RENDERS.isEmpty() || !isReady()) {
            return;
        }
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }
        if (!ItemGlowConfig.INSTANCE.enabled || !ItemGlowConfig.INSTANCE.heldItemOutline) {
            CAPTURED_RENDERS.clear();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate entityVertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        Map<OutlineStyle, List<CapturedItemRender>> groupedRenders = groupCapturedRenders(CAPTURED_RENDERS);
        if (groupedRenders.isEmpty()) {
            CAPTURED_RENDERS.clear();
            return;
        }

        entityVertexConsumers.draw();

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        if (capturedHandProjection != null) {
            RenderSystem.setProjectionMatrix(capturedHandProjection, VertexSorter.BY_DISTANCE);
        }

        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        if (capturedHandModelView != null) {
            stack.set(capturedHandModelView);
        } else {
            stack.identity();
            stack.mul(positionMatrix);
        }
        RenderSystem.applyModelViewMatrix();

        renderingMask = true;
        for (Map.Entry<OutlineStyle, List<CapturedItemRender>> entry : groupedRenders.entrySet()) {
            beginMaskPass();
            for (CapturedItemRender render : entry.getValue()) {
                renderCapturedItem(render, entityVertexConsumers);
            }
            entityVertexConsumers.draw();
            composite(entry.getKey());
        }
        renderingMask = false;

        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProjection, VertexSorter.BY_DISTANCE);

        CAPTURED_RENDERS.clear();
        capturedHandProjection = null;
        capturedHandModelView = null;
    }

    public static void renderWorldItemOutlines(float tickDelta, Camera camera) {
        if ((CAPTURED_WORLD_ITEM_RENDERS.isEmpty() && CAPTURED_ITEM_ENTITIES.isEmpty()) || !isReady()) {
            return;
        }
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }
        if (!ItemGlowConfig.INSTANCE.enabled || !ItemGlowConfig.INSTANCE.worldItemOutline) {
            CAPTURED_WORLD_ITEM_RENDERS.clear();
            CAPTURED_ITEM_ENTITIES.clear();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate entityVertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        Map<OutlineStyle, WorldOutlineBatch> batches = groupWorldBatches();
        if (batches.isEmpty()) {
            CAPTURED_WORLD_ITEM_RENDERS.clear();
            CAPTURED_ITEM_ENTITIES.clear();
            return;
        }

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        if (capturedWorldProjection != null) {
            RenderSystem.setProjectionMatrix(capturedWorldProjection, VertexSorter.BY_DISTANCE);
        }

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        if (capturedWorldModelView != null) {
            modelViewStack.set(capturedWorldModelView);
        } else {
            modelViewStack.identity();
            modelViewStack.mul(new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf())));
        }
        RenderSystem.applyModelViewMatrix();

        renderingMask = true;
        for (Map.Entry<OutlineStyle, WorldOutlineBatch> entry : batches.entrySet()) {
            WorldOutlineBatch batch = entry.getValue();
            beginMaskPass();
            copyDepthFromMain();

            for (CapturedItemRender render : batch.itemRenders) {
                renderCapturedItem(render, entityVertexConsumers);
            }

            if (!batch.itemEntities.isEmpty()) {
                EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
                Vec3d cameraPos = camera.getPos();
                MatrixStack matrices = new MatrixStack();

                for (CapturedItemEntity captured : batch.itemEntities) {
                    if (captured.entity.isTouchingWater()) {
                        continue;
                    }
                    renderItemEntity(dispatcher, captured, tickDelta, cameraPos, matrices, entityVertexConsumers);
                }

                entityVertexConsumers.draw();

                glClear(GL_DEPTH_BUFFER_BIT);
                for (CapturedItemEntity captured : batch.itemEntities) {
                    if (!captured.entity.isTouchingWater()) {
                        continue;
                    }
                    renderItemEntity(dispatcher, captured, tickDelta, cameraPos, matrices, entityVertexConsumers);
                }
            }

            entityVertexConsumers.draw();
            composite(entry.getKey());
        }
        renderingMask = false;

        // Render item beams while world projection is still active
        if (ItemGlowConfig.INSTANCE.itemBeam && !CAPTURED_ITEM_ENTITIES.isEmpty()) {
            List<ItemEntity> beamEntities = new ArrayList<>();
            for (CapturedItemEntity captured : CAPTURED_ITEM_ENTITIES) {
                beamEntities.add(captured.entity);
            }
            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
            ItemBeamRenderer.renderBeams(beamEntities, tickDelta, camera.getPos());
        }

        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProjection, VertexSorter.BY_DISTANCE);

        CAPTURED_WORLD_ITEM_RENDERS.clear();
        CAPTURED_ITEM_ENTITIES.clear();
    }

    private static void renderHotbarOutline(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!isReady() || !ItemGlowConfig.INSTANCE.enabled || !ItemGlowConfig.INSTANCE.hotbarOutline) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        List<GuiItemRender> guiItems = new ArrayList<>();
        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();
        int hotbarLeft = (screenWidth - 182) / 2;
        int hotbarTop = screenHeight - 22;

        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (!shouldOutline(stack)) {
                continue;
            }
            guiItems.add(new GuiItemRender(stack, hotbarLeft + slot * 20 + 3, hotbarTop + 3, resolveOutlineStyle(stack)));
        }

        ItemStack offhandStack = client.player.getOffHandStack();
        if (shouldOutline(offhandStack)) {
            boolean offhandOnLeft = client.player.getMainArm().getOpposite() == Arm.LEFT;
            int offhandX = offhandOnLeft ? hotbarLeft - 26 : hotbarLeft + 192;
            guiItems.add(new GuiItemRender(offhandStack, offhandX, hotbarTop + 3, resolveOutlineStyle(offhandStack)));
        }

        if (guiItems.isEmpty()) {
            return;
        }

        renderGuiGroups(guiItems);
    }

    public static void renderInventoryOutline(DrawContext drawContext, ScreenHandler handler, int guiX, int guiY, float delta) {
        if (!isReady() || !ItemGlowConfig.INSTANCE.enabled || !ItemGlowConfig.INSTANCE.inventoryOutline) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        List<GuiItemRender> guiItems = new ArrayList<>();
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (!shouldOutline(stack)) {
                continue;
            }
            guiItems.add(new GuiItemRender(stack, guiX + slot.x, guiY + slot.y, resolveOutlineStyle(stack)));
        }

        if (guiItems.isEmpty()) {
            return;
        }

        renderGuiGroups(guiItems);
    }

    private static void renderGuiGroups(List<GuiItemRender> guiItems) {
        MinecraftClient client = MinecraftClient.getInstance();
        Map<OutlineStyle, List<GuiItemRender>> grouped = new LinkedHashMap<>();
        for (GuiItemRender render : guiItems) {
            grouped.computeIfAbsent(render.style, key -> new ArrayList<>()).add(render);
        }

        for (Map.Entry<OutlineStyle, List<GuiItemRender>> entry : grouped.entrySet()) {
            beginMaskPass();
            VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
            DrawContext maskContext = new DrawContext(client, consumers);
            for (GuiItemRender render : entry.getValue()) {
                maskContext.drawItemWithoutEntity(render.stack, render.x, render.y);
            }
            maskContext.draw();
            composite(entry.getKey());
        }
    }

    private static void renderItemEntity(
            EntityRenderDispatcher dispatcher,
            CapturedItemEntity captured,
            float tickDelta,
            Vec3d cameraPos,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers
    ) {
        Entity entity = captured.entity;
        double dx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - cameraPos.x;
        double dy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - cameraPos.y;
        double dz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - cameraPos.z;

        dispatcher.render(
                entity,
                dx,
                dy,
                dz,
                MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()),
                tickDelta,
                matrices,
                vertexConsumers,
                captured.light
        );
    }

    private static void renderCapturedItem(CapturedItemRender render, VertexConsumerProvider vertexConsumers) {
        render.heldItemRenderer.renderItem(
                render.entity,
                render.stack,
                render.renderMode,
                render.leftHanded,
                render.matrices,
                vertexConsumers,
                render.light
        );
    }

    private static Map<OutlineStyle, List<CapturedItemRender>> groupCapturedRenders(List<CapturedItemRender> renders) {
        Map<OutlineStyle, List<CapturedItemRender>> grouped = new LinkedHashMap<>();
        for (CapturedItemRender render : renders) {
            grouped.computeIfAbsent(render.style, key -> new ArrayList<>()).add(render);
        }
        return grouped;
    }

    private static Map<OutlineStyle, WorldOutlineBatch> groupWorldBatches() {
        Map<OutlineStyle, WorldOutlineBatch> batches = new LinkedHashMap<>();

        for (CapturedItemRender render : CAPTURED_WORLD_ITEM_RENDERS) {
            batches.computeIfAbsent(render.style, key -> new WorldOutlineBatch()).itemRenders.add(render);
        }
        for (CapturedItemEntity entity : CAPTURED_ITEM_ENTITIES) {
            batches.computeIfAbsent(entity.style, key -> new WorldOutlineBatch()).itemEntities.add(entity);
        }

        return batches;
    }

    private static OutlineStyle resolveOutlineStyle(ItemStack stack) {
        ItemGlowConfig.ItemFilterRule rule = ItemGlowConfig.INSTANCE.findItemFilter(stack);
        if (rule != null) {
            return new OutlineStyle(false, rule.outlineColor);
        }
        return GLOBAL_STYLE;
    }

    private static boolean shouldOutline(ItemStack stack) {
        return !stack.isEmpty();
    }

    private record OutlineStyle(boolean useGlobalColor, int color) {
    }

    private record CapturedItemRender(
            LivingEntity entity,
            ItemStack stack,
            ModelTransformationMode renderMode,
            boolean leftHanded,
            MatrixStack matrices,
            int light,
            HeldItemRenderer heldItemRenderer,
            OutlineStyle style
    ) {
    }

    private record CapturedItemEntity(
            ItemEntity entity,
            int light,
            OutlineStyle style
    ) {
    }

    private record GuiItemRender(
            ItemStack stack,
            int x,
            int y,
            OutlineStyle style
    ) {
    }

    private static final class WorldOutlineBatch {
        private final List<CapturedItemRender> itemRenders = new ArrayList<>();
        private final List<CapturedItemEntity> itemEntities = new ArrayList<>();
    }
}
