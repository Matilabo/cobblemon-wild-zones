package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ZoneBannerPayload(String bannerJson) implements CustomPayload {
    public static final CustomPayload.Id<ZoneBannerPayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("zone_banner"));

    public static final PacketCodec<RegistryByteBuf, ZoneBannerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(1 << 16), ZoneBannerPayload::bannerJson,
            ZoneBannerPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
