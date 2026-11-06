package com.awildencounter.encounter;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.Locale;
import java.util.Set;

public record SpeciesEntry(String species, String form, boolean group) {
    public static final String FORM = " form=";

    public static SpeciesEntry parse(String text) {
        String entry = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (entry.startsWith("#")) {
            return new SpeciesEntry(entry, null, true);
        }
        int form = entry.indexOf(FORM);
        if (form < 0) {
            return new SpeciesEntry(entry, null, false);
        }
        String name = entry.substring(form + FORM.length()).trim();
        return new SpeciesEntry(entry.substring(0, form).trim(), name.isEmpty() ? null : name, false);
    }

    public String speciesId() {
        return species.contains(":") ? species : "cobblemon:" + species;
    }

    public String key() {
        if (group) {
            return species;
        }
        return form == null ? speciesId() : speciesId() + FORM + form;
    }

    public static String of(Species species, FormData form) {
        String id = species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
        return isStandard(species, form) ? id : id + FORM + form.getName().toLowerCase(Locale.ROOT);
    }

    public static boolean isStandard(Species species, FormData form) {
        return form == null || form == species.getStandardForm()
                || form.getName().equalsIgnoreCase(species.getStandardForm().getName());
    }

    public static FormData formNamed(Species species, String name) {
        if (name == null) {
            return null;
        }
        for (FormData form : species.getForms()) {
            if (form.getName().equalsIgnoreCase(name)) {
                return form;
            }
        }
        return null;
    }

    public static FormData formOf(Species species, PokemonProperties properties) {
        try {
            if (properties != null && properties.getForm() != null) {
                FormData named = formNamed(species, properties.getForm());
                if (named == null) {
                    named = species.getFormByShowdownId(properties.getForm());
                }
                if (named != null) {
                    return named;
                }
            }
            Set<String> aspects = properties == null ? Set.of() : properties.getAspects();
            return species.getForm(aspects == null ? Set.of() : aspects);
        } catch (Exception e) {
            return species.getStandardForm();
        }
    }
}
