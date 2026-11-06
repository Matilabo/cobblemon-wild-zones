package com.awildencounter.net;

import com.awildencounter.AWildEncounter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record SelectedPokemonPayload(String pokemonId) implements CustomPayload {
    public static final CustomPayload.Id<SelectedPokemonPayload> ID =
            new CustomPayload.Id<>(AWildEncounter.id("selected_pokemon"));

    public static final PacketCodec<RegistryByteBuf, SelectedPokemonPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SelectedPokemonPayload::pokemonId,
            SelectedPokemonPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
