package com.awildencounter.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListEditScreen extends Screen {
    private static final int FIELD_H = 20;
    private static final int BUTTON_H = 18;

    private final Screen parent;
    private final String description;
    private final List<String> initial;
    private final List<String> candidates;
    private final Consumer<List<String>> onDone;
    private TextFieldWidget field;
    private FieldSuggestions suggestions;

    public ListEditScreen(Screen parent, String title, String description, List<String> initial,
                          Consumer<List<String>> onDone) {
        this(parent, title, description, initial, List.of(), onDone);
    }

    public ListEditScreen(Screen parent, String title, String description, List<String> initial,
                          List<String> candidates, Consumer<List<String>> onDone) {
        super(Text.literal(title));
        this.parent = parent;
        this.description = description;
        this.initial = initial;
        this.candidates = candidates;
        this.onDone = onDone;
    }

    private int panelW() {
        return Math.min(460, width - 20);
    }

    private List<OrderedText> lines() {
        String text = description + " " + Tr.get("ui.list.separate", "Separate ids with commas.");
        if (!candidates.isEmpty()) {
            text += " " + Tr.get("ui.list.tab", "You can use tab to view ids.");
        }
        return textRenderer.wrapLines(Text.literal(text), panelW() - 20);
    }

    private int panelH() {
        return 30 + lines().size() * 10 + 6 + FIELD_H + 10 + BUTTON_H + 10;
    }

    private int top() {
        return Math.max(4, (height - panelH()) / 2);
    }

    private int fieldY() {
        return top() + 30 + lines().size() * 10 + 6;
    }

    private int buttonY() {
        return fieldY() + FIELD_H + 10;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(420, width - 40);
        String text = field == null ? String.join(", ", initial) : field.getText();
        field = new TextFieldWidget(textRenderer, (width - fieldWidth) / 2, fieldY(), fieldWidth, FIELD_H,
                Text.literal("ids"));
        field.setMaxLength(4000);
        field.setText(text);
        suggestions = new FieldSuggestions(textRenderer, field, () -> candidates, true);
        addSelectableChild(field);
        setInitialFocus(field);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int panelW = panelW();
        int left = (width - panelW) / 2;
        int top = top();
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
        int center = width / 2;
        Theme.button(context, textRenderer, Tr.get("ui.done", "Done"), center - 84, buttonY(), 80, BUTTON_H, true,
                Theme.inside(mouseX, mouseY, center - 84, buttonY(), 80, BUTTON_H), true);
        Theme.button(context, textRenderer, Tr.get("ui.cancel", "Cancel"), center + 4, buttonY(), 80, BUTTON_H, false,
                Theme.inside(mouseX, mouseY, center + 4, buttonY(), 80, BUTTON_H), true);
        suggestions.render(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (suggestions.mouseClicked(mouseX, mouseY)) {
            return true;
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
            List<String> values = new ArrayList<>();
            for (String part : field.getText().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            onDone.accept(values);
        }
        close();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
