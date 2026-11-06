package com.awildencounter.client.gui;

import com.awildencounter.client.zone.ClientZones;
import com.awildencounter.config.DimensionNames;
import com.awildencounter.net.DeleteZonePayload;
import com.awildencounter.zone.WildZone;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ZoneManagerScreen extends Screen {
    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 28;
    private static final int BUTTON_H = 16;
    private static final long DELETE_CONFIRM_MS = 3000;

    private final Screen parent;
    private int scroll = 0;
    private String armedDelete = null;
    private long armedAt = 0;

    public ZoneManagerScreen(Screen parent) {
        super(Text.literal("Wild Zones"));
        this.parent = parent;
    }

    private List<WildZone> zones() {
        String here = client == null || client.world == null ? ""
                : client.world.getRegistryKey().getValue().toString();
        List<WildZone> zones = new ArrayList<>(ClientZones.all());
        zones.sort(Comparator.comparing((WildZone zone) -> !zone.dimension.equals(here))
                .thenComparing(zone -> zone.dimension)
                .thenComparing(zone -> zone.name.toLowerCase(Locale.ROOT)));
        return zones;
    }

    private int listLeft() {
        return Math.max(10, (width - 520) / 2);
    }

    private int listRight() {
        return width - listLeft();
    }

    private int listTop() {
        return HEADER_H + 8;
    }

    private int listBottom() {
        return height - FOOTER_H - 8;
    }

    private void clampScroll() {
        int max = Math.max(0, zones().size() * ROW_H - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private record RowButton(String id, String label, int x, int width) {
    }

    private List<RowButton> buttons(WildZone zone) {
        List<RowButton> buttons = new ArrayList<>();
        int x = listRight() - 6;
        boolean armed = zone.id.equals(armedDelete) && Util.getMeasuringTimeMs() - armedAt < DELETE_CONFIRM_MS;
        String[][] ids = {
                {"delete", armed ? Tr.get("zone.delete_sure", "Sure?") : Tr.get("zone.delete", "Delete")},
                {"teleport", Tr.get("zones.teleport", "Teleport")},
                {"edit", Tr.get("zones.edit", "Edit")}};
        for (String[] id : ids) {
            int w = Math.max(textRenderer.getWidth(id[1]),
                    "delete".equals(id[0]) ? textRenderer.getWidth(Tr.get("zone.delete", "Delete")) : 0) + 14;
            x -= w;
            buttons.add(new RowButton(id[0], id[1], x, w));
            x -= 4;
        }
        return buttons;
    }

    private int backX() {
        return width - 12 - backW();
    }

    private int backW() {
        return textRenderer.getWidth(Tr.get("ui.back", "Back")) + 28;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        clampScroll();
        context.fill(0, 0, width, HEADER_H, Theme.MAIN);
        context.fill(0, HEADER_H - 3, width, HEADER_H, Theme.ACCENT);
        List<WildZone> zones = zones();
        String title = Tr.get("zones.title", "Wild Zones (%s)", zones.size());
        context.drawText(textRenderer, title, 12, 11, Theme.TEXT_ON_MAIN, false);

        int left = listLeft();
        int right = listRight();
        int top = listTop();
        int bottom = listBottom();
        context.fill(left - 4, top - 4, right + 4, bottom + 4, Theme.PANEL);
        if (zones.isEmpty()) {
            for (var line : textRenderer.wrapLines(Text.literal(Tr.get("ui.zones.none", "There are no Wild "
                    + "Zones yet. Hold a Bone: right click sets one corner, left click the other, and crouching twice "
                    + "makes the zone.")), right - left - 12)) {
                context.drawText(textRenderer, line, left + 6, top + 6, Theme.TEXT_MUTED, false);
                top += 11;
            }
        }
        context.enableScissor(left - 4, listTop() - 2, right + 4, bottom + 2);
        int y = listTop() - scroll;
        for (WildZone zone : zones) {
            if (y + ROW_H >= listTop() && y <= bottom) {
                drawRow(context, zone, y, mouseX, mouseY);
            }
            y += ROW_H;
        }
        context.disableScissor();

        context.fill(0, height - FOOTER_H, width, height, Theme.MAIN);
        context.fill(0, height - FOOTER_H, width, height - FOOTER_H + 2, Theme.ACCENT);
        String hint = Tr.get("zones.hint", "Hold a Bone to see them; right click one to open its menu.");
        if (12 + textRenderer.getWidth(hint) < backX() - 10) {
            context.drawText(textRenderer, hint, 12, height - FOOTER_H + 11, Theme.TEXT_ON_MAIN_MUTED, false);
        }
        int footerY = height - FOOTER_H + 6;
        Theme.button(context, textRenderer, Tr.get("ui.back", "Back"), backX(), footerY, backW(), 18, true,
                Theme.inside(mouseX, mouseY, backX(), footerY, backW(), 18), true);
    }

    private void drawRow(DrawContext context, WildZone zone, int y, int mouseX, int mouseY) {
        int left = listLeft();
        int right = listRight();
        boolean hovered = Theme.inside(mouseX, mouseY, left, y, right - left, ROW_H - 2)
                && mouseY >= listTop() && mouseY < listBottom();
        if (hovered) {
            context.fill(left, y, right, y + ROW_H - 2, 0x22FFFFFF);
        }
        context.fill(left + 4, y + 6, left + 12, y + 14, 0xFF000000 | zone.color);
        List<RowButton> buttons = buttons(zone);
        int textRight = buttons.get(buttons.size() - 1).x() - 8;
        context.drawText(textRenderer, textRenderer.trimToWidth(zone.name, textRight - left - 18), left + 18, y + 4,
                Theme.TEXT, false);
        String details = DimensionNames.defaultName(zone.dimension) + " · " + zone.size() + " · "
                + zone.minX + " " + zone.minY + " " + zone.minZ;
        if (zone.hasFilter()) {
            details += " · " + Tr.get("zones.filtered", "Whitelist %1$s, Blacklist %2$s", zone.whitelist.size(),
                    zone.blacklist.size());
        }
        context.drawText(textRenderer, textRenderer.trimToWidth(details, textRight - left - 18), left + 18, y + 15,
                Theme.TEXT_MUTED, false);
        for (RowButton button : buttons) {
            int buttonY = y + (ROW_H - 2 - BUTTON_H) / 2;
            boolean over = Theme.inside(mouseX, mouseY, button.x(), buttonY, button.width(), BUTTON_H);
            if ("delete".equals(button.id())) {
                Theme.dangerButton(context, textRenderer, button.label(), button.x(), buttonY, button.width(),
                        BUTTON_H, over);
            } else {
                Theme.button(context, textRenderer, button.label(), button.x(), buttonY, button.width(), BUTTON_H,
                        "edit".equals(button.id()), over, true);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int footerY = height - FOOTER_H + 6;
        if (Theme.inside(mouseX, mouseY, backX(), footerY, backW(), 18)) {
            playClick();
            close();
            return true;
        }
        if (mouseY < listTop() || mouseY >= listBottom()) {
            return false;
        }
        int y = listTop() - scroll;
        for (WildZone zone : zones()) {
            int buttonY = y + (ROW_H - 2 - BUTTON_H) / 2;
            for (RowButton row : buttons(zone)) {
                if (Theme.inside(mouseX, mouseY, row.x(), buttonY, row.width(), BUTTON_H)) {
                    playClick();
                    act(zone, row.id());
                    return true;
                }
            }
            y += ROW_H;
        }
        return false;
    }

    private void act(WildZone zone, String action) {
        if (client == null || client.player == null) {
            return;
        }
        switch (action) {
            case "edit" -> client.setScreen(new ZoneScreen(this, zone));
            case "teleport" -> {
                int x = (zone.minX + zone.maxX) / 2;
                int z = (zone.minZ + zone.maxZ) / 2;
                client.setScreen(null);
                client.player.networkHandler.sendChatCommand("execute in " + zone.dimension + " run tp @s " + x + " "
                        + (zone.maxY + 1) + " " + z);
            }
            default -> {
                if (zone.id.equals(armedDelete) && Util.getMeasuringTimeMs() - armedAt < DELETE_CONFIRM_MS) {
                    armedDelete = null;
                    if (ClientPlayNetworking.canSend(DeleteZonePayload.ID)) {
                        ClientPlayNetworking.send(new DeleteZonePayload(zone.id));
                    }
                } else {
                    armedDelete = zone.id;
                    armedAt = Util.getMeasuringTimeMs();
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) (verticalAmount * ROW_H);
        clampScroll();
        return true;
    }

    private void playClick() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
