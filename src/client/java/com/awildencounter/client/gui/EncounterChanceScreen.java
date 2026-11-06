package com.awildencounter.client.gui;

import com.awildencounter.client.zone.ClientZones;
import com.awildencounter.client.ChunkSender;
import com.awildencounter.net.Chunks;
import com.awildencounter.zone.WildZone;
import com.awildencounter.zone.ZoneOdds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EncounterChanceScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int ICON = 16;
    private static final int FIELD_H = 18;
    private static final int STEP_W = 14;
    private static final int TIER_W = 76;
    private static final int WEIGHT_W = 44;
    private static final int PERCENT_W = 46;
    private static final int BUTTON_W = 70;
    private static final int BUTTON_H = 18;
    private static final int[] TIER_COLORS = {0xFFD8E2AE, 0xFF8FD14F, 0xFF5AA2F0, 0xFFC27BF0};

    private final ZoneScreen parent;
    private final WildZone zone;
    private final String savedMode;
    private final LinkedHashMap<String, String> savedRarities;
    private final LinkedHashMap<String, Double> savedWeights;
    private final LinkedHashMap<String, Double> savedTierOdds;

    private long requestedAt = -1;
    private ClientZones.Odds odds = null;
    private List<String> entries = List.of();
    private Map<String, Double> percent = Map.of();
    private boolean dirty = true;

    private final TextFieldWidget[] tierFields = new TextFieldWidget[ZoneOdds.TIERS.size()];
    private TextFieldWidget weightField;
    private String editingWeight = null;
    private boolean updating = false;
    private int scroll = 0;

    public EncounterChanceScreen(ZoneScreen parent, WildZone zone) {
        super(Text.literal("Encounter Chance"));
        this.parent = parent;
        this.zone = zone;
        this.savedMode = zone.oddsMode;
        this.savedRarities = new LinkedHashMap<>(zone.rarities);
        this.savedWeights = new LinkedHashMap<>(zone.weights);
        this.savedTierOdds = new LinkedHashMap<>(zone.tierOdds);
    }

    private int panelW() {
        return Math.min(440, width - 16);
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int right() {
        return left() + panelW();
    }

    private int top() {
        return 6;
    }

    private int bottom() {
        return height - 6;
    }

    private int modeY() {
        return top() + 28;
    }

    private List<OrderedText> description() {
        return textRenderer.wrapLines(Text.literal(modeDescription()), panelW() - 20);
    }

    private int descriptionY() {
        return modeY() + FIELD_H + 6;
    }

    private int tierY() {
        return descriptionY() + Math.min(3, description().size()) * 10 + 4;
    }

    private boolean rarity() {
        return ZoneOdds.RARITY.equals(zone.oddsMode);
    }

    private boolean weight() {
        return ZoneOdds.WEIGHT.equals(zone.oddsMode);
    }

    private int listTop() {
        return tierY() + (rarity() ? FIELD_H + 8 : 0);
    }

    private int listBottom() {
        return bottom() - BUTTON_H - 12;
    }

    private int tierGroupW() {
        return (panelW() - 20) / ZoneOdds.TIERS.size();
    }

    private int tierFieldX(int tier) {
        return left() + 10 + tier * tierGroupW() + tierGroupW() - 46;
    }

    private int percentX() {
        return right() - 10 - PERCENT_W;
    }

    private int controlX() {
        return percentX() - 6 - (rarity() ? TIER_W : STEP_W * 2 + WEIGHT_W + 4);
    }

    private int rowY(int index) {
        return listTop() + index * ROW_H - scroll;
    }

    private int buttonY() {
        return bottom() - BUTTON_H - 6;
    }

    private int doneX() {
        return right() - 10 - BUTTON_W;
    }

    private int cancelX() {
        return doneX() - 6 - BUTTON_W;
    }

    private int resetX() {
        return left() + 10;
    }

    private void clampScroll() {
        int max = Math.max(0, entries.size() * ROW_H - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    protected void init() {
        if (requestedAt < 0 && ChunkSender.canSend()) {
            requestedAt = Util.getMeasuringTimeMs();
            ChunkSender.send(Chunks.ZONE_ODDS, zone.toJson());
        }
        for (int i = 0; i < tierFields.length; i++) {
            String tier = ZoneOdds.TIERS.get(i);
            TextFieldWidget old = tierFields[i];
            TextFieldWidget field = new TextFieldWidget(textRenderer, tierFieldX(i), tierY(), 42, FIELD_H,
                    Text.literal(tier));
            field.setMaxLength(7);
            field.setTextPredicate(text -> text.matches("\\d{0,5}(\\.\\d{0,2})?"));
            field.setText(old != null ? old.getText() : number(ZoneOdds.tierOdds(zone, tier)));
            field.setChangedListener(text -> {
                if (!updating) {
                    zone.tierOdds.put(tier, parse(text, 0));
                    dirty = true;
                }
            });
            tierFields[i] = field;
            addSelectableChild(field);
        }
        weightField = new TextFieldWidget(textRenderer, 0, 0, WEIGHT_W, FIELD_H, Text.literal("weight"));
        weightField.setMaxLength(8);
        weightField.setTextPredicate(text -> text.matches("\\d{0,6}(\\.\\d{0,2})?"));
        weightField.setVisible(false);
        addSelectableChild(weightField);
        clampScroll();
    }

    private static double parse(String text, double fallback) {
        try {
            return text.isEmpty() ? 0 : Math.min(ZoneOdds.MAX_VALUE, Double.parseDouble(text));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
    }

    private void refresh() {
        ClientZones.Odds latest = ClientZones.odds();
        if (latest != null && latest != odds && latest.zoneId().equals(zone.id) && latest.receivedAt() >= requestedAt) {
            odds = latest;
            entries = new ArrayList<>(latest.base().keySet());
            dirty = true;
            clampScroll();
        }
        if (dirty && odds != null) {
            percent = ZoneOdds.percentages(zone, odds.base());
            dirty = false;
        }
    }

    private String modeDescription() {
        return switch (zone.oddsMode) {
            case ZoneOdds.RARITY -> Tr.get("zone.odds.rarity.desc", "Click a Cobblemon's tier to change it (right "
                    + "click goes back). A tier is picked first by its odds, among the tiers that have Cobblemon "
                    + "here, then one of its Cobblemon as Cobblemon weighs them.");
            case ZoneOdds.WEIGHT -> Tr.get("zone.odds.weight.desc", "Each Cobblemon's weight against the others, "
                    + "like in Cobblemon's spawn files: 10 unless changed, and 0 never turns up. Click a number to "
                    + "type it. Shift: ±10.");
            default -> odds != null && !odds.natural()
                    ? Tr.get("zone.odds.cobblemon.flat", "Every Cobblemon here is as likely as the next (Any Species, "
                    + "or a whitelist where nothing on it spawns naturally).")
                    : Tr.get("zone.odds.cobblemon.desc", "As Cobblemon weighs them: their spawn weights around the "
                    + "zone's center, for this time and weather. Natural Spawns only picks what can spawn where "
                    + "the player stands, so chances there can differ.");
        };
    }

    private void setMode(int direction) {
        commitWeight();
        List<String> modes = ZoneOdds.MODES;
        zone.oddsMode = modes.get(Math.floorMod(modes.indexOf(zone.oddsMode) + direction, modes.size()));
        dirty = true;
        playClick();
        clearAndInit();
    }

    private void cycleTier(String entry, int direction) {
        List<String> tiers = ZoneOdds.TIERS;
        String next = tiers.get(Math.floorMod(tiers.indexOf(ZoneOdds.tierOf(zone, entry)) + direction, tiers.size()));
        if (ZoneOdds.COMMON.equals(next)) {
            zone.rarities.remove(entry);
        } else {
            zone.rarities.put(entry, next);
        }
        dirty = true;
        playClick();
    }

    private void stepWeight(String entry, int direction) {
        double step = hasShiftDown() ? 10 : 1;
        double value = Math.max(0, Math.min(ZoneOdds.MAX_VALUE, ZoneOdds.weightOf(zone, entry) + direction * step));
        setWeight(entry, value);
        playClick();
    }

    private void setWeight(String entry, double value) {
        if (value == ZoneOdds.DEFAULT_WEIGHT) {
            zone.weights.remove(entry);
        } else {
            zone.weights.put(entry, value);
        }
        dirty = true;
    }

    private void startWeight(String entry) {
        editingWeight = entry;
        weightField.setText(number(ZoneOdds.weightOf(zone, entry)));
        weightField.setVisible(true);
        weightField.setFocused(true);
        setFocused(weightField);
        positionWeightField();
    }

    private void positionWeightField() {
        int index = editingWeight == null ? -1 : entries.indexOf(editingWeight);
        if (index < 0) {
            return;
        }
        int y = rowY(index) + 1;
        if (y < listTop() || y + FIELD_H > listBottom()) {
            commitWeight();
            return;
        }
        weightField.setX(controlX() + STEP_W + 2);
        weightField.setY(y);
    }

    private void commitWeight() {
        if (editingWeight == null) {
            return;
        }
        setWeight(editingWeight, parse(weightField.getText(), ZoneOdds.weightOf(zone, editingWeight)));
        editingWeight = null;
        weightField.setVisible(false);
        weightField.setFocused(false);
        setFocused(null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        refresh();
        renderBackground(context, mouseX, mouseY, delta);
        int left = left();
        int right = right();
        context.fill(left, top(), right, bottom(), Theme.PANEL);
        context.fill(left, top(), right, top() + 22, Theme.MAIN);
        context.fill(left, top() + 20, right, top() + 22, Theme.ACCENT);
        String heading = Tr.get("zone.chance.title", "%s: Encounter Chance", zone.name);
        context.drawText(textRenderer, textRenderer.trimToWidth(heading, panelW() - 20), left + 10, top() + 7,
                Theme.TEXT_ON_MAIN, false);

        int my = modeY();
        int mw = panelW() - 20;
        boolean modeHovered = Theme.inside(mouseX, mouseY, left + 10, my, mw, FIELD_H);
        context.fill(left + 10, my, left + 10 + mw, my + FIELD_H, modeHovered ? Theme.MAIN_DARK : Theme.MAIN_DEEP);
        context.fill(left + 10, my + FIELD_H - 2, left + 10 + mw, my + FIELD_H, Theme.ACCENT);
        context.drawText(textRenderer, "◀", left + 15, my + 5, Theme.ACCENT, false);
        context.drawText(textRenderer, "▶", left + 10 + mw - 11, my + 5, Theme.ACCENT, false);
        String mode = ZoneScreen.modeLabel(zone.oddsMode);
        context.drawText(textRenderer, mode, left + 10 + (mw - textRenderer.getWidth(mode)) / 2, my + 5, Theme.TEXT,
                false);

        int y = descriptionY();
        List<OrderedText> lines = description();
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            context.drawText(textRenderer, lines.get(i), left + 10, y, Theme.TEXT_MUTED, false);
            y += 10;
        }

        if (rarity()) {
            for (int i = 0; i < tierFields.length; i++) {
                String label = tierLabel(ZoneOdds.TIERS.get(i));
                int labelX = left + 10 + i * tierGroupW();
                context.drawText(textRenderer, textRenderer.trimToWidth(label, tierGroupW() - 50), labelX,
                        tierY() + 5, TIER_COLORS[i], false);
                tierFields[i].render(context, mouseX, mouseY, delta);
            }
        }

        drawList(context, mouseX, mouseY);
        if (editingWeight != null) {
            weightField.render(context, mouseX, mouseY, delta);
        }

        int by = buttonY();
        Theme.button(context, textRenderer, Tr.get("zone.odds.reset", "Reset"), resetX(), by, BUTTON_W, BUTTON_H,
                false, Theme.inside(mouseX, mouseY, resetX(), by, BUTTON_W, BUTTON_H), !ZoneOdds.COBBLEMON.equals(
                        zone.oddsMode));
        Theme.button(context, textRenderer, Tr.get("ui.cancel", "Cancel"), cancelX(), by, BUTTON_W, BUTTON_H, false,
                Theme.inside(mouseX, mouseY, cancelX(), by, BUTTON_W, BUTTON_H), true);
        Theme.button(context, textRenderer, Tr.get("ui.done", "Done"), doneX(), by, BUTTON_W, BUTTON_H, true,
                Theme.inside(mouseX, mouseY, doneX(), by, BUTTON_W, BUTTON_H), true);

        if (rarity()) {
            for (int i = 0; i < tierFields.length; i++) {
                if (Theme.inside(mouseX, mouseY, left + 10 + i * tierGroupW(), tierY(), tierGroupW() - 4, FIELD_H)) {
                    context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(Tr.get(
                            "zone.odds.tier.tip", "The odds of this tier against the others that have Cobblemon "
                                    + "here. 60, 30, 7 and 3 by default.")), 200), mouseX, mouseY);
                }
            }
        }
    }

    private static String tierLabel(String tier) {
        return switch (tier) {
            case "uncommon" -> Tr.get("zone.tier.uncommon", "Uncommon");
            case "rare" -> Tr.get("zone.tier.rare", "Rare");
            case "very_rare" -> Tr.get("zone.tier.very_rare", "Very Rare");
            default -> Tr.get("zone.tier.common", "Common");
        };
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        int top = listTop();
        int bottom = listBottom();
        context.fill(left() + 8, top - 2, right() - 8, bottom + 2, 0x40000000);
        if (odds == null) {
            String waiting = Tr.get("zone.odds.loading", "Looking at what this zone can produce…");
            context.drawText(textRenderer, waiting, (width - textRenderer.getWidth(waiting)) / 2, top + 8,
                    Theme.TEXT_MUTED, false);
            return;
        }
        if (entries.isEmpty()) {
            String none = Tr.get("zone.odds.none", "No Cobblemon can turn up in this zone right now.");
            context.drawText(textRenderer, none, (width - textRenderer.getWidth(none)) / 2, top + 8,
                    Theme.TEXT_MUTED, false);
            return;
        }
        context.enableScissor(left(), top, right(), bottom);
        for (int i = 0; i < entries.size(); i++) {
            int y = rowY(i);
            if (y + ROW_H < top || y > bottom) {
                continue;
            }
            String entry = entries.get(i);
            boolean hovered = Theme.inside(mouseX, mouseY, left() + 8, y, panelW() - 16, ROW_H)
                    && mouseY >= top && mouseY < bottom;
            if (hovered) {
                context.fill(left() + 8, y, right() - 8, y + ROW_H, 0x30FFFFFF);
            }
            SpeciesIcons.draw(context, entry, left() + 12, y + 2, ICON);
            int nameW = controlX() - 6 - (left() + 12 + ICON + 4);
            double chance = percent.getOrDefault(entry, 0.0);
            int nameColor = chance > 0 ? Theme.TEXT : Theme.TEXT_DISABLED;
            context.drawText(textRenderer, textRenderer.trimToWidth(SpeciesCatalog.nameOf(entry), nameW),
                    left() + 12 + ICON + 4, y + 6, nameColor, false);
            if (rarity()) {
                String tier = ZoneOdds.tierOf(zone, entry);
                int index = ZoneOdds.TIERS.indexOf(tier);
                boolean over = Theme.inside(mouseX, mouseY, controlX(), y + 1, TIER_W, FIELD_H);
                context.fill(controlX(), y + 1, controlX() + TIER_W, y + 1 + FIELD_H,
                        over ? Theme.MAIN_DARK : Theme.MAIN_DEEP);
                context.fill(controlX(), y + FIELD_H - 1, controlX() + TIER_W, y + 1 + FIELD_H, TIER_COLORS[index]);
                String label = tierLabel(tier);
                context.drawText(textRenderer, label, controlX() + (TIER_W - textRenderer.getWidth(label)) / 2, y + 6,
                        TIER_COLORS[index], false);
            } else if (weight()) {
                int x = controlX();
                Theme.button(context, textRenderer, "-", x, y + 1, STEP_W, FIELD_H, false,
                        Theme.inside(mouseX, mouseY, x, y + 1, STEP_W, FIELD_H), true);
                int plusX = x + STEP_W + 2 + WEIGHT_W + 2;
                Theme.button(context, textRenderer, "+", plusX, y + 1, STEP_W, FIELD_H, false,
                        Theme.inside(mouseX, mouseY, plusX, y + 1, STEP_W, FIELD_H), true);
                if (!entry.equals(editingWeight)) {
                    int fx = x + STEP_W + 2;
                    boolean over = Theme.inside(mouseX, mouseY, fx, y + 1, WEIGHT_W, FIELD_H);
                    context.fill(fx, y + 1, fx + WEIGHT_W, y + 1 + FIELD_H, over ? Theme.MAIN_DARK : Theme.MAIN_DEEP);
                    double value = ZoneOdds.weightOf(zone, entry);
                    String text = number(value);
                    context.drawText(textRenderer, text, fx + (WEIGHT_W - textRenderer.getWidth(text)) / 2, y + 6,
                            zone.weights.containsKey(entry) ? Theme.ACCENT : Theme.TEXT_MUTED, false);
                }
            }
            String shown = chance <= 0 ? "0%" : chance < 0.1 ? "<0.1%"
                    : String.format(Locale.ROOT, chance < 10 ? "%.1f%%" : "%.0f%%", chance);
            context.drawText(textRenderer, shown, right() - 10 - textRenderer.getWidth(shown), y + 6,
                    chance > 0 ? Theme.ACCENT : Theme.TEXT_DISABLED, false);
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editingWeight != null) {
            if (weightField.isMouseOver(mouseX, mouseY)) {
                return weightField.mouseClicked(mouseX, mouseY, button);
            }
            commitWeight();
        }
        if (button == 0 && Theme.inside(mouseX, mouseY, left() + 10, modeY(), panelW() - 20, FIELD_H)) {
            setMode(mouseX < left() + panelW() / 2.0 ? -1 : 1);
            return true;
        }
        int by = buttonY();
        if (button == 0 && Theme.inside(mouseX, mouseY, doneX(), by, BUTTON_W, BUTTON_H)) {
            playClick();
            back();
            return true;
        }
        if (button == 0 && Theme.inside(mouseX, mouseY, cancelX(), by, BUTTON_W, BUTTON_H)) {
            playClick();
            cancel();
            return true;
        }
        if (button == 0 && !ZoneOdds.COBBLEMON.equals(zone.oddsMode)
                && Theme.inside(mouseX, mouseY, resetX(), by, BUTTON_W, BUTTON_H)) {
            playClick();
            reset();
            return true;
        }
        if (mouseY >= listTop() && mouseY < listBottom()) {
            for (int i = 0; i < entries.size(); i++) {
                int y = rowY(i);
                if (mouseY < y || mouseY >= y + ROW_H) {
                    continue;
                }
                String entry = entries.get(i);
                if (rarity() && Theme.inside(mouseX, mouseY, controlX(), y + 1, TIER_W, FIELD_H)) {
                    cycleTier(entry, button == 1 ? -1 : 1);
                    return true;
                }
                if (weight() && button == 0) {
                    int x = controlX();
                    if (Theme.inside(mouseX, mouseY, x, y + 1, STEP_W, FIELD_H)) {
                        stepWeight(entry, -1);
                        return true;
                    }
                    if (Theme.inside(mouseX, mouseY, x + STEP_W + 2 + WEIGHT_W + 2, y + 1, STEP_W, FIELD_H)) {
                        stepWeight(entry, 1);
                        return true;
                    }
                    if (Theme.inside(mouseX, mouseY, x + STEP_W + 2, y + 1, WEIGHT_W, FIELD_H)) {
                        startWeight(entry);
                        return true;
                    }
                }
                break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) (verticalAmount * ROW_H * 2);
        clampScroll();
        positionWeightField();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingWeight != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitWeight();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingWeight = null;
                weightField.setVisible(false);
                setFocused(null);
                return true;
            }
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && editingWeight == null) {
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void reset() {
        commitWeight();
        if (rarity()) {
            zone.rarities.clear();
            zone.tierOdds = ZoneOdds.defaultTierOdds();
            updating = true;
            for (int i = 0; i < tierFields.length; i++) {
                tierFields[i].setText(number(ZoneOdds.DEFAULT_TIER_ODDS.get(i)));
            }
            updating = false;
        } else if (weight()) {
            zone.weights.clear();
        }
        dirty = true;
    }

    private void cancel() {
        zone.oddsMode = savedMode;
        zone.rarities = savedRarities;
        zone.weights = savedWeights;
        zone.tierOdds = savedTierOdds;
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void back() {
        commitWeight();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void close() {
        cancel();
    }

    private void playClick() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
