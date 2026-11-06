package com.awildencounter.util;

import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Lang {
    public static final String PREFIX = "awildencounter.";

    private Lang() {
    }

    public static MutableText tr(String key, String english, Object... args) {
        MutableText text = Text.translatableWithFallback(PREFIX + key, english, args);
        Formatting base = leadingColour(english);
        return base == null ? text : text.formatted(base);
    }

    private static Formatting leadingColour(String english) {
        if (english == null || english.length() < 2 || english.charAt(0) != '§') {
            return null;
        }
        Formatting formatting = Formatting.byCode(english.charAt(1));
        return formatting != null && formatting.isColor() ? formatting : null;
    }

    public static MutableText hl(Object value, Formatting... formatting) {
        MutableText text = value instanceof Text existing ? existing.copy() : Text.literal(String.valueOf(value));
        return text.formatted(formatting);
    }

    public static MutableText biome(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return Text.literal("?");
        }
        return Text.translatableWithFallback("biome." + biomeId.replace(':', '.'), Worlds.prettyName(biomeId));
    }

    public static MutableText species(Species species) {
        if (species == null) {
            return Text.literal("?");
        }
        try {
            return species.getTranslatedName().copy();
        } catch (Exception e) {
            return Text.literal(species.getName());
        }
    }
}
