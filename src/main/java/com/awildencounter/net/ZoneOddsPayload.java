package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ZoneOddsPayload(String zoneId, String oddsJson) implements CustomPayload {
    public static final CustomPayload.Id<ZoneOddsPayload> ID = new CustomPayload.Id<>(AWildEncounter.id("zone_odds"));

    public static final PacketCodec<RegistryByteBuf, ZoneOddsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ZoneOddsPayload::zoneId,
            PacketCodecs.string(1 << 20), ZoneOddsPayload::oddsJson,
            ZoneOddsPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
