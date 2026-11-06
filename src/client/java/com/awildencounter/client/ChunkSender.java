package com.awildencounter.client;

import com.awildencounter.net.ChunkPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ChunkSender {
    private ChunkSender() {
    }

    public static boolean canSend() {
        return ClientPlayNetworking.canSend(ChunkPayload.ID);
    }

    public static void send(String channel, String message) {
        int size = ChunkPayload.PART_CHARS;
        int count = Math.max(1, (message.length() + size - 1) / size);
        for (int i = 0; i < count; i++) {
            String part = message.substring(i * size, Math.min(message.length(), (i + 1) * size));
            ClientPlayNetworking.send(new ChunkPayload(channel, i, count, part));
        }
    }
}
