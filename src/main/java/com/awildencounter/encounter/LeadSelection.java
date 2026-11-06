package com.awildencounter.encounter;

import com.awildencounter.AWildEncounter;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LeadSelection {
    private static final Map<UUID, UUID> SELECTED = new ConcurrentHashMap<>();

    private LeadSelection() {
    }

    public static void onSelected(ServerPlayerEntity player, String pokemonId) {
        if (player == null) {
            return;
        }
        try {
            if (pokemonId == null || pokemonId.isEmpty()) {
                SELECTED.remove(player.getUuid());
            } else {
                SELECTED.put(player.getUuid(), UUID.fromString(pokemonId));
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    public static void forget(UUID playerId) {
        SELECTED.remove(playerId);
    }

    public static UUID lead(ServerPlayerEntity player) {
        return usable(player, player == null ? null : SELECTED.get(player.getUuid()));
    }

    public static UUID usable(ServerPlayerEntity player, UUID wanted) {
        if (player == null || wanted == null) {
            return null;
        }
        try {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                if (pokemon.getUuid().equals(wanted)) {
                    return pokemon.getCurrentHealth() > 0 ? wanted : null;
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.debug("Could not read a party for the lead: {}", e.toString());
        }
        return null;
    }
}
