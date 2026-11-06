package com.awildencounter.config;

import com.awildencounter.AWildEncounter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigFiles {
    private static final String CONFIG_FILE = "config.json5";
    private static final String PRESETS_FILE = "presets.json5";
    private static final String OLD_CONFIG_FILE = "config.json";
    private static final String OLD_PRESETS_FILE = "presets.json";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static final String DEFAULT_DESCRIPTION = "The rules A Wild Cobblemon Appeared! comes with: Wild Zones in tall "
            + "grass, the animation before every wild battle, an Encounter Event in each new biome, and natural "
            + "spawns with Cobblemon's own levels.";
    public static final String EXPORT_DESCRIPTION = "Saved from the in-game settings.";

    private ConfigFiles() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve(AWildEncounter.MOD_ID);
    }

    private record Found(String text, boolean moved) {
    }

    private static Found read(String name, String oldName) throws Exception {
        Path file = folder().resolve(name);
        if (Files.exists(file)) {
            return new Found(Files.readString(file, StandardCharsets.UTF_8), false);
        }
        Path old = folder().resolve(oldName);
        if (Files.exists(old)) {
            return new Found(Files.readString(old, StandardCharsets.UTF_8), true);
        }
        return null;
    }

    private static void removeOld(String oldName) {
        try {
            Files.deleteIfExists(folder().resolve(oldName));
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not remove the old {}: {}", oldName, e.toString());
        }
    }

    public static EncounterConfig loadDefaults() {
        try {
            Found found = read(CONFIG_FILE, OLD_CONFIG_FILE);
            if (found == null) {
                EncounterConfig defaults = new EncounterConfig();
                saveDefaults(defaults);
                return defaults;
            }
            EncounterConfig rules = EncounterConfig.fromJsonObject(EncounterConfig.parseLenient(found.text()));
            if (found.moved() && saveDefaults(rules)) {
                removeOld(OLD_CONFIG_FILE);
            }
            return rules;
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read {}, using the default rules: {}",
                    folder().resolve(CONFIG_FILE), e.toString());
            return new EncounterConfig();
        }
    }

    public static boolean saveDefaults(EncounterConfig config) {
        Path file = folder().resolve(CONFIG_FILE);
        StringBuilder out = new StringBuilder();
        out.append("// A Wild Cobblemon Appeared! - the rules for new worlds.\n");
        out.append("// Each new world starts with these rules. Each world then keeps its own rules in its save.\n");
        out.append("// When you save ALL in /wildencounter config, the mod writes this file again.\n");
        out.append("// You can also edit this file. The changes apply to new worlds.\n");
        out.append("//\n");
        out.append("// Values:\n");
        out.append("//   true or false: the setting is on or off.\n");
        out.append("//   Numbers: write the number only, for example 48.\n");
        out.append("//   Text: write it in quotes, for example \"natural\".\n");
        out.append("//   Lists: write them in [ ], for example [\"cobblemon:pikachu\", \"#cobblemon:starter\"].\n");
        out.append("{\n");
        for (String line : ruleLines(config.toJsonObject())) {
            out.append(line.isEmpty() ? "" : "  " + line).append('\n');
        }
        out.append("}\n");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not write {}: {}", file, e.toString());
            return false;
        }
    }

    private static final Map<String, String> SECTIONS = new LinkedHashMap<>();
    private static final Map<String, String> COMMENTS = new LinkedHashMap<>();

    private static void section(String key, String title) {
        SECTIONS.put(key, title);
    }

    private static void comment(String key, String text) {
        COMMENTS.put(key, text);
    }

    static {
        comment("configVersion", "The version of these rules. Do not change this value.");
        comment("preset", "The preset that these rules come from. \"Custom\" means that you changed the rules.");

        section("wildZones", "WILD ZONES");
        comment("wildZones", "true: when you walk in a Wild Zone, an encounter can start.\n"
                + "Hold a Bone to make zones and to see them.");
        comment("zoneEncounterRate", "The chance of an encounter for each block that you walk in a zone.\n"
                + "48 means 1 in 48.");
        comment("zoneCooldownSeconds", "The time between two encounters in a zone, in seconds.");
        comment("zoneGrassOnly", "true: encounters start only when you walk on the Encounter Blocks.");
        comment("encounterBlocks", "The blocks that count as grass for zoneGrassOnly.\n"
                + "You can also use block tags, for example \"#minecraft:flowers\".");
        comment("blockNaturalSpawnsInZones", "true: Cobblemon do not spawn naturally in Wild Zones.");
        comment("zoneBanner", "true: when you go into a Wild Zone, its name and its Cobblemon show at the top of "
                + "the screen.");
        comment("zoneBannerRates", "true: the banner also shows the chance of each Cobblemon.");

        section("everyWildBattle", "OTHER TRIGGERS");
        comment("everyWildBattle", "true: the animation plays before each battle with a wild Cobblemon.");
        comment("encounterEvents", "true: when you stay in a new biome, an encounter starts.\n"
                + "This occurs one time in each biome for each player.");
        comment("eventRegion", "0: Encounter Events use biomes.\n"
                + "Other numbers: Encounter Events use square regions. The number is the size in blocks.");
        comment("eventDwellSeconds", "The time that you must stay in a new biome before its event starts, in "
                + "seconds.");

        section("autoStartBattle", "ENCOUNTERS");
        comment("autoStartBattle", "true: the battle starts when the animation covers the screen.\n"
                + "false: the Cobblemon appears, but the battle does not start.");
        comment("removeAfterBattle", "true: a Cobblemon that you did not catch or defeat goes away after the "
                + "battle.");
        comment("shinyRate", "The shiny chance, 1 in this number. Example: 4096.\n"
                + "0: use the shiny chance of Cobblemon. 1: all encounters are shiny.");
        comment("chatMessages", "true: a chat message tells you about each encounter.");
        comment("respectRepels", "true: encounters do not start near a Repel block.\n"
                + "They also do not start when you have a repel effect or the \"repel\" tag.");

        section("speciesSource", "SPECIES");
        comment("speciesSource", "\"natural\": the Cobblemon that can spawn where you stand.\n"
                + "\"random\": any Cobblemon.");
        comment("allowLegendaries", "true: legendary, mythical, paradox and ultra beast Cobblemon can appear "
                + "with \"random\".");
        comment("filterSpeciesMode", "\"dimension\": one whitelist and one blacklist for all biomes.\n"
                + "\"biome\": the biomes in biomeSpeciesFilters use their own lists.");
        comment("groupBiomeRestriction", "How groups on a whitelist work, for example \"#cobblemon:starter\".\n"
                + "\"natural\": only the group members that spawn naturally in the biome can appear.\n"
                + "\"any\": all the group members can appear in all biomes.");
        comment("speciesWhitelist", "Only these Cobblemon can appear. An empty list lets all Cobblemon appear.\n"
                + "Example: [\"cobblemon:pikachu\", \"#cobblemon:starter\"].");
        comment("speciesBlacklist", "These Cobblemon never appear. Example: [\"#cobblemon:legendary\"].");
        comment("biomeSpeciesFilters", "A whitelist and a blacklist for each biome. Used when filterSpeciesMode "
                + "is \"biome\".\n"
                + "Example: {\"minecraft:forest\": {\"whitelist\": [\"cobblemon:caterpie\"], \"blacklist\": []}}.");

        section("levelMode", "LEVELS");
        comment("levelMode", "How the mod sets the level of an encounter.\n"
                + "\"default\": the spawn level of Cobblemon.\n"
                + "\"lower\": from 1 to the level of your strongest party member.\n"
                + "\"close\": a little below the level of your strongest party member.\n"
                + "\"random\": a random level between randomLvlMin and randomLvlMax.\n"
                + "\"match\": the level of your strongest party member.\n"
                + "\"distance\": the level increases when you go far from the center.");
        comment("minimumLvl", "The lowest level for \"lower\" and \"close\".");
        comment("closeLvlRange", "For \"close\": the maximum number of levels below your strongest party "
                + "member.");
        comment("randomLvlMin", "For \"random\": the lowest level. 0 means 1.");
        comment("randomLvlMax", "For \"random\": the highest level. 0 means 100.");
        comment("distanceStartLvl", "For \"distance\": the level at the center.");
        comment("blocksPerLevel", "For \"distance\": the number of blocks for 1 more level.");
        comment("distanceCenterX", "For \"distance\": the X coordinate of the center.");
        comment("distanceCenterZ", "For \"distance\": the Z coordinate of the center.");

        section("animationPreset", "ANIMATIONS");
        comment("animationPreset", "\"minecraft\", \"retro\" or \"custom\".\n"
                + "\"custom\": you set each animation below yourself.\n"
                + "The animations that you can use below:\n"
                + String.join(", ", ConfigOptions.CLIP_IDS.subList(0, 10)) + ",\n"
                + String.join(", ", ConfigOptions.CLIP_IDS.subList(10, ConfigOptions.CLIP_IDS.size())) + ".\n"
                + "For a custom animation, write \"custom:\" and its folder name, for example \"custom:my animation\".\n"
                + "The folders are in config/awildencounter/custom encounter animations.");
        comment("animOverworld", "The animation in the Overworld.");
        comment("animWater", "The animation in water and in water biomes.");
        comment("animCave", "The animation underground.");
        comment("animNetherCrimson", "The animation in the Nether.");
        comment("animNetherWarped", "The animation in warped forests and soul sand valleys.");
        comment("animEnd", "The animation in the End.");
        comment("animDefault", "The animation in all other places, for example the dimensions of other mods.");
    }

    private static List<String> ruleLines(JsonObject rules) {
        List<String> lines = new ArrayList<>();
        int last = -1;
        for (Map.Entry<String, JsonElement> entry : rules.entrySet()) {
            String key = entry.getKey();
            String title = SECTIONS.get(key);
            if (title != null) {
                lines.add("");
                lines.add("// ===== " + title + " =====");
            }
            String text = COMMENTS.get(key);
            if (text != null) {
                for (String line : text.split("\n")) {
                    lines.add("// " + line);
                }
            }
            lines.add(GSON.toJson(key) + ": " + GSON.toJson(entry.getValue()) + ",");
            last = lines.size() - 1;
        }
        if (last >= 0) {
            String line = lines.get(last);
            lines.set(last, line.substring(0, line.length() - 1));
        }
        return lines;
    }

    public static JsonArray loadPresets() {
        JsonArray presets = new JsonArray();
        boolean moved = false;
        try {
            Found found = read(PRESETS_FILE, OLD_PRESETS_FILE);
            if (found != null) {
                moved = found.moved();
                JsonObject root = EncounterConfig.parseLenient(found.text());
                if (root.has("presets")) {
                    for (JsonElement element : root.getAsJsonArray("presets")) {
                        if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
                            presets.add(element);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read {}: {}", folder().resolve(PRESETS_FILE), e.toString());
        }
        boolean changed = moved;
        for (JsonElement element : presets) {
            JsonObject entry = element.getAsJsonObject();
            if (EncounterConfig.OLD_DEFAULT_PRESET.equalsIgnoreCase(entry.get("name").getAsString())) {
                entry.addProperty("name", EncounterConfig.DEFAULT_PRESET);
                if (entry.has("description")) {
                    entry.addProperty("description", DEFAULT_DESCRIPTION);
                }
                if (entry.has("rules") && entry.get("rules").isJsonObject()) {
                    entry.getAsJsonObject("rules").addProperty("preset", EncounterConfig.DEFAULT_PRESET);
                }
                changed = true;
            }
        }
        boolean hasDefault = false;
        for (JsonElement element : presets) {
            hasDefault |= EncounterConfig.DEFAULT_PRESET.equalsIgnoreCase(
                    element.getAsJsonObject().get("name").getAsString());
        }
        if (!hasDefault) {
            JsonArray withDefault = new JsonArray();
            withDefault.add(defaultPreset());
            withDefault.addAll(presets);
            presets = withDefault;
            changed = true;
        }
        if (changed && writePresets(presets) && moved) {
            removeOld(OLD_PRESETS_FILE);
        }
        return presets;
    }

    private static JsonObject defaultPreset() {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", EncounterConfig.DEFAULT_PRESET);
        entry.addProperty("description", DEFAULT_DESCRIPTION);
        entry.add("rules", new EncounterConfig().toJsonObject());
        return entry;
    }

    public enum ExportResult {
        ADDED,
        REPLACED,
        FAILED
    }

    public static ExportResult exportPreset(String name, EncounterConfig config) {
        String wanted = name == null ? "" : name.trim();
        if (wanted.isEmpty() || wanted.length() > 40 || config == null) {
            return ExportResult.FAILED;
        }
        EncounterConfig saved = config.copy();
        saved.preset = wanted;
        JsonObject entry = new JsonObject();
        entry.addProperty("name", wanted);
        entry.addProperty("description", EXPORT_DESCRIPTION);
        entry.add("rules", saved.toJsonObject());
        boolean replaced = false;
        JsonArray merged = new JsonArray();
        for (JsonElement element : loadPresets()) {
            if (element.getAsJsonObject().get("name").getAsString().equalsIgnoreCase(wanted)) {
                merged.add(entry);
                replaced = true;
            } else {
                merged.add(element);
            }
        }
        if (!replaced) {
            merged.add(entry);
        }
        if (!writePresets(merged)) {
            return ExportResult.FAILED;
        }
        AWildEncounter.LOGGER.info("Exported the preset {}", wanted);
        return replaced ? ExportResult.REPLACED : ExportResult.ADDED;
    }

    private static boolean writePresets(JsonArray presets) {
        Path file = folder().resolve(PRESETS_FILE);
        StringBuilder out = new StringBuilder();
        out.append("// A Wild Cobblemon Appeared! - presets.\n");
        out.append("// The presets show at the top of /wildencounter config. Click the preset name to change it.\n");
        out.append("// To add a preset, use the Export button in the menu, or copy a preset below and change it.\n");
        out.append("// The rules of a preset use the same options as config.json5.\n");
        out.append("// If you remove \"").append(EncounterConfig.DEFAULT_PRESET)
                .append("\", the mod adds it again.\n");
        out.append("{\n  \"presets\": [\n");
        for (int i = 0; i < presets.size(); i++) {
            JsonObject preset = presets.get(i).getAsJsonObject();
            out.append("    {\n");
            out.append("      \"name\": ").append(GSON.toJson(preset.get("name"))).append(",\n");
            if (preset.has("description")) {
                out.append("      \"description\": ").append(GSON.toJson(preset.get("description"))).append(",\n");
            }
            JsonObject rules = preset.has("rules") && preset.get("rules").isJsonObject()
                    ? EncounterConfig.fromJsonObject(preset.getAsJsonObject("rules")).toJsonObject()
                    : new EncounterConfig().toJsonObject();
            out.append("      \"rules\": {\n");
            for (String line : ruleLines(rules)) {
                out.append(line.isEmpty() ? "" : "        " + line).append('\n');
            }
            out.append("      }\n");
            out.append(i < presets.size() - 1 ? "    },\n" : "    }\n");
        }
        out.append("  ]\n}\n");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not write {}: {}", file, e.toString());
            return false;
        }
    }
}
