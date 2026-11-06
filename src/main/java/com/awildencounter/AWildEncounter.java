package com.awildencounter;

import com.awildencounter.command.WildEncounterCommand;
import com.awildencounter.compat.RepelCompat;
import com.awildencounter.data.WildState;
import com.awildencounter.encounter.EncounterService;
import com.awildencounter.encounter.LeadSelection;
import com.awildencounter.encounter.SpeciesFilter;
import com.awildencounter.encounter.SpeciesPicker;
import com.awildencounter.net.Networking;
import com.awildencounter.zone.ZoneService;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class AWildEncounter implements ModInitializer {
    public static final String MOD_ID = "awildencounter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Networking.register();

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.LOW,
                (Consumer<BattleStartedEvent.Pre>) EncounterService::onBattleStarting);
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.HIGH,
                (Consumer<SpawnEvent<?>>) EncounterService::onSpawn);

        ServerLifecycleEvents.SERVER_STARTED.register(AWildEncounter::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EncounterService.reset());
        ServerTickEvents.END_SERVER_TICK.register(AWildEncounter::onServerTick);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> WildEncounterCommand.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> ZoneService.sync(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LeadSelection.forget(handler.getPlayer().getUuid());
            EncounterService.forget(handler.getPlayer().getUuid());
            ZoneService.forget(handler.getPlayer());
            com.awildencounter.zone.ZoneBanner.forget(handler.getPlayer().getUuid());
            com.awildencounter.net.Chunks.forget(handler.getPlayer().getUuid());
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) ->
                com.awildencounter.encounter.BiomeSpecies.invalidate());

        LOGGER.info("A Wild Cobblemon Appeared! loaded");
    }

    private static void onServerStarted(MinecraftServer server) {
        EncounterService.reset();
        SpeciesPicker.invalidate();
        SpeciesFilter.invalidate();
        com.awildencounter.encounter.BiomeSpecies.invalidate();
        com.awildencounter.zone.ZoneSpawner.reset();
        com.awildencounter.zone.ZoneBanner.reset();
        WildState state = WildState.get(server);
        LOGGER.info("A Wild Cobblemon Appeared! ready: {} Wild Zone(s){}", state.zones().size(),
                RepelCompat.repelModInstalled() ? ", Cobblemon Repel found" : "");
    }

    private static void onServerTick(MinecraftServer server) {
        try {
            EncounterService.tick(server);
        } catch (Exception e) {
            LOGGER.error("A Wild Cobblemon Appeared! tick failed: {}", e.toString());
        }
        try {
            com.awildencounter.zone.ZoneSpawner.tick(server);
        } catch (Exception e) {
            LOGGER.error("A Wild Cobblemon Appeared! zone spawning failed: {}", e.toString());
        }
        try {
            com.awildencounter.zone.ZoneBanner.tick(server);
        } catch (Exception e) {
            LOGGER.error("A Wild Cobblemon Appeared! zone banner failed: {}", e.toString());
        }
    }
}
