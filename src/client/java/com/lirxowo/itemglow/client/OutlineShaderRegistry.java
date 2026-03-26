package com.lirxowo.itemglow.client;

import com.lirxowo.itemglow.Itemglow;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class OutlineShaderRegistry {
    @Nullable
    private static ShaderProgram shader;

    private OutlineShaderRegistry() {
    }

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(
                    Identifier.of(Itemglow.MOD_ID, "held_item_outline"),
                    VertexFormats.POSITION_TEXTURE,
                    program -> shader = program
            );
        });
    }

    @Nullable
    public static ShaderProgram getShader() {
        return shader;
    }
}
