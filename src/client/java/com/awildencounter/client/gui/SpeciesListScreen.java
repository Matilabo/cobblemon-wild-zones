package com.awildencounter.client.gui;

import com.awildencounter.encounter.SpeciesEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class SpeciesListScreen extends Screen {
    private static final int FIELD_H = 20;
    private static final int BUTTON_H = 18;
    private static final int COLUMNS = 6;
    private static final int ROWS = 5;
    private static final int SHOWN = COLUMNS * ROWS;
    private static final int CELL_W = 100;
    private static final int CELL_H = 20;
    private static final int GAP = 3;
    private static final int ICON = 16;

    private final Screen parent;
    private final String description;
    private final List<String> initial;
    private final Consumer<List<String>> onDone;
    private TextFieldWidget field;
    private FieldSuggestions suggestions;

    private String shownFor = null;
    private List<SpeciesCatalog.Option> shown = List.of();

    public SpeciesListScreen(Screen parent, String title, String description, List<String> initial,
                             Consumer<List<String>> onDone) {
        super(Text.literal(title));
        this.parent = parent;
        this.description = description;
        this.initial = initial;
        this.onDone = onDone;
    }

    private int panelW() {
        return Math.min(width - 16, COLUMNS * CELL_W + (COLUMNS - 1) * GAP + 20);
    }

    private int cellW() {
        return (panelW() - 20 - (COLUMNS - 1) * GAP) / COLUMNS;
    }

    private List<OrderedText> lines() {
        String text = description + " " + Tr.get("ui.list.separate", "Separate ids with commas.") + " "
                + Tr.get("ui.species.pick", "Click a Cobblemon below to add it.");
        return textRenderer.wrapLines(Text.literal(text), panelW() - 20);
    }

    private int listRoom() {
        int open = suggestions == null ? 0 : suggestions.openHeight();
        return open > 0 ? open + 4 : 0;
    }

    private int baseH() {
        return 30 + lines().size() * 10 + 6 + FIELD_H + 18 + ROWS * (CELL_H + GAP) + 8 + BUTTON_H + 10;
    }

    private int panelH() {
        return baseH() + listRoom();
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int top() {
        return Math.max(4, Math.min((height - baseH()) / 2, height - 4 - panelH()));
    }

    private int fieldY() {
        return top() + 30 + lines().size() * 10 + 6;
    }

    private int gridY() {
        return fieldY() + FIELD_H + 18 + listRoom();
    }

    private void placeField() {
        if (field != null) {
            field.setY(fieldY());
        }
    }

    private int buttonY() {
        return gridY() + ROWS * (CELL_H + GAP) + 8;
    }

    private int cellX(int index) {
        return left() + 10 + (index % COLUMNS) * (cellW() + GAP);
    }

    private int cellY(int index) {
        return gridY() + (index / COLUMNS) * (CELL_H + GAP);
    }

    @Override
    protected void init() {
        int fieldWidth = panelW() - 20;
        String text = field == null ? String.join(", ", initial) : field.getText();
        field = new TextFieldWidget(textRenderer, left() + 10, fieldY(), fieldWidth, FIELD_H, Text.literal("ids"));
        field.setMaxLength(8000);
        field.setText(text);
        suggestions = new FieldSuggestions(textRenderer, field, FieldSuggestions::species, true);
        addSelectableChild(field);
        setInitialFocus(field);
        shownFor = null;
    }

    private int tokenStart() {
        String text = field.getText();
        int cursor = Math.min(field.getCursor(), text.length());
        int start = text.lastIndexOf(',', cursor - 1) + 1;
        while (start < cursor && text.charAt(start) == ' ') {
            start++;
        }
        return start;
    }

    private int tokenEnd() {
        String text = field.getText();
        int end = text.indexOf(',', Math.min(field.getCursor(), text.length()));
        return end < 0 ? text.length() : end;
    }

    private String query() {
        String text = field.getText();
        int cursor = Math.min(field.getCursor(), text.length());
        String typed = text.substring(Math.min(tokenStart(), cursor), cursor).trim().toLowerCase(Locale.ROOT);
        return typed.startsWith("cobblemon:") ? typed.substring("cobblemon:".length()) : typed;
    }

    private List<SpeciesCatalog.Option> shown() {
        String state = field.getText() + "|" + field.getCursor();
        if (!state.equals(shownFor)) {
            shownFor = state;
            shown = match(query());
        }
        return shown;
    }

    private List<SpeciesCatalog.Option> match(String query) {
        if (query.startsWith("#")) {
            return List.of();
        }
        Set<String> taken = new HashSet<>();
        Set<String> wholeSpecies = new HashSet<>();
        String text = field.getText();
        int start = tokenStart();
        int position = 0;
        for (String part : text.split(",", -1)) {
            int end = position + part.length();
            if (!(start >= position && start <= end) && !part.isBlank()) {
                SpeciesEntry entry = SpeciesEntry.parse(part);
                taken.add(entry.key());
                if (entry.form() == null) {
                    wholeSpecies.add(entry.speciesId());
                }
            }
            position = end + 1;
        }
        List<SpeciesCatalog.Option> starts = new ArrayList<>();
        List<SpeciesCatalog.Option> contains = new ArrayList<>();
        for (SpeciesCatalog.Option option : SpeciesCatalog.all()) {
            if (taken.contains(option.key()) || (option.form() && wholeSpecies.contains(option.speciesId()))) {
                continue;
            }
            if (query.isEmpty()) {
                starts.add(option);
                if (starts.size() >= SHOWN) {
                    break;
                }
                continue;
            }
            String name = option.name().toLowerCase(Locale.ROOT);
            if (option.bare().startsWith(query) || name.startsWith(query)) {
                starts.add(option);
            } else if (option.bare().contains(query) || name.contains(query)) {
                contains.add(option);
            }
        }
        if (query.isEmpty()) {
            return starts;
        }
        Comparator<SpeciesCatalog.Option> alphabetical = Comparator.comparing(SpeciesCatalog.Option::bare);
        starts.sort(alphabetical);
        contains.sort(alphabetical);
        List<SpeciesCatalog.Option> result = new ArrayList<>(starts);
        result.addAll(contains);
        return result.subList(0, Math.min(SHOWN, result.size()));
    }

    private void pick(SpeciesCatalog.Option option) {
        String text = field.getText();
        int start = Math.min(tokenStart(), text.length());
        int end = Math.max(start, tokenEnd());
        String before = text.substring(0, start);
        String after = text.substring(end);
        String replaced = after.isEmpty() ? before + option.key() + ", " : before + option.key() + after;
        field.setText(replaced);
        field.setCursor(after.isEmpty() ? replaced.length() : start + option.key().length(), false);
        suggestions.close();
        setFocused(field);
        field.setFocused(true);
        playClick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        placeField();
        renderBackground(context, mouseX, mouseY, delta);
        int left = left();
        int top = top();
        int panelW = panelW();
        context.fill(left, top, left + panelW, top + panelH(), Theme.PANEL);
        context.fill(left, top, left + panelW, top + 22, Theme.MAIN);
        context.fill(left, top + 20, left + panelW, top + 22, Theme.ACCENT);
        context.drawText(textRenderer, textRenderer.trimToWidth(title.getString(), panelW - 20), left + 10, top + 7,
                Theme.TEXT_ON_MAIN, false);
        int y = top + 30;
        for (OrderedText line : lines()) {
            context.drawText(textRenderer, line, left + 10, y, Theme.TEXT_MUTED, false);
            y += 10;
        }
        field.render(context, mouseX, mouseY, delta);

        String query = query();
        String caption = query.isEmpty() ? Tr.get("ui.species.first", "Suggestions")
                : Tr.get("ui.species.matching", "Suggestions for \"%s\"", query);
        context.drawText(textRenderer, textRenderer.trimToWidth(caption, panelW - 20), left + 10, gridY() - 12,
                Theme.ACCENT, false);

        List<SpeciesCatalog.Option> options = shown();
        SpeciesCatalog.Option hovered = null;
        boolean overList = suggestions.isOver(mouseX, mouseY);
        for (int i = 0; i < options.size(); i++) {
            SpeciesCatalog.Option option = options.get(i);
            int x = cellX(i);
            int cellY = cellY(i);
            boolean over = !overList && Theme.inside(mouseX, mouseY, x, cellY, cellW(), CELL_H);
            if (over) {
                hovered = option;
            }
            context.fill(x, cellY, x + cellW(), cellY + CELL_H, over ? Theme.MAIN : Theme.MAIN_DARK);
            context.fill(x, cellY + CELL_H - 2, x + cellW(), cellY + CELL_H, Theme.MAIN_DEEP);
            SpeciesIcons.draw(context, option.key(), x + 2, cellY + 1, ICON);
            String name = textRenderer.trimToWidth(option.name(), cellW() - ICON - 7);
            context.drawText(textRenderer, name, x + ICON + 4, cellY + 6, over ? Theme.TEXT_ON_MAIN : Theme.TEXT,
                    false);
        }
        if (options.isEmpty()) {
            String none = query.startsWith("#") ? Tr.get("ui.species.group", "Groups are completed with Tab.")
                    : Tr.get("ui.species.none", "No Cobblemon match.");
            context.drawText(textRenderer, none, left + 10, gridY() + 6, Theme.TEXT_DISABLED, false);
        }

        int center = width / 2;
        Theme.button(context, textRenderer, Tr.get("ui.done", "Done"), center - 84, buttonY(), 80, BUTTON_H, true,
                Theme.inside(mouseX, mouseY, center - 84, buttonY(), 80, BUTTON_H), true);
        Theme.button(context, textRenderer, Tr.get("ui.cancel", "Cancel"), center + 4, buttonY(), 80, BUTTON_H, false,
                Theme.inside(mouseX, mouseY, center + 4, buttonY(), 80, BUTTON_H), true);
        suggestions.render(context, mouseX, mouseY);
        if (hovered != null) {
            context.drawTooltip(textRenderer, List.of(Text.literal(hovered.name()),
                    Text.literal(hovered.key()).styled(style -> style.withColor(0xAAAAAA))), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        placeField();
        if (suggestions.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (button == 0) {
            List<SpeciesCatalog.Option> options = shown();
            for (int i = 0; i < options.size(); i++) {
                if (Theme.inside(mouseX, mouseY, cellX(i), cellY(i), cellW(), CELL_H)) {
                    pick(options.get(i));
                    return true;
                }
            }
            int center = width / 2;
            if (Theme.inside(mouseX, mouseY, center - 84, buttonY(), 80, BUTTON_H)) {
                finish(true);
                return true;
            }
            if (Theme.inside(mouseX, mouseY, center + 4, buttonY(), 80, BUTTON_H)) {
                finish(false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return suggestions.mouseScrolled(mouseX, mouseY, verticalAmount)
                || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (suggestions.keyPressed(keyCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finish(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void finish(boolean keep) {
        if (keep) {
            Set<String> values = new LinkedHashSet<>();
            for (String part : field.getText().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            onDone.accept(new ArrayList<>(values));
        }
        close();
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
}
