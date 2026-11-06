package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record CreateZonePayload(BlockPos first, BlockPos second) implements CustomPayload {
    public static final CustomPayload.Id<CreateZonePayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("create_zone"));

    public static final PacketCodec<RegistryByteBuf, CreateZonePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, CreateZonePayload::first,
            BlockPos.PACKET_CODEC, CreateZonePayload::second,
            CreateZonePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
