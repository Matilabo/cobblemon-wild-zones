package com.awildencounter.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class DimensionManagerScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int BUTTON_W = 46;
    private static final int BUTTON_H = 16;
    private static final int DONE_W = 90;
    private static final int PANEL_LIGHT = 0x40C8DD3C;

    private final WildConfigScreen parent;
    private int scroll = 0;
    private int editing = -1;
    private TextFieldWidget nameField;
    private String status = "";

    public DimensionManagerScreen(WildConfigScreen parent) {
        super(Text.literal("Manage Multiple Dimensions"));
        this.parent = parent;
    }

    private List<DimensionScope> scopes() {
        return parent.scopes();
    }

    @Override
    protected void init() {
        nameField = new TextFieldWidget(textRenderer, 0, 0, nameW(), 16, Text.literal("Dimension name"));
        nameField.setMaxLength(48);
        nameField.setVisible(false);
        addSelectableChild(nameField);
        if (editing >= 0) {
            int index = editing;
            editing = -1;
            startEditing(index);
        }
    }

    private int panelW() {
        return Math.min(520, width - 24);
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int right() {
        return left() + panelW();
    }

    private int top() {
        return 16;
    }

    private int bottom() {
        return height - 16;
    }

    private List<OrderedText> intro() {
        String text = Tr.get("ui.dimensions.intro", "Every dimension of this server. Rename one (only inside "
                + "A Wild Cobblemon Appeared!), copy one's rules into another, or open its rules. A dimension without rules "
                + "of its own uses ALL's. Nothing changes until you save the menu.");
        return textRenderer.wrapLines(Text.literal(text), panelW() - 24);
    }

    private int listTop() {
        return top() + 26 + intro().size() * 10 + 18;
    }

    private int listBottom() {
        return bottom() - 40;
    }

    private int nameW() {
        return Math.max(80, Math.min(150, panelW() / 3));
    }

    private int nameX() {
        return left() + 10;
    }

    private int rowY(int index) {
        return listTop() + index * ROW_H - scroll;
    }

    private int openX() {
        return right() - 10 - BUTTON_W;
    }

    private int pasteX() {
        return openX() - 4 - BUTTON_W;
    }

    private int copyX() {
        return pasteX() - 4 - BUTTON_W;
    }

    private int doneX() {
        return (width - DONE_W) / 2;
    }

    private int doneY() {
        return bottom() - 24;
    }

    private void clampScroll() {
        int max = Math.max(0, scopes().size() * ROW_H - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(left(), top(), right(), bottom(), Theme.PANEL);
        context.fill(left(), top(), right(), top() + 22, Theme.MAIN);
        context.fill(left(), top() + 20, right(), top() + 22, Theme.ACCENT);
        String title = Tr.get("ui.dimensions.manage", "Manage Multiple Dimensions");
        context.drawText(textRenderer, title, (width - textRenderer.getWidth(title)) / 2, top() + 7,
                Theme.TEXT_ON_MAIN, false);
        int y = top() + 28;
        for (OrderedText line : intro()) {
            context.drawText(textRenderer, line, left() + 12, y, Theme.TEXT_MUTED, false);
            y += 10;
        }
        context.drawText(textRenderer, Tr.get("ui.dimensions.name", "Name"), nameX() + 2, listTop() - 11,
                Theme.ACCENT, false);

        context.enableScissor(left(), listTop() - 2, right(), listBottom() + 2);
        for (int i = 0; i < scopes().size(); i++) {
            int rowY = rowY(i);
            if (rowY + ROW_H < listTop() || rowY > listBottom()) {
                continue;
            }
            drawRow(context, scopes().get(i), i, rowY, mouseX, mouseY);
        }
        context.disableScissor();

        if (editing >= 0) {
            nameField.render(context, mouseX, mouseY, delta);
        }

        if (!status.isEmpty()) {
            String shown = textRenderer.trimToWidth(status, panelW() - 24);
            context.drawText(textRenderer, shown, (width - textRenderer.getWidth(shown)) / 2, doneY() - 13,
                    Theme.ACCENT, false);
        }
        Theme.button(context, textRenderer, Tr.get("ui.done", "Done"), doneX(), doneY(), DONE_W, 18, true,
                Theme.inside(mouseX, mouseY, doneX(), doneY(), DONE_W, 18), true);
    }

    private void drawRow(DrawContext context, DimensionScope scope, int index, int y, int mouseX, int mouseY) {
        boolean current = scope == parent.currentScope();
        if (current) {
            context.fill(left() + 4, y + 1, right() - 4, y + ROW_H - 1, PANEL_LIGHT);
            context.fill(left() + 4, y + 1, left() + 6, y + ROW_H - 1, Theme.ACCENT);
        }
        if (editing != index) {
            int x = nameX();
            int w = nameW();
            boolean editable = !scope.isAll();
            boolean hovered = editable && Theme.inside(mouseX, mouseY, x, y + 5, w, 16);
            context.fill(x, y + 5, x + w, y + 21, hovered ? Theme.MAIN_DARK : Theme.MAIN_DEEP);
            if (editable) {
                context.fill(x, y + 19, x + w, y + 21, hovered ? Theme.ACCENT : 0x55ED9130);
            }
            String text = textRenderer.trimToWidth(scope.shownName() + (scope.dirty || scope.renamed() ? "*" : ""),
                    w - 8);
            context.drawText(textRenderer, text, x + 4, y + 9, Theme.TEXT, false);
        }
        int infoX = nameX() + nameW() + 8;
        int infoW = copyX() - 6 - infoX;
        if (infoW > 30) {
            String id = textRenderer.trimToWidth(scope.isAll()
                    ? Tr.get("ui.dimensions.every", "every dimension") : scope.id, infoW);
            String rules = textRenderer.trimToWidth(scope.isAll() || scope.ownRules || scope.dirty
                    ? Tr.get("ui.dimensions.own", "its own rules") : Tr.get("ui.dimensions.uses_all", "uses ALL's rules"),
                    infoW);
            context.drawText(textRenderer, id, infoX, y + 4, Theme.TEXT_MUTED, false);
            context.drawText(textRenderer, rules, infoX, y + 14, Theme.TEXT_DISABLED, false);
        }
        int buttonY = y + 5;
        Theme.button(context, textRenderer, Tr.get("ui.copy", "Copy"), copyX(), buttonY, BUTTON_W, BUTTON_H, false,
                Theme.inside(mouseX, mouseY, copyX(), buttonY, BUTTON_W, BUTTON_H), true);
        Theme.button(context, textRenderer, Tr.get("ui.paste", "Paste"), pasteX(), buttonY, BUTTON_W, BUTTON_H, false,
                Theme.inside(mouseX, mouseY, pasteX(), buttonY, BUTTON_W, BUTTON_H), parent.hasClipboard());
        Theme.button(context, textRenderer, Tr.get("ui.open", "Open"), openX(), buttonY, BUTTON_W, BUTTON_H, current,
                Theme.inside(mouseX, mouseY, openX(), buttonY, BUTTON_W, BUTTON_H), true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (editing >= 0) {
            if (nameField.isMouseOver(mouseX, mouseY)) {
                return nameField.mouseClicked(mouseX, mouseY, button);
            }
            commitName();
        }
        if (Theme.inside(mouseX, mouseY, doneX(), doneY(), DONE_W, 18)) {
            click();
            back();
            return true;
        }
        if (mouseY < listTop() || mouseY > listBottom()) {
            return false;
        }
        for (int i = 0; i < scopes().size(); i++) {
            int y = rowY(i);
            if (mouseY < y || mouseY >= y + ROW_H) {
                continue;
            }
            DimensionScope scope = scopes().get(i);
            int buttonY = y + 5;
            if (!scope.isAll() && Theme.inside(mouseX, mouseY, nameX(), y + 5, nameW(), 16)) {
                startEditing(i);
            } else if (Theme.inside(mouseX, mouseY, copyX(), buttonY, BUTTON_W, BUTTON_H)) {
                parent.copyRules(scope);
                status = Tr.get("ui.dimensions.copied", "Copied the rules of %s.", scope.shownName());
            } else if (Theme.inside(mouseX, mouseY, pasteX(), buttonY, BUTTON_W, BUTTON_H) && parent.hasClipboard()) {
                parent.pasteRules(scope);
                status = Tr.get("ui.dimensions.pasted",
                        "Pasted the rules of %1$s into %2$s. Save the menu to keep them.",
                        parent.clipboardFrom(), scope.shownName());
            } else if (Theme.inside(mouseX, mouseY, openX(), buttonY, BUTTON_W, BUTTON_H)) {
                parent.selectScope(scope);
                click();
                back();
                return true;
            } else {
                return false;
            }
            click();
            return true;
        }
        return false;
    }

    private void startEditing(int index) {
        editing = index;
        nameField.setText(scopes().get(index).shownName());
        nameField.setVisible(true);
        nameField.setFocused(true);
        setFocused(nameField);
        positionField();
    }

    private void positionField() {
        if (editing < 0) {
            return;
        }
        int y = rowY(editing) + 5;
        if (y < listTop() - 2 || y + 16 > listBottom() + 2) {
            commitName();
            return;
        }
        nameField.setX(nameX());
        nameField.setY(y);
        nameField.setWidth(nameW());
    }

    private void commitName() {
        if (editing < 0) {
            return;
        }
        DimensionScope scope = scopes().get(editing);
        String typed = nameField.getText().trim();
        if (typed.isEmpty()) {
            scope.name = scope.defaultName;
        } else if (!typed.equals(scope.shownName())) {
            scope.name = typed;
        }
        stopEditing();
    }

    private void stopEditing() {
        editing = -1;
        nameField.setVisible(false);
        nameField.setFocused(false);
        setFocused(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitName();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                stopEditing();
                return true;
            }
            nameField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editing >= 0) {
            return nameField.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) (verticalAmount * ROW_H);
        clampScroll();
        positionField();
        return true;
    }

    private void back() {
        commitName();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void close() {
        back();
    }

    private void click() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
