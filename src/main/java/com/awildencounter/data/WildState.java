package com.awildencounter.data;

import com.awildencounter.AWildEncounter;
import com.awildencounter.config.ConfigFiles;
import com.awildencounter.config.EncounterConfig;
import com.awildencounter.util.Worlds;
import com.awildencounter.zone.WildZone;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WildState extends PersistentState {
    private static final String DATA_NAME = AWildEncounter.MOD_ID;

    public static final String ALL = "all";

    private EncounterConfig all = new EncounterConfig();
    private final Map<String, EncounterConfig> dimensions = new LinkedHashMap<>();
    private boolean affectsAll = true;
    private final Map<String, String> dimensionNames = new LinkedHashMap<>();

    private final List<WildZone> zones = new ArrayList<>();

    private final Map<UUID, Set<String>> firedEvents = new HashMap<>();

    private static final Type<WildState> TYPE = new Type<>(WildState::fresh, WildState::readNbt, DataFixTypes.LEVEL);

    public static WildState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, DATA_NAME);
    }

    private static WildState fresh() {
        WildState state = new WildState();
        state.all = ConfigFiles.loadDefaults();
        state.markDirty();
        return state;
    }

    public EncounterConfig all() {
        return all;
    }

    public boolean affectsAll() {
        return affectsAll;
    }

    public void setAffectsAll(boolean affectsAll) {
        this.affectsAll = affectsAll;
        markDirty();
    }

    public boolean hasOwnRules(String dimension) {
        return dimensions.containsKey(dimension);
    }

    public EncounterConfig exact(String scope) {
        if (ALL.equals(scope)) {
            return all;
        }
        EncounterConfig own = dimensions.get(scope);
        return own != null ? own : all;
    }

    public EncounterConfig rulesFor(String dimension) {
        if (affectsAll || dimension == null) {
            return all;
        }
        EncounterConfig own = dimensions.get(dimension);
        return own != null ? own : all;
    }

    public EncounterConfig rulesFor(World world) {
        return rulesFor(Worlds.dimensionId(world));
    }

    public void saveRules(String scope, EncounterConfig config) {
        if (ALL.equals(scope)) {
            all = config;
        } else {
            dimensions.put(scope, config);
        }
        markDirty();
    }

    public String dimensionName(String dimension) {
        String name = dimensionNames.get(dimension);
        return name != null ? name : com.awildencounter.config.DimensionNames.defaultName(dimension);
    }

    public void setDimensionName(String dimension, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
        }
        if (trimmed.isEmpty() || trimmed.equals(com.awildencounter.config.DimensionNames.defaultName(dimension))) {
            dimensionNames.remove(dimension);
        } else {
            dimensionNames.put(dimension, trimmed);
        }
        markDirty();
    }

    public List<WildZone> zones() {
        return zones;
    }

    public WildZone zone(String id) {
        for (WildZone zone : zones) {
            if (zone.id.equals(id)) {
                return zone;
            }
        }
        return null;
    }

    public WildZone zoneAt(String dimension, BlockPos pos) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            if (zones.get(i).contains(dimension, pos)) {
                return zones.get(i);
            }
        }
        return null;
    }

    public void addZone(WildZone zone) {
        zones.add(zone);
        markDirty();
    }

    public boolean replaceZone(WildZone zone) {
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).id.equals(zone.id)) {
                zones.set(i, zone);
                markDirty();
                return true;
            }
        }
        return false;
    }

    public boolean removeZone(String id) {
        boolean removed = zones.removeIf(zone -> zone.id.equals(id));
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public String nextZoneName() {
        Set<String> taken = new LinkedHashSet<>();
        zones.forEach(zone -> taken.add(zone.name));
        for (int i = 1; ; i++) {
            String name = "Wild Zone " + i;
            if (!taken.contains(name)) {
                return name;
            }
        }
    }

    public boolean hasFired(UUID player, String area) {
        Set<String> fired = firedEvents.get(player);
        return fired != null && fired.contains(area);
    }

    public void markFired(UUID player, String area) {
        firedEvents.computeIfAbsent(player, id -> new LinkedHashSet<>()).add(area);
        markDirty();
    }

    public int resetEvents(UUID player) {
        Set<String> removed = player == null ? null : firedEvents.remove(player);
        int count = removed == null ? 0 : removed.size();
        if (player == null) {
            for (Set<String> fired : firedEvents.values()) {
                count += fired.size();
            }
            firedEvents.clear();
        }
        markDirty();
        return count;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        JsonObject rules = new JsonObject();
        rules.addProperty("affectsAll", affectsAll);
        rules.add("all", all.toJsonObject());
        JsonObject own = new JsonObject();
        dimensions.forEach((dimension, config) -> own.add(dimension, config.toJsonObject()));
        rules.add("dimensions", own);
        JsonObject names = new JsonObject();
        dimensionNames.forEach(names::addProperty);
        rules.add("names", names);
        nbt.putString("rules", rules.toString());

        JsonArray zoneArray = new JsonArray();
        zones.forEach(zone -> zoneArray.add(zone.toJsonObject()));
        nbt.putString("zones", zoneArray.toString());

        JsonObject events = new JsonObject();
        firedEvents.forEach((player, areas) -> {
            JsonArray list = new JsonArray();
            areas.forEach(list::add);
            events.add(player.toString(), list);
        });
        nbt.putString("events", events.toString());
        return nbt;
    }

    private static WildState readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        WildState state = new WildState();
        try {
            if (nbt.contains("rules")) {
                JsonObject rules = JsonParser.parseString(nbt.getString("rules")).getAsJsonObject();
                state.affectsAll = !rules.has("affectsAll") || rules.get("affectsAll").getAsBoolean();
                if (rules.has("all")) {
                    state.all = EncounterConfig.fromJsonObject(rules.getAsJsonObject("all"));
                }
                if (rules.has("dimensions")) {
                    for (Map.Entry<String, JsonElement> entry : rules.getAsJsonObject("dimensions").entrySet()) {
                        state.dimensions.put(entry.getKey(),
                                EncounterConfig.fromJsonObject(entry.getValue().getAsJsonObject()));
                    }
                }
                if (rules.has("names")) {
                    for (Map.Entry<String, JsonElement> entry : rules.getAsJsonObject("names").entrySet()) {
                        state.dimensionNames.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.error("Could not read the saved encounter rules: {}", e.toString());
        }
        try {
            if (nbt.contains("zones")) {
                for (JsonElement element : JsonParser.parseString(nbt.getString("zones")).getAsJsonArray()) {
                    state.zones.add(WildZone.fromJson(element.getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.error("Could not read the saved Wild Zones: {}", e.toString());
        }
        try {
            if (nbt.contains("events")) {
                JsonObject events = JsonParser.parseString(nbt.getString("events")).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : events.entrySet()) {
                    Set<String> areas = new LinkedHashSet<>();
                    entry.getValue().getAsJsonArray().forEach(area -> areas.add(area.getAsString()));
                    state.firedEvents.put(UUID.fromString(entry.getKey()), areas);
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.error("Could not read the saved Encounter Events: {}", e.toString());
        }
        return state;
    }
}
