package com.lirxowo.itemglow.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

public class ItemGlowConfigScreen {

    public static Screen create(Screen parent) {
        ItemGlowConfig config = ItemGlowConfig.INSTANCE;
        ItemGlowConfig defaults = new ItemGlowConfig();
        List<ItemGlowConfig.ItemFilterRule> workingFilters = ItemGlowConfig.copyItemFilters(config.getItemFilters());

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("itemglow.config.title"))
                .setSavingRunnable(() -> {
                    config.itemFilters = ItemGlowConfig.copyItemFilters(workingFilters);
                    ItemGlowConfig.save();
                });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // General
        ConfigCategory general = builder.getOrCreateCategory(Text.translatable("itemglow.config.category.general"));
        general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("itemglow.config.enabled"), config.enabled)
                .setDefaultValue(defaults.enabled)
                .setSaveConsumer(v -> config.enabled = v)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("itemglow.config.heldItemOutline"), config.heldItemOutline)
                .setDefaultValue(defaults.heldItemOutline)
                .setSaveConsumer(v -> config.heldItemOutline = v)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("itemglow.config.worldItemOutline"), config.worldItemOutline)
                .setDefaultValue(defaults.worldItemOutline)
                .setSaveConsumer(v -> config.worldItemOutline = v)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("itemglow.config.hotbarOutline"), config.hotbarOutline)
                .setDefaultValue(defaults.hotbarOutline)
                .setSaveConsumer(v -> config.hotbarOutline = v)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("itemglow.config.inventoryOutline"), config.inventoryOutline)
                .setDefaultValue(defaults.inventoryOutline)
                .setSaveConsumer(v -> config.inventoryOutline = v)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("itemglow.config.pickupParticles"), config.pickupParticles)
                .setDefaultValue(defaults.pickupParticles)
                .setSaveConsumer(v -> config.pickupParticles = v)
                .build());

        // Appearance
        ConfigCategory appearance = builder.getOrCreateCategory(Text.translatable("itemglow.config.category.appearance"));
        appearance.addEntry(new ColorPickerEntry(
                Text.translatable("itemglow.config.outlineColor"),
                config.outlineColor,
                defaults.outlineColor,
                v -> config.outlineColor = v
        ));
        appearance.addEntry(entryBuilder.startFloatField(Text.translatable("itemglow.config.outlineWidth"), config.outlineWidth)
                .setDefaultValue(defaults.outlineWidth)
                .setMin(0.5F)
                .setMax(5.0F)
                .setSaveConsumer(v -> config.outlineWidth = v)
                .build());
        appearance.addEntry(entryBuilder.startFloatField(Text.translatable("itemglow.config.glowStrength"), config.glowStrength)
                .setDefaultValue(defaults.glowStrength)
                .setMin(0.0F)
                .setMax(3.0F)
                .setSaveConsumer(v -> config.glowStrength = v)
                .build());
        appearance.addEntry(entryBuilder.startEnumSelector(
                        Text.translatable("itemglow.config.outlineStyle"),
                        OutlineStyleMode.class,
                        config.outlineStyle)
                .setDefaultValue(defaults.outlineStyle)
                .setEnumNameProvider(mode -> Text.translatable(((OutlineStyleMode) mode).getTranslationKey()))
                .setSaveConsumer(v -> config.outlineStyle = v)
                .build());
        appearance.addEntry(entryBuilder.startBooleanToggle(Text.translatable("itemglow.config.gradientEnabled"), config.gradientEnabled)
                .setDefaultValue(defaults.gradientEnabled)
                .setSaveConsumer(v -> config.gradientEnabled = v)
                .build());
        appearance.addEntry(new ColorPickerEntry(
                Text.translatable("itemglow.config.gradientColor"),
                config.gradientColor,
                defaults.gradientColor,
                v -> config.gradientColor = v
        ));
        appearance.addEntry(entryBuilder.startBooleanToggle(Text.translatable("itemglow.config.itemBeam"), config.itemBeam)
                .setDefaultValue(defaults.itemBeam)
                .setSaveConsumer(v -> config.itemBeam = v)
                .build());
        appearance.addEntry(entryBuilder.startFloatField(Text.translatable("itemglow.config.beamHeight"), config.beamHeight)
                .setDefaultValue(defaults.beamHeight)
                .setMin(1.0F)
                .setMax(10.0F)
                .setSaveConsumer(v -> config.beamHeight = v)
                .build());

        // Filters
        ConfigCategory filters = builder.getOrCreateCategory(Text.translatable("itemglow.config.category.filters"));
        filters.addEntry(entryBuilder.startTextDescription(Text.translatable("itemglow.config.filters.description")).build());
        filters.addEntry(new ItemFilterEntry(Text.translatable("itemglow.config.filters.title"), workingFilters));

        // Animation
        ConfigCategory animation = builder.getOrCreateCategory(Text.translatable("itemglow.config.category.animation"));
        animation.addEntry(entryBuilder.startEnumSelector(
                        Text.translatable("itemglow.config.animationMode"),
                        AnimationMode.class,
                        config.animationMode)
                .setDefaultValue(defaults.animationMode)
                .setEnumNameProvider(mode -> Text.translatable(((AnimationMode) mode).getTranslationKey()))
                .setSaveConsumer(v -> config.animationMode = v)
                .build());
        animation.addEntry(entryBuilder.startFloatField(Text.translatable("itemglow.config.animationSpeed"), config.animationSpeed)
                .setDefaultValue(defaults.animationSpeed)
                .setMin(0.1F)
                .setMax(5.0F)
                .setSaveConsumer(v -> config.animationSpeed = v)
                .build());

        return builder.build();
    }
}
