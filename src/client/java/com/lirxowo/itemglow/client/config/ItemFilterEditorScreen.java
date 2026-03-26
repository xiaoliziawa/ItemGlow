package com.lirxowo.itemglow.client.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class ItemFilterEditorScreen extends Screen {
    private final Screen parent;
    private final List<ItemGlowConfig.ItemFilterRule> rules;
    private final List<ItemGlowConfig.ItemFilterRule> snapshot;

    private RuleListWidget listWidget;
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;

    public ItemFilterEditorScreen(Screen parent, List<ItemGlowConfig.ItemFilterRule> rules) {
        super(Text.translatable("itemglow.config.filters.editorTitle"));
        this.parent = parent;
        this.rules = rules;
        this.snapshot = ItemGlowConfig.copyItemFilters(rules);
    }

    @Override
    protected void init() {
        layoutPanel();
        this.listWidget = this.addDrawableChild(new RuleListWidget(this.client, this.width, panelBottom - 110, panelTop + 64, 50));
        this.listWidget.reload();

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("itemglow.config.filters.addRule"), button -> openNewRulePicker())
                .dimensions(this.width / 2 - 154, panelBottom - 28, 96, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> saveAndClose())
                .dimensions(this.width / 2 - 50, panelBottom - 28, 100, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> cancelAndClose())
                .dimensions(this.width / 2 + 58, panelBottom - 28, 96, 20)
                .build());
    }

    private void layoutPanel() {
        panelTop = 24;
        panelBottom = this.height - 18;
        int width = Math.min(468, this.width - 24);
        panelLeft = (this.width - width) / 2;
        panelRight = panelLeft + width;
    }

    private void openNewRulePicker() {
        this.client.setScreen(new ItemFilterItemPickerScreen(this, Text.translatable("itemglow.config.filters.pickItemTitle"), item -> {
            String itemId = Registries.ITEM.getId(item).toString();
            for (ItemGlowConfig.ItemFilterRule rule : rules) {
                if (itemId.equals(rule.itemId)) {
                    return;
                }
            }
            rules.add(new ItemGlowConfig.ItemFilterRule(itemId, ItemGlowConfig.INSTANCE.outlineColor));
            if (listWidget != null) {
                listWidget.reload();
            }
        }));
    }

    private void openRuleItemPicker(ItemGlowConfig.ItemFilterRule rule) {
        this.client.setScreen(new ItemFilterItemPickerScreen(this, Text.translatable("itemglow.config.filters.changeItemTitle"), item -> {
            rule.itemId = Registries.ITEM.getId(item).toString();
            if (listWidget != null) {
                listWidget.reload();
            }
        }));
    }

    private void removeRule(ItemGlowConfig.ItemFilterRule rule) {
        rules.remove(rule);
        listWidget.reload();
    }

    private void openColorPicker(ItemGlowConfig.ItemFilterRule rule) {
        Text colorTitle = rule.itemId == null || rule.itemId.isBlank()
                ? Text.translatable("itemglow.config.filters.colorScreenTitleEmpty")
                : Text.translatable("itemglow.config.filters.colorScreenTitle", rule.itemId);
        this.client.setScreen(new ItemFilterColorScreen(this, colorTitle, rule.outlineColor, ItemGlowConfig.INSTANCE.outlineColor, color -> rule.outlineColor = color));
    }

    private void saveAndClose() {
        this.client.setScreen(parent);
    }

    private void cancelAndClose() {
        rules.clear();
        rules.addAll(ItemGlowConfig.copyItemFilters(snapshot));
        this.client.setScreen(parent);
    }

    @Override
    public void close() {
        cancelAndClose();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        layoutPanel();

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelTop + 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("itemglow.config.filters.editorHint"), this.width / 2, panelTop + 28, 0xB0B0B0);

        if (rules.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("itemglow.config.filters.editorEmpty"), this.width / 2, panelTop + 90, 0x909090);
        }
    }

    private class RuleListWidget extends AlwaysSelectedEntryListWidget<RuleEntry> {
        private RuleListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }

        private void reload() {
            clearEntries();
            for (ItemGlowConfig.ItemFilterRule rule : rules) {
                addEntry(new RuleEntry(rule));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(430, ItemFilterEditorScreen.this.width - 44);
        }
    }

    private class RuleEntry extends AlwaysSelectedEntryListWidget.Entry<RuleEntry> {
        private final ItemGlowConfig.ItemFilterRule rule;
        private final ButtonWidget colorButton;
        private final ButtonWidget removeButton;
        private int lastX;
        private int lastY;
        private int lastWidth;
        private int lastHeight;

        private RuleEntry(ItemGlowConfig.ItemFilterRule rule) {
            this.rule = rule;
            this.colorButton = ButtonWidget.builder(Text.translatable("itemglow.config.filters.colorButton"), button -> openColorPicker(rule))
                    .dimensions(0, 0, 64, 20)
                    .build();
            this.removeButton = ButtonWidget.builder(Text.translatable("itemglow.config.filters.removeShort"), button -> removeRule(rule))
                    .dimensions(0, 0, 52, 20)
                    .build();
        }

        @Override
        public Text getNarration() {
            return Text.translatable("itemglow.config.filters.ruleNarration", rule.itemId == null || rule.itemId.isBlank() ? "empty" : rule.itemId);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            lastX = x;
            lastY = y;
            lastWidth = entryWidth;
            lastHeight = entryHeight;

            int itemAreaX = x + 8;
            int itemAreaY = y + 6;
            int itemAreaWidth = entryWidth - 148;
            int itemAreaHeight = entryHeight - 12;
            int colorButtonX = x + entryWidth - 118;
            int removeButtonX = x + entryWidth - 54;
            int buttonY = y + 15;
            boolean overItemArea = mouseX >= itemAreaX && mouseX < itemAreaX + itemAreaWidth && mouseY >= itemAreaY && mouseY < itemAreaY + itemAreaHeight;

            if (overItemArea) {
                context.fill(itemAreaX - 1, itemAreaY - 1, itemAreaX + itemAreaWidth + 1, itemAreaY, 0x80D0D7E2);
                context.fill(itemAreaX - 1, itemAreaY + itemAreaHeight, itemAreaX + itemAreaWidth + 1, itemAreaY + itemAreaHeight + 1, 0x80D0D7E2);
                context.fill(itemAreaX - 1, itemAreaY, itemAreaX, itemAreaY + itemAreaHeight, 0x80D0D7E2);
                context.fill(itemAreaX + itemAreaWidth, itemAreaY, itemAreaX + itemAreaWidth + 1, itemAreaY + itemAreaHeight, 0x80D0D7E2);
            }

            colorButton.setDimensionsAndPosition(64, 20, colorButtonX, buttonY);
            removeButton.setDimensionsAndPosition(52, 20, removeButtonX, buttonY);
            colorButton.render(context, mouseX, mouseY, delta);
            removeButton.render(context, mouseX, mouseY, delta);

            context.fill(colorButtonX + 5, buttonY + 5, colorButtonX + 15, buttonY + 15, rule.outlineColor);
            context.fill(colorButtonX + 4, buttonY + 4, colorButtonX + 16, buttonY + 5, 0xFF202020);
            context.fill(colorButtonX + 4, buttonY + 15, colorButtonX + 16, buttonY + 16, 0xFF202020);
            context.fill(colorButtonX + 4, buttonY + 5, colorButtonX + 5, buttonY + 15, 0xFF202020);
            context.fill(colorButtonX + 15, buttonY + 5, colorButtonX + 16, buttonY + 15, 0xFF202020);

            Item item = getItem();
            int iconX = itemAreaX + 6;
            int iconY = itemAreaY + 6;
            if (item != null) {
                ItemStack stack = item.getDefaultStack();
                context.fill(iconX - 2, iconY - 2, iconX + 18, iconY + 18, 0x44000000);
                context.drawItemWithoutEntity(stack, iconX, iconY);
                context.drawTextWithShadow(ItemFilterEditorScreen.this.textRenderer, item.getName(), itemAreaX + 32, y + 12, 0xFFFFFF);
                context.drawText(ItemFilterEditorScreen.this.textRenderer, Text.literal(rule.itemId), itemAreaX + 32, y + 27, 0xAEB7C3, false);
            } else {
                context.fill(iconX - 2, iconY - 2, iconX + 18, iconY + 18, 0x66000000);
                context.drawTextWithShadow(ItemFilterEditorScreen.this.textRenderer, Text.translatable("itemglow.config.filters.invalidItem"), itemAreaX + 32, y + 12, 0xFF8080);
                context.drawText(ItemFilterEditorScreen.this.textRenderer, Text.literal(rule.itemId == null ? "" : rule.itemId), itemAreaX + 32, y + 27, 0xAEB7C3, false);
            }

            context.drawText(ItemFilterEditorScreen.this.textRenderer, Text.translatable("itemglow.config.filters.clickToChange"), itemAreaX + 32, y + 39, overItemArea ? 0xD8E0EC : 0x8693A6, false);
        }

        private Item getItem() {
            Identifier identifier = Identifier.tryParse(rule.itemId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                return null;
            }
            return Registries.ITEM.get(identifier);
        }

        private boolean isInsideItemArea(double mouseX, double mouseY) {
            int itemAreaX = lastX + 8;
            int itemAreaY = lastY + 6;
            int itemAreaWidth = lastWidth - 148;
            int itemAreaHeight = lastHeight - 12;
            return mouseX >= itemAreaX && mouseX < itemAreaX + itemAreaWidth && mouseY >= itemAreaY && mouseY < itemAreaY + itemAreaHeight;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && isInsideItemArea(mouseX, mouseY)) {
                openRuleItemPicker(rule);
                return true;
            }
            if (colorButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return removeButton.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean released = colorButton.mouseReleased(mouseX, mouseY, button);
            released |= removeButton.mouseReleased(mouseX, mouseY, button);
            return released;
        }
    }
}
