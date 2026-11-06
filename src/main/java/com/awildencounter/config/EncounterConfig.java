package com.awildencounter.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EncounterConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int CURRENT_VERSION = 1;

    public int configVersion = CURRENT_VERSION;

    public static final String DEFAULT_PRESET = "Wild Cobblemon Default";
    public static final String OLD_DEFAULT_PRESET = "WildEncounterMod's Default";
    public static final String CUSTOM = "Custom";
    public String preset = DEFAULT_PRESET;

    public boolean wildZones = true;

    public int zoneEncounterRate = 48;

    public int zoneCooldownSeconds = 10;

    public boolean zoneGrassOnly = true;

    public List<String> encounterBlocks = new ArrayList<>(List.of(
            "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
            "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:sweet_berry_bush"));

    public boolean blockNaturalSpawnsInZones = true;

    public boolean zoneBanner = true;
    public boolean zoneBannerRates = true;

    public boolean everyWildBattle = true;

    public boolean encounterEvents = true;

    public int eventRegion = 0;

    public int eventDwellSeconds = 5;

    public boolean autoStartBattle = true;

    public boolean removeAfterBattle = true;

    public int shinyRate = 0;

    public boolean chatMessages = true;

    public boolean respectRepels = true;

    public String speciesSource = "natural";

    public boolean allowLegendaries = false;

    public String filterSpeciesMode = "dimension";

    public String groupBiomeRestriction = "natural";

    public List<String> speciesWhitelist = new ArrayList<>();
    public List<String> speciesBlacklist = new ArrayList<>();

    public static class BiomeSpeciesFilter {
        public List<String> whitelist = new ArrayList<>();
        public List<String> blacklist = new ArrayList<>();
    }

    public LinkedHashMap<String, BiomeSpeciesFilter> biomeSpeciesFilters = new LinkedHashMap<>();

    public String levelMode = "default";

    public int minimumLvl = 1;

    public int closeLvlRange = 5;

    public int randomLvlMin = 0;
    public int randomLvlMax = 0;

    public int distanceStartLvl = 5;
    public int blocksPerLevel = 100;
    public int distanceCenterX = 0;
    public int distanceCenterZ = 0;

    public String animationPreset = "minecraft";

    public String animOverworld = "mc_grass_random";
    public String animWater = "mc_water";
    public String animCave = "mc_cave";
    public String animNetherCrimson = "mc_nether_crimson";
    public String animNetherWarped = "mc_nether_warped";
    public String animEnd = "mc_end";
    public String animDefault = "random";

    public static final Map<String, List<String>> ANIMATION_PRESETS = Map.of(
            "minecraft", List.of("mc_grass_random", "mc_water", "mc_cave",
                    "mc_nether_crimson", "mc_nether_warped", "mc_end", "random"),
            "retro", List.of("retro_grass", "retro_water", "retro_cave",
                    "default", "retro_default", "retro_trainer", "random"),
            "custom", List.of());

    public void applyAnimationPreset(String preset) {
        List<String> clips = ANIMATION_PRESETS.get(preset);
        if (clips == null || clips.isEmpty()) {
            animationPreset = "custom";
            return;
        }
        animationPreset = preset;
        animOverworld = clips.get(0);
        animWater = clips.get(1);
        animCave = clips.get(2);
        animNetherCrimson = clips.get(3);
        animNetherWarped = clips.get(4);
        animEnd = clips.get(5);
        animDefault = clips.get(6);
    }

    public boolean anyTrigger() {
        return wildZones || everyWildBattle || encounterEvents;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public JsonObject toJsonObject() {
        return GSON.toJsonTree(this).getAsJsonObject();
    }

    public static EncounterConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new EncounterConfig();
        }
        try {
            return fromJsonObject(parseLenient(json));
        } catch (RuntimeException e) {
            return new EncounterConfig();
        }
    }

    public static EncounterConfig fromJsonObject(JsonObject object) {
        EncounterConfig parsed = GSON.fromJson(coerce(object), EncounterConfig.class);
        return parsed != null ? parsed.sanitized() : new EncounterConfig();
    }

    private static JsonObject coerce(JsonObject object) {
        JsonObject out = object.deepCopy();
        if (out.has("biomeEvents") && !out.has("encounterEvents")) {
            out.add("encounterEvents", out.get("biomeEvents"));
        }
        out.remove("biomeEvents");
        for (java.lang.reflect.Field field : EncounterConfig.class.getFields()) {
            if (field.getType() != int.class) {
                continue;
            }
            JsonElement value = out.get(field.getName());
            if (value == null || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            String text = value.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
            if (text.equals("off") || text.equals("default") || text.equals("any") || text.equals("none")) {
                out.addProperty(field.getName(), 0);
                continue;
            }
            try {
                out.addProperty(field.getName(), Integer.parseInt(text));
            } catch (NumberFormatException e) {
                out.remove(field.getName());
            }
        }
        return out;
    }

    private EncounterConfig sanitized() {
        EncounterConfig defaults = new EncounterConfig();
        if (OLD_DEFAULT_PRESET.equals(preset)) {
            preset = DEFAULT_PRESET;
        }
        if (encounterBlocks == null) {
            encounterBlocks = defaults.encounterBlocks;
        }
        if (speciesWhitelist == null) {
            speciesWhitelist = new ArrayList<>();
        }
        if (speciesBlacklist == null) {
            speciesBlacklist = new ArrayList<>();
        }
        if (biomeSpeciesFilters == null) {
            biomeSpeciesFilters = new LinkedHashMap<>();
        }
        if ("action".equals(animationPreset)) {
            applyAnimationPreset("minecraft");
        }
        List<String> minecraft = ANIMATION_PRESETS.get("minecraft");
        String[] clips = {animOverworld, animWater, animCave, animNetherCrimson, animNetherWarped, animEnd,
                animDefault};
        for (int i = 0; i < clips.length; i++) {
            if (clips[i] != null && clips[i].startsWith("action_")) {
                clips[i] = minecraft.get(i);
            }
        }
        animOverworld = clips[0];
        animWater = clips[1];
        animCave = clips[2];
        animNetherCrimson = clips[3];
        animNetherWarped = clips[4];
        animEnd = clips[5];
        animDefault = clips[6];
        for (java.lang.reflect.Field field : EncounterConfig.class.getFields()) {
            if (field.getType() != String.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                if (field.get(this) == null) {
                    field.set(this, field.get(defaults));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return this;
    }

    public static JsonObject parseLenient(String text) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true);
        JsonElement element = JsonParser.parseReader(reader);
        if (!element.isJsonObject()) {
            throw new JsonParseException("Expected a JSON object");
        }
        return element.getAsJsonObject();
    }

    public EncounterConfig copy() {
        return fromJson(toJson());
    }
}
