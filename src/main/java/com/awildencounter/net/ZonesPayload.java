package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ZonesPayload(String zonesJson) implements CustomPayload {
    public static final CustomPayload.Id<ZonesPayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("zones"));

    private static final int MAX_JSON = 1 << 21;

    public static final PacketCodec<RegistryByteBuf, ZonesPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(MAX_JSON), ZonesPayload::zonesJson,
            ZonesPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
