package com.lirxowo.itemglow.client;

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
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumerProvider;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL30.*;

public final class HeldItemOutlineRenderer {
    private static final float GLOW_RED = 0.4F;
    private static final float GLOW_GREEN = 0.95F;
    private static final float GLOW_BLUE = 1.0F;
    private static final float GLOW_ALPHA = 1.0F;
    private static final float OUTLINE_WIDTH = 2.1F;
    private static final float GLOW_STRENGTH = 1.4F;
    private static final int HOTBAR_SIZE = 9;

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

    private static void pushModelView(Matrix4f matrix) {
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        stack.mul(matrix);
        RenderSystem.applyModelViewMatrix();
    }

    private static void popModelView() {
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
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

    private static void composite() {
        ShaderProgram shader = OutlineShaderRegistry.getShader();
        if (shader == null || maskTarget == null) {
            return;
        }

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
        GlUniform glowColor = shader.getUniform("GlowColor");
        if (glowColor != null) {
            glowColor.set(GLOW_RED, GLOW_GREEN, GLOW_BLUE, GLOW_ALPHA);
        }
        GlUniform outlineWidth = shader.getUniform("OutlineWidth");
        if (outlineWidth != null) {
            outlineWidth.set(OUTLINE_WIDTH);
        }
        GlUniform glowStrength = shader.getUniform("GlowStrength");
        if (glowStrength != null) {
            glowStrength.set(GLOW_STRENGTH);
        }

        drawFullscreenQuad(shader);

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
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
                entity, stack, renderMode, leftHanded, copiedMatrices, light, heldItemRenderer
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

        CAPTURED_ITEM_ENTITIES.add(new CapturedItemEntity(entity, light));
    }

    public static void renderAfterHand(float tickDelta, Matrix4f positionMatrix) {
        if (CAPTURED_RENDERS.isEmpty() || !isReady()) {
            return;
        }
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate entityVertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();

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
        beginMaskPass();

        for (CapturedItemRender render : CAPTURED_RENDERS) {
            renderCapturedItem(render, entityVertexConsumers);
        }

        entityVertexConsumers.draw();
        renderingMask = false;

        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setProjectionMatrix(savedProjection, VertexSorter.BY_DISTANCE);

        composite();
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

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate entityVertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();

        renderingMask = true;

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

        if (capturedWorldProjection != null) {
            RenderSystem.setProjectionMatrix(capturedWorldProjection, VertexSorter.BY_DISTANCE);
        }

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        if (capturedWorldModelView != null) {
            mvStack.set(capturedWorldModelView);
        } else {
            mvStack.identity();
            mvStack.mul(new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf())));
        }
        RenderSystem.applyModelViewMatrix();

        beginMaskPass();
        copyDepthFromMain();

        for (CapturedItemRender render : CAPTURED_WORLD_ITEM_RENDERS) {
            renderCapturedItem(render, entityVertexConsumers);
        }

        if (!CAPTURED_ITEM_ENTITIES.isEmpty()) {
            EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
            Vec3d cameraPos = camera.getPos();
            MatrixStack matrices = new MatrixStack();

            for (CapturedItemEntity captured : CAPTURED_ITEM_ENTITIES) {
                if (captured.entity.isTouchingWater()) {
                    continue;
                }
                renderItemEntity(dispatcher, captured, tickDelta, cameraPos, matrices, entityVertexConsumers);
            }

            entityVertexConsumers.draw();

            glClear(GL_DEPTH_BUFFER_BIT);
            for (CapturedItemEntity captured : CAPTURED_ITEM_ENTITIES) {
                if (!captured.entity.isTouchingWater()) {
                    continue;
                }
                renderItemEntity(dispatcher, captured, tickDelta, cameraPos, matrices, entityVertexConsumers);
            }
        }

        entityVertexConsumers.draw();

        mvStack.popMatrix();
        RenderSystem.applyModelViewMatrix();

        renderingMask = false;

        RenderSystem.setProjectionMatrix(savedProjection, VertexSorter.BY_DISTANCE);

        composite();
        CAPTURED_WORLD_ITEM_RENDERS.clear();
        CAPTURED_ITEM_ENTITIES.clear();
    }

    private static void renderHotbarOutline(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!isReady()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        ItemStack offhandStack = client.player.getOffHandStack();
        boolean foundOutlinedItem = shouldOutline(offhandStack);
        if (!foundOutlinedItem) {
            for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
                if (shouldOutline(client.player.getInventory().getStack(slot))) {
                    foundOutlinedItem = true;
                    break;
                }
            }
        }

        if (!foundOutlinedItem) {
            return;
        }

        beginMaskPass();

        VertexConsumerProvider.Immediate hotbarVertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        DrawContext maskContext = new DrawContext(client, hotbarVertexConsumers);
        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();
        int hotbarLeft = (screenWidth - 182) / 2;
        int hotbarTop = screenHeight - 22;

        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (!shouldOutline(stack)) {
                continue;
            }

            int iconX = hotbarLeft + slot * 20 + 3;
            int iconY = hotbarTop + 3;
            maskContext.drawItemWithoutEntity(stack, iconX, iconY);
        }

        if (shouldOutline(offhandStack)) {
            boolean offhandOnLeft = client.player.getMainArm().getOpposite() == net.minecraft.util.Arm.LEFT;
            int offhandX = offhandOnLeft ? hotbarLeft - 26 : hotbarLeft + 182 + 10;
            int offhandY = hotbarTop + 3;
            maskContext.drawItemWithoutEntity(offhandStack, offhandX, offhandY);
        }

        maskContext.draw();
        composite();
    }

    public static void renderInventoryOutline(DrawContext drawContext, ScreenHandler handler, int guiX, int guiY, float delta) {
        if (!isReady()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        boolean foundOutlinedItem = false;
        for (Slot slot : handler.slots) {
            if (shouldOutline(slot.getStack())) {
                foundOutlinedItem = true;
                break;
            }
        }

        if (!foundOutlinedItem) {
            return;
        }

        beginMaskPass();

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        DrawContext maskContext = new DrawContext(client, consumers);

        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (!shouldOutline(stack)) {
                continue;
            }

            int iconX = guiX + slot.x;
            int iconY = guiY + slot.y;
            maskContext.drawItemWithoutEntity(stack, iconX, iconY);
        }

        maskContext.draw();
        composite();
    }

    private static void renderItemEntity(
            EntityRenderDispatcher dispatcher, CapturedItemEntity captured,
            float tickDelta, Vec3d cameraPos, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers
    ) {
        Entity entity = captured.entity;
        double dx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - cameraPos.x;
        double dy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - cameraPos.y;
        double dz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - cameraPos.z;

        dispatcher.render(
                entity, dx, dy, dz,
                MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()),
                tickDelta, matrices, vertexConsumers, captured.light
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

    private static boolean shouldOutline(ItemStack stack) {
        return !stack.isEmpty();
    }

    private record CapturedItemRender(
            LivingEntity entity,
            ItemStack stack,
            ModelTransformationMode renderMode,
            boolean leftHanded,
            MatrixStack matrices,
            int light,
            HeldItemRenderer heldItemRenderer
    ) {
    }

    private record CapturedItemEntity(
            ItemEntity entity,
            int light
    ) {
    }
}
