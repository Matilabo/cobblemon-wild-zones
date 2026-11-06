package com.awildencounter.client.gui;

import com.awildencounter.AWildEncounter;
import com.awildencounter.client.cutscene.CutscenePlayer;
import com.awildencounter.client.zone.ClientZones;
import com.awildencounter.config.ConfigOptions;
import com.awildencounter.config.ConfigOptions.Kind;
import com.awildencounter.config.ConfigOptions.Spec;
import com.awildencounter.config.ConfigOptions.Tab;
import com.awildencounter.config.EncounterConfig;
import com.awildencounter.encounter.EncounterAnimations;
import com.awildencounter.client.ChunkSender;
import com.awildencounter.net.Chunks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class WildConfigScreen extends Screen {
    private static final int HEADER_H = 44;
    private static final int TAB_H = 20;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 20;
    private static final int VALUE_W = 150;

    private static final Identifier TITLE = AWildEncounter.id("textures/gui/title.png");
    private static final int TITLE_W = 1024;
    private static final int TITLE_H = 64;

    private record Preset(String name, String description, JsonObject rules) {
    }

    private EncounterConfig config;
    private final List<DimensionScope> scopes;
    private int scopeIndex;
    private final boolean canEdit;
    private final boolean repelInstalled;
    private final List<Preset> presets = new ArrayList<>();
    private boolean titleFiltered = false;

    private EncounterConfig clipboard = null;
    private String clipboardFrom = null;

    private Tab tab = Tab.ENCOUNTERS;
    private final List<Spec> rows = new ArrayList<>();
    private int scroll = 0;
    private int selected = -1;
    private Spec dragging = null;

    private String editingKey = null;
    private StringBuilder editBuffer = new StringBuilder();

    private boolean onHighHalf = false;

    private static final String BIOME_ROW = "biomefilter|";
    private static final String OTHER_BIOMES = "*";
    private static final int SUGGESTIONS_SHOWN = 8;
    private static final int SUGGESTION_H = 12;
    private static final int PLUS_W = 16;

    private boolean biomeEditing = false;
    private StringBuilder biomeBuffer = new StringBuilder();
    private List<String> knownBiomes = null;
    private List<String> biomeSuggestions = new ArrayList<>();
    private int biomeSuggestionWidth = 0;
    private int biomeHighlight = 0;
    private int biomeFirst = 0;
    private boolean biomeFilled = false;
    private EncounterConfig.BiomeSpeciesFilter biomeClipboard = null;
    private String biomeClipboardFrom = null;

    public WildConfigScreen(List<DimensionScope> scopes, int selected, String presetsJson, boolean canEdit,
                            boolean repelInstalled) {
        super(Text.literal("A Wild Cobblemon Appeared!"));
        this.scopes = scopes;
        this.scopeIndex = Math.max(0, Math.min(selected, scopes.size() - 1));
        this.config = scopes.get(scopeIndex).config;
        this.canEdit = canEdit;
        this.repelInstalled = repelInstalled;
        try {
            JsonArray array = JsonParser.parseString(presetsJson).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject preset = element.getAsJsonObject();
                presets.add(new Preset(
                        preset.get("name").getAsString(),
                        preset.has("description") ? preset.get("description").getAsString() : "",
                        preset.has("rules") ? preset.getAsJsonObject("rules") : new JsonObject()));
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read the presets: {}", e.toString());
        }
    }

    @Override
    protected void init() {
        customClips = com.awildencounter.client.cutscene.CustomAnimations.list();
        rebuildRows();
    }

    private List<com.awildencounter.client.cutscene.CustomAnimations.Entry> customClips = List.of();

    private Spec withCustomClips(Spec spec) {
        if (!isAnimationRow(spec)) {
            return spec;
        }
        List<String> ids = new ArrayList<>(spec.ids());
        List<String> labels = new ArrayList<>(spec.labels());
        for (var entry : customClips) {
            ids.add(entry.id());
            labels.add(entry.name());
        }
        String current = getString(spec.key());
        if (com.awildencounter.encounter.CustomClips.isCustom(current) && !ids.contains(current)) {
            ids.add(current);
            labels.add(Tr.get("ui.custom.missing", "%s (missing)",
                    com.awildencounter.encounter.CustomClips.nameOf(current)));
        }
        return new Spec(spec.key(), spec.label(), spec.description(), spec.tab(), spec.parent(), spec.kind(),
                spec.values(), labels, spec.unit(), ids);
    }

    private void runAction(Spec spec) {
        playClick();
        if (ConfigOptions.OPEN_CUSTOM_FOLDER.equals(spec.key())) {
            com.awildencounter.client.cutscene.CustomAnimations.openFolder();
        } else if (ConfigOptions.IMPORT_GIF.equals(spec.key()) && client != null) {
            cancelEditing();
            client.setScreen(new AnimationImportScreen(this));
        }
    }

    private void rebuildRows() {
        rows.clear();
        boolean biomeRows = false;
        for (Spec spec : ConfigOptions.all()) {
            if (spec.tab() == tab && isVisible(spec)) {
                rows.add(withCustomClips(spec));
                if (spec.kind() == Kind.BIOME_FILTERS) {
                    addBiomeRows(spec);
                    biomeRows = true;
                }
            }
        }
        if (!biomeRows) {
            biomeEditing = false;
        }
        clampScroll();
        if (selected >= rows.size()) {
            selected = rows.size() - 1;
        }
    }

    private boolean isVisible(Spec spec) {
        if (spec.parent() == null) {
            return true;
        }
        for (String alternative : spec.parent().split("\\|")) {
            if (holds(alternative)) {
                return true;
            }
        }
        return false;
    }

    private boolean holds(String condition) {
        boolean negate = condition.startsWith("!");
        String body = negate ? condition.substring(1) : condition;
        int equals = body.indexOf('=');
        String key = equals >= 0 ? body.substring(0, equals) : body;
        boolean on;
        if (equals >= 0) {
            on = false;
            String value = getString(key);
            for (String wanted : body.substring(equals + 1).split(",")) {
                on |= wanted.trim().equals(value);
            }
        } else {
            on = getBool(key);
        }
        if (negate == on) {
            return false;
        }
        Spec parent = ConfigOptions.byKey(key);
        return parent == null || isVisible(parent);
    }

    private int depth(Spec spec) {
        if (isBiomeRow(spec)) {
            return spec.kind() == Kind.BIOME_ENTRY ? 1 : 2;
        }
        int depth = 0;
        String parent = spec.parent();
        while (parent != null) {
            Spec up = ConfigOptions.byKey(ConfigOptions.primaryParentKey(parent));
            if (up != null && up.tab() != spec.tab()) {
                break;
            }
            depth++;
            parent = up == null ? null : up.parent();
        }
        return depth;
    }

    private boolean getBool(String key) {
        try {
            Field field = field(key);
            if (field.getType() == boolean.class) {
                return field.getBoolean(config);
            }
            if (field.getType() == int.class) {
                return field.getInt(config) != 0;
            }
            Object value = field.get(config);
            return value != null && !value.toString().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private String getString(String key) {
        try {
            Object value = field(key).get(config);
            return value == null ? "" : value.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private int getInt(String key) {
        try {
            return field(key).getInt(config);
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(String key) {
        if (key.startsWith(BIOME_ROW)) {
            return biomeList(key, null);
        }
        try {
            return (List<String>) field(key).get(config);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private void set(String key, Object value) {
        if (key.startsWith(BIOME_ROW)) {
            biomeList(key, (List<String>) value);
            return;
        }
        try {
            field(key).set(config, value);
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not set {}: {}", key, e.toString());
        }
    }

    private static Field field(String key) throws NoSuchFieldException {
        return EncounterConfig.class.getField(key);
    }

    private void animationsChanged(String key) {
        if ("animationPreset".equals(key)) {
            config.applyAnimationPreset(getString(key));
        } else if (ConfigOptions.ANIMATION_KEYS.contains(key)) {
            config.animationPreset = "custom";
        }
    }

    private void edited() {
        config.preset = EncounterConfig.CUSTOM;
        currentScope().dirty = true;
        rebuildRows();
        playClick();
    }

    DimensionScope currentScope() {
        return scopes.get(scopeIndex);
    }

    List<DimensionScope> scopes() {
        return scopes;
    }

    void selectScope(DimensionScope scope) {
        int index = scopes.indexOf(scope);
        if (index < 0 || index == scopeIndex) {
            return;
        }
        cancelEditing();
        scopeIndex = index;
        config = scope.config;
        scroll = 0;
        selected = -1;
        rebuildRows();
    }

    private void cycleScope(int direction) {
        selectScope(scopes.get(Math.floorMod(scopeIndex + direction, scopes.size())));
        playClick();
    }

    void copyRules(DimensionScope from) {
        clipboard = from.config.copy();
        clipboardFrom = from.shownName();
    }

    boolean hasClipboard() {
        return clipboard != null;
    }

    String clipboardFrom() {
        return clipboardFrom;
    }

    void pasteRules(DimensionScope into) {
        if (clipboard == null) {
            return;
        }
        into.config = clipboard.copy();
        into.dirty = true;
        if (into == currentScope()) {
            config = into.config;
            rebuildRows();
        }
    }

    private void openDimensionManager() {
        if (client != null) {
            cancelEditing();
            client.setScreen(new DimensionManagerScreen(this));
        }
    }

    private void applyPreset(Preset preset) {
        cancelEditing();
        EncounterConfig fromPreset = EncounterConfig.fromJsonObject(preset.rules());
        fromPreset.preset = preset.name();
        currentScope().config = fromPreset;
        config = fromPreset;
        currentScope().dirty = true;
        rebuildRows();
        playClick();
    }

    private void cyclePreset(int direction) {
        if (!canEdit || presets.isEmpty()) {
            return;
        }
        int current = -1;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name().equals(config.preset)) {
                current = i;
            }
        }
        int next = current < 0
                ? (direction > 0 ? 0 : presets.size() - 1)
                : Math.floorMod(current + direction, presets.size());
        applyPreset(presets.get(next));
    }

    private Preset currentPreset() {
        for (Preset preset : presets) {
            if (preset.name().equals(config.preset)) {
                return preset;
            }
        }
        return null;
    }

    private void openExport() {
        if (client == null) {
            return;
        }
        cancelEditing();
        List<String> names = new ArrayList<>();
        for (Preset preset : presets) {
            names.add(preset.name());
        }
        client.setScreen(new ExportPresetScreen(this, config.toJson(), names, this::onExported));
    }

    private void onExported(String name) {
        EncounterConfig exported = config.copy();
        exported.preset = name;
        Preset entry = new Preset(name, Tr.get("ui.export.description", "Saved from the in-game settings."),
                exported.toJsonObject());
        boolean replaced = false;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name().equalsIgnoreCase(name)) {
                presets.set(i, entry);
                replaced = true;
            }
        }
        if (!replaced) {
            presets.add(entry);
        }
        config.preset = name;
        currentScope().dirty = true;
    }

    private void step(Spec spec, int direction) {
        if (spec.kind() == Kind.ACTION) {
            runAction(spec);
            return;
        }
        if (!canEdit || spec.isHeading()) {
            return;
        }
        switch (spec.kind()) {
            case TOGGLE -> set(spec.key(), !getBool(spec.key()));
            case OPTION -> {
                int size = spec.ids().size();
                int next = ((spec.indexOfId(getString(spec.key())) + direction) % size + size) % size;
                set(spec.key(), spec.ids().get(next));
                animationsChanged(spec.key());
            }
            case SLIDER -> {
                int index = Math.max(0, Math.min(spec.values().size() - 1, sliderIndex(spec) + direction));
                set(spec.key(), spec.values().get(index));
            }
            case RANGE -> {
                String key = rangeKey(spec, onHighHalf);
                int index = Math.max(0, Math.min(spec.values().size() - 1,
                        indexOfValue(spec, getInt(key)) + direction));
                set(key, spec.values().get(index));
                clampRange(spec);
            }
            case POINT -> set(rangeKey(spec, onHighHalf), getInt(rangeKey(spec, onHighHalf)) + direction * POINT_STEP);
            case LIST -> {
                openListEditor(spec);
                return;
            }
            case BIOME_FILTERS -> {
                beginBiomeEditing();
                return;
            }
            default -> {
                return;
            }
        }
        edited();
    }

    private static String rangeKey(Spec spec, boolean high) {
        return spec.ids().get(high && spec.ids().size() > 1 ? 1 : 0);
    }

    private void clampRange(Spec spec) {
        int low = getInt(rangeKey(spec, false));
        int high = getInt(rangeKey(spec, true));
        if (low <= 0 || high <= 0 || low <= high) {
            return;
        }
        set(rangeKey(spec, !onHighHalf), onHighHalf ? high : low);
    }

    private int indexOfValue(Spec spec, int value) {
        int exact = spec.values().indexOf(value);
        if (exact >= 0) {
            return exact;
        }
        int best = 0;
        for (int i = 0; i < spec.values().size(); i++) {
            if (Math.abs(spec.values().get(i) - value) < Math.abs(spec.values().get(best) - value)) {
                best = i;
            }
        }
        return best;
    }

    private String halfLabel(Spec spec, boolean high) {
        int value = getInt(rangeKey(spec, high));
        int index = spec.values().indexOf(value);
        return index >= 0 && !spec.labels().isEmpty() ? Tr.value(spec.labels().get(index)) : String.valueOf(value);
    }

    private int sliderIndex(Spec spec) {
        return indexOfValue(spec, getInt(spec.key()));
    }

    private String valueLabel(Spec spec) {
        return switch (spec.kind()) {
            case TOGGLE -> getBool(spec.key()) ? Tr.value("On") : Tr.value("Off");
            case OPTION -> Tr.value(spec.labels().get(spec.indexOfId(getString(spec.key()))));
            case ACTION -> ConfigOptions.IMPORT_GIF.equals(spec.key()) ? Tr.get("ui.import", "Import ›")
                    : Tr.get("ui.open", "Open") + " ›";
            case SLIDER -> {
                int value = getInt(spec.key());
                int index = spec.values().indexOf(value);
                if (index >= 0 && !spec.labels().isEmpty()) {
                    yield Tr.value(spec.labels().get(index));
                }
                yield switch (spec.unit()) {
                    case "in" -> value <= 1 ? Tr.value("Always") : Tr.get("value.one_in", "1 in %s", value);
                    case "s" -> value <= 0 ? Tr.value("Off") : duration(value);
                    default -> String.valueOf(value);
                };
            }
            case LIST -> {
                int size = getList(spec.key()).size();
                String label = size == 0 ? Tr.get("ui.list.empty", "Empty ›")
                        : size == 1 ? Tr.get("ui.list.one", "1 item ›") : Tr.get("ui.list.many", "%s items ›", size);
                yield canEdit ? label : label.replace("›", "").trim();
            }
            default -> "";
        };
    }

    private static String duration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int rest = seconds % 60;
        if (hours > 0) {
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
        if (minutes > 0) {
            return minutes + "m" + (rest > 0 ? " " + rest + "s" : "");
        }
        return rest + "s";
    }

    private void beginEditing(Spec spec) {
        beginEditing(spec, spec.key());
    }

    private void beginEditing(Spec spec, String key) {
        if (!canEdit) {
            return;
        }
        editingKey = key;
        editBuffer = new StringBuilder();
        playClick();
    }

    private void commitEditing() {
        if (editingKey == null) {
            return;
        }
        String key = editingKey;
        String typed = editBuffer.toString();
        editingKey = null;
        editBuffer = new StringBuilder();
        if (typed.isEmpty() || typed.equals("-")) {
            return;
        }
        try {
            int typedValue = Integer.parseInt(typed);
            set(key, isPointKey(key) ? typedValue : Math.max(0, typedValue));
            Spec spec = ConfigOptions.byKey(key);
            if (spec != null && spec.kind() == Kind.RANGE) {
                clampRange(spec);
            }
            edited();
        } catch (NumberFormatException ignored) {
        }
    }

    private static boolean isPointKey(String key) {
        for (Spec spec : ConfigOptions.all()) {
            if (spec.kind() == Kind.POINT && spec.ids().contains(key)) {
                return true;
            }
        }
        return false;
    }

    private void cancelEditing() {
        editingKey = null;
        editBuffer = new StringBuilder();
        biomeEditing = false;
    }

    private String currentCoordinate(String key) {
        if (client == null || client.player == null) {
            return null;
        }
        for (Spec spec : ConfigOptions.all()) {
            if (spec.kind() == Kind.POINT && spec.ids().contains(key)) {
                boolean z = spec.ids().size() > 1 && spec.ids().get(1).equals(key);
                return String.valueOf(z ? client.player.getBlockZ() : client.player.getBlockX());
            }
        }
        return null;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (biomeEditing) {
            typeBiome(String.valueOf(chr));
            return true;
        }
        if (editingKey != null) {
            if (chr >= '0' && chr <= '9' && editBuffer.length() < 9) {
                editBuffer.append(chr);
            } else if (chr == '-' && editBuffer.isEmpty() && isPointKey(editingKey)) {
                editBuffer.append(chr);
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void previewAnimation(Spec spec) {
        String clip = EncounterAnimations.resolve(getString(spec.key()),
                EncounterAnimations.environmentFor(spec.key()), new java.util.Random());
        CutscenePlayer.play(clip);
        playClick();
    }

    private void openListEditor(Spec spec) {
        if (client != null) {
            String title = isBiomeRow(spec)
                    ? biomeName(biomeOf(spec.key())) + ": " + Tr.label(spec) : Tr.label(spec);
            java.util.function.Consumer<List<String>> onDone = values -> {
                set(spec.key(), values);
                edited();
            };
            if ("encounterBlocks".equals(spec.key())) {
                client.setScreen(new ListEditScreen(this, title, Tr.description(spec), getList(spec.key()),
                        FieldSuggestions.blocks(), onDone));
            } else {
                client.setScreen(new SpeciesListScreen(this, title, Tr.description(spec), getList(spec.key()), onDone));
            }
        }
    }

    private void save() {
        DimensionScope current = currentScope();
        JsonObject edited = new JsonObject();
        for (DimensionScope scope : scopes) {
            if (scope.dirty || (scope == current && !scope.isAll())) {
                edited.add(scope.id, scope.config.toJsonObject());
            }
        }
        JsonObject names = new JsonObject();
        for (DimensionScope scope : scopes) {
            if (!scope.isAll() && scope.renamed()) {
                names.addProperty(scope.id, scope.name);
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("selected", current.id);
        root.add("scopes", edited);
        root.add("names", names);
        if (ChunkSender.canSend()) {
            ChunkSender.send(Chunks.SAVE_CONFIG, root.toString());
        }
        close();
    }

    private int listLeft() {
        return 14;
    }

    private int listRight() {
        return Math.max(listLeft() + 260, (int) (width * 0.60));
    }

    private int listTop() {
        return HEADER_H + TAB_H + 8;
    }

    private int listBottom() {
        return height - FOOTER_H - 8;
    }

    private int valueLeft() {
        return listRight() - VALUE_W - 8;
    }

    private int valueRight() {
        return listRight() - 8;
    }

    private int numberBoxLeft() {
        return valueRight() - 58;
    }

    private int sliderBarRight() {
        return numberBoxLeft() - 6;
    }

    private static final int RANGE_BOX_W = 34;

    private static final int POINT_STEP = 16;

    private int rangeBoxLeft() {
        return valueRight() - RANGE_BOX_W * 2 - 4;
    }

    private int previewButtonX() {
        return valueLeft() - 24;
    }

    private static boolean isAnimationRow(Spec spec) {
        return spec.key() != null && ConfigOptions.ANIMATION_KEYS.contains(spec.key());
    }

    private void clampScroll() {
        int max = Math.max(0, rows.size() * ROW_H - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private record FooterButton(String label, boolean primary, int x, int y, int width, int height, Runnable action) {
    }

    private List<FooterButton> footerButtons() {
        List<FooterButton> buttons = new ArrayList<>();
        int y = height - FOOTER_H + 6;
        int h = 18;
        int x = width - 12;
        String[] ids = canEdit ? new String[]{"save", "export", "cancel"} : new String[]{"close"};
        for (String id : ids) {
            String label = switch (id) {
                case "cancel" -> Tr.get("ui.cancel", "Cancel");
                case "save" -> Tr.get("ui.save", "Save");
                case "export" -> Tr.get("ui.export", "Export");
                default -> Tr.get("ui.close", "Close");
            };
            int w = textRenderer.getWidth(label) + 28;
            x -= w;
            Runnable action = switch (id) {
                case "save" -> this::save;
                case "export" -> this::openExport;
                default -> this::close;
            };
            buttons.add(new FooterButton(label, id.equals("save"), x, y, w, h, action));
            x -= 8;
        }
        return buttons;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        drawHeader(context, mouseX, mouseY);
        drawTabs(context, mouseX, mouseY);
        drawList(context, mouseX, mouseY);
        drawDescription(context);
        drawFooter(context, mouseX, mouseY);
        drawBiomeSuggestions(context, mouseX, mouseY);
        drawSelectorTooltip(context, mouseX, mouseY);
    }

    private String zonesLabel() {
        int zones = ClientZones.all().size();
        String count = zones == 1 ? Tr.get("ui.zones.one", "1 Wild Zone")
                : Tr.get("ui.zones.many", "%s Wild Zones", zones);
        return canEdit ? count + " ›" : count;
    }

    private int zonesButtonW() {
        return textRenderer.getWidth(zonesLabel()) + 24;
    }

    private int zonesButtonX() {
        return width - 12 - zonesButtonW();
    }

    private static final int ZONES_BUTTON_Y = 11;
    private static final int ZONES_BUTTON_H = 20;

    private int presetBoxWidth() {
        return Math.max(120, Math.min(200, width / 4));
    }

    private int presetBoxX() {
        return (width - presetBoxWidth()) / 2;
    }

    private static final int PRESET_BOX_Y = 12;
    private static final int PRESET_BOX_H = 20;

    private void drawHeader(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, HEADER_H, Theme.MAIN);
        context.fill(0, HEADER_H - 3, width, HEADER_H, Theme.ACCENT);

        if (!titleFiltered && client != null) {
            client.getTextureManager().getTexture(TITLE).setFilter(true, false);
            titleFiltered = true;
        }
        int titleW = Math.max(64, Math.min(24 * TITLE_W / TITLE_H, presetBoxX() - 22));
        int titleH = titleW * TITLE_H / TITLE_W;
        RenderSystem.enableBlend();
        context.drawTexture(TITLE, 10, (HEADER_H - 3 - titleH) / 2, titleW, titleH, 0.0f, 0.0f, TITLE_W, TITLE_H,
                TITLE_W, TITLE_H);

        int boxW = presetBoxWidth();
        int boxX = presetBoxX();
        int boxY = PRESET_BOX_Y;
        boolean boxHovered = canEdit && Theme.inside(mouseX, mouseY, boxX, boxY, boxW, PRESET_BOX_H);
        context.fill(boxX, boxY, boxX + boxW, boxY + PRESET_BOX_H, boxHovered ? Theme.MAIN_DARK : Theme.MAIN_DEEP);
        context.fill(boxX, boxY + PRESET_BOX_H - 2, boxX + boxW, boxY + PRESET_BOX_H, Theme.ACCENT);
        if (canEdit) {
            context.drawText(textRenderer, "◀", boxX + 6, boxY + 6, Theme.ACCENT, false);
            context.drawText(textRenderer, "▶", boxX + boxW - 12, boxY + 6, Theme.ACCENT, false);
        }
        String shownPreset = EncounterConfig.CUSTOM.equals(config.preset)
                ? Tr.get("ui.preset.custom", "Custom") : config.preset;
        String presetLabel = textRenderer.trimToWidth(shownPreset, boxW - 40);
        context.drawText(textRenderer, presetLabel, boxX + (boxW - textRenderer.getWidth(presetLabel)) / 2, boxY + 6,
                Theme.TEXT, false);
        String caption = Tr.get("ui.preset", "PRESET");
        context.drawText(textRenderer, caption, boxX + (boxW - textRenderer.getWidth(caption)) / 2, 2,
                Theme.TEXT_ON_MAIN_MUTED, false);

        int right = width - 12;
        if (!canEdit) {
            String readOnly = Tr.get("ui.read_only", "Read only");
            String count = zonesLabel();
            context.drawText(textRenderer, readOnly, right - textRenderer.getWidth(readOnly), 10, Theme.MAIN_DEEP,
                    false);
            context.drawText(textRenderer, count, right - textRenderer.getWidth(count), 22,
                    Theme.TEXT_ON_MAIN_MUTED, false);
            return;
        }
        int x = zonesButtonX();
        boolean hovered = Theme.inside(mouseX, mouseY, x, ZONES_BUTTON_Y, zonesButtonW(), ZONES_BUTTON_H);
        context.fill(x, ZONES_BUTTON_Y, x + zonesButtonW(), ZONES_BUTTON_Y + ZONES_BUTTON_H,
                hovered ? Theme.MAIN_DARK : Theme.MAIN_DEEP);
        context.fill(x, ZONES_BUTTON_Y + ZONES_BUTTON_H - 2, x + zonesButtonW(), ZONES_BUTTON_Y + ZONES_BUTTON_H,
                Theme.ACCENT);
        String label = zonesLabel();
        context.drawText(textRenderer, label, x + (zonesButtonW() - textRenderer.getWidth(label)) / 2,
                ZONES_BUTTON_Y + 6, hovered ? Theme.TEXT : Theme.ACCENT, false);
    }

    private void drawTabs(DrawContext context, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        int left = listLeft();
        int total = listRight() - left;
        int tabW = total / tabs.length;
        int y = HEADER_H + 4;
        for (int i = 0; i < tabs.length; i++) {
            int x = left + i * tabW;
            boolean active = tabs[i] == tab;
            boolean hovered = Theme.inside(mouseX, mouseY, x, y, tabW - 2, TAB_H - 2);
            int fill = active ? Theme.ACCENT : hovered ? Theme.MAIN : Theme.MAIN_DARK;
            context.fill(x, y, x + tabW - 2, y + TAB_H - 2, fill);
            context.fill(x, y + TAB_H - 4, x + tabW - 2, y + TAB_H - 2, active ? Theme.ACCENT_DARK : Theme.MAIN_DEEP);
            String title = textRenderer.trimToWidth(Tr.tab(tabs[i]), tabW - 6);
            int colour = active ? Theme.TEXT_ON_ACCENT : hovered ? Theme.TEXT_ON_MAIN : Theme.TEXT;
            context.drawText(textRenderer, title, x + (tabW - 2 - textRenderer.getWidth(title)) / 2, y + 5,
                    colour, false);
        }
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        int left = listLeft();
        int right = listRight();
        int top = listTop();
        int bottom = listBottom();
        context.fill(left - 4, top - 4, right + 4, bottom + 4, Theme.PANEL);
        context.fill(left - 4, top - 4, right + 4, top - 3, 0x55FFFFFF);
        context.fill(left - 4, bottom + 3, right + 4, bottom + 4, 0x55FFFFFF);

        context.enableScissor(left - 4, top - 2, right + 4, bottom + 2);
        int y = top - scroll;
        for (int i = 0; i < rows.size(); i++, y += ROW_H) {
            if (y + ROW_H < top || y > bottom) {
                continue;
            }
            Spec spec = rows.get(i);
            if (spec.isHeading()) {
                drawHeading(context, spec, left, y, right);
                continue;
            }
            drawRow(context, spec, left, y, right, i == selected, mouseX, mouseY);
        }
        context.disableScissor();

        int content = rows.size() * ROW_H;
        int view = bottom - top;
        if (content > view) {
            int trackX = right + 8;
            context.fill(trackX, top, trackX + 2, bottom, 0x44FFFFFF);
            int thumb = Math.max(18, view * view / content);
            int thumbY = top + (view - thumb) * scroll / Math.max(1, content - view);
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumb, Theme.TEXT);
        }
    }

    private void drawHeading(DrawContext context, Spec spec, int left, int y, int right) {
        String heading = Tr.label(spec).toUpperCase(Locale.ROOT);
        context.drawText(textRenderer, heading, left + 4, y + 8, Theme.ACCENT, false);
        int lineX = left + 10 + textRenderer.getWidth(heading);
        context.fill(lineX, y + 11, right - 4, y + 12, 0x44ED9130);
    }

    private void drawRow(DrawContext context, Spec spec, int left, int y, int right, boolean isSelected,
                         int mouseX, int mouseY) {
        int indent = depth(spec) * 12;
        if (isSelected) {
            context.fill(left + indent, y + 1, right, y + ROW_H - 1, Theme.ACCENT);
            context.fill(left + indent, y + ROW_H - 3, right, y + ROW_H - 1, Theme.ACCENT_DARK);

            context.drawText(textRenderer, "▶", left + indent - 9, y + 6, Theme.ACCENT, false);
        }
        int labelColour = isSelected ? Theme.TEXT_ON_ACCENT : Theme.TEXT;
        if (indent > 0 && !isSelected) {
            context.fill(left + indent - 6, y + 9, left + indent - 3, y + 10, 0x66FFFFFF);
        }

        int labelWidth = valueLeft() - left - indent - 12 - (isAnimationRow(spec) ? 26 : 0);
        if (spec.kind() == Kind.BIOME_FILTERS) {
            labelWidth = biomeBoxLeft() - left - indent - 10;
        } else if (spec.kind() == Kind.BIOME_ENTRY) {
            labelWidth = biomeButtons(spec).get(biomeButtons(spec).size() - 1).x() - left - indent - 10;
        }
        String label = textRenderer.trimToWidth(rowLabel(spec), labelWidth);
        context.drawText(textRenderer, label, left + indent + 6, y + 6, labelColour, false);

        int valueColour = isSelected ? Theme.TEXT_ON_ACCENT
                : spec.kind() == Kind.TOGGLE ? (getBool(spec.key()) ? Theme.ON : Theme.OFF)
                : Theme.TEXT_MUTED;
        switch (spec.kind()) {
            case SLIDER -> {
                drawSlider(context, spec, y, isSelected, valueColour);
                return;
            }
            case RANGE -> {
                drawRange(context, spec, y, isSelected, valueColour);
                return;
            }
            case POINT -> {
                drawPointBox(context, false, spec, y, isSelected, valueColour);
                drawPointBox(context, true, spec, y, isSelected, valueColour);
                return;
            }
            case BIOME_FILTERS -> {
                drawBiomeBox(context, y, isSelected, mouseX, mouseY);
                return;
            }
            case BIOME_ENTRY -> {
                for (RowButton button : biomeButtons(spec)) {
                    boolean enabled = button.id().equals("copy") || (canEdit && (!button.id().equals("paste")
                            || biomeClipboard != null));
                    Theme.button(context, textRenderer, button.label(), button.x(), y + 3, button.width(), ROW_H - 6,
                            false, Theme.inside(mouseX, mouseY, button.x(), y + 3, button.width(), ROW_H - 6),
                            enabled);
                }
                return;
            }
            default -> {
            }
        }
        String value = valueLabel(spec);
        int center = (valueLeft() + valueRight()) / 2;
        context.drawText(textRenderer, value, center - textRenderer.getWidth(value) / 2, y + 6, valueColour, false);
        if (isAnimationRow(spec)) {
            drawPreviewButton(context, y, Theme.inside(mouseX, mouseY, previewButtonX(), y + 4, 16, ROW_H - 8));
        }
        if (isSelected && canEdit && spec.kind() != Kind.LIST && spec.kind() != Kind.ACTION) {
            drawArrow(context, valueLeft() + 4, y, true);
            drawArrow(context, valueRight() - 14, y, false);
        }
    }

    private void drawSlider(DrawContext context, Spec spec, int y, boolean isSelected, int valueColour) {
        int barLeft = valueLeft() + 16;
        int barRight = sliderBarRight();
        int barY = y + 9;
        int index = sliderIndex(spec);
        int steps = Math.max(1, spec.values().size() - 1);
        int knob = barLeft + (barRight - barLeft) * index / steps;
        int track = isSelected ? Theme.MAIN_DEEP : 0x88FFFFFF;
        context.fill(barLeft, barY, barRight, barY + 2, track);
        context.fill(barLeft, barY, knob, barY + 2, isSelected ? Theme.MAIN_DARK : Theme.TEXT);
        context.fill(knob - 1, barY - 4, knob + 2, barY + 6, isSelected ? Theme.MAIN_DEEP : Theme.TEXT);
        drawNumberBox(context, spec, y, valueColour);
        if (isSelected && canEdit) {
            drawArrow(context, valueLeft() + 2, y, true);
        }
    }

    private void drawNumberBox(DrawContext context, Spec spec, int y, int valueColour) {
        int left = numberBoxLeft();
        int right = valueRight();
        boolean editing = spec.key().equals(editingKey);
        context.fill(left, y + 3, right, y + ROW_H - 3, editing ? Theme.MAIN_DEEP : 0x33000000);
        if (editing) {
            context.fill(left, y + ROW_H - 5, right, y + ROW_H - 3, Theme.ACCENT);
        }
        String text = editing ? editBuffer + "_" : valueLabel(spec);
        text = textRenderer.trimToWidth(text, right - left - 6);
        context.drawText(textRenderer, text, right - 3 - textRenderer.getWidth(text), y + 6,
                editing ? Theme.TEXT : valueColour, false);
    }

    private void drawRange(DrawContext context, Spec spec, int y, boolean isSelected, int valueColour) {
        int barLeft = valueLeft() + 16;
        int barRight = rangeBoxLeft() - 6;
        int barY = y + 9;
        int steps = Math.max(1, spec.values().size() - 1);
        int lowKnob = barLeft + (barRight - barLeft) * indexOfValue(spec, getInt(rangeKey(spec, false))) / steps;
        int highKnob = barLeft + (barRight - barLeft) * indexOfValue(spec, getInt(rangeKey(spec, true))) / steps;

        context.fill(barLeft, barY, barRight, barY + 2, isSelected ? Theme.MAIN_DEEP : 0x88FFFFFF);
        context.fill(Math.min(lowKnob, highKnob), barY, Math.max(lowKnob, highKnob), barY + 2,
                isSelected ? Theme.MAIN_DARK : Theme.TEXT);
        for (int knob : new int[]{lowKnob, highKnob}) {
            context.fill(knob - 1, barY - 4, knob + 2, barY + 6, isSelected ? Theme.MAIN_DEEP : Theme.TEXT);
        }
        drawHalfBox(context, spec, false, y, isSelected, valueColour);
        drawHalfBox(context, spec, true, y, isSelected, valueColour);
    }

    private void drawHalfBox(DrawContext context, Spec spec, boolean high, int y, boolean isSelected,
                             int valueColour) {
        int left = high ? rangeBoxLeft() + RANGE_BOX_W + 4 : rangeBoxLeft();
        int right = left + RANGE_BOX_W;
        String key = rangeKey(spec, high);
        boolean editing = key.equals(editingKey);
        boolean active = isSelected && onHighHalf == high;
        context.fill(left, y + 3, right, y + ROW_H - 3, editing ? Theme.MAIN_DEEP : 0x33000000);
        if (editing || active) {
            context.fill(left, y + ROW_H - 5, right, y + ROW_H - 3, editing ? Theme.ACCENT : Theme.MAIN_DEEP);
        }
        String text = editing ? editBuffer + "_" : halfLabel(spec, high);
        text = textRenderer.trimToWidth(text, right - left - 6);
        context.drawText(textRenderer, text, right - 3 - textRenderer.getWidth(text), y + 6,
                editing ? Theme.TEXT : valueColour, false);
    }

    private int pointMiddle() {
        return (valueLeft() + valueRight()) / 2;
    }

    private void drawPointBox(DrawContext context, boolean high, Spec spec, int y, boolean isSelected,
                              int valueColour) {
        int left = high ? pointMiddle() + 2 : valueLeft() + 2;
        int right = high ? valueRight() : pointMiddle() - 2;
        String key = rangeKey(spec, high);
        boolean editing = key.equals(editingKey);
        boolean active = isSelected && onHighHalf == high;
        context.fill(left, y + 3, right, y + ROW_H - 3, editing ? Theme.MAIN_DEEP : 0x33000000);
        if (editing || active) {
            context.fill(left, y + ROW_H - 5, right, y + ROW_H - 3, editing ? Theme.ACCENT : Theme.MAIN_DEEP);
        }
        String axis = high ? "Z" : "X";
        context.drawText(textRenderer, axis, left + 4, y + 6, isSelected ? Theme.TEXT_ON_ACCENT : Theme.TEXT_MUTED,
                false);
        String here = editing && editBuffer.isEmpty() ? currentCoordinate(key) : null;
        if (here != null) {
            String hint = textRenderer.trimToWidth(here, right - left - 22);
            context.drawText(textRenderer, hint, right - 3 - textRenderer.getWidth("_") - textRenderer.getWidth(hint),
                    y + 6, Theme.TEXT_DISABLED, false);
        }
        String text = editing ? editBuffer + "_" : String.valueOf(getInt(key));
        text = textRenderer.trimToWidth(text, right - left - 16);
        context.drawText(textRenderer, text, right - 3 - textRenderer.getWidth(text), y + 6,
                editing ? Theme.TEXT : valueColour, false);
    }

    private void drawPreviewButton(DrawContext context, int y, boolean hovered) {
        int x = previewButtonX();
        context.fill(x, y + 4, x + 16, y + ROW_H - 4, hovered ? Theme.ACCENT_DARK : Theme.MAIN_DEEP);
        context.drawText(textRenderer, "▶", x + 5, y + 6, hovered ? Theme.TEXT_ON_ACCENT : Theme.ACCENT, false);
    }

    private void drawArrow(DrawContext context, int x, int y, boolean left) {
        context.fill(x, y + 4, x + 11, y + 15, Theme.MAIN_DEEP);
        context.fill(x + 1, y + 3, x + 10, y + 16, Theme.MAIN_DEEP);
        context.drawText(textRenderer, left ? "◀" : "▶", x + 2, y + 6, Theme.TEXT, false);
    }

    private void drawDescription(DrawContext context) {
        int left = listRight() + 20;
        int right = width - 14;
        int top = listTop() + 6;
        if (right - left < 80) {
            return;
        }
        String title;
        String body;
        Spec spec = selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
        if (spec != null && !spec.isHeading()) {
            title = rowLabel(spec);
            body = Tr.description(spec);
            if (spec.kind() == Kind.BIOME_FILTERS) {
                body = Tr.get("ui.biome.add.desc", "Type the name of a biome, as in /locate biome. Tab "
                        + "completes the name, and the arrow keys move through the list. Push Enter or + to add "
                        + "the biome. Each biome that you add gets its own Whitelist and Blacklist. The other "
                        + "biomes use Every Other Biome.");
            }
            if (spec.kind() == Kind.BIOME_ENTRY && biomeClipboard != null) {
                body += " " + Tr.get("ui.biome.copied", "Copied: %s.", biomeClipboardFrom);
            }
            if (spec.kind() == Kind.POINT) {
                body += " " + Tr.get("ui.point.tab", "While typing in X or Z, Tab fills in where you stand.");
            }
            if ("respectRepels".equals(spec.key()) && !repelInstalled) {
                body += " " + Tr.get("ui.no_repel", "Cobblemon Repel is not installed, so only repel effects and "
                        + "the repel tag count.");
            }
        } else {
            Preset preset = currentPreset();
            title = preset != null ? preset.name()
                    : Tr.get("ui.rules_for", "Rules for %s", currentScope().shownName());
            body = (preset != null && !preset.description().isEmpty() ? preset.description() + " " : "")
                    + Tr.get("ui.pick_hint", "Hover or use the arrow keys to pick an option. Q and E switch tabs.");
        }
        context.fill(left, top, left + 3, top + 12, Theme.ACCENT);
        context.drawText(textRenderer, title, left + 9, top + 2, Theme.TEXT, true);
        int y = top + 22;
        for (var line : textRenderer.wrapLines(Text.literal(body), right - left - 9)) {
            context.drawText(textRenderer, line, left + 9, y, Theme.TEXT_MUTED, true);
            y += 11;
        }
    }

    private record SelectorLayout(int labelX, String label, int boxX, int boxW, int squareX, int y) {
    }

    private static final int SELECTOR_H = 18;

    private SelectorLayout selectorLayout() {
        int buttonsLeft = width - 12;
        for (FooterButton button : footerButtons()) {
            buttonsLeft = Math.min(buttonsLeft, button.x());
        }
        int y = height - FOOTER_H + 6;
        int longest = 0;
        for (DimensionScope scope : scopes) {
            longest = Math.max(longest, textRenderer.getWidth(scope.shownName()));
        }
        int boxW = Math.max(90, Math.min(170, longest + 40));
        String label = Tr.get("ui.dimensions.label", "Affected dimensions:");
        int labelW = textRenderer.getWidth(label) + 6;
        int square = canEdit ? SELECTOR_H + 4 : 0;
        int total = labelW + boxW + square;
        int x = (width - total) / 2;
        if (x + total > buttonsLeft - 10) {
            x = buttonsLeft - 10 - total;
        }
        if (x < 12) {
            label = "";
            labelW = 0;
            total = boxW + square;
            x = Math.max(12, Math.min((width - total) / 2, buttonsLeft - 10 - total));
            boxW = Math.max(60, Math.min(boxW, buttonsLeft - 10 - x - square));
        }
        int boxX = x + labelW;
        return new SelectorLayout(x, label, boxX, boxW, boxX + boxW + 4, y);
    }

    private void drawSelector(DrawContext context, int mouseX, int mouseY) {
        SelectorLayout layout = selectorLayout();
        if (!layout.label().isEmpty()) {
            context.drawText(textRenderer, layout.label(), layout.labelX(), layout.y() + 5, Theme.TEXT_ON_MAIN_MUTED,
                    false);
        }
        boolean hovered = Theme.inside(mouseX, mouseY, layout.boxX(), layout.y(), layout.boxW(), SELECTOR_H);
        context.fill(layout.boxX(), layout.y(), layout.boxX() + layout.boxW(), layout.y() + SELECTOR_H,
                hovered ? Theme.MAIN_DARK : Theme.MAIN_DEEP);
        context.fill(layout.boxX(), layout.y() + SELECTOR_H - 2, layout.boxX() + layout.boxW(), layout.y() + SELECTOR_H,
                Theme.ACCENT);
        context.drawText(textRenderer, "◀", layout.boxX() + 5, layout.y() + 5, Theme.ACCENT, false);
        context.drawText(textRenderer, "▶", layout.boxX() + layout.boxW() - 11, layout.y() + 5, Theme.ACCENT, false);
        DimensionScope scope = currentScope();
        String name = textRenderer.trimToWidth(scope.shownName() + (scope.dirty ? "*" : ""), layout.boxW() - 34);
        context.drawText(textRenderer, name, layout.boxX() + (layout.boxW() - textRenderer.getWidth(name)) / 2,
                layout.y() + 5, Theme.TEXT, false);
        if (canEdit) {
            int sx = layout.squareX();
            boolean squareHovered = Theme.inside(mouseX, mouseY, sx, layout.y(), SELECTOR_H, SELECTOR_H);
            context.fill(sx, layout.y(), sx + SELECTOR_H, layout.y() + SELECTOR_H,
                    squareHovered ? Theme.ACCENT : Theme.MAIN_DEEP);
            context.fill(sx, layout.y() + SELECTOR_H - 2, sx + SELECTOR_H, layout.y() + SELECTOR_H,
                    squareHovered ? Theme.ACCENT_DARK : Theme.ACCENT);
            context.drawText(textRenderer, "≡", sx + 6, layout.y() + 5,
                    squareHovered ? Theme.TEXT_ON_ACCENT : Theme.ACCENT, false);
        }
    }

    private void drawSelectorTooltip(DrawContext context, int mouseX, int mouseY) {
        SelectorLayout layout = selectorLayout();
        if (canEdit && Theme.inside(mouseX, mouseY, layout.squareX(), layout.y(), SELECTOR_H, SELECTOR_H)) {
            context.drawTooltip(textRenderer, Text.literal(Tr.get("ui.dimensions.manage",
                    "Manage Multiple Dimensions")), mouseX, mouseY);
        } else if (Theme.inside(mouseX, mouseY, layout.boxX(), layout.y(), layout.boxW(), SELECTOR_H)) {
            String help = Tr.get("ui.dimensions.help", "These rules apply to this dimension. ALL is the rule set "
                    + "for every dimension. Saving with a dimension selected switches to per-dimension rules, and "
                    + "dimensions without rules of their own keep using ALL's; saving with ALL selected makes the "
                    + "ALL rules apply to every dimension again, without deleting the other dimensions' rules. "
                    + "* means there are unsaved changes.");
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(help), 220), mouseX, mouseY);
        }
    }

    private boolean clickSelector(double mouseX, double mouseY) {
        SelectorLayout layout = selectorLayout();
        if (canEdit && Theme.inside(mouseX, mouseY, layout.squareX(), layout.y(), SELECTOR_H, SELECTOR_H)) {
            playClick();
            openDimensionManager();
            return true;
        }
        if (Theme.inside(mouseX, mouseY, layout.boxX(), layout.y(), layout.boxW(), SELECTOR_H)) {
            cycleScope(mouseX < layout.boxX() + layout.boxW() / 2.0 ? -1 : 1);
            return true;
        }
        return false;
    }

    private void drawFooter(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, height - FOOTER_H, width, height, Theme.MAIN);
        context.fill(0, height - FOOTER_H, width, height - FOOTER_H + 2, Theme.ACCENT);
        String keys = Tr.get("ui.keys", "Q/E: tabs   ◀ ▶: change   ↑ ↓: select");
        if (12 + textRenderer.getWidth(keys) < selectorLayout().labelX() - 10) {
            context.drawText(textRenderer, keys, 12, height - FOOTER_H + 11, Theme.TEXT_ON_MAIN_MUTED, false);
        }
        drawSelector(context, mouseX, mouseY);
        for (FooterButton button : footerButtons()) {
            Theme.button(context, textRenderer, button.label(), button.x(), button.y(), button.width(),
                    button.height(), button.primary(),
                    Theme.inside(mouseX, mouseY, button.x(), button.y(), button.width(), button.height()), true);
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (insideSuggestions(mouseX, mouseY)) {
            return;
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0 && !rows.get(index).isHeading()) {
            selected = index;
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listLeft() || mouseX >= listRight() || mouseY < listTop() || mouseY >= listBottom()) {
            return -1;
        }
        int index = ((int) mouseY - listTop() + scroll) / ROW_H;
        return index >= 0 && index < rows.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (biomeEditing && clickWhileTypingBiome(mouseX, mouseY)) {
            return true;
        }
        if (editingKey != null) {
            commitEditing();
        }
        for (FooterButton footer : footerButtons()) {
            if (Theme.inside(mouseX, mouseY, footer.x(), footer.y(), footer.width(), footer.height())) {
                playClick();
                footer.action().run();
                return true;
            }
        }
        if (clickSelector(mouseX, mouseY)) {
            return true;
        }
        if (canEdit && Theme.inside(mouseX, mouseY, presetBoxX(), PRESET_BOX_Y, presetBoxWidth(), PRESET_BOX_H)) {
            cyclePreset(mouseX < presetBoxX() + presetBoxWidth() / 2.0 ? -1 : 1);
            return true;
        }
        if (canEdit && client != null
                && Theme.inside(mouseX, mouseY, zonesButtonX(), ZONES_BUTTON_Y, zonesButtonW(), ZONES_BUTTON_H)) {
            playClick();
            cancelEditing();
            client.setScreen(new ZoneManagerScreen(this));
            return true;
        }
        Tab[] tabs = Tab.values();
        int tabW = (listRight() - listLeft()) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            if (Theme.inside(mouseX, mouseY, listLeft() + i * tabW, HEADER_H + 4, tabW - 2, TAB_H - 2)) {
                switchTab(tabs[i]);
                return true;
            }
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        Spec spec = rows.get(index);
        if (spec.isHeading()) {
            return false;
        }
        selected = index;
        if (isAnimationRow(spec) && mouseX >= previewButtonX() && mouseX < previewButtonX() + 16) {
            previewAnimation(spec);
            return true;
        }
        if (spec.kind() == Kind.ACTION) {
            runAction(spec);
            return true;
        }
        if (!canEdit) {
            return false;
        }
        clickValue(spec, mouseX);
        return true;
    }

    private void clickValue(Spec spec, double mouseX) {
        switch (spec.kind()) {
            case SLIDER -> {
                if (mouseX >= numberBoxLeft()) {
                    beginEditing(spec);
                } else if (mouseX < valueLeft() + 14) {
                    step(spec, -1);
                } else {
                    dragging = spec;
                    setSliderFromMouse(spec, mouseX);
                }
            }
            case RANGE -> {
                if (mouseX >= rangeBoxLeft()) {
                    onHighHalf = mouseX >= rangeBoxLeft() + RANGE_BOX_W + 4;
                    beginEditing(spec, rangeKey(spec, onHighHalf));
                } else {
                    onHighHalf = nearerHalfIsHigh(spec, mouseX);
                    dragging = spec;
                    setRangeFromMouse(spec, mouseX);
                }
            }
            case POINT -> {
                onHighHalf = mouseX >= pointMiddle();
                beginEditing(spec, rangeKey(spec, onHighHalf));
            }
            case BIOME_FILTERS -> {
                if (mouseX >= plusX()) {
                    addBiome();
                } else if (mouseX >= biomeBoxLeft()) {
                    beginBiomeEditing();
                }
            }
            case BIOME_ENTRY -> clickBiomeButton(spec, mouseX);
            case OPTION -> step(spec, mouseX < valueLeft() + VALUE_W / 3.0 ? -1 : 1);
            default -> step(spec, 1);
        }
    }

    private void setSliderFromMouse(Spec spec, double mouseX) {
        int barLeft = valueLeft() + 16;
        int barRight = sliderBarRight();
        double fraction = Math.max(0.0, Math.min(1.0, (mouseX - barLeft) / (double) (barRight - barLeft)));
        int index = (int) Math.round(fraction * (spec.values().size() - 1));
        int value = spec.values().get(index);
        if (value != getInt(spec.key())) {
            set(spec.key(), value);
            edited();
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging == null) {
            return false;
        }
        if (dragging.kind() == Kind.RANGE) {
            setRangeFromMouse(dragging, mouseX);
        } else {
            setSliderFromMouse(dragging, mouseX);
        }
        return true;
    }

    private boolean nearerHalfIsHigh(Spec spec, double mouseX) {
        int barLeft = valueLeft() + 16;
        int barRight = rangeBoxLeft() - 6;
        int steps = Math.max(1, spec.values().size() - 1);
        int low = barLeft + (barRight - barLeft) * indexOfValue(spec, getInt(rangeKey(spec, false))) / steps;
        int high = barLeft + (barRight - barLeft) * indexOfValue(spec, getInt(rangeKey(spec, true))) / steps;
        return Math.abs(mouseX - high) <= Math.abs(mouseX - low);
    }

    private void setRangeFromMouse(Spec spec, double mouseX) {
        int barLeft = valueLeft() + 16;
        int barRight = rangeBoxLeft() - 6;
        double fraction = Math.max(0.0, Math.min(1.0, (mouseX - barLeft) / (double) (barRight - barLeft)));
        int value = spec.values().get((int) Math.round(fraction * (spec.values().size() - 1)));
        String key = rangeKey(spec, onHighHalf);
        if (value != getInt(key)) {
            set(key, value);
            clampRange(spec);
            edited();
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (insideSuggestions(mouseX, mouseY)) {
            int count = Math.min(SUGGESTIONS_SHOWN, biomeSuggestions.size());
            biomeFirst = Math.max(0, Math.min(biomeSuggestions.size() - count,
                    biomeFirst - (int) Math.signum(verticalAmount)));
            return true;
        }
        scroll -= (int) (verticalAmount * ROW_H);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (biomeEditing) {
            biomeKey(keyCode);
            return true;
        }
        if (editingKey != null) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> cancelEditing();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitEditing();
                case GLFW.GLFW_KEY_TAB -> {
                    String here = currentCoordinate(editingKey);
                    if (here != null && !here.equals(editBuffer.toString())) {
                        editBuffer = new StringBuilder(here);
                    } else {
                        commitEditing();
                    }
                }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (editBuffer.length() > 0) {
                        editBuffer.setLength(editBuffer.length() - 1);
                    }
                }
                default -> {
                    return true;
                }
            }
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                close();
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                moveSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                moveSelection(1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                selectedSpec(spec -> step(spec, -1));
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> {
                selectedSpec(spec -> step(spec, 1));
                return true;
            }
            case GLFW.GLFW_KEY_Q -> {
                switchTab(Tab.values()[(tab.ordinal() + Tab.values().length - 1) % Tab.values().length]);
                return true;
            }
            case GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_TAB -> {
                switchTab(Tab.values()[(tab.ordinal() + 1) % Tab.values().length]);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void selectedSpec(java.util.function.Consumer<Spec> action) {
        if (selected >= 0 && selected < rows.size() && !rows.get(selected).isHeading()) {
            action.accept(rows.get(selected));
        }
    }

    private void moveSelection(int direction) {
        if (rows.isEmpty()) {
            return;
        }
        int index = selected;
        for (int i = 0; i < rows.size(); i++) {
            index = Math.floorMod(index + direction, rows.size());
            if (!rows.get(index).isHeading()) {
                break;
            }
        }
        selected = index;
        int rowTop = index * ROW_H;
        int view = listBottom() - listTop();
        if (rowTop < scroll) {
            scroll = rowTop;
        } else if (rowTop + ROW_H > scroll + view) {
            scroll = rowTop + ROW_H - view;
        }
        clampScroll();
    }

    private static boolean isBiomeRow(Spec spec) {
        return spec.key() != null && spec.key().startsWith(BIOME_ROW);
    }

    private static String biomeOf(String key) {
        String rest = key.substring(BIOME_ROW.length());
        int side = rest.indexOf('|');
        return side < 0 ? rest : rest.substring(0, side);
    }

    private String rowLabel(Spec spec) {
        return spec.kind() == Kind.BIOME_FILTERS ? Tr.get("ui.biome.add", "Add Biome") : Tr.label(spec);
    }

    private LinkedHashMap<String, EncounterConfig.BiomeSpeciesFilter> biomeFilters() {
        if (config.biomeSpeciesFilters == null) {
            config.biomeSpeciesFilters = new LinkedHashMap<>();
        }
        return config.biomeSpeciesFilters;
    }

    private void addBiomeRows(Spec add) {
        List<String> biomes = new ArrayList<>();
        biomes.add(OTHER_BIOMES);
        biomes.addAll(biomeFilters().keySet());
        Spec whitelist = ConfigOptions.byKey("speciesWhitelist");
        Spec blacklist = ConfigOptions.byKey("speciesBlacklist");
        for (String biome : biomes) {
            String key = BIOME_ROW + biome;
            rows.add(new Spec(key, biomeName(biome), biomeDescription(biome), add.tab(), add.parent(),
                    Kind.BIOME_ENTRY, List.of(), List.of(), "", List.of()));
            rows.add(new Spec(key + "|white", Tr.get("ui.biome.whitelist", "Whitelist"),
                    whitelist == null ? "" : Tr.description(whitelist), add.tab(), add.parent(), Kind.LIST,
                    List.of(), List.of(), "", List.of()));
            rows.add(new Spec(key + "|black", Tr.get("ui.biome.blacklist", "Blacklist"),
                    blacklist == null ? "" : Tr.description(blacklist), add.tab(), add.parent(), Kind.LIST,
                    List.of(), List.of(), "", List.of()));
        }
    }

    private static String biomeName(String biome) {
        if (OTHER_BIOMES.equals(biome)) {
            return Tr.get("ui.biome.every_other", "Every Other Biome");
        }
        Identifier id = Identifier.tryParse(biome);
        if (id != null) {
            String key = Util.createTranslationKey("biome", id);
            if (I18n.hasTranslation(key)) {
                return I18n.translate(key);
            }
        }
        return biome;
    }

    private static String biomeDescription(String biome) {
        if (OTHER_BIOMES.equals(biome)) {
            return Tr.get("ui.biome.every_other.desc", "The Whitelist and Blacklist for all the biomes that "
                    + "you did not add. Copy and Paste move the two lists from one biome to another.");
        }
        return Tr.get("ui.biome.entry.desc", "%s: encounters in this biome use this Whitelist and Blacklist "
                + "instead of Every Other Biome's. Copy and Paste move both lists between entries, and - removes "
                + "the biome.", biome);
    }

    private List<String> biomeList(String key, List<String> replacement) {
        String biome = biomeOf(key);
        boolean white = key.endsWith("|white");
        if (OTHER_BIOMES.equals(biome)) {
            if (replacement != null) {
                if (white) {
                    config.speciesWhitelist = new ArrayList<>(replacement);
                } else {
                    config.speciesBlacklist = new ArrayList<>(replacement);
                }
            }
            List<String> list = white ? config.speciesWhitelist : config.speciesBlacklist;
            return list == null ? new ArrayList<>() : list;
        }
        EncounterConfig.BiomeSpeciesFilter filter = biomeFilters().get(biome);
        if (filter == null) {
            return new ArrayList<>();
        }
        if (replacement != null) {
            if (white) {
                filter.whitelist = new ArrayList<>(replacement);
            } else {
                filter.blacklist = new ArrayList<>(replacement);
            }
        }
        List<String> list = white ? filter.whitelist : filter.blacklist;
        return list == null ? new ArrayList<>() : list;
    }

    private record RowButton(String id, String label, int x, int width) {
    }

    private List<RowButton> biomeButtons(Spec spec) {
        List<RowButton> buttons = new ArrayList<>();
        int x = valueRight();
        if (!OTHER_BIOMES.equals(biomeOf(spec.key()))) {
            x -= PLUS_W;
            buttons.add(new RowButton("remove", "-", x, PLUS_W));
            x -= 3;
        }
        for (String id : new String[]{"paste", "copy"}) {
            String label = id.equals("paste") ? Tr.get("ui.paste", "Paste") : Tr.get("ui.copy", "Copy");
            int w = textRenderer.getWidth(label) + 10;
            x -= w;
            buttons.add(new RowButton(id, label, x, w));
            x -= 3;
        }
        return buttons;
    }

    private void clickBiomeButton(Spec spec, double mouseX) {
        String biome = biomeOf(spec.key());
        for (RowButton button : biomeButtons(spec)) {
            if (mouseX < button.x() || mouseX >= button.x() + button.width()) {
                continue;
            }
            switch (button.id()) {
                case "copy" -> {
                    EncounterConfig.BiomeSpeciesFilter copied = new EncounterConfig.BiomeSpeciesFilter();
                    copied.whitelist = new ArrayList<>(biomeList(BIOME_ROW + biome + "|white", null));
                    copied.blacklist = new ArrayList<>(biomeList(BIOME_ROW + biome + "|black", null));
                    biomeClipboard = copied;
                    biomeClipboardFrom = biomeName(biome);
                    playClick();
                }
                case "paste" -> {
                    if (biomeClipboard != null) {
                        biomeList(BIOME_ROW + biome + "|white", biomeClipboard.whitelist);
                        biomeList(BIOME_ROW + biome + "|black", biomeClipboard.blacklist);
                        edited();
                    }
                }
                default -> {
                    biomeFilters().remove(biome);
                    edited();
                }
            }
            return;
        }
    }

    private int biomeBoxLeft() {
        int afterLabel = listLeft() + 12 + 6 + textRenderer.getWidth(Tr.get("ui.biome.add", "Add Biome")) + 8;
        return Math.min(valueLeft(), Math.max(afterLabel, valueLeft() - 60));
    }

    private int plusX() {
        return valueRight() - PLUS_W;
    }

    private int biomeRowY() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind() == Kind.BIOME_FILTERS) {
                return listTop() - scroll + i * ROW_H;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void drawBiomeBox(DrawContext context, int y, boolean isSelected, int mouseX, int mouseY) {
        int left = biomeBoxLeft();
        int right = plusX() - 3;
        context.fill(left, y + 3, right, y + ROW_H - 3, biomeEditing ? Theme.MAIN_DEEP : 0x33000000);
        if (biomeEditing) {
            context.fill(left, y + ROW_H - 5, right, y + ROW_H - 3, Theme.ACCENT);
        }
        int room = right - left - 8;
        String typed = biomeBuffer.toString();
        if (typed.isEmpty() && !biomeEditing) {
            context.drawText(textRenderer, textRenderer.trimToWidth(Tr.get("ui.biome.placeholder",
                    "minecraft:plains"), room), left + 4, y + 6, Theme.TEXT_DISABLED, false);
        } else {
            String shown = textRenderer.trimToWidth(typed + (biomeEditing ? "_" : ""), room, true);
            context.drawText(textRenderer, shown, left + 4, y + 6, Theme.TEXT, false);
            String hint = biomeHint();
            int hintX = left + 4 + textRenderer.getWidth(shown) - (biomeEditing ? textRenderer.getWidth("_") : 0);
            if (!hint.isEmpty() && hintX < right - 4) {
                context.drawText(textRenderer, textRenderer.trimToWidth(hint, right - 4 - hintX), hintX, y + 6,
                        Theme.TEXT_DISABLED, false);
            }
        }
        Theme.button(context, textRenderer, "+", plusX(), y + 3, PLUS_W, ROW_H - 6, !isSelected,
                Theme.inside(mouseX, mouseY, plusX(), y + 3, PLUS_W, ROW_H - 6), canEdit);
    }

    private String biomeHint() {
        if (!biomeEditing || biomeFilled || biomeSuggestions.isEmpty()) {
            return "";
        }
        String typed = biomeBuffer.toString();
        String suggestion = biomeSuggestions.get(Math.min(biomeHighlight, biomeSuggestions.size() - 1));
        if (suggestion.startsWith(typed)) {
            return suggestion.substring(typed.length());
        }
        String path = suggestion.substring(suggestion.indexOf(':') + 1);
        return path.startsWith(typed) ? path.substring(typed.length()) : "";
    }

    private List<String> knownBiomes() {
        if (knownBiomes == null) {
            knownBiomes = new ArrayList<>();
            try {
                if (client != null && client.getNetworkHandler() != null) {
                    for (Identifier id : client.getNetworkHandler().getRegistryManager().get(RegistryKeys.BIOME)
                            .getIds()) {
                        knownBiomes.add(id.toString());
                    }
                }
            } catch (Exception e) {
                AWildEncounter.LOGGER.debug("Could not list the biomes: {}", e.toString());
            }
            Collections.sort(knownBiomes);
        }
        return knownBiomes;
    }

    private void beginBiomeEditing() {
        if (!canEdit) {
            return;
        }
        if (editingKey != null) {
            commitEditing();
        }
        biomeEditing = true;
        refreshBiomeSuggestions();
        playClick();
    }

    private void refreshBiomeSuggestions() {
        String typed = biomeBuffer.toString();
        biomeSuggestions = new ArrayList<>();
        for (String id : knownBiomes()) {
            if (biomeFilters().containsKey(id)) {
                continue;
            }
            String path = id.substring(id.indexOf(':') + 1);
            if (typed.isEmpty() || id.startsWith(typed) || path.startsWith(typed) || path.contains("_" + typed)
                    || path.contains("/" + typed)) {
                biomeSuggestions.add(id);
            }
        }
        biomeSuggestionWidth = 0;
        for (String id : biomeSuggestions) {
            biomeSuggestionWidth = Math.max(biomeSuggestionWidth, textRenderer.getWidth(id));
        }
        biomeHighlight = 0;
        biomeFirst = 0;
        biomeFilled = false;
    }

    private void typeBiome(String text) {
        boolean changed = false;
        for (char letter : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Identifier.isCharValid(letter) && biomeBuffer.length() < 120) {
                biomeBuffer.append(letter);
                changed = true;
            }
        }
        if (changed) {
            refreshBiomeSuggestions();
        }
    }

    private void biomeKey(int keyCode) {
        boolean shift = hasShiftDown();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> biomeEditing = false;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> addBiome();
            case GLFW.GLFW_KEY_TAB -> {
                if (biomeFilled) {
                    highlightBiome(biomeHighlight + (shift ? -1 : 1));
                }
                fillBiome();
            }
            case GLFW.GLFW_KEY_UP -> {
                highlightBiome(biomeHighlight - 1);
                if (biomeFilled) {
                    fillBiome();
                }
            }
            case GLFW.GLFW_KEY_DOWN -> {
                highlightBiome(biomeHighlight + 1);
                if (biomeFilled) {
                    fillBiome();
                }
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (biomeBuffer.length() > 0) {
                    if (hasControlDown()) {
                        biomeBuffer.setLength(0);
                    } else {
                        biomeBuffer.setLength(biomeBuffer.length() - 1);
                    }
                    refreshBiomeSuggestions();
                }
            }
            default -> {
                if (Screen.isPaste(keyCode) && client != null) {
                    typeBiome(client.keyboard.getClipboard());
                }
            }
        }
    }

    private void highlightBiome(int index) {
        int size = biomeSuggestions.size();
        if (size == 0) {
            return;
        }
        biomeHighlight = Math.floorMod(index, size);
        int count = Math.min(SUGGESTIONS_SHOWN, size);
        if (biomeHighlight < biomeFirst) {
            biomeFirst = biomeHighlight;
        } else if (biomeHighlight >= biomeFirst + count) {
            biomeFirst = biomeHighlight - count + 1;
        }
    }

    private void fillBiome() {
        if (biomeSuggestions.isEmpty()) {
            return;
        }
        biomeBuffer = new StringBuilder(biomeSuggestions.get(biomeHighlight));
        biomeFilled = true;
    }

    private String resolveBiome(String typed) {
        if (typed.isEmpty()) {
            return null;
        }
        List<String> known = knownBiomes();
        String full = typed.contains(":") ? typed : "minecraft:" + typed;
        if (known.contains(full)) {
            return full;
        }
        String match = null;
        for (String id : known) {
            if (id.endsWith(":" + typed)) {
                if (match != null) {
                    return null;
                }
                match = id;
            }
        }
        if (match != null) {
            return match;
        }
        return known.isEmpty() && Identifier.tryParse(full) != null ? full : null;
    }

    private void addBiome() {
        if (!canEdit) {
            return;
        }
        String biome = resolveBiome(biomeBuffer.toString().trim());
        if (biome == null) {
            if (!biomeEditing) {
                beginBiomeEditing();
            } else if (!biomeSuggestions.isEmpty()) {
                fillBiome();
            }
            return;
        }
        if (!biomeFilters().containsKey(biome)) {
            biomeFilters().put(biome, new EncounterConfig.BiomeSpeciesFilter());
        }
        biomeEditing = false;
        biomeBuffer = new StringBuilder();
        edited();
        for (int i = 0; i < rows.size(); i++) {
            if ((BIOME_ROW + biome).equals(rows.get(i).key())) {
                selected = i;
                int view = listBottom() - listTop();
                scroll = Math.max(scroll, (i + 3) * ROW_H - view);
                clampScroll();
            }
        }
    }

    private record Suggestions(int x, int y, int width, int count) {
    }

    private Suggestions biomeDropdown() {
        int rowY = biomeRowY();
        if (!biomeEditing || biomeSuggestions.isEmpty() || rowY == Integer.MIN_VALUE
                || rowY + ROW_H <= listTop() || rowY >= listBottom()) {
            return null;
        }
        int count = Math.min(SUGGESTIONS_SHOWN, biomeSuggestions.size());
        int boxWidth = Math.max(plusX() - 3 - biomeBoxLeft(), biomeSuggestionWidth + 8);
        int x = Math.max(2, Math.min(biomeBoxLeft(), this.width - boxWidth - 2));
        int boxHeight = count * SUGGESTION_H + 2;
        int y = rowY + ROW_H - 3;
        if (y + boxHeight > this.height - 2) {
            y = rowY + 3 - boxHeight;
        }
        return new Suggestions(x, y, boxWidth, count);
    }

    private boolean insideSuggestions(double mouseX, double mouseY) {
        Suggestions box = biomeDropdown();
        return box != null && Theme.inside(mouseX, mouseY, box.x(), box.y(), box.width(),
                box.count() * SUGGESTION_H + 2);
    }

    private void drawBiomeSuggestions(DrawContext context, int mouseX, int mouseY) {
        Suggestions box = biomeDropdown();
        if (box == null) {
            return;
        }
        int boxHeight = box.count() * SUGGESTION_H + 2;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        context.fill(box.x(), box.y(), box.x() + box.width(), box.y() + boxHeight, 0xF0000000);
        for (int i = 0; i < box.count(); i++) {
            int index = biomeFirst + i;
            if (index >= biomeSuggestions.size()) {
                break;
            }
            int lineY = box.y() + 1 + i * SUGGESTION_H;
            if (Theme.inside(mouseX, mouseY, box.x(), lineY, box.width(), SUGGESTION_H)) {
                context.fill(box.x(), lineY, box.x() + box.width(), lineY + SUGGESTION_H, 0x33FFFFFF);
            }
            context.drawText(textRenderer, biomeSuggestions.get(index), box.x() + 4, lineY + 2,
                    index == biomeHighlight ? 0xFFFFFF00 : 0xFFAAAAAA, false);
        }
        if (biomeFirst > 0) {
            for (int x = box.x(); x < box.x() + box.width(); x += 2) {
                context.fill(x, box.y(), x + 1, box.y() + 1, 0xFFAAAAAA);
            }
        }
        if (biomeFirst + box.count() < biomeSuggestions.size()) {
            for (int x = box.x(); x < box.x() + box.width(); x += 2) {
                context.fill(x, box.y() + boxHeight - 1, x + 1, box.y() + boxHeight, 0xFFAAAAAA);
            }
        }
        context.getMatrices().pop();
    }

    private boolean clickWhileTypingBiome(double mouseX, double mouseY) {
        Suggestions box = biomeDropdown();
        if (box != null && insideSuggestions(mouseX, mouseY)) {
            int index = biomeFirst + (int) ((mouseY - box.y() - 1) / SUGGESTION_H);
            if (index >= 0 && index < biomeSuggestions.size()) {
                biomeHighlight = index;
                fillBiome();
                playClick();
            }
            return true;
        }
        int rowY = biomeRowY();
        if (rowY != Integer.MIN_VALUE && mouseY >= rowY && mouseY < rowY + ROW_H) {
            if (mouseX >= plusX() && mouseX < valueRight()) {
                addBiome();
                return true;
            }
            if (mouseX >= biomeBoxLeft() && mouseX < plusX()) {
                return true;
            }
        }
        biomeEditing = false;
        return false;
    }

    private void switchTab(Tab next) {
        if (next != tab) {
            cancelEditing();
            tab = next;
            scroll = 0;
            selected = -1;
            rebuildRows();
            playClick();
        }
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
