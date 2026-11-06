package com.awildencounter.client.gui;

import com.awildencounter.client.ChunkSender;
import com.awildencounter.net.Chunks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ExportPresetScreen extends Screen {
    private static final int MAX_LENGTH = 40;
    private static final int PANEL_W = 300;
    private static final int BUTTON_W = 110;
    private static final int BUTTON_H = 18;

    private final Screen parent;
    private final String configJson;
    private final List<String> existingNames;
    private final Consumer<String> onExported;

    private TextFieldWidget field;
    private FieldSuggestions suggestions;

    public ExportPresetScreen(Screen parent, String configJson, List<String> existingNames,
                              Consumer<String> onExported) {
        super(Text.literal("Export Preset"));
        this.parent = parent;
        this.configJson = configJson;
        this.existingNames = existingNames;
        this.onExported = onExported;
    }

    @Override
    protected void init() {
        String typed = field == null ? "" : field.getText();
        field = new TextFieldWidget(textRenderer, width / 2 - 110, fieldY(), 220, 20, Text.literal("Preset name"));
        field.setMaxLength(MAX_LENGTH);
        field.setText(typed);
        suggestions = new FieldSuggestions(textRenderer, field, () -> existingNames, false);
        addSelectableChild(field);
        setInitialFocus(field);
    }

    private String prompt() {
        return Tr.get("ui.export.prompt", "Name your preset! It is written to config/awildencounter/presets.json5 "
                + "and appears in the preset switcher at the top.");
    }

    private String overwriteNote(String name) {
        return "§e" + Tr.get("ui.export.overwrite", "A preset called %s already exists and will be replaced.", name);
    }

    private String tipNote() {
        return "§7" + Tr.get("ui.export.tip", "You can replace an existing preset by giving it the same name. Tab "
                + "suggests the existing ones.");
    }

    private List<OrderedText> wrap(String text) {
        return textRenderer.wrapLines(Text.literal(text), PANEL_W - 28);
    }

    private int noteLines() {
        int lines = wrap(tipNote()).size();
        for (String existing : existingNames) {
            lines = Math.max(lines, wrap(overwriteNote(existing)).size());
        }
        return lines;
    }

    private int panelH() {
        return 30 + wrap(prompt()).size() * 10 + 6 + 20 + 8 + noteLines() * 10 + 8 + BUTTON_H + 10;
    }

    private int top() {
        return Math.max(4, (height - panelH()) / 2);
    }

    private int fieldY() {
        return top() + 30 + wrap(prompt()).size() * 10 + 6;
    }

    private int noteY() {
        return fieldY() + 20 + 8;
    }

    private String name() {
        return field == null ? "" : field.getText().trim();
    }

    private boolean canConfirm() {
        return !name().isEmpty();
    }

    private boolean overwrites() {
        String wanted = name().toLowerCase(Locale.ROOT);
        for (String existing : existingNames) {
            if (existing.toLowerCase(Locale.ROOT).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    private int confirmX() {
        return width / 2 + 6;
    }

    private int cancelX() {
        return width / 2 - BUTTON_W - 6;
    }

    private int buttonY() {
        return noteY() + noteLines() * 10 + 8;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = (width - PANEL_W) / 2;
        int top = top();
        context.fill(left, top, left + PANEL_W, top + panelH(), Theme.PANEL);
        context.fill(left, top, left + PANEL_W, top + 22, Theme.MAIN);
        context.fill(left, top + 20, left + PANEL_W, top + 22, Theme.ACCENT);

        String header = Tr.get("ui.export.header", "Export these settings as a preset");
        context.drawText(textRenderer, header, (width - textRenderer.getWidth(header)) / 2, top + 7,
                Theme.TEXT_ON_MAIN, false);

        int promptY = top + 30;
        for (OrderedText line : wrap(prompt())) {
            context.drawText(textRenderer, line, left + 14, promptY, Theme.TEXT_MUTED, false);
            promptY += 10;
        }

        field.render(context, mouseX, mouseY, delta);

        String note = overwrites() ? overwriteNote(name()) : tipNote();
        int noteY = noteY();
        for (OrderedText line : wrap(note)) {
            context.drawText(textRenderer, line, left + 14, noteY, Theme.TEXT_MUTED, false);
            noteY += 10;
        }

        Theme.button(context, textRenderer, Tr.get("ui.cancel", "Cancel"), cancelX(), buttonY(), BUTTON_W, BUTTON_H,
                false, Theme.inside(mouseX, mouseY, cancelX(), buttonY(), BUTTON_W, BUTTON_H), true);
        Theme.button(context, textRenderer, overwrites() ? Tr.get("ui.replace", "Replace") : Tr.get("ui.export", "Export"),
                confirmX(), buttonY(), BUTTON_W, BUTTON_H, true,
                Theme.inside(mouseX, mouseY, confirmX(), buttonY(), BUTTON_W, BUTTON_H), canConfirm());
        suggestions.render(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (suggestions.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (Theme.inside(mouseX, mouseY, cancelX(), buttonY(), BUTTON_W, BUTTON_H)) {
            back();
            return true;
        }
        if (Theme.inside(mouseX, mouseY, confirmX(), buttonY(), BUTTON_W, BUTTON_H)) {
            confirm();
            return true;
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
            confirm();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        if (!canConfirm()) {
            return;
        }
        if (ChunkSender.canSend()) {
            JsonObject root = new JsonObject();
            root.addProperty("name", name());
            root.add("config", JsonParser.parseString(configJson));
            ChunkSender.send(Chunks.EXPORT_PRESET, root.toString());
            onExported.accept(name());
        }
        back();
    }

    private void back() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void close() {
        back();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
