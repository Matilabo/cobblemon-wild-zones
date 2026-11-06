package com.awildencounter.zone;

import com.awildencounter.AWildEncounter;
import com.awildencounter.data.WildState;
import com.awildencounter.encounter.SpeciesPicker;
import com.awildencounter.net.ZoneOddsPayload;
import com.awildencounter.net.ZonePreviewPayload;
import com.awildencounter.net.ZonesPayload;
import com.awildencounter.util.Lang;
import com.awildencounter.util.Worlds;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public final class ZoneService {
    public static final int EDIT_PERMISSION = 2;
    private static final long ODDS_COOLDOWN_MS = 1000;
    private static final int MAX_ODDS_ENTRIES = 2000;

    private ZoneService() {
    }

    public static boolean canEdit(ServerPlayerEntity player) {
        return player != null && player.hasPermissionLevel(EDIT_PERMISSION);
    }

    public static void create(ServerPlayerEntity player, BlockPos first, BlockPos second) {
        if (!canEdit(player)) {
            player.sendMessage(Lang.tr("zone.denied", "§cOnly operators can make Wild Zones."));
            return;
        }
        int width = Math.abs(first.getX() - second.getX()) + 1;
        int height = Math.abs(first.getY() - second.getY()) + 1;
        int length = Math.abs(first.getZ() - second.getZ()) + 1;
        if (Math.max(width, Math.max(height, length)) > WildZone.MAX_SIZE) {
            player.sendMessage(Lang.tr("zone.too_big", "§cA Wild Zone can be at most %s blocks on each side.",
                    WildZone.MAX_SIZE));
            return;
        }
        WildState state = WildState.get(player.getServer());
        WildZone zone = WildZone.between(Worlds.dimensionId(player.getServerWorld()), first, second);
        zone.name = state.nextZoneName();
        state.addZone(zone);
        sync(player.getServer());
        player.sendMessage(Lang.tr("zone.created", "§a★ %1$s§a is now a Wild Zone §7(%2$s blocks). Right click it "
                        + "with a Bone to rename it, change its color or size, or delete it.",
                Lang.hl(zone.name, Formatting.YELLOW), zone.size()));
        AWildEncounter.LOGGER.info("{} made the Wild Zone {} in {} ({})", player.getName().getString(), zone.name,
                zone.dimension, zone.corners());
    }

    public static void update(ServerPlayerEntity player, String json) {
        if (!canEdit(player)) {
            player.sendMessage(Lang.tr("zone.denied", "§cOnly operators can make Wild Zones."));
            return;
        }
        WildState state = WildState.get(player.getServer());
        WildZone edited;
        try {
            edited = WildZone.fromJson(json);
        } catch (Exception e) {
            return;
        }
        WildZone saved = state.zone(edited.id);
        if (saved == null) {
            player.sendMessage(Lang.tr("zone.missing", "§cThat Wild Zone no longer exists."));
            sync(player.getServer());
            return;
        }
        WildZone updated = saved.copy();
        String name = edited.name == null ? "" : edited.name.strip();
        updated.name = name.isEmpty() ? saved.name
                : name.substring(0, Math.min(WildZone.MAX_NAME, name.length()));
        updated.color = edited.color & 0xFFFFFF;
        updated.whitelist = edited.whitelist;
        updated.blacklist = edited.blacklist;
        updated.biomeRestriction = edited.biomeRestriction;
        updated.oddsMode = edited.oddsMode;
        updated.rarities = edited.rarities;
        updated.weights = edited.weights;
        updated.tierOdds = edited.tierOdds;
        boolean stoppedSpawning = updated.spawning && !edited.spawning;
        updated.spawning = edited.spawning;
        updated.spawnCooldown = edited.spawnCooldown;
        updated.spawnCap = edited.spawnCap;
        updated.setWidth(edited.width());
        updated.setHeight(edited.height());
        updated.setLength(edited.length());
        state.replaceZone(updated);
        if (stoppedSpawning) {
            ZoneSpawner.clear(player.getServer(), saved);
        }
        sync(player.getServer());
        player.sendMessage(Lang.tr("zone.saved", "§aSaved the Wild Zone %1$s§a §7(%2$s blocks).",
                Lang.hl(updated.name, Formatting.YELLOW), updated.size()));
    }

    public static void delete(ServerPlayerEntity player, String id) {
        if (!canEdit(player)) {
            player.sendMessage(Lang.tr("zone.denied", "§cOnly operators can make Wild Zones."));
            return;
        }
        WildState state = WildState.get(player.getServer());
        WildZone zone = state.zone(id);
        if (zone != null && state.removeZone(id)) {
            ZoneSpawner.clear(player.getServer(), zone);
            sync(player.getServer());
            player.sendMessage(Lang.tr("zone.deleted", "§7Deleted the Wild Zone %s§7.",
                    Lang.hl(zone.name, Formatting.YELLOW)));
            AWildEncounter.LOGGER.info("{} deleted the Wild Zone {}", player.getName().getString(), zone.name);
        }
    }

    public static final int PREVIEW_SIZE = 30;
    private static final long PREVIEW_COOLDOWN_MS = 3000;
    private static final Map<String, Long> lastPreview = new HashMap<>();

    public static void sendPreview(ServerPlayerEntity player, String zoneId) {
        if (!canEdit(player)) {
            return;
        }
        String key = player.getUuid() + "|" + zoneId;
        long now = Util.getMeasuringTimeMs();
        Long last = lastPreview.get(key);
        if (last != null && now - last < PREVIEW_COOLDOWN_MS) {
            return;
        }
        lastPreview.put(key, now);
        MinecraftServer server = player.getServer();
        WildState state = WildState.get(server);
        WildZone zone = state.zone(zoneId);
        if (zone == null) {
            return;
        }
        Identifier dimension = Identifier.tryParse(zone.dimension);
        ServerWorld world = dimension == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimension));
        if (world == null) {
            return;
        }
        SpeciesPicker.Preview preview = SpeciesPicker.preview(player, world, zone, state.rulesFor(world), PREVIEW_SIZE);
        JsonArray entries = new JsonArray();
        preview.entries().forEach(entries::add);
        if (ServerPlayNetworking.canSend(player, ZonePreviewPayload.ID)) {
            ServerPlayNetworking.send(player, new ZonePreviewPayload(zoneId, entries.toString(), preview.total()));
        }
    }

    public static void sendOdds(ServerPlayerEntity player, String zoneJson) {
        if (!canEdit(player)) {
            return;
        }
        WildZone edited;
        try {
            edited = WildZone.fromJson(zoneJson);
        } catch (Exception e) {
            return;
        }
        String key = player.getUuid() + "|odds";
        long now = Util.getMeasuringTimeMs();
        Long last = lastPreview.get(key);
        if (last != null && now - last < ODDS_COOLDOWN_MS) {
            return;
        }
        lastPreview.put(key, now);
        MinecraftServer server = player.getServer();
        WildState state = WildState.get(server);
        WildZone saved = state.zone(edited.id);
        ServerWorld world = saved == null ? null : worldOf(server, saved);
        if (world == null) {
            return;
        }
        WildZone working = saved.copy();
        working.whitelist = edited.whitelist;
        working.blacklist = edited.blacklist;
        working.biomeRestriction = edited.biomeRestriction;
        SpeciesPicker.Odds odds = SpeciesPicker.odds(player, world, working, state.rulesFor(world));
        JsonArray entries = new JsonArray();
        odds.base().forEach((entry, weight) -> {
            if (entries.size() < MAX_ODDS_ENTRIES) {
                JsonArray pair = new JsonArray();
                pair.add(entry);
                pair.add(weight);
                entries.add(pair);
            }
        });
        JsonObject root = new JsonObject();
        root.addProperty("natural", odds.natural());
        root.add("entries", entries);
        if (ServerPlayNetworking.canSend(player, ZoneOddsPayload.ID)) {
            ServerPlayNetworking.send(player, new ZoneOddsPayload(saved.id, root.toString()));
        }
    }

    public static ServerWorld worldOf(MinecraftServer server, WildZone zone) {
        Identifier dimension = Identifier.tryParse(zone.dimension);
        return dimension == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimension));
    }

    private static final java.util.Set<java.util.UUID> zoneEditors = new java.util.HashSet<>();

    public static boolean inZoneEdit(ServerPlayerEntity player) {
        return zoneEditors.contains(player.getUuid());
    }

    public static void setZoneEdit(ServerPlayerEntity player, boolean on) {
        if (on) {
            zoneEditors.add(player.getUuid());
        } else {
            zoneEditors.remove(player.getUuid());
        }
    }

    public static void forget(ServerPlayerEntity player) {
        zoneEditors.remove(player.getUuid());
        String prefix = player.getUuid() + "|";
        lastPreview.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static String zonesJson(MinecraftServer server) {
        JsonArray array = new JsonArray();
        WildState.get(server).zones().forEach(zone -> array.add(zone.toJsonObject()));
        return array.toString();
    }

    public static void sync(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ZonesPayload payload = new ZonesPayload(zonesJson(server));
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, ZonesPayload.ID)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void sync(ServerPlayerEntity player) {
        if (player != null && ServerPlayNetworking.canSend(player, ZonesPayload.ID)) {
            ServerPlayNetworking.send(player, new ZonesPayload(zonesJson(player.getServer())));
        }
    }
}
