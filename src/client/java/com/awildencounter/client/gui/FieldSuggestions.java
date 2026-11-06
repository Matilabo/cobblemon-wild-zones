package com.awildencounter.client.gui;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

public final class FieldSuggestions {
    private static final int SHOWN = 8;
    private static final int LINE_H = 12;

    private static List<String> speciesIds = null;
    private static List<String> blockIds = null;

    private final TextRenderer font;
    private final TextFieldWidget field;
    private final Supplier<List<String>> candidates;
    private final boolean commaList;

    private List<String> matches = new ArrayList<>();
    private int highlight = 0;
    private int first = 0;
    private int widest = 0;
    private boolean open = false;
    private boolean filled = false;
    private int tokenStart = 0;
    private boolean applying = false;

    private int boxX;
    private int boxY;
    private int boxW;
    private int boxCount = 0;

    public FieldSuggestions(TextRenderer font, TextFieldWidget field, Supplier<List<String>> candidates,
                            boolean commaList) {
        this.font = font;
        this.field = field;
        this.candidates = candidates;
        this.commaList = commaList;
        field.setChangedListener(text -> {
            if (!applying) {
                refresh();
                open = !typed().isEmpty() && !matches.isEmpty();
                updateHint();
            }
        });
    }

    public static List<String> species() {
        if (speciesIds == null || speciesIds.isEmpty()) {
            Set<String> ids = new TreeSet<>();
            Set<String> groups = new TreeSet<>(List.of("#cobblemon:legendary", "#cobblemon:mythical",
                    "#cobblemon:ultra_beast", "#cobblemon:paradox", "#cobblemon:baby", "#cobblemon:fossil",
                    "#cobblemon:starter"));
            for (int gen = 1; gen <= 9; gen++) {
                groups.add("#cobblemon:gen" + gen);
            }
            try {
                for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
                    for (String label : species.getLabels()) {
                        groups.add("#cobblemon:" + label.toLowerCase(Locale.ROOT));
                    }
                }
                for (SpeciesCatalog.Option option : SpeciesCatalog.all()) {
                    ids.add(option.key());
                }
            } catch (Exception ignored) {
            }
            List<String> all = new ArrayList<>(groups);
            all.addAll(ids);
            speciesIds = all;
        }
        return speciesIds;
    }

    public static List<String> blocks() {
        if (blockIds == null || blockIds.isEmpty()) {
            Set<String> groups = new TreeSet<>();
            try {
                Registries.BLOCK.streamTags().forEach(tag -> groups.add("#" + tag.id()));
            } catch (Exception ignored) {
            }
            Set<String> ids = new TreeSet<>();
            for (Identifier id : Registries.BLOCK.getIds()) {
                if (!id.getPath().equals("air")) {
                    ids.add(id.toString());
                }
            }
            List<String> all = new ArrayList<>(groups);
            all.addAll(ids);
            blockIds = all;
        }
        return blockIds;
    }

    private String typed() {
        String text = field.getText();
        int cursor = Math.min(field.getCursor(), text.length());
        int start = Math.min(tokenStart(text, cursor), cursor);
        return text.substring(start, cursor).trim().toLowerCase(Locale.ROOT);
    }

    private int tokenStart(String text, int cursor) {
        if (!commaList) {
            return 0;
        }
        int start = text.lastIndexOf(',', cursor - 1) + 1;
        while (start < cursor && text.charAt(start) == ' ') {
            start++;
        }
        return start;
    }

    private void refresh() {
        String text = field.getText();
        int cursor = Math.min(field.getCursor(), text.length());
        tokenStart = tokenStart(text, cursor);
        String typed = text.substring(Math.min(tokenStart, cursor), cursor).trim().toLowerCase(Locale.ROOT);
        Set<String> taken = new HashSet<>();
        if (commaList) {
            int position = 0;
            for (String part : text.split(",", -1)) {
                int end = position + part.length();
                if (!(tokenStart >= position && tokenStart <= end)) {
                    taken.add(part.trim().toLowerCase(Locale.ROOT));
                }
                position = end + 1;
            }
        }
        List<String> starts = new ArrayList<>();
        List<String> inside = new ArrayList<>();
        for (String candidate : candidates.get()) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (taken.contains(lower)) {
                continue;
            }
            int match = match(lower, typed);
            if (match == 1) {
                starts.add(candidate);
            } else if (match == 2) {
                inside.add(candidate);
            }
        }
        matches = starts;
        matches.addAll(inside);
        widest = 0;
        for (String match : matches) {
            widest = Math.max(widest, font.getWidth(match));
        }
        highlight = 0;
        first = 0;
        filled = false;
    }

    private static int match(String candidate, String typed) {
        if (typed.isEmpty()) {
            return 1;
        }
        boolean group = typed.startsWith("#");
        if (group && !candidate.startsWith("#")) {
            return 0;
        }
        String bare = candidate.startsWith("#") ? candidate.substring(1) : candidate;
        String wanted = group ? typed.substring(1) : typed;
        String path = bare.substring(bare.indexOf(':') + 1);
        if (bare.startsWith(wanted) || path.startsWith(wanted)) {
            return 1;
        }
        return path.contains("_" + wanted) || path.contains("/" + wanted) || bare.contains(" " + wanted) ? 2 : 0;
    }

    private void updateHint() {
        String hint = null;
        if (open && !filled && !matches.isEmpty() && field.getCursor() == field.getText().length()) {
            String typed = typed();
            String match = matches.get(highlight).toLowerCase(Locale.ROOT);
            String path = match.substring(match.indexOf(':') + 1);
            if (!typed.isEmpty() && match.startsWith(typed)) {
                hint = matches.get(highlight).substring(typed.length());
            } else if (!typed.isEmpty() && path.startsWith(typed)) {
                hint = path.substring(typed.length());
            }
        }
        field.setSuggestion(hint == null || hint.isEmpty() ? null : hint);
    }

    public boolean isOpen() {
        return open && !matches.isEmpty();
    }

    public void close() {
        open = false;
        field.setSuggestion(null);
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        if (!field.isFocused() || !field.isVisible()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB -> {
                if (!open) {
                    refresh();
                    open = true;
                }
                if (!matches.isEmpty()) {
                    if (filled) {
                        move((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
                    }
                    fill();
                }
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                if (!open) {
                    refresh();
                    open = true;
                } else if (!matches.isEmpty()) {
                    move(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
                    if (filled) {
                        fill();
                    }
                }
                updateHint();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (isOpen()) {
                    close();
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private void move(int direction) {
        highlight = Math.floorMod(highlight + direction, matches.size());
        int count = Math.min(SHOWN, matches.size());
        if (highlight < first) {
            first = highlight;
        } else if (highlight >= first + count) {
            first = highlight - count + 1;
        }
    }

    private void fill() {
        String choice = matches.get(highlight);
        String text = field.getText();
        int start = Math.min(tokenStart, text.length());
        int end = commaList ? text.indexOf(',', start) : text.length();
        if (end < 0) {
            end = text.length();
        }
        String replaced = text.substring(0, start) + choice + text.substring(end);
        applying = true;
        field.setText(replaced);
        field.setCursor(Math.min(start + choice.length(), field.getText().length()), false);
        applying = false;
        filled = true;
        field.setSuggestion(null);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isOpen() || !isOver(mouseX, mouseY)) {
            return false;
        }
        int index = first + (int) ((mouseY - boxY - 1) / LINE_H);
        if (index >= 0 && index < matches.size()) {
            highlight = index;
            fill();
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isOpen() || !isOver(mouseX, mouseY)) {
            return false;
        }
        int count = Math.min(SHOWN, matches.size());
        first = Math.max(0, Math.min(matches.size() - count, first - (int) Math.signum(amount)));
        return true;
    }

    public int openHeight() {
        if (!isOpen() || !field.isVisible() || !field.isFocused()) {
            return 0;
        }
        return Math.min(SHOWN, matches.size()) * LINE_H + 3;
    }

    public boolean isOver(double mouseX, double mouseY) {
        return isOpen() && boxCount > 0 && Theme.inside(mouseX, mouseY, boxX, boxY, boxW, boxCount * LINE_H + 2);
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        boxCount = 0;
        if (!isOpen() || !field.isVisible() || !field.isFocused()) {
            return;
        }
        int count = Math.min(SHOWN, matches.size());
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        boxW = Math.min(screenW - 4, Math.max(field.getWidth(), widest + 8));
        boxX = Math.max(2, Math.min(field.getX(), screenW - boxW - 2));
        int height = count * LINE_H + 2;
        boxY = field.getY() + field.getHeight() + 1;
        if (boxY + height > screenH - 2) {
            boxY = field.getY() - height - 1;
        }
        boxCount = count;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        context.fill(boxX, boxY, boxX + boxW, boxY + height, 0xF0000000);
        for (int i = 0; i < count; i++) {
            int index = first + i;
            if (index >= matches.size()) {
                break;
            }
            int lineY = boxY + 1 + i * LINE_H;
            if (Theme.inside(mouseX, mouseY, boxX, lineY, boxW, LINE_H)) {
                context.fill(boxX, lineY, boxX + boxW, lineY + LINE_H, 0x33FFFFFF);
            }
            String text = font.trimToWidth(matches.get(index), boxW - 6);
            context.drawText(font, text, boxX + 4, lineY + 2, index == highlight ? 0xFFFFFF00 : 0xFFAAAAAA, false);
        }
        if (first > 0) {
            for (int x = boxX; x < boxX + boxW; x += 2) {
                context.fill(x, boxY, x + 1, boxY + 1, 0xFFAAAAAA);
            }
        }
        if (first + count < matches.size()) {
            for (int x = boxX; x < boxX + boxW; x += 2) {
                context.fill(x, boxY + height - 1, x + 1, boxY + height, 0xFFAAAAAA);
            }
        }
        context.getMatrices().pop();
    }
}
