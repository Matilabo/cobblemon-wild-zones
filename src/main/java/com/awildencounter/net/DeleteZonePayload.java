package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record DeleteZonePayload(String zoneId) implements CustomPayload {
    public static final CustomPayload.Id<DeleteZonePayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("delete_zone"));

    public static final PacketCodec<RegistryByteBuf, DeleteZonePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, DeleteZonePayload::zoneId,
            DeleteZonePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
