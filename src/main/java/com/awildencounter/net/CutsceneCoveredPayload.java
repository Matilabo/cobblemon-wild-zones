package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record CutsceneCoveredPayload(String clipId) implements CustomPayload {
    public static final CustomPayload.Id<CutsceneCoveredPayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("cutscene_covered"));

    public static final PacketCodec<RegistryByteBuf, CutsceneCoveredPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, CutsceneCoveredPayload::clipId,
            CutsceneCoveredPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
