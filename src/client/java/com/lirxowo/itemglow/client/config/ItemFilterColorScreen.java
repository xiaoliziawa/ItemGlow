package com.lirxowo.itemglow.client.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;
import java.util.function.Consumer;

public class ItemFilterColorScreen extends Screen {
    private static final int PALETTE_SIZE = 128;
    private static final int BAR_HEIGHT = 14;
    private static final int PREVIEW_SIZE = 42;
    private static final int SAMPLE_STEP = 2;

    private final Screen parent;
    private final Consumer<Integer> saveConsumer;
    private final int defaultColor;

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
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;

    public ItemFilterColorScreen(Screen parent, Text title, int currentColor, int defaultColor, Consumer<Integer> saveConsumer) {
        super(title);
        this.parent = parent;
        this.value = currentColor;
        this.defaultColor = defaultColor;
        this.saveConsumer = saveConsumer;
        updateHsvFromColor(currentColor);
    }

    @Override
    protected void init() {
        layout();
        int buttonY = panelBottom - 28;
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> saveAndClose())
                .dimensions(this.width / 2 - 102, buttonY, 96, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
                .dimensions(this.width / 2 + 6, buttonY, 96, 20)
                .build());
    }

    private void saveAndClose() {
        saveConsumer.accept(value);
        this.client.setScreen(parent);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        layout();

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelTop + 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("itemglow.config.filters.colorScreenHint"), this.width / 2, panelTop + 28, 0xB6B6B6);
        context.drawText(this.textRenderer, Text.literal(formatColor(value)), previewX, previewY + PREVIEW_SIZE + 8, 0xE0E0E0, false);
        context.drawText(this.textRenderer, Text.translatable("itemglow.config.outlineColor.hue"), hueX, hueY - 10, 0xC0C0C0, false);
        context.drawText(this.textRenderer, Text.translatable("itemglow.config.outlineColor.alpha"), alphaX, alphaY - 10, 0xC0C0C0, false);

        drawPalette(context);
        drawHueBar(context);
        drawAlphaBar(context);
        drawPreview(context, mouseX, mouseY);
        drawIndicators(context);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && isInsidePreview(mouseX, mouseY)) {
            setColor(defaultColor);
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragMode = DRAG_NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void layout() {
        panelLeft = this.width / 2 - 122;
        panelRight = this.width / 2 + 122;
        panelTop = 24;
        panelBottom = this.height - 18;

        int buttonY = panelBottom - 28;
        int contentBottom = buttonY - 12;

        int totalContentHeight = PALETTE_SIZE + 16 + BAR_HEIGHT + 18 + BAR_HEIGHT;
        int contentStartY = panelTop + 52;
        int availableHeight = contentBottom - contentStartY;

        int paletteSizeUsed = PALETTE_SIZE;
        if (totalContentHeight > availableHeight) {
            paletteSizeUsed = Math.max(64, availableHeight - 16 - BAR_HEIGHT - 18 - BAR_HEIGHT);
        }

        paletteX = this.width / 2 - 104;
        paletteY = contentStartY;
        hueX = paletteX;
        hueY = paletteY + paletteSizeUsed + 16;
        alphaX = paletteX;
        alphaY = hueY + BAR_HEIGHT + 18;
        previewX = paletteX + paletteSizeUsed + 18;
        previewY = paletteY;
    }

    private int getEffectivePaletteSize() {
        int buttonY = panelBottom - 28;
        int contentBottom = buttonY - 12;
        int contentStartY = panelTop + 52;
        int availableHeight = contentBottom - contentStartY;
        int totalContentHeight = PALETTE_SIZE + 16 + BAR_HEIGHT + 18 + BAR_HEIGHT;
        if (totalContentHeight > availableHeight) {
            return Math.max(64, availableHeight - 16 - BAR_HEIGHT - 18 - BAR_HEIGHT);
        }
        return PALETTE_SIZE;
    }

    private void drawPalette(DrawContext context) {
        int size = getEffectivePaletteSize();
        for (int dx = 0; dx < size; dx += SAMPLE_STEP) {
            float sampleSaturation = dx / (float) (size - 1);
            for (int dy = 0; dy < size; dy += SAMPLE_STEP) {
                float sampleBrightness = 1.0F - dy / (float) (size - 1);
                int rgb = MathHelper.hsvToRgb(hue, sampleSaturation, sampleBrightness);
                int color = ColorHelper.Argb.getArgb(255, ColorHelper.Argb.getRed(rgb), ColorHelper.Argb.getGreen(rgb), ColorHelper.Argb.getBlue(rgb));
                context.fill(paletteX + dx, paletteY + dy, paletteX + dx + SAMPLE_STEP, paletteY + dy + SAMPLE_STEP, color);
            }
        }
        drawBorder(context, paletteX, paletteY, size, size, 0xFF202020);
    }

    private void drawHueBar(DrawContext context) {
        int size = getEffectivePaletteSize();
        for (int dx = 0; dx < size; dx++) {
            float sampleHue = dx / (float) (size - 1);
            int rgb = MathHelper.hsvToRgb(sampleHue, 1.0F, 1.0F);
            int color = ColorHelper.Argb.getArgb(255, ColorHelper.Argb.getRed(rgb), ColorHelper.Argb.getGreen(rgb), ColorHelper.Argb.getBlue(rgb));
            context.fill(hueX + dx, hueY, hueX + dx + 1, hueY + BAR_HEIGHT, color);
        }
        drawBorder(context, hueX, hueY, size, BAR_HEIGHT, 0xFF202020);
    }

    private void drawAlphaBar(DrawContext context) {
        int size = getEffectivePaletteSize();
        int baseRgb = MathHelper.hsvToRgb(hue, saturation, brightness);
        drawCheckerboard(context, alphaX, alphaY, size, BAR_HEIGHT);
        for (int dx = 0; dx < size; dx++) {
            float sampleAlpha = dx / (float) (size - 1);
            int color = ColorHelper.Argb.getArgb(
                    Math.round(sampleAlpha * 255.0F),
                    ColorHelper.Argb.getRed(baseRgb),
                    ColorHelper.Argb.getGreen(baseRgb),
                    ColorHelper.Argb.getBlue(baseRgb)
            );
            context.fill(alphaX + dx, alphaY, alphaX + dx + 1, alphaY + BAR_HEIGHT, color);
        }
        drawBorder(context, alphaX, alphaY, size, BAR_HEIGHT, 0xFF202020);
    }

    private void drawPreview(DrawContext context, int mouseX, int mouseY) {
        drawCheckerboard(context, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE);
        context.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, value);
        drawBorder(context, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, isInsidePreview(mouseX, mouseY) ? 0xFFFFFFFF : 0xFF202020);
        context.drawText(this.textRenderer, Text.translatable("itemglow.config.outlineColor.defaultValue"), previewX - 4, previewY + PREVIEW_SIZE + 20, 0x909090, false);
    }

    private void drawIndicators(DrawContext context) {
        int size = getEffectivePaletteSize();
        int paletteMarkerX = paletteX + Math.round(saturation * (size - 1));
        int paletteMarkerY = paletteY + Math.round((1.0F - brightness) * (size - 1));
        context.fill(paletteMarkerX - 2, paletteMarkerY - 2, paletteMarkerX + 3, paletteMarkerY + 3, 0xFF000000);
        context.fill(paletteMarkerX - 1, paletteMarkerY - 1, paletteMarkerX + 2, paletteMarkerY + 2, 0xFFFFFFFF);

        int hueMarkerX = hueX + Math.round(hue * (size - 1));
        context.fill(hueMarkerX - 1, hueY - 1, hueMarkerX + 1, hueY + BAR_HEIGHT + 1, 0xFFFFFFFF);

        int alphaMarkerX = alphaX + Math.round(alpha * (size - 1));
        context.fill(alphaMarkerX - 1, alphaY - 1, alphaMarkerX + 1, alphaY + BAR_HEIGHT + 1, 0xFFFFFFFF);
    }

    private void updatePalette(double mouseX, double mouseY) {
        int size = getEffectivePaletteSize();
        saturation = MathHelper.clamp((float) ((mouseX - paletteX) / (double) (size - 1)), 0.0F, 1.0F);
        brightness = 1.0F - MathHelper.clamp((float) ((mouseY - paletteY) / (double) (size - 1)), 0.0F, 1.0F);
        syncColorFromHsv();
    }

    private void updateHue(double mouseX) {
        int size = getEffectivePaletteSize();
        hue = MathHelper.clamp((float) ((mouseX - hueX) / (double) (size - 1)), 0.0F, 1.0F);
        syncColorFromHsv();
    }

    private void updateAlpha(double mouseX) {
        int size = getEffectivePaletteSize();
        alpha = MathHelper.clamp((float) ((mouseX - alphaX) / (double) (size - 1)), 0.0F, 1.0F);
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
        int size = getEffectivePaletteSize();
        return mouseX >= paletteX && mouseX < paletteX + size && mouseY >= paletteY && mouseY < paletteY + size;
    }

    private boolean isInsideHueBar(double mouseX, double mouseY) {
        int size = getEffectivePaletteSize();
        return mouseX >= hueX && mouseX < hueX + size && mouseY >= hueY && mouseY < hueY + BAR_HEIGHT;
    }

    private boolean isInsideAlphaBar(double mouseX, double mouseY) {
        int size = getEffectivePaletteSize();
        return mouseX >= alphaX && mouseX < alphaX + size && mouseY >= alphaY && mouseY < alphaY + BAR_HEIGHT;
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
