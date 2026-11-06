package com.awildencounter.client.gui;

import com.awildencounter.AWildEncounter;
import com.awildencounter.config.DimensionNames;
import com.awildencounter.config.EncounterConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public final class DimensionScope {
    public static final String ALL = "all";

    public final String id;
    public String name;
    public final String defaultName;
    public final String savedName;
    public final boolean ownRules;
    public EncounterConfig config;
    public boolean dirty = false;

    private DimensionScope(String id, String name, String defaultName, boolean ownRules, EncounterConfig config) {
        this.id = id;
        this.name = name;
        this.defaultName = defaultName;
        this.savedName = name;
        this.ownRules = ownRules;
        this.config = config;
    }

    public boolean renamed() {
        return !name.equals(savedName);
    }

    public boolean isAll() {
        return ALL.equals(id);
    }

    public String shownName() {
        if (!name.equals(defaultName)) {
            return name;
        }
        return switch (id) {
            case ALL -> Tr.get("ui.dimensions.all", "ALL");
            case DimensionNames.OVERWORLD -> Tr.get("ui.dimension.overworld", "Overworld");
            case DimensionNames.NETHER -> Tr.get("ui.dimension.nether", "Nether");
            case DimensionNames.END -> Tr.get("ui.dimension.end", "End");
            default -> name;
        };
    }

    public static Parsed parse(String json) {
        List<DimensionScope> scopes = new ArrayList<>();
        String selected = ALL;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("selected")) {
                selected = root.get("selected").getAsString();
            }
            for (JsonElement element : root.getAsJsonArray("scopes")) {
                JsonObject entry = element.getAsJsonObject();
                String name = entry.get("name").getAsString();
                scopes.add(new DimensionScope(
                        entry.get("id").getAsString(),
                        name,
                        entry.has("defaultName") ? entry.get("defaultName").getAsString() : name,
                        entry.get("own").getAsBoolean(),
                        EncounterConfig.fromJsonObject(entry.getAsJsonObject("config"))));
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read the dimension rules: {}", e.toString());
        }
        if (scopes.isEmpty()) {
            scopes.add(new DimensionScope(ALL, "ALL", "ALL", true, new EncounterConfig()));
        }
        int index = 0;
        for (int i = 0; i < scopes.size(); i++) {
            if (scopes.get(i).id.equals(selected)) {
                index = i;
            }
        }
        return new Parsed(scopes, index);
    }

    public record Parsed(List<DimensionScope> scopes, int selected) {
    }
}
