package com.lirxowo.itemglow.client.config;

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ItemFilterEntry extends TooltipListEntry<List<ItemGlowConfig.ItemFilterRule>> {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<ItemGlowConfig.ItemFilterRule> rules;
    private final List<ItemGlowConfig.ItemFilterRule> initialRules;
    private final ButtonWidget editButton;

    public ItemFilterEntry(Text fieldName, List<ItemGlowConfig.ItemFilterRule> rules) {
        super(fieldName, () -> Optional.of(new Text[] {
                Text.translatable("itemglow.config.filters.tooltip"),
                Text.translatable("itemglow.config.filters.tooltip2")
        }));
        this.rules = rules;
        this.initialRules = ItemGlowConfig.copyItemFilters(rules);
        this.editButton = ButtonWidget.builder(Text.empty(), button -> openEditor()).dimensions(0, 0, 116, 20).build();
        refreshButtonText();
    }

    private void openEditor() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        minecraft.setScreen(new ItemFilterEditorScreen(minecraft.currentScreen, rules));
    }

    private void refreshButtonText() {
        editButton.setMessage(Text.translatable("itemglow.config.filters.manageButton", rules.size()));
    }

    private int countValidRules() {
        int count = 0;
        for (ItemGlowConfig.ItemFilterRule rule : rules) {
            if (rule != null && rule.isValid()) {
                count++;
            }
        }
        return count;
    }

    private static boolean sameRules(List<ItemGlowConfig.ItemFilterRule> left, List<ItemGlowConfig.ItemFilterRule> right) {
        if (left.size() != right.size()) {
            return false;
        }

        for (int i = 0; i < left.size(); i++) {
            ItemGlowConfig.ItemFilterRule a = left.get(i);
            ItemGlowConfig.ItemFilterRule b = right.get(i);
            if (!a.itemId.equals(b.itemId) || a.outlineColor != b.outlineColor) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<ItemGlowConfig.ItemFilterRule> getValue() {
        return ItemGlowConfig.copyItemFilters(rules);
    }

    @Override
    public Optional<List<ItemGlowConfig.ItemFilterRule>> getDefaultValue() {
        return Optional.of(Collections.emptyList());
    }

    @Override
    public boolean isEdited() {
        return !sameRules(initialRules, rules);
    }

    @Override
    public void save() {
    }

    @Override
    public int getItemHeight() {
        return 34;
    }

    @Override
    public List<? extends Element> children() {
        return List.of(editButton);
    }

    @Override
    public List<? extends Selectable> narratables() {
        return List.of(editButton);
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
        super.render(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);

        refreshButtonText();
        int titleColor = isEditable() ? 0xFFFFFF : 0xA0A0A0;
        Text summary = rules.isEmpty()
                ? Text.translatable("itemglow.config.filters.empty")
                : Text.translatable("itemglow.config.filters.summary", countValidRules(), rules.size());

        context.drawTextWithShadow(client.textRenderer, getFieldName(), x + 6, y + 6, titleColor);
        context.drawText(client.textRenderer, summary, x + 6, y + 20, 0xB0B0B0, false);

        editButton.active = isEditable();
        editButton.setDimensionsAndPosition(116, 20, x + entryWidth - 122, y + 7);
        editButton.render(context, mouseX, mouseY, delta);
    }
}
