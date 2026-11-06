package com.awildencounter.zone;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ZoneOdds {
    public static final String COBBLEMON = "cobblemon";
    public static final String RARITY = "rarity";
    public static final String WEIGHT = "weight";
    public static final List<String> MODES = List.of(COBBLEMON, RARITY, WEIGHT);

    public static final String COMMON = "common";
    public static final List<String> TIERS = List.of(COMMON, "uncommon", "rare", "very_rare");
    public static final List<Double> DEFAULT_TIER_ODDS = List.of(60.0, 30.0, 7.0, 3.0);
    public static final double DEFAULT_WEIGHT = 10.0;
    public static final double MAX_VALUE = 100000.0;

    private ZoneOdds() {
    }

    public static LinkedHashMap<String, Double> defaultTierOdds() {
        LinkedHashMap<String, Double> odds = new LinkedHashMap<>();
        for (int i = 0; i < TIERS.size(); i++) {
            odds.put(TIERS.get(i), DEFAULT_TIER_ODDS.get(i));
        }
        return odds;
    }

    public static String tierOf(WildZone zone, String entry) {
        String tier = zone.rarities == null ? null : zone.rarities.get(entry);
        return tier != null && TIERS.contains(tier) ? tier : COMMON;
    }

    public static double tierOdds(WildZone zone, String tier) {
        Double odds = zone.tierOdds == null ? null : zone.tierOdds.get(tier);
        return odds != null ? Math.max(0, odds) : DEFAULT_TIER_ODDS.get(Math.max(0, TIERS.indexOf(tier)));
    }

    public static double weightOf(WildZone zone, String entry) {
        Double weight = zone.weights == null ? null : zone.weights.get(entry);
        return weight != null ? Math.max(0, weight) : DEFAULT_WEIGHT;
    }

    public static LinkedHashMap<String, Double> apply(WildZone zone, Map<String, Double> base) {
        LinkedHashMap<String, Double> out = new LinkedHashMap<>();
        String mode = zone == null ? COBBLEMON : zone.oddsMode;
        if (WEIGHT.equals(mode)) {
            base.forEach((entry, weight) -> out.put(entry, weight > 0 ? weightOf(zone, entry) : 0.0));
            return out;
        }
        if (!RARITY.equals(mode)) {
            base.forEach((entry, weight) -> out.put(entry, Math.max(0, weight)));
            return out;
        }
        Map<String, Double> tierTotals = new HashMap<>();
        base.forEach((entry, weight) -> {
            if (weight > 0) {
                tierTotals.merge(tierOf(zone, entry), weight, Double::sum);
            }
        });
        double oddsTotal = 0;
        for (String tier : tierTotals.keySet()) {
            oddsTotal += tierOdds(zone, tier);
        }
        double total = oddsTotal;
        base.forEach((entry, weight) -> {
            if (weight <= 0 || total <= 0) {
                out.put(entry, 0.0);
                return;
            }
            String tier = tierOf(zone, entry);
            out.put(entry, tierOdds(zone, tier) / total * weight / tierTotals.get(tier));
        });
        return out;
    }

    public static LinkedHashMap<String, Double> percentages(WildZone zone, Map<String, Double> base) {
        LinkedHashMap<String, Double> weights = apply(zone, base);
        double total = 0;
        for (double weight : weights.values()) {
            total += weight;
        }
        double sum = total;
        LinkedHashMap<String, Double> out = new LinkedHashMap<>();
        weights.forEach((entry, weight) -> out.put(entry, sum > 0 ? weight * 100.0 / sum : 0.0));
        return out;
    }
}
