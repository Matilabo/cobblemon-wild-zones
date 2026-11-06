package com.awildencounter.client.gui;

import com.awildencounter.AWildEncounter;
import com.awildencounter.client.cutscene.ChromaKey;
import com.awildencounter.client.cutscene.CustomAnimations;
import com.awildencounter.client.cutscene.CutsceneClip;
import com.awildencounter.client.cutscene.CutscenePlayer;
import com.awildencounter.client.cutscene.GifConverter;
import com.awildencounter.encounter.CustomClips;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnimationImportScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int BUTTON_H = 18;
    private static final int HEADER_H = 22;
    private static final int APPEAR_FLASH_MS = 600;

    private enum Mode {
        LIST,
        CONVERTING,
        EDIT,
        SAVING
    }

    private enum RowKind {
        HEADING,
        GIF,
        CLIP,
        EMPTY
    }

    private record Row(RowKind kind, String label, String info, String button, Runnable action) {
    }

    private record Button(String label, int x, int y, int width, boolean primary, boolean enabled, Runnable action) {
    }

    private final Screen parent;
    private Mode mode = Mode.LIST;

    private final List<Row> rows = new ArrayList<>();
    private int scroll = 0;
    private String status = null;
    private int statusColour = Theme.TEXT_MUTED;

    private volatile int convertDone = 0;
    private volatile int convertTotal = 0;
    private volatile boolean convertCancelled = false;
    private String convertingName = "";

    private CutsceneClip clip;
    private GifConverter.Converted converted;
    private String editingId;
    private String sourceName = "";
    private String typedName = "";
    private TextFieldWidget nameField;
    private int frame = 0;
    private int appear = 0;
    private boolean smooth = true;
    private boolean playing = false;
    private long playStartedAt = 0;
    private int playFrom = 0;
    private long appearFlashAt = -1;
    private boolean draggingTimeline = false;

    private NativeImageBackedTexture texture;
    private Identifier textureId;
    private int textureW = 0;
    private int textureH = 0;
    private int shownFrame = -1;

    private Integer keyColour = null;
    private int keyIntensity = ChromaKey.DEFAULT_INTENSITY;
    private boolean picking = false;
    private boolean draggingIntensity = false;
    private int[] rawPixels = new int[0];

    public AnimationImportScreen(Screen parent) {
        super(Text.literal("Import A GIF"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (mode == Mode.LIST) {
            refresh();
        }
        if (mode == Mode.EDIT && converted != null) {
            String text = nameField == null ? typedName : nameField.getText();
            nameField = new TextFieldWidget(textRenderer, nameFieldX(), nameRowY(), nameFieldW(), 16,
                    Text.literal("Animation name"));
            nameField.setMaxLength(64);
            nameField.setText(text);
            nameField.setChangedListener(value -> typedName = value);
            addSelectableChild(nameField);
        } else {
            nameField = null;
        }
    }

    private int panelW() {
        return Math.min(width - 20, 520);
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int top() {
        return 8;
    }

    private int bottom() {
        return height - 8;
    }

    private int footerY() {
        return bottom() - BUTTON_H - 6;
    }

    private void refresh() {
        rows.clear();
        List<Path> gifs = CustomAnimations.gifs();
        List<CustomAnimations.Entry> clips = CustomAnimations.list();
        rows.add(new Row(RowKind.HEADING, Tr.get("import.gifs", "GIF files"), "", "", null));
        if (gifs.isEmpty()) {
            rows.add(new Row(RowKind.EMPTY, Tr.get("import.gifs.none", "No GIF files here yet. Open the folder "
                    + "and put a GIF in it."), "", "", null));
        }
        for (Path gif : gifs) {
            boolean imported = CustomAnimations.exists(CustomAnimations.nameFor(gif));
            rows.add(new Row(RowKind.GIF, gif.getFileName().toString(),
                    imported ? Tr.get("import.imported", "imported") : size(gif),
                    imported ? Tr.get("import.again", "Import Again") : Tr.get("import.import", "Import"),
                    () -> startConverting(gif)));
        }
        rows.add(new Row(RowKind.HEADING, Tr.get("import.animations", "Custom animations"), "", "", null));
        if (clips.isEmpty()) {
            rows.add(new Row(RowKind.EMPTY, Tr.get("import.animations.none", "None yet. Each imported GIF gets "
                    + "its own folder here."), "", "", null));
        }
        for (CustomAnimations.Entry entry : clips) {
            String info = Tr.get("import.clip.info", "%1$s s, appears at %2$s s", seconds(entry.durationMs()),
                    seconds(entry.appearFrame() * 1000 / entry.fps()));
            rows.add(new Row(RowKind.CLIP, entry.name(), info, Tr.get("zones.edit", "Edit"),
                    () -> openExisting(entry)));
        }
        clampScroll();
    }

    private static String size(Path file) {
        try {
            long bytes = Files.size(file);
            return bytes >= 1024 * 1024 ? String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
                    : Math.max(1, bytes / 1024) + " KB";
        } catch (Exception e) {
            return "";
        }
    }

    private static String seconds(int ms) {
        return String.format(Locale.ROOT, "%.2f", ms / 1000.0);
    }

    private int listTop() {
        return top() + HEADER_H + 8 + helpLines().size() * 10 + 6 + BUTTON_H + 8;
    }

    private int listBottom() {
        return footerY() - (status == null ? 6 : 6 + wrap(status).size() * 10 + 4);
    }

    private void clampScroll() {
        int max = Math.max(0, rows.size() * ROW_H - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private List<OrderedText> wrap(String text) {
        return textRenderer.wrapLines(Text.literal(text), panelW() - 28);
    }

    private List<OrderedText> helpLines() {
        return wrap(Tr.get("import.help", "Put GIF files in config/awildencounter/custom encounter animations, "
                + "then click Import. You choose the frame where the Cobblemon appears, and the mod saves the "
                + "animation in its own folder there. All the animations in that folder show in the animation "
                + "rows of the Animations tab."));
    }

    private List<Button> listButtons() {
        List<Button> buttons = new ArrayList<>();
        int y = top() + HEADER_H + 8 + helpLines().size() * 10 + 6;
        String open = Tr.get("import.open_folder", "Open Folder");
        String refresh = Tr.get("import.refresh", "Refresh");
        int openW = textRenderer.getWidth(open) + 24;
        buttons.add(new Button(open, left() + 14, y, openW, false, true, () -> {
            CustomAnimations.openFolder();
            playClick();
        }));
        buttons.add(new Button(refresh, left() + 14 + openW + 6, y, textRenderer.getWidth(refresh) + 24, false,
                true, () -> {
            status = null;
            refresh();
            playClick();
        }));
        String back = Tr.get("ui.back", "Back");
        int backW = textRenderer.getWidth(back) + 28;
        buttons.add(new Button(back, left() + panelW() - 14 - backW, footerY(), backW, true, true, this::close));
        return buttons;
    }

    private int rowButtonW() {
        return 84;
    }

    private int rowButtonX() {
        return left() + panelW() - 14 - rowButtonW();
    }

    private void renderList(DrawContext context, int mouseX, int mouseY) {
        int y = top() + HEADER_H + 8;
        for (OrderedText line : helpLines()) {
            context.drawText(textRenderer, line, left() + 14, y, Theme.TEXT_MUTED, false);
            y += 10;
        }
        int listTop = listTop();
        int listBottom = listBottom();
        context.enableScissor(left() + 4, listTop, left() + panelW() - 4, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = listTop + i * ROW_H - scroll;
            if (rowY + ROW_H < listTop || rowY > listBottom) {
                continue;
            }
            switch (row.kind()) {
                case HEADING -> {
                    String heading = row.label().toUpperCase(Locale.ROOT);
                    context.drawText(textRenderer, heading, left() + 14, rowY + 8, Theme.ACCENT, false);
                    int lineX = left() + 20 + textRenderer.getWidth(heading);
                    context.fill(lineX, rowY + 11, left() + panelW() - 14, rowY + 12, 0x44ED9130);
                }
                case EMPTY -> context.drawText(textRenderer,
                        textRenderer.trimToWidth(row.label(), panelW() - 40), left() + 20, rowY + 6,
                        Theme.TEXT_DISABLED, false);
                default -> {
                    boolean hovered = Theme.inside(mouseX, mouseY, left() + 8, rowY, panelW() - 16, ROW_H)
                            && mouseY >= listTop && mouseY < listBottom;
                    if (hovered) {
                        context.fill(left() + 8, rowY + 1, left() + panelW() - 8, rowY + ROW_H - 1, 0x33FFFFFF);
                    }
                    int infoW = textRenderer.getWidth(row.info());
                    int labelW = rowButtonX() - left() - 20 - infoW - 12;
                    context.drawText(textRenderer, textRenderer.trimToWidth(row.label(), Math.max(20, labelW)),
                            left() + 20, rowY + 6, Theme.TEXT, false);
                    context.drawText(textRenderer, row.info(), rowButtonX() - 8 - infoW, rowY + 6,
                            Theme.TEXT_MUTED, false);
                    boolean buttonHovered = Theme.inside(mouseX, mouseY, rowButtonX(), rowY + 1, rowButtonW(),
                            BUTTON_H) && mouseY >= listTop && mouseY < listBottom;
                    Theme.button(context, textRenderer, row.button(), rowButtonX(), rowY + 1, rowButtonW(),
                            BUTTON_H, row.kind() == RowKind.GIF, buttonHovered, true);
                }
            }
        }
        context.disableScissor();
        if (status != null) {
            int statusY = listBottom + 6;
            for (OrderedText line : wrap(status)) {
                context.drawText(textRenderer, line, left() + 14, statusY, statusColour, false);
                statusY += 10;
            }
        }
    }

    private boolean clickList(double mouseX, double mouseY) {
        if (mouseY < listTop() || mouseY >= listBottom()) {
            return false;
        }
        int index = (int) ((mouseY - listTop() + scroll) / ROW_H);
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        Row row = rows.get(index);
        int rowY = listTop() + index * ROW_H - scroll;
        if (row.action() != null && Theme.inside(mouseX, mouseY, rowButtonX(), rowY + 1, rowButtonW(), BUTTON_H)) {
            playClick();
            row.action().run();
            return true;
        }
        return false;
    }

    private void startConverting(Path gif) {
        mode = Mode.CONVERTING;
        convertDone = 0;
        convertTotal = 0;
        convertCancelled = false;
        convertingName = gif.getFileName().toString();
        status = null;
        Thread thread = new Thread(() -> {
            GifConverter.Converted result = null;
            String error = null;
            try {
                result = GifConverter.convert(gif, new GifConverter.Progress() {
                    @Override
                    public void update(int done, int total) {
                        convertDone = done;
                        convertTotal = total;
                    }

                    @Override
                    public boolean cancelled() {
                        return convertCancelled;
                    }
                });
            } catch (GifConverter.ConvertException e) {
                error = Tr.get(e.key, e.english);
            } catch (Exception e) {
                error = e.toString();
                AWildEncounter.LOGGER.warn("Could not import {}: {}", gif, e.toString());
            } catch (OutOfMemoryError e) {
                error = Tr.get("import.error.memory", "There was not enough memory to read this GIF.");
            }
            GifConverter.Converted done = result;
            String failure = error;
            if (client != null) {
                client.execute(() -> converted(gif, done, failure));
            }
        }, "A Wild Cobblemon Appeared! GIF import");
        thread.setDaemon(true);
        thread.start();
    }

    private void converted(Path gif, GifConverter.Converted result, String error) {
        if (mode != Mode.CONVERTING) {
            return;
        }
        if (convertCancelled || result == null) {
            mode = Mode.LIST;
            if (error != null) {
                status = Tr.get("import.failed", "Could not import %1$s: %2$s", gif.getFileName().toString(), error);
                statusColour = 0xFFE0573C;
            }
            clearAndInit();
            return;
        }
        converted = result;
        editingId = null;
        sourceName = gif.getFileName().toString();
        typedName = CustomAnimations.nameFor(gif);
        nameField = null;
        clip = result.toClip(CustomClips.id(typedName), true);
        appear = clip.frameCount() / 2;
        smooth = true;
        ChromaKey saved = CustomAnimations.savedKey(typedName);
        keyColour = saved == null ? null : saved.rgb();
        keyIntensity = saved == null ? ChromaKey.DEFAULT_INTENSITY : saved.intensity();
        beginEditing();
    }

    private void renderConverting(DrawContext context) {
        int y = top() + HEADER_H + 30;
        String text;
        if (mode == Mode.SAVING) {
            text = Tr.get("import.saving", "Saving %1$s: frame %2$s of %3$s", convertingName, convertDone,
                    convertTotal);
        } else {
            text = convertTotal > 0
                    ? Tr.get("import.converting", "Converting %1$s: frame %2$s of %3$s", convertingName,
                    convertDone, convertTotal)
                    : Tr.get("import.reading", "Reading %s…", convertingName);
        }
        context.drawText(textRenderer, text, (width - textRenderer.getWidth(text)) / 2, y, Theme.TEXT, false);
        int barW = panelW() - 80;
        int barX = (width - barW) / 2;
        context.fill(barX, y + 16, barX + barW, y + 22, Theme.MAIN_DEEP);
        if (convertTotal > 0) {
            context.fill(barX, y + 16, barX + barW * convertDone / convertTotal, y + 22, Theme.ACCENT);
        }
    }

    private List<Button> convertingButtons() {
        String cancel = Tr.get("ui.cancel", "Cancel");
        int w = textRenderer.getWidth(cancel) + 28;
        return List.of(new Button(cancel, (width - w) / 2, footerY(), w, false, true, this::cancelWork));
    }

    private void cancelWork() {
        convertCancelled = true;
        mode = mode == Mode.SAVING ? Mode.EDIT : Mode.LIST;
        clearAndInit();
    }

    private void openExisting(CustomAnimations.Entry entry) {
        try {
            clip = CutsceneClip.loadCustom(entry.id());
        } catch (Exception e) {
            status = Tr.get("import.open_failed", "Could not open %1$s: %2$s", entry.name(), e.getMessage());
            statusColour = 0xFFE0573C;
            return;
        }
        converted = null;
        editingId = entry.id();
        sourceName = entry.name();
        appear = Math.max(0, Math.min(clip.frameCount() - 1, entry.appearFrame()));
        smooth = clip.smooth();
        keyColour = null;
        beginEditing();
    }

    private void beginEditing() {
        mode = Mode.EDIT;
        frame = appear;
        playing = false;
        picking = false;
        shownFrame = -1;
        appearFlashAt = -1;
        clearAndInit();
    }

    private int previewBottomSpace() {
        return 16 + 14 + BUTTON_H + 8 + 22 + (importing() ? 22 : 0) + 10 + BUTTON_H + 12;
    }

    private int boxW() {
        int availableW = panelW() - 28;
        int availableH = bottom() - (top() + HEADER_H + 8) - previewBottomSpace();
        return Math.max(64, Math.min(availableW, availableH * 16 / 9));
    }

    private int boxH() {
        return boxW() * 9 / 16;
    }

    private int boxX() {
        return (width - boxW()) / 2;
    }

    private int boxY() {
        return top() + HEADER_H + 8;
    }

    private int timelineX() {
        return boxX();
    }

    private int timelineW() {
        return boxW();
    }

    private int timelineY() {
        return boxY() + boxH() + 8;
    }

    private int infoY() {
        return timelineY() + 14;
    }

    private int controlsY() {
        return infoY() + 14;
    }

    private int nameRowY() {
        return controlsY() + BUTTON_H + 8;
    }

    private int keyRowY() {
        return nameRowY() + 22;
    }

    private boolean importing() {
        return converted != null;
    }

    private ChromaKey key() {
        return keyColour == null ? null : new ChromaKey(keyColour, keyIntensity);
    }

    private int keySwatchX() {
        return left() + 14 + textRenderer.getWidth(Tr.get("import.key", "Chroma Key")) + 6;
    }

    private int pickButtonX() {
        return keySwatchX() + 16 + 6;
    }

    private int pickButtonW() {
        return Math.max(textRenderer.getWidth(Tr.get("import.key.pick", "Pick Color")),
                textRenderer.getWidth(Tr.get("import.key.picking", "Click the Preview"))) + 20;
    }

    private int clearButtonX() {
        return pickButtonX() + pickButtonW() + 4;
    }

    private int clearButtonW() {
        return textRenderer.getWidth(Tr.get("import.key.clear", "Clear")) + 20;
    }

    private int intensityLabelX() {
        return clearButtonX() + clearButtonW() + 10;
    }

    private int sliderX() {
        return intensityLabelX() + textRenderer.getWidth(Tr.get("import.key.intensity", "Intensity")) + 6;
    }

    private int sliderW() {
        return Math.max(40, left() + panelW() - 14 - textRenderer.getWidth("100") - 6 - sliderX());
    }

    private boolean onSlider(double mouseX, double mouseY) {
        return importing() && mouseX >= sliderX() - 4 && mouseX <= sliderX() + sliderW() + 4
                && mouseY >= keyRowY() - 1 && mouseY <= keyRowY() + BUTTON_H - 1;
    }

    private void setIntensity(int value) {
        int clamped = Math.max(1, Math.min(100, value));
        if (clamped != keyIntensity) {
            keyIntensity = clamped;
            shownFrame = -1;
        }
    }

    private void seekIntensity(double mouseX) {
        double fraction = Math.max(0, Math.min(1, (mouseX - sliderX()) / (double) sliderW()));
        setIntensity(1 + (int) Math.round(fraction * 99));
    }

    private void togglePicking() {
        picking = !picking;
        playing = false;
        shownFrame = -1;
        playClick();
    }

    private int[] imageRect() {
        float scale = Math.min(boxW() / (float) Math.max(1, textureW), boxH() / (float) Math.max(1, textureH));
        int drawW = Math.round(textureW * scale);
        int drawH = Math.round(textureH * scale);
        return new int[]{boxX() + (boxW() - drawW) / 2, boxY() + (boxH() - drawH) / 2, drawW, drawH};
    }

    private Integer colourAt(double mouseX, double mouseY) {
        if (textureId == null || rawPixels.length != textureW * textureH) {
            return null;
        }
        int[] rect = imageRect();
        if (mouseX < rect[0] || mouseY < rect[1] || mouseX >= rect[0] + rect[2] || mouseY >= rect[1] + rect[3]) {
            return null;
        }
        int px = Math.min(textureW - 1, (int) ((mouseX - rect[0]) * textureW / rect[2]));
        int py = Math.min(textureH - 1, (int) ((mouseY - rect[1]) * textureH / rect[3]));
        return rawPixels[py * textureW + px];
    }

    private int nameFieldX() {
        return left() + 14 + textRenderer.getWidth(Tr.get("zone.name", "Name")) + 8;
    }

    private int nameFieldW() {
        return Math.max(60, Math.min(220, pixelButtonX() - 8 - nameFieldX()));
    }

    private String pixelLabel() {
        return Tr.get("import.pixel", "Pixel Art: %s", smooth ? Tr.value("Off") : Tr.value("On"));
    }

    private int pixelButtonW() {
        return textRenderer.getWidth(pixelLabel()) + 20;
    }

    private int pixelButtonX() {
        return left() + panelW() - 14 - pixelButtonW();
    }

    private String savingName() {
        return converted != null ? typedName.strip() : CustomClips.nameOf(editingId);
    }

    private boolean nameOk() {
        return CustomClips.safeName(savingName());
    }

    private boolean replaces() {
        return converted != null && nameOk() && CustomAnimations.exists(savingName());
    }

    private List<Button> editButtons() {
        List<Button> buttons = new ArrayList<>();
        int y = controlsY();
        int x = left() + 14;
        String previous = "◀";
        String play = playing ? Tr.get("import.pause", "Pause") : Tr.get("import.play", "Play");
        String next = "▶";
        buttons.add(new Button(previous, x, y, 20, false, true, () -> stepFrame(-1)));
        x += 24;
        int playW = Math.max(textRenderer.getWidth(Tr.get("import.pause", "Pause")),
                textRenderer.getWidth(Tr.get("import.play", "Play"))) + 20;
        buttons.add(new Button(play, x, y, playW, false, true, this::togglePlay));
        x += playW + 4;
        buttons.add(new Button(next, x, y, 20, false, true, () -> stepFrame(1)));
        x += 28;
        String mark = Tr.get("import.mark", "Set Appearing Frame");
        int markW = textRenderer.getWidth(mark) + 20;
        buttons.add(new Button(mark, x, y, markW, true, frame != appear, () -> {
            appear = frame;
            appearFlashAt = Util.getMeasuringTimeMs();
            playClick();
        }));
        x += markW + 4;
        String show = Tr.get("import.show", "Show Appearing Frame");
        int showW = textRenderer.getWidth(show) + 20;
        buttons.add(new Button(show, x, y, showW, false, true, () -> {
            playing = false;
            frame = appear;
            appearFlashAt = Util.getMeasuringTimeMs();
            playClick();
        }));
        buttons.add(new Button(pixelLabel(), pixelButtonX(), nameRowY() - 1, pixelButtonW(), false, true, () -> {
            smooth = !smooth;
            if (texture != null) {
                texture.setFilter(smooth, false);
            }
            playClick();
        }));

        if (importing()) {
            String pick = picking ? Tr.get("import.key.picking", "Click the Preview")
                    : Tr.get("import.key.pick", "Pick Color");
            buttons.add(new Button(pick, pickButtonX(), keyRowY() - 1, pickButtonW(), picking, true,
                    this::togglePicking));
            buttons.add(new Button(Tr.get("import.key.clear", "Clear"), clearButtonX(), keyRowY() - 1,
                    clearButtonW(), false, keyColour != null, () -> {
                keyColour = null;
                picking = false;
                shownFrame = -1;
                playClick();
            }));
        }

        String save = replaces() ? Tr.get("ui.replace", "Replace") : Tr.get("ui.save", "Save");
        int saveW = textRenderer.getWidth(save) + 28;
        int saveX = left() + panelW() - 14 - saveW;
        buttons.add(new Button(save, saveX, footerY(), saveW, true, nameOk(), this::save));
        String cancel = Tr.get("ui.cancel", "Cancel");
        int cancelW = textRenderer.getWidth(cancel) + 28;
        buttons.add(new Button(cancel, saveX - 8 - cancelW, footerY(), cancelW, false, true, this::backToList));
        return buttons;
    }

    private void stepFrame(int direction) {
        playing = false;
        frame = Math.max(0, Math.min(clip.frameCount() - 1, frame + direction));
        playClick();
    }

    private void togglePlay() {
        if (playing) {
            playing = false;
        } else {
            playFrom = frame >= clip.frameCount() - 1 ? 0 : frame;
            playStartedAt = Util.getMeasuringTimeMs();
            playing = true;
        }
        playClick();
    }

    private void advancePlayback() {
        if (!playing) {
            return;
        }
        int before = frame;
        int at = playFrom + (int) ((Util.getMeasuringTimeMs() - playStartedAt) * clip.fps() / 1000L);
        if (at >= clip.frameCount() - 1) {
            at = clip.frameCount() - 1;
            playing = false;
        }
        frame = at;
        if (before < appear && frame >= appear) {
            appearFlashAt = Util.getMeasuringTimeMs();
        }
    }

    private void showFrame() {
        if (frame == shownFrame || clip == null) {
            return;
        }
        NativeImage image = CutscenePlayer.decodeFrame(clip.frames().get(frame));
        if (image == null) {
            return;
        }
        keyPreview(image);
        if (texture == null || image.getWidth() != textureW || image.getHeight() != textureH) {
            destroyTexture();
            texture = new NativeImageBackedTexture(image);
            textureW = image.getWidth();
            textureH = image.getHeight();
            textureId = AWildEncounter.id("import_preview_" + Integer.toHexString(System.identityHashCode(this)));
            if (client != null) {
                client.getTextureManager().registerTexture(textureId, texture);
            }
        } else {
            texture.setImage(image);
            texture.upload();
        }
        texture.setFilter(smooth, false);
        shownFrame = frame;
    }

    private void keyPreview(NativeImage image) {
        if (!importing()) {
            rawPixels = new int[0];
            return;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        if (rawPixels.length != w * h) {
            rawPixels = new int[w * h];
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                rawPixels[y * w + x] = ChromaKey.abgrToArgb(image.getColor(x, y));
            }
        }
        ChromaKey key = key();
        if (key == null || picking) {
            return;
        }
        int[] keyed = rawPixels.clone();
        key.apply(keyed);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setColor(x, y, ChromaKey.argbToAbgr(keyed[y * w + x]));
            }
        }
    }

    private void destroyTexture() {
        if (textureId != null && client != null) {
            client.getTextureManager().destroyTexture(textureId);
        }
        texture = null;
        textureId = null;
        shownFrame = -1;
    }

    private void renderEditor(DrawContext context, int mouseX, int mouseY) {
        advancePlayback();
        showFrame();
        int x = boxX();
        int y = boxY();
        int w = boxW();
        int h = boxH();
        context.fill(x, y, x + w, y + h, 0xFF1A1A1A);
        if (clip.transparent() || (importing() && keyColour != null && !picking)) {
            context.enableScissor(x, y, x + w, y + h);
            for (int cy = 0; cy < h; cy += 8) {
                for (int cx = (cy / 8) % 2 == 0 ? 0 : 8; cx < w; cx += 16) {
                    context.fill(x + cx, y + cy, x + cx + 8, y + cy + 8, 0xFF2C2C2C);
                }
            }
            context.disableScissor();
        }
        if (textureId != null) {
            int[] rect = imageRect();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.drawTexture(textureId, rect[0], rect[1], rect[2], rect[3], 0.0f, 0.0f,
                    textureW, textureH, textureW, textureH);
            RenderSystem.disableBlend();
        }
        boolean flashing = appearFlashAt >= 0 && Util.getMeasuringTimeMs() - appearFlashAt < APPEAR_FLASH_MS;
        if (frame == appear || flashing) {
            int edge = Theme.ACCENT;
            context.fill(x - 2, y - 2, x + w + 2, y, edge);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, edge);
            context.fill(x - 2, y, x, y + h, edge);
            context.fill(x + w, y, x + w + 2, y + h, edge);
            String tag = Tr.get("import.appears_here", "The Cobblemon appears here");
            int tagW = textRenderer.getWidth(tag) + 8;
            context.fill(x, y, x + tagW, y + 12, Theme.ACCENT);
            context.drawText(textRenderer, tag, x + 4, y + 2, Theme.TEXT_ON_ACCENT, false);
        }

        int tx = timelineX();
        int tw = timelineW();
        int ty = timelineY();
        int count = clip.frameCount();
        context.fill(tx, ty + 2, tx + tw, ty + 6, Theme.MAIN_DEEP);
        int at = tx + (count <= 1 ? 0 : tw * frame / (count - 1));
        context.fill(tx, ty + 2, at, ty + 6, Theme.MAIN_DARK);
        int mark = tx + (count <= 1 ? 0 : tw * appear / (count - 1));
        context.fill(mark - 1, ty - 2, mark + 1, ty + 10, Theme.ACCENT);
        context.fill(at - 1, ty, at + 2, ty + 8, Theme.TEXT);

        String info = Tr.get("import.frame_info", "Frame %1$s of %2$s (%3$s s of %4$s s). The Cobblemon appears "
                        + "at frame %5$s (%6$s s).", frame + 1, count, seconds(frame * 1000 / clip.fps()),
                seconds(count * 1000 / clip.fps()), appear + 1, seconds(appear * 1000 / clip.fps()));
        context.drawText(textRenderer, textRenderer.trimToWidth(info, panelW() - 28), left() + 14, infoY(),
                Theme.TEXT_MUTED, false);

        if (nameField != null) {
            String label = Tr.get("zone.name", "Name");
            context.drawText(textRenderer, label, left() + 14, nameRowY() + 4, Theme.TEXT, false);
            nameField.render(context, mouseX, mouseY, 0);
            String note;
            int noteColour = Theme.TEXT_MUTED;
            if (!nameOk()) {
                note = Tr.get("import.bad_name", "Not a folder name");
                noteColour = 0xFFE0573C;
            } else if (replaces()) {
                note = Tr.get("import.replaces", "Replaces the animation with this name");
                noteColour = 0xFFF5C542;
            } else {
                note = CustomClips.id(savingName());
            }
            int noteX = nameFieldX() + nameFieldW() + 8;
            context.drawText(textRenderer, textRenderer.trimToWidth(note, Math.max(10, pixelButtonX() - 8 - noteX)),
                    noteX, nameRowY() + 4, noteColour, false);
        }
        if (importing()) {
            renderKeyRow(context, mouseX, mouseY);
        }
        String hint = picking ? Tr.get("import.key.hint", "Click a color in the preview to make it see-through.")
                : Tr.get("import.keys", "← →: frame   Space: play   M: set the appearing frame");
        context.drawText(textRenderer, textRenderer.trimToWidth(hint, panelW() / 2), left() + 14, footerY() + 5,
                picking ? Theme.ACCENT : Theme.TEXT_DISABLED, false);
        if (picking) {
            renderPicker(context, mouseX, mouseY);
        }
    }

    private void renderKeyRow(DrawContext context, int mouseX, int mouseY) {
        int y = keyRowY();
        context.drawText(textRenderer, Tr.get("import.key", "Chroma Key"), left() + 14, y + 4, Theme.TEXT, false);
        drawSwatch(context, keySwatchX(), y, keyColour);
        boolean on = keyColour != null;
        context.drawText(textRenderer, Tr.get("import.key.intensity", "Intensity"), intensityLabelX(), y + 4,
                on ? Theme.TEXT : Theme.TEXT_DISABLED, false);
        int sx = sliderX();
        int sw = sliderW();
        int barY = y + 6;
        context.fill(sx, barY, sx + sw, barY + 4, Theme.MAIN_DEEP);
        int knob = sx + sw * (keyIntensity - 1) / 99;
        context.fill(sx, barY, knob, barY + 4, on ? Theme.MAIN_DARK : 0xFF3A3F1A);
        boolean hovered = onSlider(mouseX, mouseY) || draggingIntensity;
        context.fill(knob - 2, y + 2, knob + 3, y + 14, hovered ? Theme.ACCENT_HOVER
                : on ? Theme.ACCENT : Theme.TEXT_DISABLED);
        String value = Integer.toString(keyIntensity);
        context.drawText(textRenderer, value, sx + sw + 6, y + 4, on ? Theme.TEXT : Theme.TEXT_DISABLED, false);
    }

    private void drawSwatch(DrawContext context, int x, int y, Integer rgb) {
        context.fill(x, y, x + 16, y + 16, Theme.TEXT_MUTED);
        if (rgb == null) {
            context.fill(x + 1, y + 1, x + 15, y + 15, 0xFF1A1A1A);
            for (int i = 2; i < 14; i++) {
                context.fill(x + i, y + 15 - i, x + i + 1, y + 16 - i, 0xFFE0573C);
            }
        } else {
            context.fill(x + 1, y + 1, x + 15, y + 15, 0xFF000000 | rgb);
        }
    }

    private void renderPicker(DrawContext context, int mouseX, int mouseY) {
        Integer colour = colourAt(mouseX, mouseY);
        if (colour == null) {
            return;
        }
        int rgb = colour & 0xFFFFFF;
        String hex = String.format("#%06X", rgb);
        int w = 16 + 4 + textRenderer.getWidth(hex) + 6;
        int x = Math.min(mouseX + 10, width - w - 2);
        int y = Math.max(2, mouseY - 22);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        context.fill(x, y, x + w, y + 20, 0xF0101010);
        drawSwatch(context, x + 2, y + 2, rgb);
        context.drawText(textRenderer, hex, x + 22, y + 6, Theme.TEXT, false);
        context.fill(mouseX - 4, mouseY, mouseX - 1, mouseY + 1, Theme.TEXT);
        context.fill(mouseX + 2, mouseY, mouseX + 5, mouseY + 1, Theme.TEXT);
        context.fill(mouseX, mouseY - 4, mouseX + 1, mouseY - 1, Theme.TEXT);
        context.fill(mouseX, mouseY + 2, mouseX + 1, mouseY + 5, Theme.TEXT);
        context.getMatrices().pop();
    }

    private void seekTimeline(double mouseX) {
        int count = clip.frameCount();
        double fraction = Math.max(0, Math.min(1, (mouseX - timelineX()) / (double) timelineW()));
        frame = (int) Math.round(fraction * (count - 1));
        playing = false;
    }

    private boolean onTimeline(double mouseX, double mouseY) {
        return mouseX >= timelineX() - 4 && mouseX <= timelineX() + timelineW() + 4
                && mouseY >= timelineY() - 4 && mouseY <= timelineY() + 12;
    }

    private void save() {
        if (!nameOk() || mode != Mode.EDIT) {
            return;
        }
        String name = savingName();
        ChromaKey key = importing() ? key() : null;
        if (key != null) {
            startSaving(name, key);
            return;
        }
        try {
            if (converted != null) {
                CustomAnimations.save(name, converted, appear, smooth, null);
            } else {
                CustomAnimations.saveTiming(editingId, appear, smooth);
            }
            status = Tr.get("import.saved", "Saved %s. Choose it in an animation row of the Animations tab.",
                    CustomClips.id(name));
            statusColour = Theme.ON;
        } catch (Exception e) {
            status = Tr.get("import.save_failed", "Could not save %1$s: %2$s", name, e.toString());
            statusColour = 0xFFE0573C;
            AWildEncounter.LOGGER.warn("Could not save the custom animation {}: {}", name, e.toString());
        }
        playClick();
        backToList();
    }

    private void startSaving(String name, ChromaKey key) {
        GifConverter.Converted source = converted;
        int appearFrame = appear;
        boolean smoothFrames = smooth;
        mode = Mode.SAVING;
        picking = false;
        playing = false;
        convertDone = 0;
        convertTotal = source.files().size();
        convertCancelled = false;
        convertingName = name;
        clearAndInit();
        Thread thread = new Thread(() -> {
            String error = null;
            boolean written = false;
            try {
                GifConverter.Converted keyed = GifConverter.keyed(source, key, new GifConverter.Progress() {
                    @Override
                    public void update(int done, int total) {
                        convertDone = done;
                        convertTotal = total;
                    }

                    @Override
                    public boolean cancelled() {
                        return convertCancelled;
                    }
                });
                if (keyed != null && !convertCancelled) {
                    CustomAnimations.save(name, keyed, appearFrame, smoothFrames, key);
                    written = true;
                }
            } catch (Exception e) {
                error = e.toString();
                AWildEncounter.LOGGER.warn("Could not save the custom animation {}: {}", name, e.toString());
            } catch (OutOfMemoryError e) {
                error = Tr.get("import.error.memory", "There was not enough memory to read this GIF.");
            }
            String failure = error;
            boolean done = written;
            if (client != null) {
                client.execute(() -> saved(name, done, failure));
            }
        }, "A Wild Cobblemon Appeared! animation save");
        thread.setDaemon(true);
        thread.start();
    }

    private void saved(String name, boolean written, String error) {
        if (written) {
            status = Tr.get("import.saved", "Saved %s. Choose it in an animation row of the Animations tab.",
                    CustomClips.id(name));
            statusColour = Theme.ON;
            playClick();
            if (mode != Mode.LIST) {
                backToList();
            }
            return;
        }
        if (mode != Mode.SAVING) {
            return;
        }
        if (error != null) {
            status = Tr.get("import.save_failed", "Could not save %1$s: %2$s", name, error);
            statusColour = 0xFFE0573C;
            backToList();
            return;
        }
        mode = Mode.EDIT;
        clearAndInit();
    }

    private void backToList() {
        destroyTexture();
        clip = null;
        converted = null;
        editingId = null;
        playing = false;
        picking = false;
        rawPixels = new int[0];
        mode = Mode.LIST;
        clearAndInit();
    }

    private List<Button> buttons() {
        return switch (mode) {
            case LIST -> listButtons();
            case CONVERTING, SAVING -> convertingButtons();
            case EDIT -> editButtons();
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(left(), top(), left() + panelW(), bottom(), Theme.PANEL);
        context.fill(left(), top(), left() + panelW(), top() + HEADER_H, Theme.MAIN);
        context.fill(left(), top() + HEADER_H - 2, left() + panelW(), top() + HEADER_H, Theme.ACCENT);
        String header = switch (mode) {
            case LIST, CONVERTING -> Tr.get("import.title", "Custom Encounter Animations");
            case EDIT, SAVING -> converted != null ? Tr.get("import.title.import", "Import %s", sourceName)
                    : Tr.get("import.title.edit", "Edit %s", sourceName);
        };
        header = textRenderer.trimToWidth(header, panelW() - 20);
        context.drawText(textRenderer, header, (width - textRenderer.getWidth(header)) / 2, top() + 7,
                Theme.TEXT_ON_MAIN, false);

        switch (mode) {
            case LIST -> renderList(context, mouseX, mouseY);
            case CONVERTING, SAVING -> renderConverting(context);
            case EDIT -> renderEditor(context, mouseX, mouseY);
        }
        for (Button button : buttons()) {
            Theme.button(context, textRenderer, button.label(), button.x(), button.y(), button.width(), BUTTON_H,
                    button.primary(), button.enabled()
                            && Theme.inside(mouseX, mouseY, button.x(), button.y(), button.width(), BUTTON_H),
                    button.enabled());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (Button each : buttons()) {
            if (each.enabled() && Theme.inside(mouseX, mouseY, each.x(), each.y(), each.width(), BUTTON_H)) {
                each.action().run();
                return true;
            }
        }
        if (mode == Mode.LIST && clickList(mouseX, mouseY)) {
            return true;
        }
        if (mode == Mode.EDIT && picking) {
            Integer colour = colourAt(mouseX, mouseY);
            picking = false;
            shownFrame = -1;
            if (colour != null) {
                keyColour = colour & 0xFFFFFF;
                playClick();
                return true;
            }
        }
        if (mode == Mode.EDIT && onSlider(mouseX, mouseY)) {
            draggingIntensity = true;
            seekIntensity(mouseX);
            return true;
        }
        if (mode == Mode.EDIT && onTimeline(mouseX, mouseY)) {
            draggingTimeline = true;
            seekTimeline(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingTimeline && mode == Mode.EDIT) {
            seekTimeline(mouseX);
            return true;
        }
        if (draggingIntensity && mode == Mode.EDIT) {
            seekIntensity(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingTimeline = false;
        draggingIntensity = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mode == Mode.LIST) {
            scroll -= (int) (verticalAmount * ROW_H);
            clampScroll();
            return true;
        }
        if (mode == Mode.EDIT && onSlider(mouseX, mouseY)) {
            setIntensity(keyIntensity + (int) Math.signum(verticalAmount));
            return true;
        }
        if (mode == Mode.EDIT && mouseY < timelineY() + 12) {
            playing = false;
            frame = Math.max(0, Math.min(clip.frameCount() - 1, frame - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (mode == Mode.LIST) {
                close();
            } else if (mode == Mode.CONVERTING || mode == Mode.SAVING) {
                cancelWork();
            } else if (picking) {
                picking = false;
                shownFrame = -1;
            } else {
                backToList();
            }
            return true;
        }
        if (mode == Mode.EDIT && (nameField == null || !nameField.isFocused())) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                    stepFrame(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                    stepFrame(1);
                    return true;
                }
                case GLFW.GLFW_KEY_SPACE -> {
                    togglePlay();
                    return true;
                }
                case GLFW.GLFW_KEY_K -> {
                    if (importing()) {
                        togglePicking();
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_M -> {
                    appear = frame;
                    appearFlashAt = Util.getMeasuringTimeMs();
                    playClick();
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    save();
                    return true;
                }
                default -> {
                }
            }
        }
        if (mode == Mode.EDIT && nameField != null && nameField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    @Override
    public void removed() {
        convertCancelled = true;
        destroyTexture();
        super.removed();
    }

    @Override
    public void close() {
        convertCancelled = true;
        destroyTexture();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
