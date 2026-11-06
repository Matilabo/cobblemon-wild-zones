package com.awildencounter.zone;

import com.awildencounter.AWildEncounter;
import com.awildencounter.config.EncounterConfig;
import com.awildencounter.data.WildState;
import com.awildencounter.encounter.SpeciesPicker;
import com.awildencounter.net.ZoneBannerPayload;
import com.awildencounter.util.Worlds;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ZoneBanner {
    private static final int CHECK_INTERVAL = 5;
    private static final int QUIET_TICKS = 100;
    public static final int MAX_ENTRIES = 30;

    private record Left(String zoneId, int at) {
    }

    private static final Map<UUID, String> inside = new HashMap<>();
    private static final Map<UUID, Left> left = new HashMap<>();
    private static int tickCounter = 0;

    private ZoneBanner() {
    }

    public static void reset() {
        inside.clear();
        left.clear();
        tickCounter = 0;
    }

    public static void forget(UUID player) {
        inside.remove(player);
        left.remove(player);
    }

    public static void tick(MinecraftServer server) {
        if (++tickCounter % CHECK_INTERVAL != 0) {
            return;
        }
        WildState state = WildState.get(server);
        int now = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            ServerWorld world = player.getServerWorld();
            WildZone zone = state.zoneAt(Worlds.dimensionId(world), player.getBlockPos());
            String zoneId = zone == null ? null : zone.id;
            String was = inside.get(id);
            if (Objects.equals(zoneId, was)) {
                continue;
            }
            if (was != null) {
                left.put(id, new Left(was, now));
            }
            if (zone == null) {
                inside.remove(id);
                continue;
            }
            inside.put(id, zoneId);
            Left last = left.get(id);
            if (last != null && last.zoneId().equals(zoneId) && now - last.at() < QUIET_TICKS) {
                continue;
            }
            try {
                show(player, world, state.rulesFor(world), zone);
            } catch (Exception e) {
                AWildEncounter.LOGGER.debug("Could not show the Zone Banner: {}", e.toString());
            }
        }
    }

    private static void show(ServerPlayerEntity player, ServerWorld world, EncounterConfig config, WildZone zone) {
        if (!config.zoneBanner || (!config.wildZones && !zone.spawning)
                || !ServerPlayNetworking.canSend(player, ZoneBannerPayload.ID)) {
            return;
        }
        SpeciesPicker.Odds odds = SpeciesPicker.odds(player, world, zone, config);
        List<Map.Entry<String, Double>> chances = new ArrayList<>();
        for (Map.Entry<String, Double> chance : ZoneOdds.percentages(zone, odds.base()).entrySet()) {
            if (chance.getValue() > 0) {
                chances.add(chance);
            }
        }
        chances.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        JsonArray entries = new JsonArray();
        for (Map.Entry<String, Double> chance : chances.subList(0, Math.min(MAX_ENTRIES, chances.size()))) {
            JsonArray pair = new JsonArray();
            pair.add(chance.getKey());
            pair.add(config.zoneBannerRates ? Math.round(chance.getValue() * 100.0) / 100.0 : 0.0);
            entries.add(pair);
        }
        JsonObject root = new JsonObject();
        root.addProperty("name", zone.name);
        root.addProperty("color", zone.color & 0xFFFFFF);
        root.addProperty("rates", config.zoneBannerRates);
        root.addProperty("total", chances.size());
        root.add("entries", entries);
        ServerPlayNetworking.send(player, new ZoneBannerPayload(root.toString()));
    }
}
