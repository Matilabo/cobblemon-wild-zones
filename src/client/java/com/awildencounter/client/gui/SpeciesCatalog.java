package com.awildencounter.client.gui;

import com.awildencounter.encounter.SpeciesEntry;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SpeciesCatalog {
    public record Option(String key, String bare, String speciesId, boolean form, String name, int dex) {
    }

    private static List<Option> options = List.of();
    private static int builtFrom = -1;

    private SpeciesCatalog() {
    }

    public static List<Option> all() {
        int count = PokemonSpecies.getSpecies().size();
        if (count != builtFrom) {
            options = build();
            builtFrom = count;
        }
        return options;
    }

    private static List<Option> build() {
        List<Option> built = new ArrayList<>();
        List<Species> species = new ArrayList<>(PokemonSpecies.getSpecies());
        species.removeIf(each -> !each.getImplemented() || each.getNationalPokedexNumber() <= 0 || !real(each));
        species.sort(Comparator.comparingInt(Species::getNationalPokedexNumber)
                .thenComparing(each -> each.getResourceIdentifier().toString()));
        for (Species each : species) {
            String name = speciesName(each);
            built.add(option(SpeciesEntry.of(each, null), false, name, each.getNationalPokedexNumber()));
            List<Option> forms = new ArrayList<>();
            for (FormData form : each.getForms()) {
                String key = SpeciesEntry.of(each, form);
                if (!SpeciesEntry.isStandard(each, form) && SpeciesIcons.index().containsKey(key)) {
                    forms.add(option(key, true, name + " (" + form.getName() + ")", each.getNationalPokedexNumber()));
                }
            }
            forms.sort(Comparator.comparing(Option::key));
            built.addAll(forms);
        }
        return built;
    }

    private static final Set<String> GIMMICK_LABELS = Set.of(
            "mega", "gmax", "gigantamax", "primal", "eternamax", "ultra_burst", "totem");

    private static boolean real(Species species) {
        String id = species.getResourceIdentifier().getPath();
        if (id.contains(".") || id.contains("_") || GIMMICK_LABELS.contains(id.toLowerCase(Locale.ROOT))) {
            return false;
        }
        try {
            for (String label : species.getLabels()) {
                if (GIMMICK_LABELS.contains(label.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
        } catch (Exception ignored) {
        }
        String name = speciesName(species);
        return !(name.contains(".species.") || name.endsWith(".name"));
    }

    private static Option option(String key, boolean form, String name, int dex) {
        SpeciesEntry entry = SpeciesEntry.parse(key);
        String bare = key.substring(key.indexOf(':') + 1);
        return new Option(key, bare, entry.speciesId(), form, name, dex);
    }

    private static String speciesName(Species species) {
        try {
            return species.getTranslatedName().getString();
        } catch (Exception e) {
            return species.getName();
        }
    }

    public static String nameOf(String entry) {
        String key = SpeciesEntry.parse(entry).key();
        for (Option option : all()) {
            if (option.key().equals(key)) {
                return option.name();
            }
        }
        return entry.toLowerCase(Locale.ROOT);
    }
}
