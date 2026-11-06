package com.awildencounter.net;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class Chunks {
    public static final String SAVE_CONFIG = "save_config";
    public static final String EXPORT_PRESET = "export_preset";
    public static final String UPDATE_ZONE = "update_zone";
    public static final String ZONE_ODDS = "zone_odds";

    private static final int MAX_PARTS = 256;

    private record Pending(String[] parts, int[] received) {
    }

    private static final Map<String, BiConsumer<ServerPlayerEntity, String>> HANDLERS = new HashMap<>();
    private static final Map<String, Pending> PENDING = new HashMap<>();

    private Chunks() {
    }

    public static void handle(String channel, BiConsumer<ServerPlayerEntity, String> handler) {
        HANDLERS.put(channel, handler);
    }

    static void receive(ServerPlayerEntity player, ChunkPayload payload) {
        BiConsumer<ServerPlayerEntity, String> handler = HANDLERS.get(payload.channel());
        int count = payload.count();
        int index = payload.index();
        if (handler == null || count < 1 || count > MAX_PARTS || index < 0 || index >= count) {
            return;
        }
        String key = player.getUuid() + "|" + payload.channel();
        Pending pending = PENDING.get(key);
        if (index == 0 || pending == null || pending.parts().length != count) {
            pending = new Pending(new String[count], new int[]{0});
            PENDING.put(key, pending);
        }
        if (pending.parts()[index] == null) {
            pending.received()[0]++;
        }
        pending.parts()[index] = payload.part();
        if (pending.received()[0] < count) {
            return;
        }
        PENDING.remove(key);
        handler.accept(player, String.join("", pending.parts()));
    }

    public static void forget(UUID playerId) {
        String prefix = playerId + "|";
        PENDING.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
