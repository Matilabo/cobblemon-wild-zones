package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.packet.CustomPayload;

public record ZoneCornerPayload(boolean first, BlockPos pos) implements CustomPayload {
    public static final CustomPayload.Id<ZoneCornerPayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("zone_corner"));

    public static final PacketCodec<RegistryByteBuf, ZoneCornerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, ZoneCornerPayload::first,
            BlockPos.PACKET_CODEC, ZoneCornerPayload::pos,
            ZoneCornerPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
