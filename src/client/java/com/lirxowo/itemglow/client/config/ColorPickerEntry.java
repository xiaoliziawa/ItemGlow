package com.lirxowo.itemglow.client.config;

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public class ColorPickerEntry extends TooltipListEntry<Integer> {
    private static final int ENTRY_HEIGHT = 108;
    private static final int PALETTE_SIZE = 64;
    private static final int BAR_HEIGHT = 10;
    private static final int PREVIEW_SIZE = 28;
    private static final int CONTROL_GAP = 6;
    private static final int SAMPLE_STEP = 2;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final int defaultColor;
    private final int initialColor;
    private final Consumer<Integer> saveConsumer;

    private int value;
    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;

    private static final int DRAG_NONE = 0;
    private static final int DRAG_PALETTE = 1;
    private static final int DRAG_HUE = 2;
    private static final int DRAG_ALPHA = 3;

    private int dragMode = DRAG_NONE;
    private int paletteX;
    private int paletteY;
    private int hueX;
    private int hueY;
    private int alphaX;
    private int alphaY;
    private int previewX;
    private int previewY;

    public ColorPickerEntry(Text fieldName, int value, int defaultColor, Consumer<Integer> saveConsumer) {
        super(fieldName, () -> Optional.of(new Text[] {
                Text.translatable("itemglow.config.outlineColor.tooltip"),
                Text.translatable("itemglow.config.outlineColor.reset")
        }));
        this.value = value;
        this.initialColor = value;
        this.defaultColor = defaultColor;
        this.saveConsumer = saveConsumer;
        updateHsvFromColor(value);
    }

    @Override
    public Integer getValue() {
        return value;
    }

    @Override
    public Optional<Integer> getDefaultValue() {
        return Optional.of(defaultColor);
    }

    @Override
    public boolean isEdited() {
        return value != initialColor;
    }

    @Override
    public int getItemHeight() {
        return ENTRY_HEIGHT;
    }

    @Override
    public void save() {
        if (saveConsumer != null) {
            saveConsumer.accept(value);
        }
    }

    @Override
    public List<? extends Element> children() {
        return Collections.emptyList();
    }

    @Override
    public List<? extends Selectable> narratables() {
        return Collections.emptyList();
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
        super.render(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);

        int textColor = isEditable() ? 0xFFFFFF : 0xA0A0A0;
        int labelX = x + 6;
        int labelY = y + 6;
        int controlsX = x + entryWidth - 168;

        paletteX = controlsX;
        paletteY = y + 6;
        hueX = paletteX;
        hueY = paletteY + PALETTE_SIZE + CONTROL_GAP;
        alphaX = paletteX;
        alphaY = hueY + BAR_HEIGHT + CONTROL_GAP;
        previewX = paletteX + PALETTE_SIZE + CONTROL_GAP;
        previewY = paletteY;

        context.drawTextWithShadow(client.textRenderer, getFieldName(), labelX, labelY, textColor);
        context.drawText(client.textRenderer, Text.translatable("itemglow.config.outlineColor.preview"), labelX, labelY + 16, 0xC0C0C0, false);
        context.drawText(client.textRenderer, Text.literal(formatColor(value)), labelX, labelY + 28, 0xE0E0E0, false);
        context.drawText(client.textRenderer, Text.translatable("itemglow.config.outlineColor.hue"), labelX, hueY + 1, 0xC0C0C0, false);
        context.drawText(client.textRenderer, Text.translatable("itemglow.config.outlineColor.alpha"), labelX, alphaY + 1, 0xC0C0C0, false);

        drawPalette(context);
        drawHueBar(context);
        drawAlphaBar(context);
        drawPreview(context, mouseX, mouseY);
        drawIndicators(context);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isEditable()) {
            return false;
        }

        if (button == 1 && isInsidePreview(mouseX, mouseY)) {
            setColor(defaultColor);
            return true;
        }

        if (button != 0) {
            return false;
        }

        if (isInsidePalette(mouseX, mouseY)) {
            dragMode = DRAG_PALETTE;
            updatePalette(mouseX, mouseY);
            return true;
        }

        if (isInsideHueBar(mouseX, mouseY)) {
            dragMode = DRAG_HUE;
            updateHue(mouseX);
            return true;
        }

        if (isInsideAlphaBar(mouseX, mouseY)) {
            dragMode = DRAG_ALPHA;
            updateAlpha(mouseX);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!isEditable() || button != 0) {
            return false;
        }

        if (dragMode == DRAG_PALETTE) {
            updatePalette(mouseX, mouseY);
            return true;
        }
        if (dragMode == DRAG_HUE) {
            updateHue(mouseX);
            return true;
        }
        if (dragMode == DRAG_ALPHA) {
            updateAlpha(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragMode = DRAG_NONE;
        return false;
    }

    private void drawPalette(DrawContext context) {
        for (int dx = 0; dx < PALETTE_SIZE; dx += SAMPLE_STEP) {
            float sampleSaturation = dx / (float) (PALETTE_SIZE - 1);
            for (int dy = 0; dy < PALETTE_SIZE; dy += SAMPLE_STEP) {
                float sampleBrightness = 1.0F - dy / (float) (PALETTE_SIZE - 1);
                int rgb = MathHelper.hsvToRgb(hue, sampleSaturation, sampleBrightness);
                int color = ColorHelper.Argb.getArgb(255, ColorHelper.Argb.getRed(rgb), ColorHelper.Argb.getGreen(rgb), ColorHelper.Argb.getBlue(rgb));
                context.fill(paletteX + dx, paletteY + dy, paletteX + dx + SAMPLE_STEP, paletteY + dy + SAMPLE_STEP, color);
            }
        }

        drawBorder(context, paletteX, paletteY, PALETTE_SIZE, PALETTE_SIZE, 0xFF202020);
    }

    private void drawHueBar(DrawContext context) {
        for (int dx = 0; dx < PALETTE_SIZE; dx++) {
            float sampleHue = dx / (float) (PALETTE_SIZE - 1);
            int rgb = MathHelper.hsvToRgb(sampleHue, 1.0F, 1.0F);
            int color = ColorHelper.Argb.getArgb(255, ColorHelper.Argb.getRed(rgb), ColorHelper.Argb.getGreen(rgb), ColorHelper.Argb.getBlue(rgb));
            context.fill(hueX + dx, hueY, hueX + dx + 1, hueY + BAR_HEIGHT, color);
        }

        drawBorder(context, hueX, hueY, PALETTE_SIZE, BAR_HEIGHT, 0xFF202020);
    }

    private void drawAlphaBar(DrawContext context) {
        int baseRgb = MathHelper.hsvToRgb(hue, saturation, brightness);
        drawCheckerboard(context, alphaX, alphaY, PALETTE_SIZE, BAR_HEIGHT);

        for (int dx = 0; dx < PALETTE_SIZE; dx++) {
            float sampleAlpha = dx / (float) (PALETTE_SIZE - 1);
            int color = ColorHelper.Argb.getArgb(
                    Math.round(sampleAlpha * 255.0F),
                    ColorHelper.Argb.getRed(baseRgb),
                    ColorHelper.Argb.getGreen(baseRgb),
                    ColorHelper.Argb.getBlue(baseRgb)
            );
            context.fill(alphaX + dx, alphaY, alphaX + dx + 1, alphaY + BAR_HEIGHT, color);
        }

        drawBorder(context, alphaX, alphaY, PALETTE_SIZE, BAR_HEIGHT, 0xFF202020);
    }

    private void drawPreview(DrawContext context, int mouseX, int mouseY) {
        drawCheckerboard(context, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE);
        context.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, value);
        drawBorder(context, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, isInsidePreview(mouseX, mouseY) ? 0xFFFFFFFF : 0xFF202020);

        context.drawText(client.textRenderer, Text.translatable("itemglow.config.outlineColor.current"), previewX, previewY + PREVIEW_SIZE + 4, 0xC0C0C0, false);
        context.drawText(client.textRenderer, Text.translatable("itemglow.config.outlineColor.defaultValue"), previewX, previewY + PREVIEW_SIZE + 16, 0x909090, false);
    }

    private void drawIndicators(DrawContext context) {
        int paletteMarkerX = paletteX + Math.round(saturation * (PALETTE_SIZE - 1));
        int paletteMarkerY = paletteY + Math.round((1.0F - brightness) * (PALETTE_SIZE - 1));
        context.fill(paletteMarkerX - 2, paletteMarkerY - 2, paletteMarkerX + 3, paletteMarkerY + 3, 0xFF000000);
        context.fill(paletteMarkerX - 1, paletteMarkerY - 1, paletteMarkerX + 2, paletteMarkerY + 2, 0xFFFFFFFF);

        int hueMarkerX = hueX + Math.round(hue * (PALETTE_SIZE - 1));
        context.fill(hueMarkerX - 1, hueY - 1, hueMarkerX + 1, hueY + BAR_HEIGHT + 1, 0xFFFFFFFF);

        int alphaMarkerX = alphaX + Math.round(alpha * (PALETTE_SIZE - 1));
        context.fill(alphaMarkerX - 1, alphaY - 1, alphaMarkerX + 1, alphaY + BAR_HEIGHT + 1, 0xFFFFFFFF);
    }

    private void updatePalette(double mouseX, double mouseY) {
        saturation = MathHelper.clamp((float) ((mouseX - paletteX) / (double) (PALETTE_SIZE - 1)), 0.0F, 1.0F);
        brightness = 1.0F - MathHelper.clamp((float) ((mouseY - paletteY) / (double) (PALETTE_SIZE - 1)), 0.0F, 1.0F);
        syncColorFromHsv();
    }

    private void updateHue(double mouseX) {
        hue = MathHelper.clamp((float) ((mouseX - hueX) / (double) (PALETTE_SIZE - 1)), 0.0F, 1.0F);
        syncColorFromHsv();
    }

    private void updateAlpha(double mouseX) {
        alpha = MathHelper.clamp((float) ((mouseX - alphaX) / (double) (PALETTE_SIZE - 1)), 0.0F, 1.0F);
        syncColorFromHsv();
    }

    private void syncColorFromHsv() {
        int rgb = MathHelper.hsvToRgb(hue, saturation, brightness);
        value = ColorHelper.Argb.getArgb(
                Math.round(alpha * 255.0F),
                ColorHelper.Argb.getRed(rgb),
                ColorHelper.Argb.getGreen(rgb),
                ColorHelper.Argb.getBlue(rgb)
        );
    }

    private void setColor(int color) {
        value = color;
        updateHsvFromColor(color);
    }

    private void updateHsvFromColor(int color) {
        float red = ColorHelper.Argb.getRed(color) / 255.0F;
        float green = ColorHelper.Argb.getGreen(color) / 255.0F;
        float blue = ColorHelper.Argb.getBlue(color) / 255.0F;
        alpha = ColorHelper.Argb.getAlpha(color) / 255.0F;

        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;

        brightness = max;
        saturation = max == 0.0F ? 0.0F : delta / max;

        if (delta == 0.0F) {
            hue = 0.0F;
            return;
        }

        if (max == red) {
            hue = ((green - blue) / delta) % 6.0F;
        } else if (max == green) {
            hue = (blue - red) / delta + 2.0F;
        } else {
            hue = (red - green) / delta + 4.0F;
        }

        hue /= 6.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }
    }

    private boolean isInsidePalette(double mouseX, double mouseY) {
        return mouseX >= paletteX && mouseX < paletteX + PALETTE_SIZE && mouseY >= paletteY && mouseY < paletteY + PALETTE_SIZE;
    }

    private boolean isInsideHueBar(double mouseX, double mouseY) {
        return mouseX >= hueX && mouseX < hueX + PALETTE_SIZE && mouseY >= hueY && mouseY < hueY + BAR_HEIGHT;
    }

    private boolean isInsideAlphaBar(double mouseX, double mouseY) {
        return mouseX >= alphaX && mouseX < alphaX + PALETTE_SIZE && mouseY >= alphaY && mouseY < alphaY + BAR_HEIGHT;
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        return mouseX >= previewX && mouseX < previewX + PREVIEW_SIZE && mouseY >= previewY && mouseY < previewY + PREVIEW_SIZE;
    }

    private void drawCheckerboard(DrawContext context, int startX, int startY, int width, int height) {
        for (int dx = 0; dx < width; dx += 4) {
            for (int dy = 0; dy < height; dy += 4) {
                boolean even = ((dx + dy) / 4) % 2 == 0;
                context.fill(startX + dx, startY + dy, startX + dx + 4, startY + dy + 4, even ? 0xFFC0C0C0 : 0xFF7A7A7A);
            }
        }
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x - 1, y - 1, x + width + 1, y, color);
        context.fill(x - 1, y + height, x + width + 1, y + height + 1, color);
        context.fill(x - 1, y, x, y + height, color);
        context.fill(x + width, y, x + width + 1, y + height, color);
    }

    private String formatColor(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }
}
