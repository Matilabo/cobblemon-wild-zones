package com.awildencounter.client.zone;

import com.awildencounter.AWildEncounter;
import com.awildencounter.client.gui.SpeciesIcons;
import com.awildencounter.client.gui.Tr;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ZoneBannerHud {
    private static final long FADE_IN_MS = 250;
    private static final long HOLD_MS = 5000;
    private static final long FADE_OUT_MS = 800;
    private static final int TOP = 6;
    private static final int PADDING = 6;
    private static final int ICON = 20;
    private static final int GAP = 2;
    private static final int PER_ROW = 10;
    private static final int MAX_ROWS = 2;
    private static final int BACKGROUND = 0xC0101208;
    private static final int MUTED = 0xD8E2AE;

    private record Entry(String entry, double percent) {
    }

    private static String name = null;
    private static int color = 0xFFFFFF;
    private static boolean rates = false;
    private static int total = 0;
    private static List<Entry> entries = List.of();
    private static long shownAt = -1;

    private ZoneBannerHud() {
    }

    public static void show(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<Entry> parsed = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("entries")) {
                JsonArray pair = element.getAsJsonArray();
                parsed.add(new Entry(pair.get(0).getAsString(), pair.get(1).getAsDouble()));
            }
            name = root.get("name").getAsString();
            color = root.get("color").getAsInt() & 0xFFFFFF;
            rates = root.get("rates").getAsBoolean();
            total = root.get("total").getAsInt();
            entries = parsed;
            shownAt = Util.getMeasuringTimeMs();
        } catch (Exception e) {
            AWildEncounter.LOGGER.debug("Could not read a Zone Banner: {}", e.toString());
        }
    }

    public static void clear() {
        name = null;
        entries = List.of();
        shownAt = -1;
    }

    private static float alpha(long elapsed) {
        if (elapsed < FADE_IN_MS) {
            return elapsed / (float) FADE_IN_MS;
        }
        long fading = elapsed - FADE_IN_MS - HOLD_MS;
        return fading <= 0 ? 1.0f : 1.0f - fading / (float) FADE_OUT_MS;
    }

    private static int withAlpha(int rgb, int baseAlpha, float alpha) {
        return ((int) (baseAlpha * alpha) << 24) | (rgb & 0xFFFFFF);
    }

    private static String percentText(double percent) {
        if (percent < 0.1) {
            return "<0.1%";
        }
        return String.format(Locale.ROOT, percent < 10 ? "%.1f%%" : "%.0f%%", percent);
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (name == null || shownAt < 0 || client.options.hudHidden || client.player == null) {
            return;
        }
        long elapsed = Util.getMeasuringTimeMs() - shownAt;
        float alpha = alpha(elapsed);
        if (alpha <= 0.0f) {
            clear();
            return;
        }
        if (alpha < 0.05f) {
            return;
        }
        TextRenderer font = client.textRenderer;

        Text title = Text.literal(name).formatted(Formatting.BOLD);
        int shown = Math.min(entries.size(), PER_ROW * MAX_ROWS);
        int more = Math.max(0, total - shown);
        String subtitle;
        if (total == 0) {
            subtitle = Tr.get("banner.empty", "No Cobblemon turn up here right now");
        } else if (more > 0) {
            subtitle = Tr.get("banner.more", "%s Cobblemon here, showing the first %s", total, shown);
        } else {
            subtitle = total == 1 ? Tr.get("banner.one", "1 Cobblemon here")
                    : Tr.get("banner.count", "%s Cobblemon here", total);
        }

        int cell = ICON;
        if (rates) {
            for (int i = 0; i < shown; i++) {
                cell = Math.max(cell, font.getWidth(percentText(entries.get(i).percent())));
            }
        }
        cell += GAP * 2;
        int columns = Math.min(PER_ROW, shown);
        int rows = (shown + PER_ROW - 1) / PER_ROW;
        int rowHeight = ICON + (rates ? 10 : 0) + GAP;
        int gridW = columns * cell;
        int gridH = rows * rowHeight;
        int innerW = Math.max(gridW, Math.max(font.getWidth(title), font.getWidth(subtitle)));
        int innerH = 9 + 2 + 9 + (shown > 0 ? 5 + gridH : 0);

        int width = innerW + PADDING * 2;
        int height = innerH + PADDING * 2;
        int left = (context.getScaledWindowWidth() - width) / 2;
        int top = TOP;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        context.fill(left, top, left + width, top + height, withAlpha(BACKGROUND, BACKGROUND >>> 24, alpha));
        context.fill(left, top, left + width, top + 2, withAlpha(color, 255, alpha));

        int y = top + PADDING;
        int textColor = withAlpha(0xFFFFFF, 255, alpha);
        context.drawTextWithShadow(font, title, left + (width - font.getWidth(title)) / 2, y, textColor);
        y += 11;
        context.drawTextWithShadow(font, subtitle, left + (width - font.getWidth(subtitle)) / 2, y,
                withAlpha(MUTED, 255, alpha));
        y += 9 + 5;

        int gridLeft = left + (width - gridW) / 2;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        for (int i = 0; i < shown; i++) {
            Entry entry = entries.get(i);
            int rowItems = Math.min(PER_ROW, shown - (i / PER_ROW) * PER_ROW);
            int rowLeft = gridLeft + (columns - rowItems) * cell / 2;
            int x = rowLeft + (i % PER_ROW) * cell;
            int cellTop = y + (i / PER_ROW) * rowHeight;
            int iconX = x + (cell - ICON) / 2;
            if (!SpeciesIcons.draw(context, entry.entry(), iconX, cellTop, ICON)) {
                context.drawTextWithShadow(font, "?", iconX + (ICON - font.getWidth("?")) / 2,
                        cellTop + (ICON - 8) / 2, withAlpha(MUTED, 255, alpha));
            }
            if (rates) {
                String text = percentText(entry.percent());
                context.drawTextWithShadow(font, text, x + (cell - font.getWidth(text)) / 2, cellTop + ICON + 1,
                        textColor);
            }
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        context.getMatrices().pop();
    }
}
