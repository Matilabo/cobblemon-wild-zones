package com.awildencounter.zone;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WildZone {
    private static final Gson GSON = new Gson();

    public static final int MAX_SIZE = 1024;
    public static final int MAX_NAME = 48;
    public static final int DEFAULT_COLOR = 0xFFFFFF;

    public String id = UUID.randomUUID().toString();
    public String name = "Wild Zone";
    public int color = DEFAULT_COLOR;
    public String dimension = "minecraft:overworld";
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public List<String> whitelist = new ArrayList<>();
    public List<String> blacklist = new ArrayList<>();
    public static final String RESTRICTION_DEFAULT = "default";
    public static final List<String> RESTRICTIONS = List.of(RESTRICTION_DEFAULT, "natural", "any");
    public String biomeRestriction = RESTRICTION_DEFAULT;

    public String oddsMode = ZoneOdds.COBBLEMON;
    public LinkedHashMap<String, String> rarities = new LinkedHashMap<>();
    public LinkedHashMap<String, Double> weights = new LinkedHashMap<>();
    public LinkedHashMap<String, Double> tierOdds = ZoneOdds.defaultTierOdds();

    public static final int MAX_COOLDOWN = 3600;
    public static final int MAX_CAP = 32;
    public boolean spawning = false;
    public int spawnCooldown = 30;
    public int spawnCap = 3;

    public String restriction(String configured) {
        return RESTRICTION_DEFAULT.equals(biomeRestriction) ? configured : biomeRestriction;
    }

    public boolean hasFilter() {
        return (whitelist != null && !whitelist.isEmpty()) || (blacklist != null && !blacklist.isEmpty());
    }

    public static WildZone between(String dimension, BlockPos a, BlockPos b) {
        WildZone zone = new WildZone();
        zone.dimension = dimension;
        zone.minX = Math.min(a.getX(), b.getX());
        zone.minY = Math.min(a.getY(), b.getY());
        zone.minZ = Math.min(a.getZ(), b.getZ());
        zone.maxX = Math.max(a.getX(), b.getX());
        zone.maxY = Math.max(a.getY(), b.getY());
        zone.maxZ = Math.max(a.getZ(), b.getZ());
        zone.clampSize();
        return zone;
    }

    public boolean contains(String dimension, BlockPos pos) {
        return this.dimension.equals(dimension) && pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int length() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) width() * height() * length();
    }

    public void setWidth(int width) {
        maxX = minX + clamp(width) - 1;
    }

    public void setHeight(int height) {
        maxY = minY + clamp(height) - 1;
    }

    public void setLength(int length) {
        maxZ = minZ + clamp(length) - 1;
    }

    public void clampSize() {
        setWidth(width());
        setHeight(height());
        setLength(length());
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(MAX_SIZE, size));
    }

    public String corners() {
        return minX + " " + minY + " " + minZ + " → " + maxX + " " + maxY + " " + maxZ;
    }

    public String size() {
        return width() + "×" + height() + "×" + length();
    }

    public WildZone copy() {
        return fromJson(toJsonObject());
    }

    public JsonObject toJsonObject() {
        return GSON.toJsonTree(this).getAsJsonObject();
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static WildZone fromJson(JsonObject object) {
        WildZone zone = GSON.fromJson(object, WildZone.class);
        if (zone.id == null || zone.id.isBlank()) {
            zone.id = UUID.randomUUID().toString();
        }
        if (zone.name == null) {
            zone.name = "Wild Zone";
        }
        if (zone.dimension == null) {
            zone.dimension = "minecraft:overworld";
        }
        zone.color &= 0xFFFFFF;
        if (zone.biomeRestriction == null || !RESTRICTIONS.contains(zone.biomeRestriction)) {
            zone.biomeRestriction = RESTRICTION_DEFAULT;
        }
        if (zone.oddsMode == null || !ZoneOdds.MODES.contains(zone.oddsMode)) {
            zone.oddsMode = ZoneOdds.COBBLEMON;
        }
        zone.rarities = cleanTiers(zone.rarities);
        zone.weights = cleanNumbers(zone.weights, MAX_ENTRIES);
        LinkedHashMap<String, Double> tiers = ZoneOdds.defaultTierOdds();
        LinkedHashMap<String, Double> saved = cleanNumbers(zone.tierOdds, ZoneOdds.TIERS.size());
        saved.forEach((tier, odds) -> {
            if (tiers.containsKey(tier)) {
                tiers.put(tier, odds);
            }
        });
        zone.tierOdds = tiers;
        zone.spawnCooldown = Math.max(1, Math.min(MAX_COOLDOWN, zone.spawnCooldown));
        zone.spawnCap = Math.max(1, Math.min(MAX_CAP, zone.spawnCap));
        zone.whitelist = cleanList(zone.whitelist);
        zone.blacklist = cleanList(zone.blacklist);
        return zone;
    }

    public static final int MAX_ENTRIES = 300;
    private static final int MAX_ENTRY_LENGTH = 100;

    private static List<String> cleanList(List<String> list) {
        List<String> clean = new ArrayList<>();
        if (list == null) {
            return clean;
        }
        for (String entry : list) {
            String trimmed = entry == null ? "" : entry.strip();
            if (!trimmed.isEmpty() && trimmed.length() <= MAX_ENTRY_LENGTH && !clean.contains(trimmed)
                    && clean.size() < MAX_ENTRIES) {
                clean.add(trimmed);
            }
        }
        return clean;
    }

    private static LinkedHashMap<String, String> cleanTiers(Map<String, String> map) {
        LinkedHashMap<String, String> clean = new LinkedHashMap<>();
        if (map == null) {
            return clean;
        }
        map.forEach((entry, tier) -> {
            if (entry != null && entry.length() <= MAX_ENTRY_LENGTH && ZoneOdds.TIERS.contains(tier)
                    && !ZoneOdds.COMMON.equals(tier) && clean.size() < MAX_ENTRIES * 4) {
                clean.put(entry, tier);
            }
        });
        return clean;
    }

    private static LinkedHashMap<String, Double> cleanNumbers(Map<String, Double> map, int max) {
        LinkedHashMap<String, Double> clean = new LinkedHashMap<>();
        if (map == null) {
            return clean;
        }
        map.forEach((entry, value) -> {
            if (entry != null && value != null && !value.isNaN() && entry.length() <= MAX_ENTRY_LENGTH
                    && clean.size() < max * 4) {
                clean.put(entry, Math.max(0, Math.min(ZoneOdds.MAX_VALUE, value)));
            }
        });
        return clean;
    }

    public static WildZone fromJson(String json) {
        return fromJson(GSON.fromJson(json, JsonObject.class));
    }
}
