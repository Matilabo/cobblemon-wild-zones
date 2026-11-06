package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ChunkPayload(String channel, int index, int count, String part) implements CustomPayload {
    public static final CustomPayload.Id<ChunkPayload> ID = new CustomPayload.Id<>(AWildEncounter.id("chunk"));

    public static final int PART_CHARS = 9000;

    public static final PacketCodec<RegistryByteBuf, ChunkPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(32), ChunkPayload::channel,
            PacketCodecs.VAR_INT, ChunkPayload::index,
            PacketCodecs.VAR_INT, ChunkPayload::count,
            PacketCodecs.string(PART_CHARS), ChunkPayload::part,
            ChunkPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
