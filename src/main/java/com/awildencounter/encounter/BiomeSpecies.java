package com.awildencounter.encounter;

import com.awildencounter.AWildEncounter;
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BiomeSpecies {
    private static final Map<String, Set<Species>> CACHE = new ConcurrentHashMap<>();

    private BiomeSpecies() {
    }

    public static void invalidate() {
        CACHE.clear();
    }

    public static Set<Species> at(ServerWorld world, BlockPos pos) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        Identifier dimension = world.getRegistryKey().getValue();
        String biomeId = biome.getKey().map(key -> key.getValue().toString()).orElse("?");
        return CACHE.computeIfAbsent(dimension + "|" + biomeId, key -> compute(biome, dimension));
    }

    private static Set<Species> compute(RegistryEntry<Biome> biome, Identifier dimension) {
        Set<Species> found = new HashSet<>();
        try {
            for (SpawnDetail detail : CobblemonSpawnPools.WORLD_SPAWN_POOL.getDetails()) {
                if (!(detail instanceof PokemonSpawnDetail pokemon) || !pokemon.isValid()) {
                    continue;
                }
                if (!fits(detail.getConditions(), biome, dimension, true)
                        || fits(detail.getAnticonditions(), biome, dimension, false)) {
                    continue;
                }
                String name = pokemon.getPokemon().getSpecies();
                Species species = name == null ? null : SpeciesPicker.speciesNamed(name);
                if (species != null) {
                    found.add(species);
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read Cobblemon's spawns for {}: {}", biome, e.toString());
        }
        return found;
    }

    private static boolean fits(List<SpawningCondition<?>> conditions, RegistryEntry<Biome> biome,
                                Identifier dimension, boolean whenEmpty) {
        if (conditions == null || conditions.isEmpty()) {
            return whenEmpty;
        }
        for (SpawningCondition<?> condition : conditions) {
            Collection<Identifier> dimensions = condition.getDimensions();
            if (dimensions != null && !dimensions.isEmpty() && !dimensions.contains(dimension)) {
                continue;
            }
            Set<RegistryLikeCondition<Biome>> biomes = condition.getBiomes();
            if (biomes == null || biomes.isEmpty()) {
                if (whenEmpty) {
                    return true;
                }
                continue;
            }
            for (RegistryLikeCondition<Biome> wanted : biomes) {
                if (wanted.fits(biome)) {
                    return true;
                }
            }
        }
        return false;
    }
}
