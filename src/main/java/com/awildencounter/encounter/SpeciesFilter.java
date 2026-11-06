package com.awildencounter.encounter;

import com.awildencounter.AWildEncounter;
import com.awildencounter.config.EncounterConfig;
import com.awildencounter.zone.WildZone;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.config.starter.StarterCategory;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SpeciesFilter {
    private static volatile Set<String> starters = null;

    private SpeciesFilter() {
    }

    public static void invalidate() {
        starters = null;
    }

    public record Lists(List<String> whitelist, List<String> blacklist) {
        public boolean active() {
            return !whitelist.isEmpty() || !blacklist.isEmpty();
        }
    }

    public static Lists lists(EncounterConfig config, String biomeId, WildZone zone) {
        if (zone != null && zone.hasFilter()) {
            return new Lists(orEmpty(zone.whitelist), orEmpty(zone.blacklist));
        }
        if ("biome".equals(config.filterSpeciesMode) && biomeId != null && config.biomeSpeciesFilters != null) {
            EncounterConfig.BiomeSpeciesFilter own = config.biomeSpeciesFilters.get(biomeId);
            if (own != null) {
                return new Lists(orEmpty(own.whitelist), orEmpty(own.blacklist));
            }
        }
        return new Lists(orEmpty(config.speciesWhitelist), orEmpty(config.speciesBlacklist));
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }

    public static boolean allowed(Species species, FormData form, Lists lists) {
        if (species == null) {
            return true;
        }
        if (!lists.whitelist().isEmpty() && !matchesAny(species, form, lists.whitelist())) {
            return false;
        }
        return lists.blacklist().isEmpty() || !matchesAny(species, form, lists.blacklist());
    }

    private static boolean matchesAny(Species species, FormData form, List<String> entries) {
        for (String entry : entries) {
            if (entry != null && matches(species, form, SpeciesEntry.parse(entry))) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(Species species, FormData form, SpeciesEntry entry) {
        if (entry.species().isEmpty()) {
            return false;
        }
        String path = species.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
        if (entry.group()) {
            String group = entry.species().substring(entry.species().indexOf(':') + 1);
            if (group.equals("starter")) {
                return starters().contains(path);
            }
            return hasLabel(species, group);
        }
        String full = species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
        boolean sameSpecies = entry.species().equals(full) || entry.species().equals(path)
                || entry.species().equals(species.getName().toLowerCase(Locale.ROOT));
        if (!sameSpecies) {
            return false;
        }
        if (entry.form() == null) {
            return true;
        }
        FormData actual = form == null ? species.getStandardForm() : form;
        return actual.getName().equalsIgnoreCase(entry.form());
    }

    public static boolean hasLabel(Species species, String label) {
        try {
            for (String own : species.getLabels()) {
                if (own.equalsIgnoreCase(label)) {
                    return true;
                }
            }
            for (String own : species.getStandardForm().getLabels()) {
                if (own.equalsIgnoreCase(label)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Set<String> starters() {
        Set<String> cached = starters;
        if (cached != null) {
            return cached;
        }
        Set<String> found = new HashSet<>();
        try {
            for (StarterCategory category : Cobblemon.INSTANCE.getStarterConfig().getStarters()) {
                for (PokemonProperties properties : category.getPokemon()) {
                    String name = properties.getSpecies();
                    if (name == null) {
                        continue;
                    }
                    Species species = PokemonSpecies.getByName(name.substring(name.indexOf(':') + 1)
                            .toLowerCase(Locale.ROOT));
                    if (species == null) {
                        continue;
                    }
                    for (String member : EvolutionLine.of(species)) {
                        Species line = PokemonSpecies.getByName(member.toLowerCase(Locale.ROOT));
                        found.add(line != null ? line.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT)
                                : member.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.debug("Could not read Cobblemon's starters: {}", e.toString());
        }
        starters = found;
        return found;
    }
}
