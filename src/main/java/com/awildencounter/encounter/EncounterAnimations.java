package com.awildencounter.encounter;

import com.awildencounter.AWildEncounter;
import com.awildencounter.config.EncounterConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class EncounterAnimations {
    public static final String DEFAULT = "default";

    private static final int FALLBACK_BATTLE_AT_MS = 2500;

    private static final Map<String, Integer> BATTLE_AT_MS = new ConcurrentHashMap<>();

    private static final Map<String, Integer> COVER_AT_MS = new ConcurrentHashMap<>();

    private static final Map<String, Integer> COVER_UNTIL_MS = new ConcurrentHashMap<>();

    private static final int FALLBACK_COVER_MS = 500;

    public static final int CUSTOM_WAIT_MS = 30_000;

    private EncounterAnimations() {
    }

    public enum Environment {
        OVERWORLD,
        WATER,
        CAVE,
        NETHER_CRIMSON,
        NETHER_WARPED,
        END,
        OTHER
    }

    public static final String RANDOM = "random";
    public static final String MC_GRASS_RANDOM = "mc_grass_random";

    private static final Map<Environment, List<String>> BY_ENVIRONMENT = Map.of(
            Environment.OVERWORLD, List.of("mc_grass", "mc_grass_flowers", "retro_grass"),
            Environment.WATER, List.of("mc_water", "retro_water"),
            Environment.CAVE, List.of("mc_cave", "retro_cave"),
            Environment.NETHER_CRIMSON, List.of("mc_nether_crimson", "retro_default"),
            Environment.NETHER_WARPED, List.of("mc_nether_warped", "retro_default"),
            Environment.END, List.of("mc_end", "retro_trainer"),

            Environment.OTHER, List.of("retro_default", "retro_trainer", "retro_grass", "retro_water",
                    "retro_cave"));

    public static String choose(ServerPlayerEntity player, EncounterConfig config) {
        Environment environment = environment(player);
        return resolve(clipFor(environment, config), environment, new Random());
    }

    public static String resolve(String clipId, Environment environment, Random random) {
        if (clipId == null || clipId.isBlank()) {
            return DEFAULT;
        }
        if (MC_GRASS_RANDOM.equals(clipId)) {
            return random.nextBoolean() ? "mc_grass" : "mc_grass_flowers";
        }
        if (RANDOM.equals(clipId)) {
            List<String> choices = BY_ENVIRONMENT.getOrDefault(environment, List.of());
            return choices.isEmpty() ? DEFAULT : choices.get(random.nextInt(choices.size()));
        }
        return clipId;
    }

    public static Environment environmentFor(String key) {
        return switch (key) {
            case "animWater" -> Environment.WATER;
            case "animCave" -> Environment.CAVE;
            case "animNetherCrimson" -> Environment.NETHER_CRIMSON;
            case "animNetherWarped" -> Environment.NETHER_WARPED;
            case "animEnd" -> Environment.END;
            case "animDefault" -> Environment.OTHER;
            default -> Environment.OVERWORLD;
        };
    }

    public static String clipFor(Environment environment, EncounterConfig config) {
        String clip = switch (environment) {
            case OVERWORLD -> config.animOverworld;
            case WATER -> config.animWater;
            case CAVE -> config.animCave;
            case NETHER_CRIMSON -> config.animNetherCrimson;
            case NETHER_WARPED -> config.animNetherWarped;
            case END -> config.animEnd;
            case OTHER -> config.animDefault;
        };
        return clip == null || clip.isBlank() ? DEFAULT : clip;
    }

    public static Environment environment(ServerPlayerEntity player) {
        try {
            ServerWorld world = player.getServerWorld();
            BlockPos feet = player.getBlockPos();
            RegistryEntry<Biome> biome = world.getBiome(feet);

            if (player.isTouchingWater() || biome.isIn(BiomeTags.IS_OCEAN)
                    || biome.isIn(BiomeTags.IS_DEEP_OCEAN) || biome.isIn(BiomeTags.IS_RIVER)) {
                return Environment.WATER;
            }
            if (world.getRegistryKey() == World.END || biome.isIn(BiomeTags.IS_END)) {
                return Environment.END;
            }
            boolean netherLike = world.getRegistryKey() == World.NETHER
                    || (!world.getDimension().hasSkyLight() && world.getDimension().hasCeiling());
            if (netherLike || biome.isIn(BiomeTags.IS_NETHER)) {
                String name = biomeName(biome);
                return name.contains("warped") || name.contains("soul")
                        ? Environment.NETHER_WARPED
                        : Environment.NETHER_CRIMSON;
            }
            if (world.getDimension().hasSkyLight()) {
                return world.getLightLevel(LightType.SKY, feet.up()) == 0
                        ? Environment.CAVE
                        : Environment.OVERWORLD;
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.debug("Could not read the encounter environment: {}", e.toString());
        }
        return Environment.OTHER;
    }

    private static String biomeName(RegistryEntry<Biome> biome) {
        return biome.getKey()
                .map(key -> key.getValue().getPath())
                .orElse("")
                .toLowerCase(Locale.ROOT);
    }

    public static int battleAtMs(String clipId) {
        if (CustomClips.isCustom(clipId)) {
            return customTiming(clipId, "battleAtMs", CUSTOM_WAIT_MS);
        }
        return BATTLE_AT_MS.computeIfAbsent(clipId, id -> timing(id, "battleAtMs", FALLBACK_BATTLE_AT_MS));
    }

    public static int coverAtMs(String clipId) {
        if (CustomClips.isCustom(clipId)) {
            return customTiming(clipId, "coverAtMs", battleAtMs(clipId));
        }
        return COVER_AT_MS.computeIfAbsent(clipId, id -> timing(id, "coverAtMs", battleAtMs(id)));
    }

    public static int coverUntilMs(String clipId) {
        if (CustomClips.isCustom(clipId)) {
            return customTiming(clipId, "coverUntilMs", coverAtMs(clipId) + FALLBACK_COVER_MS);
        }
        return COVER_UNTIL_MS.computeIfAbsent(clipId,
                id -> timing(id, "coverUntilMs", coverAtMs(id) + FALLBACK_COVER_MS));
    }

    private static int customTiming(String clipId, String field, int fallback) {
        JsonObject meta = CustomClips.readMeta(clipId);
        try {
            return meta != null && meta.has(field) ? meta.get(field).getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int timing(String clipId, String field, int fallback) {
        String path = "/assets/" + AWildEncounter.MOD_ID + "/cutscenes/" + clipId + "/meta.json";
        try (InputStream stream = EncounterAnimations.class.getResourceAsStream(path)) {
            if (stream == null) {
                return fallback;
            }
            JsonObject meta = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return meta.has(field) ? meta.get(field).getAsInt() : fallback;
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read cutscene timing for {}: {}", clipId, e.toString());
            return fallback;
        }
    }
}
