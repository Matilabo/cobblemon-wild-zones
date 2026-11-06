package com.awildencounter.client.zone;

import com.awildencounter.AWildEncounter;
import com.awildencounter.net.ZonePreviewRequestPayload;
import com.awildencounter.zone.WildZone;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientZones {
    private static List<WildZone> zones = List.of();
    private static WildZone preview = null;

    public record Spawns(List<String> entries, int total, long receivedAt) {
    }

    private static final long SPAWNS_FRESH_MS = 15_000;
    private static final long SPAWNS_RETRY_MS = 4_000;
    private static final Map<String, Spawns> spawns = new HashMap<>();
    private static final Map<String, Long> asked = new HashMap<>();

    public record Odds(String zoneId, LinkedHashMap<String, Double> base, boolean natural, long receivedAt) {
    }

    private static Odds odds = null;

    private ClientZones() {
    }

    public static void setOdds(String zoneId, String json) {
        LinkedHashMap<String, Double> base = new LinkedHashMap<>();
        boolean natural = false;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            natural = root.has("natural") && root.get("natural").getAsBoolean();
            for (JsonElement element : root.getAsJsonArray("entries")) {
                JsonArray pair = element.getAsJsonArray();
                base.put(pair.get(0).getAsString(), pair.get(1).getAsDouble());
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.debug("Could not read a zone's odds: {}", e.toString());
        }
        odds = new Odds(zoneId, base, natural, Util.getMeasuringTimeMs());
    }

    public static Odds odds() {
        return odds;
    }

    public static void set(String json) {
        spawns.clear();
        asked.clear();
        List<WildZone> parsed = new ArrayList<>();
        try {
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                parsed.add(WildZone.fromJson(element.getAsJsonObject()));
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read the Wild Zones: {}", e.toString());
        }
        zones = parsed;
    }

    public static void clear() {
        zones = List.of();
        preview = null;
        spawns.clear();
        asked.clear();
    }

    public static void setSpawns(String zoneId, String entriesJson, int total) {
        List<String> entries = new ArrayList<>();
        try {
            for (JsonElement element : JsonParser.parseString(entriesJson).getAsJsonArray()) {
                entries.add(element.getAsString());
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.debug("Could not read a zone's spawns: {}", e.toString());
        }
        spawns.put(zoneId, new Spawns(entries, total, Util.getMeasuringTimeMs()));
    }

    public static Spawns spawns(String zoneId) {
        long now = Util.getMeasuringTimeMs();
        Spawns known = spawns.get(zoneId);
        boolean stale = known == null || now - known.receivedAt() > SPAWNS_FRESH_MS;
        Long last = asked.get(zoneId);
        if (stale && (last == null || now - last > SPAWNS_RETRY_MS)
                && ClientPlayNetworking.canSend(ZonePreviewRequestPayload.ID)) {
            asked.put(zoneId, now);
            ClientPlayNetworking.send(new ZonePreviewRequestPayload(zoneId));
        }
        return known;
    }

    public static List<WildZone> all() {
        return zones;
    }

    public static WildZone get(String id) {
        for (WildZone zone : zones) {
            if (zone.id.equals(id)) {
                return zone;
            }
        }
        return null;
    }

    public static WildZone zoneAt(String dimension, BlockPos pos) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            if (zones.get(i).contains(dimension, pos)) {
                return zones.get(i);
            }
        }
        return null;
    }

    public static WildZone zoneAlong(String dimension, Vec3d from, Vec3d direction, double distance) {
        WildZone nearest = null;
        double nearestEnter = Double.MAX_VALUE;
        double nearestExit = 0;
        double[] span = new double[2];
        for (WildZone zone : zones) {
            if (zone.dimension.equals(dimension) && crossing(zone, from, direction, distance, span)
                    && span[0] < nearestEnter) {
                nearest = zone;
                nearestEnter = span[0];
                nearestExit = span[1];
            }
        }
        if (nearest == null) {
            return null;
        }
        for (int i = zones.size() - 1; i >= 0; i--) {
            WildZone zone = zones.get(i);
            if (zone == nearest) {
                return zone;
            }
            if (zone.dimension.equals(dimension) && crossing(zone, from, direction, distance, span)
                    && span[0] < nearestExit && span[1] > nearestEnter) {
                return zone;
            }
        }
        return nearest;
    }

    private static boolean crossing(WildZone zone, Vec3d from, Vec3d direction, double distance, double[] span) {
        double enter = 0;
        double exit = distance;
        double[] origin = {from.x, from.y, from.z};
        double[] step = {direction.x, direction.y, direction.z};
        double[] low = {zone.minX, zone.minY, zone.minZ};
        double[] high = {zone.maxX + 1, zone.maxY + 1, zone.maxZ + 1};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(step[axis]) < 1e-9) {
                if (origin[axis] < low[axis] || origin[axis] > high[axis]) {
                    return false;
                }
                continue;
            }
            double a = (low[axis] - origin[axis]) / step[axis];
            double b = (high[axis] - origin[axis]) / step[axis];
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
            if (enter > exit) {
                return false;
            }
        }
        span[0] = enter;
        span[1] = exit;
        return true;
    }

    public static WildZone preview() {
        return preview;
    }

    public static void setPreview(WildZone zone) {
        preview = zone;
    }
}
