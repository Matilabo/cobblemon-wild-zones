package com.awildencounter.encounter;

import com.awildencounter.AWildEncounter;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.HashSet;
import java.util.Set;

public final class EvolutionLine {
    private EvolutionLine() {
    }

    public static Set<String> of(Species species) {
        Set<String> line = new HashSet<>();
        if (species == null) {
            return line;
        }
        line.add(species.getName());
        try {
            Species current = species;
            PreEvolution pre;
            while ((pre = current.getPreEvolution()) != null && pre.getSpecies() != null) {
                current = pre.getSpecies();
                if (!line.add(current.getName())) {
                    break;
                }
            }
            collectForwards(species, line);
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not walk the evolution line of {}: {}",
                    species.getName(), e.toString());
        }
        return line;
    }

    private static void collectForwards(Species species, Set<String> line) {
        try {
            for (Evolution evolution : species.getEvolutions()) {
                addResult(evolution, line);
            }
            for (FormData form : species.getForms()) {
                for (Evolution evolution : form.getEvolutions()) {
                    addResult(evolution, line);
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.debug("Could not read evolutions: {}", e.toString());
        }
    }

    private static void addResult(Evolution evolution, Set<String> line) {
        PokemonProperties result = evolution.getResult();
        String name = result == null ? null : result.getSpecies();
        if (name == null || name.isEmpty() || !line.add(name)) {
            return;
        }
        Species next = PokemonSpecies.getByName(name);
        if (next != null) {
            collectForwards(next, line);
        }
    }
}
