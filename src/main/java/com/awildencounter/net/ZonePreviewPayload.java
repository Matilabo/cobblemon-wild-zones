package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ZonePreviewPayload(String zoneId, String entriesJson, int total) implements CustomPayload {
    public static final CustomPayload.Id<ZonePreviewPayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("zone_preview"));

    public static final PacketCodec<RegistryByteBuf, ZonePreviewPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ZonePreviewPayload::zoneId,
            PacketCodecs.STRING, ZonePreviewPayload::entriesJson,
            PacketCodecs.VAR_INT, ZonePreviewPayload::total,
            ZonePreviewPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
