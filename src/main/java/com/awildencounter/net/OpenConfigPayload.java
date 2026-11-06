package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record OpenConfigPayload(String scopesJson, String presetsJson, boolean canEdit, boolean repelInstalled)
        implements CustomPayload {
    public static final CustomPayload.Id<OpenConfigPayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("open_config"));

    private static final int MAX_JSON = 1 << 21;

    public static final PacketCodec<RegistryByteBuf, OpenConfigPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(MAX_JSON), OpenConfigPayload::scopesJson,
            PacketCodecs.string(MAX_JSON), OpenConfigPayload::presetsJson,
            PacketCodecs.BOOL, OpenConfigPayload::canEdit,
            PacketCodecs.BOOL, OpenConfigPayload::repelInstalled,
            OpenConfigPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
