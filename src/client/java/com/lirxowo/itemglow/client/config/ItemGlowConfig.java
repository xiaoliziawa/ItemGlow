package com.lirxowo.itemglow.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ItemGlowConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("itemglow.json");
    private static final int DEFAULT_FILTER_COLOR = 0xFF66F2FF;

    public static ItemGlowConfig INSTANCE = new ItemGlowConfig();

    public boolean enabled = true;
    public boolean heldItemOutline = true;
    public boolean worldItemOutline = true;
    public boolean hotbarOutline = true;
    public boolean inventoryOutline = true;

    public int outlineColor = 0xFF66F2FF;
    public List<ItemFilterRule> itemFilters = new ArrayList<>();

    public float outlineWidth = 2.1F;
    public float glowStrength = 1.4F;

    public AnimationMode animationMode = AnimationMode.NONE;
    public float animationSpeed = 1.0F;

    public OutlineStyleMode outlineStyle = OutlineStyleMode.SOLID;
    public boolean gradientEnabled = false;
    public int gradientColor = 0xFFFF6699;

    public boolean pickupParticles = true;
    public boolean itemBeam = false;
    public float beamHeight = 4.0F;
    public float beamWidth = 0.12F;
    public float beamAlpha = 0.45F;
    public BeamStyleMode beamStyle = BeamStyleMode.STRAIGHT;

    public float getRed() {
        return ((outlineColor >> 16) & 0xFF) / 255.0F;
    }

    public float getGreen() {
        return ((outlineColor >> 8) & 0xFF) / 255.0F;
    }

    public float getBlue() {
        return (outlineColor & 0xFF) / 255.0F;
    }

    public float getAlpha() {
        return ((outlineColor >> 24) & 0xFF) / 255.0F;
    }

    public List<ItemFilterRule> getItemFilters() {
        if (itemFilters == null) {
            itemFilters = new ArrayList<>();
        }
        return itemFilters;
    }

    public ItemFilterRule findItemFilter(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        for (ItemFilterRule rule : getItemFilters()) {
            if (rule != null && itemId.equals(rule.itemId)) {
                return rule;
            }
        }
        return null;
    }

    public void normalize() {
        outlineWidth = MathHelper.clamp(outlineWidth, 0.5F, 5.0F);
        glowStrength = MathHelper.clamp(glowStrength, 0.0F, 3.0F);
        animationSpeed = MathHelper.clamp(animationSpeed, 0.1F, 5.0F);
        beamHeight = MathHelper.clamp(beamHeight, 1.0F, 10.0F);
        beamWidth = MathHelper.clamp(beamWidth, 0.02F, 0.75F);
        beamAlpha = MathHelper.clamp(beamAlpha, 0.05F, 1.0F);
        animationMode = animationMode == null ? AnimationMode.NONE : animationMode;
        outlineStyle = outlineStyle == null ? OutlineStyleMode.SOLID : outlineStyle;
        beamStyle = beamStyle == null ? BeamStyleMode.STRAIGHT : beamStyle;

        Map<String, ItemFilterRule> normalized = new LinkedHashMap<>();
        for (ItemFilterRule rule : getItemFilters()) {
            if (rule == null) {
                continue;
            }
            String itemId = rule.itemId == null ? "" : rule.itemId.trim();
            if (itemId.isEmpty()) {
                continue;
            }
            Identifier identifier = Identifier.tryParse(itemId);
            String normalizedId = identifier != null ? identifier.toString() : itemId;
            normalized.put(normalizedId, new ItemFilterRule(normalizedId, rule.outlineColor));
        }
        itemFilters = new ArrayList<>(normalized.values());
    }

    public static List<ItemFilterRule> copyItemFilters(List<ItemFilterRule> source) {
        List<ItemFilterRule> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }

        for (ItemFilterRule rule : source) {
            if (rule != null) {
                copy.add(rule.copy());
            }
        }
        return copy;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(reader, ItemGlowConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new ItemGlowConfig();
                }
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                INSTANCE = new ItemGlowConfig();
            }
        }

        INSTANCE.normalize();
        save();
    }

    public static void save() {
        INSTANCE.normalize();
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class ItemFilterRule {
        public String itemId = "";
        public int outlineColor = DEFAULT_FILTER_COLOR;

        public ItemFilterRule() {
        }

        public ItemFilterRule(String itemId, int outlineColor) {
            this.itemId = itemId;
            this.outlineColor = outlineColor;
        }

        public ItemFilterRule copy() {
            return new ItemFilterRule(this.itemId, this.outlineColor);
        }

        public Identifier getIdentifier() {
            return Identifier.tryParse(itemId);
        }

        public boolean isValid() {
            Identifier identifier = getIdentifier();
            return identifier != null && Registries.ITEM.containsId(identifier);
        }
    }
}
