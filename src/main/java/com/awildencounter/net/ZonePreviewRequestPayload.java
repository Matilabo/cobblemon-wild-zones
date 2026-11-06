package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ZonePreviewRequestPayload(String zoneId) implements CustomPayload {
    public static final CustomPayload.Id<ZonePreviewRequestPayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("zone_preview_request"));

    public static final PacketCodec<RegistryByteBuf, ZonePreviewRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ZonePreviewRequestPayload::zoneId,
            ZonePreviewRequestPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
