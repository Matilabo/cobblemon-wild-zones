package com.awildencounter.util;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public final class Worlds {
    private Worlds() {
    }

    public static String dimensionId(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    public static RegistryKey<Biome> biomeAt(ServerWorld world, BlockPos pos) {
        try {
            Registry<Biome> registry = world.getRegistryManager().getOptional(RegistryKeys.BIOME).orElse(null);
            if (registry == null) {
                return null;
            }
            return registry.getKey(world.getBiome(pos).value()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static String biomeId(ServerWorld world, BlockPos pos) {
        RegistryKey<Biome> biome = biomeAt(world, pos);
        return biome == null ? null : biome.getValue().toString();
    }

    public static String biomeId(ServerPlayerEntity player) {
        return biomeId(player.getServerWorld(), player.getBlockPos());
    }

    public static String prettyName(String id) {
        if (id == null) {
            return "Unknown";
        }
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? path : out.toString();
    }
}
