package com.lirxowo.itemglow.client.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ItemFilterItemPickerScreen extends Screen {
    private static final int CELL_SIZE = 22;
    private static final int CELL_PADDING = 3;
    private static final int PANEL_WIDTH = 324;
    private static final int PANEL_TOP = 24;
    private static final int PANEL_BOTTOM = 32;
    private static final int GRID_TOP_OFFSET = 64;
    private static final int GRID_BOTTOM_OFFSET = 56;

    private final Screen parent;
    private final Consumer<Item> selectionConsumer;
    private final List<Item> allItems = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();

    private TextFieldWidget searchField;
    private double scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarDragStartY;
    private double scrollbarDragStartOffset;
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;

    public ItemFilterItemPickerScreen(Screen parent, Text title, Consumer<Item> selectionConsumer) {
        super(title);
        this.parent = parent;
        this.selectionConsumer = selectionConsumer;
    }

    @Override
    protected void init() {
        if (allItems.isEmpty()) {
            loadItems();
        }

        layoutPanel();
        this.searchField = this.addDrawableChild(new TextFieldWidget(this.textRenderer, this.width / 2 - 110, panelTop + 26, 220, 20, Text.translatable("itemglow.config.filters.searchPlaceholder")));
        this.searchField.setMaxLength(128);
        this.searchField.setPlaceholder(Text.translatable("itemglow.config.filters.searchPlaceholder"));
        this.searchField.setChangedListener(value -> refreshFilter());
        this.setInitialFocus(this.searchField);
        this.refreshFilter();

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
                .dimensions(this.width / 2 - 50, panelBottom - 26, 100, 20)
                .build());
    }

    private void layoutPanel() {
        panelTop = PANEL_TOP;
        panelBottom = this.height - PANEL_BOTTOM;
        int width = Math.min(PANEL_WIDTH, this.width - 24);
        panelLeft = (this.width - width) / 2;
        panelRight = panelLeft + width;
    }

    private void loadItems() {
        for (Item item : Registries.ITEM) {
            ItemStack stack = item.getDefaultStack();
            if (!stack.isEmpty()) {
                allItems.add(item);
            }
        }
        allItems.sort(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()));
    }

    private void refreshFilter() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredItems.clear();

        for (Item item : allItems) {
            if (query.isEmpty()) {
                filteredItems.add(item);
                continue;
            }

            Identifier id = Registries.ITEM.getId(item);
            String itemId = id.toString();
            String itemName = item.getName().getString().toLowerCase(Locale.ROOT);
            if (itemId.contains(query) || itemName.contains(query)) {
                filteredItems.add(item);
            }
        }

        clampScroll();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isInsideScrollbar(mouseX, mouseY) && getMaxScroll() > 0.0) {
                draggingScrollbar = true;
                scrollbarDragStartY = mouseY;
                scrollbarDragStartOffset = scrollOffset;
                return true;
            }
            Item clickedItem = getItemAt(mouseX, mouseY);
            if (clickedItem != null) {
                selectionConsumer.accept(clickedItem);
                this.client.setScreen(parent);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingScrollbar) {
            int top = panelTop + GRID_TOP_OFFSET;
            int height = panelBottom - panelTop - GRID_TOP_OFFSET - GRID_BOTTOM_OFFSET;
            int width = panelRight - panelLeft - 28;
            int columns = Math.max(1, width / CELL_SIZE);
            int totalRows = (filteredItems.size() + columns - 1) / columns;
            int scrollbarHeight = Math.max(20, (int) ((height / (double) (totalRows * CELL_SIZE)) * height));
            int scrollbarRange = height - scrollbarHeight;

            if (scrollbarRange > 0) {
                double deltaScroll = (mouseY - scrollbarDragStartY) / scrollbarRange * getMaxScroll();
                this.scrollOffset = MathHelper.clamp(scrollbarDragStartOffset + deltaScroll, 0.0, getMaxScroll());
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInsideGrid(mouseX, mouseY)) {
            this.scrollOffset = MathHelper.clamp(this.scrollOffset - verticalAmount * CELL_SIZE * 2.0, 0.0, getMaxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return this.searchField != null && this.searchField.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (super.charTyped(chr, modifiers)) {
            return true;
        }
        return this.searchField != null && this.searchField.charTyped(chr, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        layoutPanel();

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelTop + 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("itemglow.config.filters.pickItemHint"), this.width / 2, panelTop + 48, 0xB6B6B6);
        renderGrid(context, mouseX, mouseY);

        Item hoveredItem = getItemAt(mouseX, mouseY);
        if (hoveredItem != null) {
            Identifier id = Registries.ITEM.getId(hoveredItem);
            this.setTooltip(Text.literal(hoveredItem.getName().getString() + " [" + id + "]"));
        }
    }

    private void renderGrid(DrawContext context, int mouseX, int mouseY) {
        int left = panelLeft + 14;
        int top = panelTop + GRID_TOP_OFFSET;
        int width = panelRight - panelLeft - 28;
        int height = panelBottom - panelTop - GRID_TOP_OFFSET - GRID_BOTTOM_OFFSET;
        int columns = Math.max(1, width / CELL_SIZE);
        int totalRows = (filteredItems.size() + columns - 1) / columns;
        int startRow = (int) (scrollOffset / CELL_SIZE);
        int rowOffset = (int) (scrollOffset % CELL_SIZE);
        int visibleRows = height / CELL_SIZE + 2;

        if (filteredItems.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("itemglow.config.filters.noMatches"), this.width / 2, top + height / 2 - 4, 0x909090);
            return;
        }

        for (int row = 0; row < visibleRows; row++) {
            int actualRow = startRow + row;
            if (actualRow >= totalRows) {
                break;
            }
            int cellY = top + row * CELL_SIZE - rowOffset;
            if (cellY + CELL_SIZE < top || cellY > top + height) {
                continue;
            }

            for (int col = 0; col < columns; col++) {
                int index = actualRow * columns + col;
                if (index >= filteredItems.size()) {
                    break;
                }

                int cellX = left + col * CELL_SIZE;
                Item item = filteredItems.get(index);
                boolean hovered = mouseX >= cellX && mouseX < cellX + CELL_SIZE && mouseY >= cellY && mouseY < cellY + CELL_SIZE;
                if (hovered) {
                    context.fill(cellX, cellY, cellX + CELL_SIZE - 1, cellY + CELL_SIZE - 1, 0x44FFFFFF);
                }
                context.drawItemWithoutEntity(item.getDefaultStack(), cellX + CELL_PADDING, cellY + CELL_PADDING);
                if (hovered) {
                    context.fill(cellX, cellY, cellX + CELL_SIZE - 1, cellY + 1, 0xAAFFFFFF);
                    context.fill(cellX, cellY + CELL_SIZE - 2, cellX + CELL_SIZE - 1, cellY + CELL_SIZE - 1, 0xAAFFFFFF);
                    context.fill(cellX, cellY, cellX + 1, cellY + CELL_SIZE - 1, 0xAAFFFFFF);
                    context.fill(cellX + CELL_SIZE - 2, cellY, cellX + CELL_SIZE - 1, cellY + CELL_SIZE - 1, 0xAAFFFFFF);
                }
            }
        }

        if (getMaxScroll() > 0.0) {
            int scrollbarHeight = Math.max(20, (int) ((height / (double) (totalRows * CELL_SIZE)) * height));
            int scrollbarRange = height - scrollbarHeight;
            int scrollbarY = top + (int) ((scrollOffset / getMaxScroll()) * scrollbarRange);
            int scrollbarX = left + width + 6;
            context.fill(scrollbarX, top, scrollbarX + 4, top + height, 0xFF14181E);
            context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0xFF8B97AA);
        }
    }

    private boolean isInsideGrid(double mouseX, double mouseY) {
        int left = panelLeft + 14;
        int top = panelTop + GRID_TOP_OFFSET;
        int width = panelRight - panelLeft - 28;
        int height = panelBottom - panelTop - GRID_TOP_OFFSET - GRID_BOTTOM_OFFSET;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    private Item getItemAt(double mouseX, double mouseY) {
        int left = panelLeft + 14;
        int top = panelTop + GRID_TOP_OFFSET;
        int width = panelRight - panelLeft - 28;
        int height = panelBottom - panelTop - GRID_TOP_OFFSET - GRID_BOTTOM_OFFSET;
        if (mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= top + height) {
            return null;
        }

        int columns = Math.max(1, width / CELL_SIZE);
        int row = (int) ((mouseY - top + scrollOffset) / CELL_SIZE);
        int col = (int) ((mouseX - left) / CELL_SIZE);
        int index = row * columns + col;
        if (index < 0 || index >= filteredItems.size()) {
            return null;
        }
        return filteredItems.get(index);
    }

    private double getMaxScroll() {
        int width = panelRight - panelLeft - 28;
        int height = panelBottom - panelTop - GRID_TOP_OFFSET - GRID_BOTTOM_OFFSET;
        int columns = Math.max(1, width / CELL_SIZE);
        int totalRows = (filteredItems.size() + columns - 1) / columns;
        return Math.max(0.0, totalRows * CELL_SIZE - height);
    }

    private void clampScroll() {
        this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0.0, getMaxScroll());
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int left = panelLeft + 14;
        int top = panelTop + GRID_TOP_OFFSET;
        int width = panelRight - panelLeft - 28;
        int height = panelBottom - panelTop - GRID_TOP_OFFSET - GRID_BOTTOM_OFFSET;
        int scrollbarX = left + width + 6;
        return mouseX >= scrollbarX && mouseX < scrollbarX + 4 && mouseY >= top && mouseY < top + height;
    }
}
