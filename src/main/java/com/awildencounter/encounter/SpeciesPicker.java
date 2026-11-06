package com.awildencounter.encounter;

import com.awildencounter.AWildEncounter;
import com.awildencounter.config.EncounterConfig;
import com.awildencounter.zone.WildZone;
import com.awildencounter.zone.ZoneOdds;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.BestSpawner;
import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.SpawningZone;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.position.AreaSpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import kotlin.ranges.IntRange;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class SpeciesPicker {
    public static final String NATURAL = "natural";
    public static final String ANY_BIOME = "any";

    private static final int HORIZONTAL_RADIUS = 8;
    private static final int VERTICAL_RADIUS = 4;
    private static final int RANDOM_ATTEMPTS = 40;
    private static final int PREVIEW_MAX_SIDE = 32;
    private static final int PREVIEW_MAX_HEIGHT = 24;

    private static final Set<String> RESTRICTED_LABELS = Set.of(
            "legendary", "mythical", "ultra_beast", "paradox", "restricted");

    private static final Set<String> GIMMICK_LABELS = Set.of(
            "mega", "gmax", "gigantamax", "primal", "eternamax", "ultra_burst", "totem");

    private static volatile List<Species> battleSafe = null;
    private static volatile List<Species> battleSafeWithLegendaries = null;

    public record Pick(Species species, FormData form, PokemonProperties properties, IntRange levelRange) {
    }

    private record Weighted(PokemonSpawnDetail detail, Species species, FormData form, double weight) {
    }

    public record Preview(List<String> entries, int total) {
    }

    private SpeciesPicker() {
    }

    public static void invalidate() {
        battleSafe = null;
        battleSafeWithLegendaries = null;
    }

    public static Pick pick(ServerPlayerEntity player, EncounterConfig config, String biomeId, WildZone zone,
                            Random random) {
        return pickAt(player, player.getServerWorld(), player.getBlockPos(), config, biomeId, zone, random);
    }

    public static Pick pickAt(ServerPlayerEntity player, ServerWorld world, BlockPos pos, EncounterConfig config,
                              String biomeId, WildZone zone, Random random) {
        SpeciesFilter.Lists lists = SpeciesFilter.lists(config, biomeId, zone);
        BlockPos base = pos.add(-HORIZONTAL_RADIUS, -VERTICAL_RADIUS, -HORIZONTAL_RADIUS);
        Candidates candidates = candidates(player, world, base, HORIZONTAL_RADIUS * 2 + 1, VERTICAL_RADIUS * 2 + 1,
                pos, config, lists, zone, NATURAL.equals(config.speciesSource));
        String entry = chooseKey(ZoneOdds.apply(zone, candidates.base()), random);
        if (entry == null) {
            return null;
        }
        List<Weighted> natural = candidates.natural().get(entry);
        if (natural != null && !natural.isEmpty()) {
            Weighted chosen = weighted(natural, random);
            if (chosen != null) {
                return new Pick(chosen.species(), chosen.form(), chosen.detail().getPokemon().copy(),
                        chosen.detail().getLevelRange());
            }
        }
        return candidates.pool().get(entry);
    }

    private record Candidates(LinkedHashMap<String, Double> base, Map<String, List<Weighted>> natural,
                              Map<String, Pick> pool) {
    }

    private static Candidates candidates(ServerPlayerEntity player, ServerWorld world, BlockPos base, int side,
                                         int height, BlockPos poolPos, EncounterConfig config,
                                         SpeciesFilter.Lists lists, WildZone zone, boolean lookNatural) {
        Map<String, List<Weighted>> natural = new LinkedHashMap<>();
        Map<String, Double> naturalWeights = new LinkedHashMap<>();
        if (lookNatural) {
            for (Weighted candidate : natural(player, world, base, side, height, lists)) {
                String entry = SpeciesEntry.of(candidate.species(), candidate.form());
                natural.computeIfAbsent(entry, key -> new ArrayList<>()).add(candidate);
                naturalWeights.merge(entry, candidate.weight(), Double::sum);
            }
        }
        boolean whitelist = !lists.whitelist().isEmpty();
        Map<String, Pick> pool = new LinkedHashMap<>();
        if (whitelist || naturalWeights.isEmpty()) {
            for (Pick pick : randomPool(config, lists, zone, world, poolPos)) {
                pool.putIfAbsent(SpeciesEntry.of(pick.species(), pick.form()), pick);
            }
        }
        LinkedHashMap<String, Double> weights = new LinkedHashMap<>();
        double total = 0;
        for (double weight : naturalWeights.values()) {
            total += weight;
        }
        double scale = whitelist && total > 0 ? naturalWeights.size() / total : 1.0;
        naturalWeights.forEach((entry, weight) -> weights.put(entry, weight * scale));
        if (whitelist || naturalWeights.isEmpty()) {
            pool.keySet().forEach(entry -> weights.putIfAbsent(entry, 1.0));
        }
        return new Candidates(weights, natural, pool);
    }

    private static String chooseKey(Map<String, Double> weights, Random random) {
        double total = 0;
        for (double weight : weights.values()) {
            total += weight;
        }
        if (total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        String last = null;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            last = entry.getKey();
            roll -= entry.getValue();
            if (roll <= 0) {
                return last;
            }
        }
        return last;
    }

    public static boolean canPick(ServerPlayerEntity player, EncounterConfig config, String biomeId, WildZone zone) {
        return pick(player, config, biomeId, zone, new Random()) != null;
    }

    public static Preview preview(ServerPlayerEntity player, ServerWorld world, WildZone zone, EncounterConfig config,
                                  int limit) {
        Map<String, Double> weights = ZoneOdds.apply(zone, odds(player, world, zone, config).base());
        List<String> entries = new ArrayList<>();
        weights.forEach((entry, weight) -> {
            if (weight > 0) {
                entries.add(entry);
            }
        });
        entries.sort(Comparator.comparingDouble((String entry) -> weights.get(entry)).reversed());
        return new Preview(new ArrayList<>(entries.subList(0, Math.min(limit, entries.size()))), entries.size());
    }

    public record Odds(LinkedHashMap<String, Double> base, boolean natural) {
    }

    public static Odds odds(ServerPlayerEntity player, ServerWorld world, WildZone zone, EncounterConfig config) {
        String biomeId = com.awildencounter.util.Worlds.biomeId(world, center(zone));
        SpeciesFilter.Lists lists = SpeciesFilter.lists(config, biomeId, zone);
        int side = Math.min(PREVIEW_MAX_SIDE, Math.max(HORIZONTAL_RADIUS * 2 + 1,
                Math.max(zone.width(), zone.length())));
        int height = Math.min(PREVIEW_MAX_HEIGHT, Math.max(VERTICAL_RADIUS * 2 + 1, zone.height() + 4));
        BlockPos center = center(zone);
        BlockPos base = new BlockPos(center.getX() - side / 2, zone.minY - 2, center.getZ() - side / 2);
        boolean lookNatural = NATURAL.equals(config.speciesSource) && world.isChunkLoaded(center);
        Candidates candidates = candidates(player, world, base, side, height, center, config, lists, zone,
                lookNatural);
        Map<String, Double> weights = candidates.base();
        List<String> entries = new ArrayList<>(weights.keySet());
        entries.sort(Comparator.comparingDouble((String entry) -> weights.get(entry)).reversed());
        LinkedHashMap<String, Double> sorted = new LinkedHashMap<>();
        entries.forEach(entry -> sorted.put(entry, weights.get(entry)));
        return new Odds(sorted, !candidates.natural().isEmpty());
    }

    private static BlockPos center(WildZone zone) {
        return new BlockPos((zone.minX + zone.maxX) / 2, (zone.minY + zone.maxY) / 2, (zone.minZ + zone.maxZ) / 2);
    }

    private static List<Weighted> natural(ServerPlayerEntity player, ServerWorld world, BlockPos base, int side,
                                          int height, SpeciesFilter.Lists lists) {
        List<Weighted> candidates = new ArrayList<>();
        try {
            PlayerSpawner spawner = PlayerExtensionsKt.getSpawner(player);
            SpawningZoneInput input = new SpawningZoneInput(new SpawnCause(spawner, player), world,
                    base.getX(), base.getY(), base.getZ(), side, height, side);
            SpawningZone zone = Cobblemon.INSTANCE.getSpawningZoneGenerator().generate(spawner, input);
            List<AreaSpawnablePosition> positions = Cobblemon.INSTANCE.getAreaSpawnablePositionResolver()
                    .resolve(spawner, SpawnablePositionCalculator.Companion.getPrioritizedAreaCalculators(), zone);
            if (positions.isEmpty()) {
                return candidates;
            }
            for (Map.Entry<String, Float> bucket : BestSpawner.INSTANCE.getConfig().getWorldBuckets().entrySet()) {
                Map<SpawnDetail, Float> chances = spawner.getSelector()
                        .getProbabilities(spawner, bucket.getKey(), positions);
                for (Map.Entry<SpawnDetail, Float> chance : chances.entrySet()) {
                    if (!(chance.getKey() instanceof PokemonSpawnDetail detail) || !detail.isValid()) {
                        continue;
                    }
                    Species species = speciesOf(detail.getPokemon());
                    double weight = (double) bucket.getValue() * chance.getValue();
                    if (species == null || weight <= 0) {
                        continue;
                    }
                    FormData form = SpeciesEntry.formOf(species, detail.getPokemon());
                    if (SpeciesFilter.allowed(species, form, lists)) {
                        candidates.add(new Weighted(detail, species, form, weight));
                    }
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.debug("Could not read what spawns around {}: {}", base, e.toString());
        }
        return candidates;
    }

    private static Weighted weighted(List<Weighted> candidates, Random random) {
        double total = 0;
        for (Weighted candidate : candidates) {
            total += candidate.weight();
        }
        if (candidates.isEmpty() || total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        for (Weighted candidate : candidates) {
            roll -= candidate.weight();
            if (roll <= 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static Species speciesOf(PokemonProperties properties) {
        String name = properties == null ? null : properties.getSpecies();
        return name == null ? null : speciesNamed(name);
    }

    public static Species speciesNamed(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains(":")) {
            Identifier id = Identifier.tryParse(lower);
            Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
            if (species != null) {
                return species;
            }
            lower = lower.substring(lower.indexOf(':') + 1);
        }
        return PokemonSpecies.getByName(lower);
    }

    private static List<Pick> randomPool(EncounterConfig config, SpeciesFilter.Lists lists, WildZone zone,
                                         ServerWorld world, BlockPos pos) {
        List<Pick> pool = new ArrayList<>();
        if (!lists.whitelist().isEmpty()) {
            Set<Species> natives = null;
            String restriction = zone == null ? config.groupBiomeRestriction
                    : zone.restriction(config.groupBiomeRestriction);
            boolean restrict = !ANY_BIOME.equals(restriction);
            for (String text : lists.whitelist()) {
                SpeciesEntry entry = SpeciesEntry.parse(text);
                if (entry.group()) {
                    if (restrict && natives == null) {
                        natives = BiomeSpecies.at(world, pos);
                    }
                    for (Species species : battleSafe(true)) {
                        if ((natives == null || natives.contains(species))
                                && SpeciesFilter.matches(species, null, entry)) {
                            addIfAllowed(pool, species, null, lists);
                        }
                    }
                    continue;
                }
                Species species = speciesNamed(entry.species());
                if (species == null) {
                    continue;
                }
                FormData form = SpeciesEntry.formNamed(species, entry.form());
                if (entry.form() != null && form == null) {
                    continue;
                }
                addIfAllowed(pool, species, form, lists);
            }
            return pool;
        }
        for (Species species : battleSafe(config.allowLegendaries)) {
            addIfAllowed(pool, species, null, lists);
        }
        return pool;
    }

    private static void addIfAllowed(List<Pick> pool, Species species, FormData form, SpeciesFilter.Lists lists) {
        if (!SpeciesFilter.allowed(species, form, lists)) {
            return;
        }
        for (Pick existing : pool) {
            if (existing.species() == species && existing.form() == form) {
                return;
            }
        }
        PokemonProperties properties = new PokemonProperties();
        properties.setSpecies(species.getResourceIdentifier().toString());
        pool.add(new Pick(species, form, properties, null));
    }

    private static List<Species> battleSafe(boolean withLegendaries) {
        List<Species> cached = withLegendaries ? battleSafeWithLegendaries : battleSafe;
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<Species> built = new ArrayList<>();
        try {
            for (Species species : PokemonSpecies.getSpecies()) {
                String name = species.getName();
                if (name == null || name.contains(".") || name.contains("_")
                        || species.getNationalPokedexNumber() <= 0 || !species.getImplemented()) {
                    continue;
                }
                if (hasLabel(species, GIMMICK_LABELS) || (!withLegendaries && hasLabel(species, RESTRICTED_LABELS))
                        || GIMMICK_LABELS.contains(species.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                built.add(species);
            }
            built.sort(Comparator.comparingInt(Species::getNationalPokedexNumber));
        } catch (Exception e) {
            AWildEncounter.LOGGER.error("Could not read the species registry: {}", e.toString());
        }
        if (withLegendaries) {
            battleSafeWithLegendaries = built;
        } else {
            battleSafe = built;
        }
        return built;
    }

    private static boolean hasLabel(Species species, Set<String> labels) {
        try {
            for (String label : species.getLabels()) {
                if (labels.contains(label.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
