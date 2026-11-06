package com.awildencounter.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class Theme {
    public static final int MAIN = 0xFFC8DD3C;
    public static final int MAIN_DARK = 0xFF7A8725;
    public static final int MAIN_DEEP = 0xFF4A5216;
    public static final int ACCENT = 0xFFED9130;
    public static final int ACCENT_HOVER = 0xFFF5A852;
    public static final int ACCENT_DARK = 0xFFB96E1F;

    public static final int PANEL = 0xD0262B0B;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFD8E2AE;
    public static final int TEXT_DISABLED = 0xFF7F8758;
    public static final int TEXT_ON_MAIN = 0xFF262B0B;
    public static final int TEXT_ON_MAIN_MUTED = 0xFF4E5720;
    public static final int TEXT_ON_ACCENT = 0xFF2E1804;
    public static final int ON = 0xFFC8DD3C;
    public static final int OFF = 0xFFD8E2AE;
    public static final int DANGER = 0xFFC8452C;
    public static final int DANGER_HOVER = 0xFFE0573C;

    private Theme() {
    }

    public static void button(DrawContext context, TextRenderer font, String label, int x, int y, int width,
                              int height, boolean primary, boolean hovered, boolean enabled) {
        int fill;
        int edge;
        int textColour;
        if (!enabled) {
            fill = MAIN_DEEP;
            edge = MAIN_DEEP;
            textColour = TEXT_DISABLED;
        } else if (primary) {
            fill = hovered ? ACCENT_HOVER : ACCENT;
            edge = ACCENT_DARK;
            textColour = TEXT_ON_ACCENT;
        } else {
            fill = hovered ? MAIN : MAIN_DARK;
            edge = MAIN_DEEP;
            textColour = hovered ? TEXT_ON_MAIN : TEXT;
        }
        context.fill(x, y, x + width, y + height, fill);
        context.fill(x, y + height - 2, x + width, y + height, edge);
        context.drawText(font, Text.literal(label), x + (width - font.getWidth(label)) / 2,
                y + (height - 8) / 2 - 1, textColour, false);
    }

    public static void dangerButton(DrawContext context, TextRenderer font, String label, int x, int y, int width,
                                    int height, boolean hovered) {
        context.fill(x, y, x + width, y + height, hovered ? DANGER_HOVER : DANGER);
        context.fill(x, y + height - 2, x + width, y + height, 0xFF7A2615);
        context.drawText(font, Text.literal(label), x + (width - font.getWidth(label)) / 2,
                y + (height - 8) / 2 - 1, TEXT, false);
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
