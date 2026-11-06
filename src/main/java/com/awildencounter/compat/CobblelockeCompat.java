package com.awildencounter.compat;

import com.awildencounter.AWildEncounter;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class CobblelockeCompat {
    public static final String MOD_ID = "cobblelocke";
    private static final String KEY_EVENT = "cobblelocke_event";

    private static boolean looked = false;
    private static Method hasEncounterUnderway;
    private static Method stateGet;
    private static Method scope;
    private static Method getConfig;
    private static Field runActive;
    private static Field eventLocked;

    private CobblelockeCompat() {
    }

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static boolean isEventPokemon(PokemonEntity entity) {
        if (!installed()) {
            return false;
        }
        try {
            return entity.getPokemon().getPersistentData().getBoolean(KEY_EVENT);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean encounterUnderway(ServerPlayerEntity player) {
        if (!lookUp() || hasEncounterUnderway == null) {
            return false;
        }
        try {
            return (boolean) hasEncounterUnderway.invoke(null, player.getUuid());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean eventLockActive(ServerPlayerEntity player) {
        if (!lookUp() || stateGet == null) {
            return false;
        }
        try {
            Object state = stateGet.invoke(null, player.getServer());
            Object runScope = scope.invoke(state, player);
            Object config = getConfig.invoke(runScope);
            return runActive.getBoolean(config) && eventLocked.getBoolean(config);
        } catch (Exception e) {
            return false;
        }
    }

    private static synchronized boolean lookUp() {
        if (!installed()) {
            return false;
        }
        if (looked) {
            return true;
        }
        looked = true;
        try {
            Class<?> service = Class.forName("com.cobblelocke.eventlock.EventLockService");
            hasEncounterUnderway = service.getMethod("hasEncounterUnderway", UUID.class);
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Cobblelocke found, but not its event lock: {}", e.toString());
        }
        try {
            Class<?> state = Class.forName("com.cobblelocke.data.CobblelockeState");
            stateGet = state.getMethod("get", MinecraftServer.class);
            scope = state.getMethod("scope", ServerPlayerEntity.class);
            getConfig = scope.getReturnType().getMethod("getConfig");
            Class<?> config = getConfig.getReturnType();
            runActive = config.getField("runActive");
            eventLocked = config.getField("firstCatchEventLocked");
        } catch (Exception e) {
            stateGet = null;
            AWildEncounter.LOGGER.warn("Cobblelocke found, but not its run settings: {}", e.toString());
        }
        AWildEncounter.LOGGER.info("Cobblelocke found: A Wild Cobblemon Appeared! leaves its event lock encounters to it");
        return true;
    }
}
