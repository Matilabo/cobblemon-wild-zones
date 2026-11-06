package com.awildencounter.client.gui;

import com.awildencounter.client.zone.ClientZones;
import com.awildencounter.config.DimensionNames;
import com.awildencounter.net.DeleteZonePayload;
import com.awildencounter.client.ChunkSender;
import com.awildencounter.net.Chunks;
import com.awildencounter.zone.WildZone;
import com.awildencounter.zone.ZoneOdds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ZoneScreen extends Screen {
    private static final int ROW_GAP = 22;
    private static final int FIELD_H = 18;
    private static final int LABEL_W = 84;
    private static final int SWATCH = 14;
    private static final int BUTTON_H = 18;
    private static final int STEP_W = 14;
    private static final int SIZE_FIELD_W = 32;
    private static final int SIZE_GROUP_W = STEP_W * 2 + SIZE_FIELD_W + 4;
    private static final int TOGGLE_W = 48;
    private static final long DELETE_CONFIRM_MS = 3000;

    private static final List<Integer> SWATCHES = List.of(
            0xFFFFFF, 0xC8DD3C, 0xED9130, 0xE0524A, 0x4A90E0, 0x4AD0C8, 0xA866E0, 0xF2DE48);
    private static final List<Integer> COOLDOWNS = List.of(
            1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1800, 3600);

    private static final int NAME_ROW = 0;
    private static final int COLOR_ROW = 1;
    private static final int SIZE_ROW = 2;
    private static final int FILTER_ROW = 3;
    private static final int RESTRICTION_ROW = 4;
    private static final int CHANCE_ROW = 5;
    private static final int SPAWNING_ROW = 6;
    private static final int COOLDOWN_ROW = 7;
    private static final int INFO_ROW = 8;

    private final Screen parent;
    private final WildZone original;
    private final WildZone editing;

    private TextFieldWidget nameField;
    private TextFieldWidget colorField;
    private final TextFieldWidget[] sizeFields = new TextFieldWidget[3];
    private boolean updating = false;
    private long deleteArmedAt = 0;

    private record Size(String label, IntSupplier get, IntConsumer set) {
    }

    private final List<Size> sizes;

    public ZoneScreen(Screen parent, WildZone zone) {
        super(Text.literal(zone.name));
        this.parent = parent;
        this.original = zone;
        this.editing = zone.copy();
        this.sizes = List.of(
                new Size(Tr.get("zone.width", "Width (X)"), editing::width, editing::setWidth),
                new Size(Tr.get("zone.height", "Height (Y)"), editing::height, editing::setHeight),
                new Size(Tr.get("zone.length", "Length (Z)"), editing::length, editing::setLength));
    }

    private int panelW() {
        return Math.min(310, width - 20);
    }

    private int panelH() {
        return 30 + ROW_GAP * INFO_ROW + 28 + BUTTON_H + 12;
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int top() {
        return Math.max(2, (height - panelH()) / 2);
    }

    private int rowY(int row) {
        return top() + 30 + row * ROW_GAP;
    }

    private int fieldX() {
        return left() + LABEL_W;
    }

    private int fieldW() {
        return left() + panelW() - 10 - fieldX();
    }

    private int buttonY() {
        return rowY(INFO_ROW) + 28;
    }

    private int filterW() {
        return (fieldW() - 4) / 2;
    }

    private int filterX(boolean blacklist) {
        return blacklist ? fieldX() + filterW() + 4 : fieldX();
    }

    private int swatchX(int index) {
        return fieldX() + index * (SWATCH + 2);
    }

    private int sizeX(int axis) {
        int gap = Math.max(2, (fieldW() - SIZE_GROUP_W * 3) / 2);
        return fieldX() + axis * (SIZE_GROUP_W + gap);
    }

    private int capMinusX() {
        return fieldX() + TOGGLE_W + 8 + textRenderer.getWidth(capLabel()) + 4;
    }

    private int capPlusX() {
        return capMinusX() + STEP_W + 2 + 26 + 2;
    }

    private int cooldownPlusX() {
        return fieldX() + STEP_W + 2 + 40 + 2;
    }

    private String capLabel() {
        return Tr.get("zone.spawn_cap", "Cap");
    }

    @Override
    protected void init() {
        ClientZones.setPreview(editing);
        nameField = keep(nameField, new TextFieldWidget(textRenderer, fieldX(), rowY(NAME_ROW), fieldW(), FIELD_H,
                Text.literal("name")));
        nameField.setMaxLength(WildZone.MAX_NAME);
        if (nameField.getText().isEmpty()) {
            nameField.setText(editing.name);
        }
        nameField.setChangedListener(text -> editing.name = text);

        int colorX = swatchX(SWATCHES.size()) + 4;
        colorField = keep(colorField, new TextFieldWidget(textRenderer, colorX, rowY(COLOR_ROW),
                Math.max(40, left() + panelW() - 10 - colorX), FIELD_H, Text.literal("color")));
        colorField.setMaxLength(7);
        if (colorField.getText().isEmpty()) {
            colorField.setText(hex(editing.color));
        }
        colorField.setChangedListener(this::colorTyped);

        for (int axis = 0; axis < 3; axis++) {
            sizeFields[axis] = sizeField(sizeFields[axis], axis);
        }

        addSelectableChild(nameField);
        addSelectableChild(colorField);
        for (TextFieldWidget field : sizeFields) {
            addSelectableChild(field);
        }
        setInitialFocus(nameField);
    }

    private TextFieldWidget keep(TextFieldWidget old, TextFieldWidget made) {
        if (old != null) {
            made.setText(old.getText());
        }
        return made;
    }

    private TextFieldWidget sizeField(TextFieldWidget old, int axis) {
        Size size = sizes.get(axis);
        TextFieldWidget field = keep(old, new TextFieldWidget(textRenderer, sizeX(axis) + STEP_W + 2, rowY(SIZE_ROW),
                SIZE_FIELD_W, FIELD_H, Text.literal(size.label())));
        field.setMaxLength(4);
        field.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));
        if (field.getText().isEmpty()) {
            field.setText(String.valueOf(size.get().getAsInt()));
        }
        field.setChangedListener(text -> {
            if (updating || text.isEmpty()) {
                return;
            }
            try {
                size.set().accept(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
            }
        });
        return field;
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private void colorTyped(String text) {
        if (updating) {
            return;
        }
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.length() == 6) {
            try {
                editing.color = Integer.parseInt(digits, 16) & 0xFFFFFF;
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void setColor(int color) {
        editing.color = color;
        updating = true;
        colorField.setText(hex(color));
        updating = false;
        playClick();
    }

    private void stepSize(int axis, int direction) {
        Size size = sizes.get(axis);
        int amount = hasShiftDown() ? 10 : 1;
        size.set().accept(size.get().getAsInt() + direction * amount);
        updating = true;
        sizeFields[axis].setText(String.valueOf(size.get().getAsInt()));
        updating = false;
        playClick();
    }

    private String restrictionLabel() {
        return switch (editing.biomeRestriction) {
            case "natural" -> Tr.get("zone.restriction.natural", "Natural Biome Species");
            case "any" -> Tr.get("zone.restriction.any", "Any Cobblemon");
            default -> Tr.get("zone.restriction.default", "Config Menu's");
        };
    }

    private void cycleRestriction(int direction) {
        List<String> values = WildZone.RESTRICTIONS;
        int index = Math.max(0, values.indexOf(editing.biomeRestriction));
        editing.biomeRestriction = values.get(Math.floorMod(index + direction, values.size()));
        playClick();
    }

    static String modeLabel(String mode) {
        return switch (mode) {
            case ZoneOdds.RARITY -> Tr.get("zone.odds.rarity", "Rarity Tiers");
            case ZoneOdds.WEIGHT -> Tr.get("zone.odds.weight", "Custom Weights");
            default -> Tr.get("zone.odds.cobblemon", "Cobblemon's Odds");
        };
    }

    private void stepCap(int direction) {
        int amount = hasShiftDown() ? 5 : 1;
        editing.spawnCap = Math.max(1, Math.min(WildZone.MAX_CAP, editing.spawnCap + direction * amount));
        playClick();
    }

    private void stepCooldown(int direction) {
        int index = 0;
        for (int i = 0; i < COOLDOWNS.size(); i++) {
            if (COOLDOWNS.get(i) <= editing.spawnCooldown) {
                index = i;
            }
        }
        if (direction < 0 && COOLDOWNS.get(index) < editing.spawnCooldown) {
            index++;
        }
        index = Math.max(0, Math.min(COOLDOWNS.size() - 1, index + direction));
        editing.spawnCooldown = COOLDOWNS.get(index);
        playClick();
    }

    static String duration(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return seconds / 60 + "m";
        }
        if (seconds > 60) {
            return seconds / 60 + "m " + seconds % 60 + "s";
        }
        return seconds + "s";
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0x50101010, 0x70101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = left();
        int top = top();
        int panelW = panelW();
        context.fill(left, top, left + panelW, top + panelH(), Theme.PANEL);
        context.fill(left, top, left + panelW, top + 22, Theme.MAIN);
        context.fill(left, top + 20, left + panelW, top + 22, Theme.ACCENT);
        String heading = Tr.get("zone.title", "Wild Zone: %s", editing.name.isBlank() ? original.name : editing.name);
        context.drawText(textRenderer, textRenderer.trimToWidth(heading, panelW - 20), left + 10, top + 7,
                Theme.TEXT_ON_MAIN, false);

        drawLabel(context, Tr.get("zone.name", "Name"), NAME_ROW);
        nameField.render(context, mouseX, mouseY, delta);

        drawLabel(context, Tr.get("zone.color", "Color"), COLOR_ROW);
        for (int i = 0; i < SWATCHES.size(); i++) {
            int x = swatchX(i);
            int y = rowY(COLOR_ROW) + 2;
            int color = SWATCHES.get(i);
            boolean chosen = color == editing.color;
            boolean hovered = Theme.inside(mouseX, mouseY, x, y, SWATCH, SWATCH);
            context.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1,
                    chosen ? Theme.ACCENT : hovered ? Theme.TEXT : 0xFF000000);
            context.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | color);
        }
        colorField.render(context, mouseX, mouseY, delta);

        drawLabel(context, Tr.get("zone.size", "Size W×H×L"), SIZE_ROW);
        int sizeY = rowY(SIZE_ROW);
        String hoveredSize = null;
        for (int axis = 0; axis < 3; axis++) {
            int x = sizeX(axis);
            int plusX = x + STEP_W + 2 + SIZE_FIELD_W + 2;
            stepButton(context, "-", x, sizeY, mouseX, mouseY, true);
            stepButton(context, "+", plusX, sizeY, mouseX, mouseY, true);
            sizeFields[axis].render(context, mouseX, mouseY, delta);
            if (Theme.inside(mouseX, mouseY, x, sizeY, SIZE_GROUP_W, FIELD_H)) {
                hoveredSize = sizes.get(axis).label();
            }
        }

        drawLabel(context, Tr.get("zone.cobblemon", "Cobblemon"), FILTER_ROW);
        List<String> hoveredList = null;
        for (boolean blacklist : new boolean[]{false, true}) {
            List<String> list = blacklist ? editing.blacklist : editing.whitelist;
            String name = blacklist ? Tr.get("ui.biome.blacklist", "Blacklist") : Tr.get("ui.biome.whitelist", "Whitelist");
            String label = textRenderer.trimToWidth(list.isEmpty() ? name + " ›" : name + " (" + list.size() + ") ›",
                    filterW() - 6);
            int x = filterX(blacklist);
            boolean hovered = Theme.inside(mouseX, mouseY, x, rowY(FILTER_ROW), filterW(), FIELD_H);
            Theme.button(context, textRenderer, label, x, rowY(FILTER_ROW), filterW(), FIELD_H, !list.isEmpty(),
                    hovered, true);
            if (hovered) {
                hoveredList = list;
            }
        }

        drawLabel(context, Tr.get("zone.restriction", "Biome Rule"), RESTRICTION_ROW);
        int ry = rowY(RESTRICTION_ROW);
        boolean restrictionHovered = Theme.inside(mouseX, mouseY, fieldX(), ry, fieldW(), FIELD_H);
        context.fill(fieldX(), ry, fieldX() + fieldW(), ry + FIELD_H,
                restrictionHovered ? Theme.MAIN_DARK : Theme.MAIN_DEEP);
        context.fill(fieldX(), ry + FIELD_H - 2, fieldX() + fieldW(), ry + FIELD_H, Theme.ACCENT);
        context.drawText(textRenderer, "◀", fieldX() + 5, ry + 5, Theme.ACCENT, false);
        context.drawText(textRenderer, "▶", fieldX() + fieldW() - 11, ry + 5, Theme.ACCENT, false);
        String restriction = textRenderer.trimToWidth(restrictionLabel(), fieldW() - 34);
        context.drawText(textRenderer, restriction, fieldX() + (fieldW() - textRenderer.getWidth(restriction)) / 2,
                ry + 5, WildZone.RESTRICTION_DEFAULT.equals(editing.biomeRestriction) ? Theme.TEXT_MUTED : Theme.TEXT,
                false);

        drawLabel(context, Tr.get("zone.chance", "Chance"), CHANCE_ROW);
        int cy = rowY(CHANCE_ROW);
        boolean chanceHovered = Theme.inside(mouseX, mouseY, fieldX(), cy, fieldW(), FIELD_H);
        Theme.button(context, textRenderer, textRenderer.trimToWidth(Tr.get("zone.chance.button",
                        "Encounter Chance: %s ›", modeLabel(editing.oddsMode)), fieldW() - 6), fieldX(), cy, fieldW(),
                FIELD_H, !ZoneOdds.COBBLEMON.equals(editing.oddsMode), chanceHovered, true);

        drawLabel(context, Tr.get("zone.spawning", "Spawning"), SPAWNING_ROW);
        int sy = rowY(SPAWNING_ROW);
        boolean toggleHovered = Theme.inside(mouseX, mouseY, fieldX(), sy, TOGGLE_W, FIELD_H);
        Theme.button(context, textRenderer, editing.spawning ? Tr.get("ui.on", "On") : Tr.get("ui.off", "Off"),
                fieldX(), sy, TOGGLE_W, FIELD_H, editing.spawning, toggleHovered, true);
        int muted = editing.spawning ? Theme.TEXT : Theme.TEXT_DISABLED;
        context.drawText(textRenderer, capLabel(), fieldX() + TOGGLE_W + 8, sy + 5, muted, false);
        stepButton(context, "-", capMinusX(), sy, mouseX, mouseY, editing.spawning);
        String cap = String.valueOf(editing.spawnCap);
        context.drawText(textRenderer, cap, capMinusX() + STEP_W + 2 + (26 - textRenderer.getWidth(cap)) / 2, sy + 5,
                muted, false);
        stepButton(context, "+", capPlusX(), sy, mouseX, mouseY, editing.spawning);
        boolean spawningHovered = Theme.inside(mouseX, mouseY, fieldX(), sy, fieldW(), FIELD_H);

        drawLabel(context, Tr.get("zone.cooldown", "Cooldown"), COOLDOWN_ROW);
        int dy = rowY(COOLDOWN_ROW);
        stepButton(context, "-", fieldX(), dy, mouseX, mouseY, editing.spawning);
        String cooldown = duration(editing.spawnCooldown);
        context.drawText(textRenderer, cooldown, fieldX() + STEP_W + 2 + (40 - textRenderer.getWidth(cooldown)) / 2,
                dy + 5, muted, false);
        stepButton(context, "+", cooldownPlusX(), dy, mouseX, mouseY, editing.spawning);
        context.drawText(textRenderer, textRenderer.trimToWidth(Tr.get("zone.cooldown.between", "between spawns"),
                left + panelW - 10 - (cooldownPlusX() + STEP_W + 6)), cooldownPlusX() + STEP_W + 6, dy + 5,
                editing.spawning ? Theme.TEXT_MUTED : Theme.TEXT_DISABLED, false);
        boolean cooldownHovered = Theme.inside(mouseX, mouseY, fieldX(), dy, fieldW(), FIELD_H);

        int infoY = rowY(INFO_ROW);
        String where = DimensionNames.defaultName(editing.dimension) + "  " + editing.corners();
        context.drawText(textRenderer, textRenderer.trimToWidth(where, panelW - 20), left + 10, infoY,
                Theme.TEXT_MUTED, false);
        context.drawText(textRenderer, textRenderer.trimToWidth(Tr.get("zone.hint",
                        "Sizes grow from the lowest north-west corner. Shift: ±10."), panelW - 20), left + 10,
                infoY + 11, Theme.TEXT_DISABLED, false);

        int buttonY = buttonY();
        boolean armed = Util.getMeasuringTimeMs() - deleteArmedAt < DELETE_CONFIRM_MS;
        String delete = armed ? Tr.get("zone.delete_confirm", "Click again to delete") : Tr.get("zone.delete", "Delete");
        Theme.dangerButton(context, textRenderer, delete, left + 10, buttonY, deleteWidth(), BUTTON_H,
                Theme.inside(mouseX, mouseY, left + 10, buttonY, deleteWidth(), BUTTON_H));
        Theme.button(context, textRenderer, Tr.get("ui.save", "Save"), saveX(), buttonY, 64, BUTTON_H, true,
                Theme.inside(mouseX, mouseY, saveX(), buttonY, 64, BUTTON_H), true);
        Theme.button(context, textRenderer, Tr.get("ui.cancel", "Cancel"), cancelX(), buttonY, 64, BUTTON_H, false,
                Theme.inside(mouseX, mouseY, cancelX(), buttonY, 64, BUTTON_H), true);

        if (hoveredList != null) {
            drawFilterTooltip(context, hoveredList, mouseX, mouseY);
        } else if (hoveredSize != null) {
            context.drawTooltip(textRenderer, Text.literal(hoveredSize), mouseX, mouseY);
        } else if (restrictionHovered) {
            tooltip(context, Tr.get("zone.restriction.tip",
                    "Biome Restriction for this zone: how a group on a whitelist (like #cobblemon:starter) is read "
                            + "here. Config Menu's: the config menu's setting. Natural Biome Species: only the "
                            + "group's Cobblemon that spawn naturally in the biome. Any Cobblemon: every Cobblemon "
                            + "from groups, whatever the biome."), mouseX, mouseY);
        } else if (chanceHovered) {
            tooltip(context, Tr.get("zone.chance.tip", "How likely each Cobblemon of this zone is, for its "
                    + "encounters and its spawns. Opens the list of its Cobblemon: keep Cobblemon's own odds, give "
                    + "each a rarity tier, or give each a weight."), mouseX, mouseY);
        } else if (spawningHovered) {
            tooltip(context, Tr.get("zone.spawning.tip", "The zone also spawns its own Cobblemon inside itself "
                    + "while a player is near, picked like its encounters. Cap: how many of them can be around at "
                    + "once. They leave after a few minutes once nobody is close."), mouseX, mouseY);
        } else if (cooldownHovered) {
            tooltip(context, Tr.get("zone.cooldown.tip", "How long the zone waits between two spawns."), mouseX,
                    mouseY);
        }
    }

    private void tooltip(DrawContext context, String text, int mouseX, int mouseY) {
        context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(text), 220), mouseX, mouseY);
    }

    private void stepButton(DrawContext context, String label, int x, int y, int mouseX, int mouseY, boolean enabled) {
        Theme.button(context, textRenderer, label, x, y, STEP_W, FIELD_H, false,
                enabled && Theme.inside(mouseX, mouseY, x, y, STEP_W, FIELD_H), enabled);
    }

    private void drawFilterTooltip(DrawContext context, List<String> list, int mouseX, int mouseY) {
        if (list.isEmpty()) {
            tooltip(context, Tr.get("zone.filter.empty", "Empty. A zone with nothing on its lists uses the species "
                    + "filters of the config menu; with entries, only its own lists count here."), mouseX, mouseY);
            return;
        }
        List<Text> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(12, list.size()); i++) {
            lines.add(Text.literal(SpeciesCatalog.nameOf(list.get(i))));
        }
        if (list.size() > 12) {
            lines.add(Text.literal(Tr.get("zone.filter.more", "and %s more", list.size() - 12))
                    .styled(style -> style.withColor(0xAAAAAA)));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private void openList(boolean blacklist) {
        if (client == null) {
            return;
        }
        playClick();
        String list = blacklist ? Tr.get("ui.biome.blacklist", "Blacklist") : Tr.get("ui.biome.whitelist", "Whitelist");
        String description = blacklist
                ? Tr.get("zone.blacklist.desc", "These Cobblemon never turn up in this zone.")
                : Tr.get("zone.whitelist.desc", "Only the Cobblemon on this list can appear in this zone. "
                + "Groups on this list follow the zone's Biome Rule.");
        client.setScreen(new SpeciesListScreen(this, editing.name + ": " + list, description,
                blacklist ? editing.blacklist : editing.whitelist, values -> {
                    if (blacklist) {
                        editing.blacklist = new ArrayList<>(values);
                    } else {
                        editing.whitelist = new ArrayList<>(values);
                    }
                }));
    }

    private void drawLabel(DrawContext context, String label, int row) {
        context.drawText(textRenderer, textRenderer.trimToWidth(label, LABEL_W - 14), left() + 10, rowY(row) + 5,
                Theme.TEXT, false);
    }

    private int deleteWidth() {
        return Math.max(textRenderer.getWidth(Tr.get("zone.delete", "Delete")),
                textRenderer.getWidth(Tr.get("zone.delete_confirm", "Click again to delete"))) + 20;
    }

    private int saveX() {
        return left() + panelW() - 10 - 64;
    }

    private int cancelX() {
        return saveX() - 6 - 64;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < SWATCHES.size(); i++) {
                if (Theme.inside(mouseX, mouseY, swatchX(i), rowY(COLOR_ROW) + 2, SWATCH, SWATCH)) {
                    setColor(SWATCHES.get(i));
                    return true;
                }
            }
            int sizeY = rowY(SIZE_ROW);
            for (int axis = 0; axis < 3; axis++) {
                int x = sizeX(axis);
                if (Theme.inside(mouseX, mouseY, x, sizeY, STEP_W, FIELD_H)) {
                    stepSize(axis, -1);
                    return true;
                }
                if (Theme.inside(mouseX, mouseY, x + STEP_W + 2 + SIZE_FIELD_W + 2, sizeY, STEP_W, FIELD_H)) {
                    stepSize(axis, 1);
                    return true;
                }
            }
            for (boolean blacklist : new boolean[]{false, true}) {
                if (Theme.inside(mouseX, mouseY, filterX(blacklist), rowY(FILTER_ROW), filterW(), FIELD_H)) {
                    openList(blacklist);
                    return true;
                }
            }
            if (Theme.inside(mouseX, mouseY, fieldX(), rowY(RESTRICTION_ROW), fieldW(), FIELD_H)) {
                cycleRestriction(mouseX < fieldX() + fieldW() / 2.0 ? -1 : 1);
                return true;
            }
            if (Theme.inside(mouseX, mouseY, fieldX(), rowY(CHANCE_ROW), fieldW(), FIELD_H) && client != null) {
                playClick();
                client.setScreen(new EncounterChanceScreen(this, editing));
                return true;
            }
            int sy = rowY(SPAWNING_ROW);
            if (Theme.inside(mouseX, mouseY, fieldX(), sy, TOGGLE_W, FIELD_H)) {
                editing.spawning = !editing.spawning;
                playClick();
                return true;
            }
            if (editing.spawning && Theme.inside(mouseX, mouseY, capMinusX(), sy, STEP_W, FIELD_H)) {
                stepCap(-1);
                return true;
            }
            if (editing.spawning && Theme.inside(mouseX, mouseY, capPlusX(), sy, STEP_W, FIELD_H)) {
                stepCap(1);
                return true;
            }
            int dy = rowY(COOLDOWN_ROW);
            if (editing.spawning && Theme.inside(mouseX, mouseY, fieldX(), dy, STEP_W, FIELD_H)) {
                stepCooldown(-1);
                return true;
            }
            if (editing.spawning && Theme.inside(mouseX, mouseY, cooldownPlusX(), dy, STEP_W, FIELD_H)) {
                stepCooldown(1);
                return true;
            }
            int buttonY = buttonY();
            if (Theme.inside(mouseX, mouseY, left() + 10, buttonY, deleteWidth(), BUTTON_H)) {
                playClick();
                if (Util.getMeasuringTimeMs() - deleteArmedAt < DELETE_CONFIRM_MS) {
                    delete();
                } else {
                    deleteArmedAt = Util.getMeasuringTimeMs();
                }
                return true;
            }
            if (Theme.inside(mouseX, mouseY, saveX(), buttonY, 64, BUTTON_H)) {
                playClick();
                save();
                return true;
            }
            if (Theme.inside(mouseX, mouseY, cancelX(), buttonY, 64, BUTTON_H)) {
                playClick();
                close();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void save() {
        if (editing.name == null || editing.name.isBlank()) {
            editing.name = original.name;
        }
        editing.clampSize();
        if (ChunkSender.canSend()) {
            ChunkSender.send(Chunks.UPDATE_ZONE, editing.toJson());
        }
        close();
    }

    private void delete() {
        if (ClientPlayNetworking.canSend(DeleteZonePayload.ID)) {
            ClientPlayNetworking.send(new DeleteZonePayload(original.id));
        }
        close();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        ClientZones.setPreview(null);
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
