package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import com.awildencounter.compat.RepelCompat;
import com.awildencounter.config.ConfigFiles;
import com.awildencounter.config.DimensionNames;
import com.awildencounter.config.EncounterConfig;
import com.awildencounter.data.WildState;
import com.awildencounter.encounter.EncounterService;
import com.awildencounter.encounter.LeadSelection;
import com.awildencounter.util.Lang;
import com.awildencounter.util.Worlds;
import com.awildencounter.zone.ZoneService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Networking {
    public static final int CONFIG_PERMISSION = 2;

    private Networking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenConfigPayload.ID, OpenConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CutscenePayload.ID, CutscenePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ZonesPayload.ID, ZonesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ZonePreviewPayload.ID, ZonePreviewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ZonePreviewRequestPayload.ID, ZonePreviewRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ZoneOddsPayload.ID, ZoneOddsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ZoneBannerPayload.ID, ZoneBannerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ZoneCornerPayload.ID, ZoneCornerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ZoneEditPayload.ID, ZoneEditPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChunkPayload.ID, ChunkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CutsceneCoveredPayload.ID, CutsceneCoveredPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectedPokemonPayload.ID, SelectedPokemonPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CreateZonePayload.ID, CreateZonePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteZonePayload.ID, DeleteZonePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ChunkPayload.ID,
                (payload, context) -> context.server().execute(() -> Chunks.receive(context.player(), payload)));
        Chunks.handle(Chunks.SAVE_CONFIG, Networking::handleSave);
        Chunks.handle(Chunks.EXPORT_PRESET, Networking::handleExport);
        Chunks.handle(Chunks.UPDATE_ZONE, ZoneService::update);
        Chunks.handle(Chunks.ZONE_ODDS, ZoneService::sendOdds);
        ServerPlayNetworking.registerGlobalReceiver(CutsceneCoveredPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> EncounterService.onScreenCovered(context.player(), payload.clipId())));
        ServerPlayNetworking.registerGlobalReceiver(SelectedPokemonPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> LeadSelection.onSelected(context.player(), payload.pokemonId())));
        ServerPlayNetworking.registerGlobalReceiver(CreateZonePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> ZoneService.create(context.player(), payload.first(), payload.second())));
        ServerPlayNetworking.registerGlobalReceiver(DeleteZonePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> ZoneService.delete(context.player(), payload.zoneId())));
        ServerPlayNetworking.registerGlobalReceiver(ZonePreviewRequestPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> ZoneService.sendPreview(context.player(), payload.zoneId())));
    }

    public static boolean canEditConfig(ServerPlayerEntity player) {
        return player != null && player.hasPermissionLevel(CONFIG_PERMISSION);
    }

    public static boolean canPlayCutscene(ServerPlayerEntity player) {
        return ServerPlayNetworking.canSend(player, CutscenePayload.ID);
    }

    public static boolean sendCutscene(ServerPlayerEntity player, String clipId) {
        if (!canPlayCutscene(player)) {
            return false;
        }
        ServerPlayNetworking.send(player, new CutscenePayload(clipId));
        return true;
    }

    public static void sendOpenConfig(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, OpenConfigPayload.ID)) {
            player.sendMessage(Lang.tr("config.no_client", "§cA Wild Cobblemon Appeared! is not installed on your client, so "
                    + "the config screen cannot open. Edit config/awildencounter/config.json5 instead."));
            return;
        }
        ServerPlayNetworking.send(player, new OpenConfigPayload(scopesJson(player),
                ConfigFiles.loadPresets().toString(), canEditConfig(player), RepelCompat.repelModInstalled()));
    }

    private static String scopesJson(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        WildState state = WildState.get(server);
        JsonObject root = new JsonObject();
        String here = Worlds.dimensionId(player.getServerWorld());
        root.addProperty("selected", state.affectsAll() ? WildState.ALL : here);
        JsonArray scopes = new JsonArray();
        JsonObject all = new JsonObject();
        all.addProperty("id", WildState.ALL);
        all.addProperty("name", "ALL");
        all.addProperty("own", true);
        all.add("config", state.all().toJsonObject());
        scopes.add(all);
        for (String dimension : dimensions(server)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", dimension);
            entry.addProperty("name", state.dimensionName(dimension));
            entry.addProperty("defaultName", DimensionNames.defaultName(dimension));
            entry.addProperty("own", state.hasOwnRules(dimension));
            entry.add("config", state.exact(dimension).toJsonObject());
            scopes.add(entry);
        }
        root.add("scopes", scopes);
        return root.toString();
    }

    public static List<String> dimensions(MinecraftServer server) {
        List<String> ids = new ArrayList<>();
        for (RegistryKey<World> key : server.getWorldRegistryKeys()) {
            ids.add(key.getValue().toString());
        }
        return DimensionNames.sorted(ids);
    }

    private static void handleSave(ServerPlayerEntity player, String rulesJson) {
        if (!canEditConfig(player)) {
            player.sendMessage(Lang.tr("config.denied", "§cOnly operators can change these settings."));
            return;
        }
        MinecraftServer server = player.getServer();
        WildState state = WildState.get(server);
        List<String> known = dimensions(server);
        String selected;
        try {
            JsonObject root = JsonParser.parseString(rulesJson).getAsJsonObject();
            selected = root.has("selected") ? root.get("selected").getAsString() : WildState.ALL;
            JsonObject scopes = root.has("scopes") ? root.getAsJsonObject("scopes") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : scopes.entrySet()) {
                String scope = entry.getKey();
                if (!WildState.ALL.equals(scope) && !known.contains(scope)) {
                    continue;
                }
                EncounterConfig rules = EncounterConfig.fromJsonObject(entry.getValue().getAsJsonObject());
                state.saveRules(scope, rules);
                if (WildState.ALL.equals(scope)) {
                    ConfigFiles.saveDefaults(rules);
                }
            }
            if (root.has("names")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("names").entrySet()) {
                    if (known.contains(entry.getKey())) {
                        state.setDimensionName(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read the rules {} saved: {}", player.getName().getString(),
                    e.toString());
            return;
        }
        boolean affectsAll = WildState.ALL.equals(selected) || !known.contains(selected);
        state.setAffectsAll(affectsAll);
        if (affectsAll) {
            player.sendMessage(Lang.tr("config.saved", "§aSettings saved. §7They apply to every "
                    + "dimension."));
        } else {
            player.sendMessage(Lang.tr("config.saved_for", "§aSettings saved for %s§a. §7Each "
                            + "dimension now uses its own rules (ALL's where it has none).",
                    Lang.hl(state.dimensionName(selected), Formatting.YELLOW)));
        }
        AWildEncounter.LOGGER.info("{} saved the encounter rules ({})", player.getName().getString(),
                affectsAll ? "every dimension" : "per dimension");
    }

    private static void handleExport(ServerPlayerEntity player, String json) {
        if (!canEditConfig(player)) {
            player.sendMessage(Lang.tr("export.denied", "§cOnly operators can export a preset."));
            return;
        }
        String presetName;
        EncounterConfig rules;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            presetName = root.get("name").getAsString();
            rules = EncounterConfig.fromJsonObject(root.getAsJsonObject("config"));
        } catch (Exception e) {
            return;
        }
        Text name = Lang.hl(presetName.trim(), Formatting.WHITE);
        player.sendMessage(switch (ConfigFiles.exportPreset(presetName, rules)) {
            case ADDED -> Lang.tr("export.added", "§aSaved these settings as the preset %s§a. §7It is in "
                    + "config/awildencounter/presets.json5 and in the preset switcher.", name);
            case REPLACED -> Lang.tr("export.replaced", "§aReplaced the preset %s§a with these settings. §7It is "
                    + "in config/awildencounter/presets.json5 and in the preset switcher.", name);
            case FAILED -> Lang.tr("export.failed", "§cCould not write the preset. Check the log and "
                    + "config/awildencounter/presets.json5.");
        });
    }
}
