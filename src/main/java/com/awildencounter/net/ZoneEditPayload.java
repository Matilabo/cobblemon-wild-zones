package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ZoneEditPayload(boolean on) implements CustomPayload {
    public static final CustomPayload.Id<ZoneEditPayload> ID = new CustomPayload.Id<>(AWildEncounter.id("zone_edit"));

    public static final PacketCodec<RegistryByteBuf, ZoneEditPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, ZoneEditPayload::on,
            ZoneEditPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
