package com.awildencounter.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DimensionNames {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";

    private DimensionNames() {
    }

    public static String defaultName(String dimensionId) {
        if (dimensionId == null) {
            return "?";
        }
        switch (dimensionId) {
            case OVERWORLD:
                return "Overworld";
            case NETHER:
                return "Nether";
            case END:
                return "End";
            default:
                break;
        }
        int colon = dimensionId.indexOf(':');
        String path = colon >= 0 ? dimensionId.substring(colon + 1) : dimensionId;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_\\-./]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() == 0 ? dimensionId : out.toString();
    }

    public static List<String> sorted(Collection<String> dimensionIds) {
        List<String> ids = new ArrayList<>(dimensionIds);
        ids.sort(Comparator.comparingInt(DimensionNames::rank)
                .thenComparing(id -> defaultName(id).toLowerCase(Locale.ROOT))
                .thenComparing(Comparator.naturalOrder()));
        return ids;
    }

    private static int rank(String id) {
        return switch (id) {
            case OVERWORLD -> 0;
            case NETHER -> 1;
            case END -> 2;
            default -> 3;
        };
    }
}
