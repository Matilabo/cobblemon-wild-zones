package com.awildencounter.encounter;

import com.awildencounter.config.EncounterConfig;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import kotlin.ranges.IntRange;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class LevelRules {
    public static final String DEFAULT = "default";
    public static final String LOWER = "lower";
    public static final String CLOSE = "close";
    public static final String RANDOM = "random";
    public static final String MATCH = "match";
    public static final String DISTANCE = "distance";

    private static final int MAX_LEVEL = 100;

    private LevelRules() {
    }

    public static int level(EncounterConfig config, ServerPlayerEntity player, Species species, IntRange naturalRange,
                            Random random) {
        String mode = config.levelMode == null ? DEFAULT : config.levelMode;
        if (DEFAULT.equals(mode)) {
            if (naturalRange != null) {
                return between(clamp(naturalRange.getFirst()),
                        clamp(Math.max(naturalRange.getFirst(), naturalRange.getLast())), random);
            }
            int level = spawnLevel(species, random);
            if (level > 0) {
                return level;
            }
            mode = LOWER;
        }
        if (DISTANCE.equals(mode)) {
            BlockPos pos = player.getBlockPos();
            return levelAt(config.distanceStartLvl, config.blocksPerLevel, config.distanceCenterX,
                    config.distanceCenterZ, pos.getX(), pos.getZ());
        }
        if (RANDOM.equals(mode)) {
            int low = config.randomLvlMin > 0 ? clamp(config.randomLvlMin) : 1;
            int high = config.randomLvlMax > 0 ? clamp(config.randomLvlMax) : MAX_LEVEL;
            if (low > high) {
                int swap = low;
                low = high;
                high = swap;
            }
            return between(low, high, random);
        }
        int highest = highestInParty(player);
        if (MATCH.equals(mode)) {
            return highest;
        }
        int minimum = clamp(config.minimumLvl);
        int high = Math.max(highest, minimum);
        int low = CLOSE.equals(mode)
                ? Math.max(minimum, highest - Math.max(0, config.closeLvlRange))
                : minimum;
        return between(Math.min(low, high), high, random);
    }

    public static int spawnLevel(Species species, Random random) {
        if (species == null) {
            return -1;
        }
        String wanted = species.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
        List<IntRange> ranges = new ArrayList<>();
        try {
            for (SpawnDetail detail : CobblemonSpawnPools.WORLD_SPAWN_POOL) {
                if (!(detail instanceof PokemonSpawnDetail pokemonDetail)) {
                    continue;
                }
                String name = pokemonDetail.getPokemon().getSpecies();
                if (name == null) {
                    continue;
                }
                String path = name.substring(name.indexOf(':') + 1).toLowerCase(Locale.ROOT);
                IntRange range = pokemonDetail.getLevelRange();
                if (path.equals(wanted) && range != null) {
                    ranges.add(range);
                }
            }
        } catch (Exception e) {
            return -1;
        }
        if (ranges.isEmpty()) {
            return -1;
        }
        IntRange range = ranges.get(random.nextInt(ranges.size()));
        return between(clamp(range.getFirst()), clamp(Math.max(range.getFirst(), range.getLast())), random);
    }

    public static int highestInParty(ServerPlayerEntity player) {
        int highest = 1;
        try {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                highest = Math.max(highest, pokemon.getLevel());
            }
        } catch (Exception ignored) {
        }
        return clamp(highest);
    }

    public static int levelAt(int start, int blocksPerLevel, int centerX, int centerZ, int x, int z) {
        double distance = Math.hypot((double) x - centerX, (double) z - centerZ);
        long gained = blocksPerLevel > 0 ? (long) (distance / blocksPerLevel) : 0L;
        return clamp((int) Math.min(MAX_LEVEL, Math.max(1, start) + gained));
    }

    private static int between(int low, int high, Random random) {
        return low + random.nextInt(Math.max(1, high - low + 1));
    }

    private static int clamp(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }
}
