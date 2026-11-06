package com.awildencounter.config;

import java.util.ArrayList;
import java.util.List;

public final class ConfigOptions {
    public enum Tab {
        ENCOUNTERS("Encounters"),
        SPECIES("Species & Levels"),
        ANIMATIONS("Animations");

        public final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    public enum Kind {
        HEADING,
        TOGGLE,

        OPTION,

        SLIDER,

        RANGE,

        POINT,

        LIST,

        BIOME_FILTERS,

        BIOME_ENTRY,

        ACTION
    }

    public record Spec(String key, String label, String description, Tab tab, String parent, Kind kind,
                       List<Integer> values, List<String> labels, String unit, List<String> ids) {
        public boolean isHeading() {
            return kind == Kind.HEADING;
        }

        public int indexOfId(String id) {
            int index = ids.indexOf(id);
            return index < 0 ? 0 : index;
        }
    }

    private static final List<Spec> ALL = new ArrayList<>();

    private ConfigOptions() {
    }

    public static List<Spec> all() {
        return ALL;
    }

    public static String primaryParentKey(String parent) {
        if (parent == null) {
            return null;
        }
        String first = parent.split("\\|")[0];
        if (first.startsWith("!")) {
            first = first.substring(1);
        }
        int equals = first.indexOf('=');
        return equals >= 0 ? first.substring(0, equals) : first;
    }

    public static Spec byKey(String key) {
        for (Spec spec : ALL) {
            if (key.equals(spec.key())) {
                return spec;
            }
        }
        return null;
    }

    private static Tab current;

    private static void tab(Tab tab) {
        current = tab;
    }

    private static void heading(String title) {
        ALL.add(new Spec(null, title, null, current, null, Kind.HEADING, List.of(), List.of(), "", List.of()));
    }

    private static void toggle(String key, String parent, String label, String description) {
        ALL.add(new Spec(key, label, description, current, parent, Kind.TOGGLE, List.of(), List.of(), "",
                List.of()));
    }

    private static void option(String key, String parent, String label, String description,
                               List<String> ids, List<String> labels) {
        ALL.add(new Spec(key, label, description, current, parent, Kind.OPTION, List.of(), labels, "", ids));
    }

    private static void slider(String key, String parent, String label, String description, String unit,
                               List<Integer> values, List<String> labels) {
        ALL.add(new Spec(key, label, description, current, parent, Kind.SLIDER, values, labels, unit, List.of()));
    }

    private static void range(String lowKey, String highKey, String parent, String label,
                              String description, List<Integer> values, List<String> labels) {
        ALL.add(new Spec(lowKey, label, description, current, parent, Kind.RANGE, values, labels, "",
                List.of(lowKey, highKey)));
    }

    private static void point(String xKey, String zKey, String parent, String label, String description) {
        ALL.add(new Spec(xKey, label, description, current, parent, Kind.POINT, List.of(), List.of(), "",
                List.of(xKey, zKey)));
    }

    private static void action(String key, String label, String description) {
        ALL.add(new Spec(key, label, description, current, null, Kind.ACTION, List.of(), List.of(), "", List.of()));
    }

    public static final String OPEN_CUSTOM_FOLDER = "openCustomAnimations";
    public static final String IMPORT_GIF = "importAnimation";

    private static void list(String key, String parent, String label, String description) {
        ALL.add(new Spec(key, label, description, current, parent, Kind.LIST, List.of(), List.of(), "", List.of()));
    }

    private static final List<Integer> REGION_SIZES = List.of(0, 32, 64, 128, 256, 512, 1024, 2048);
    private static final List<String> REGION_SIZE_LABELS =
            List.of("Off", "32", "64", "128", "256", "512", "1024", "2048");

    private static final List<Integer> ENCOUNTER_RATES =
            List.of(1, 4, 8, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 1024);

    private static final List<Integer> COOLDOWNS = List.of(0, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600);

    private static final List<Integer> DWELL_SECONDS = List.of(0, 1, 2, 3, 5, 8, 10, 15, 20, 30, 60);

    private static final List<Integer> SHINY_RATES =
            List.of(0, 1, 16, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384);
    private static final List<String> SHINY_RATE_LABELS =
            List.of("Default", "Always", "1 in 16", "1 in 64", "1 in 128", "1 in 256", "1 in 512", "1 in 1024",
                    "1 in 2048", "1 in 4096", "1 in 8192", "1 in 16384");

    public static final List<String> LEVEL_MODE_IDS = List.of("default", "lower", "close", "random", "match", "distance");
    private static final List<String> LEVEL_MODE_LABELS =
            List.of("Default", "Lower than Highest", "Close", "True Random Level", "Match Highest",
                    "Stronger By Distance");
    private static final String LEVEL_MODE_HELP =
            "Lower than Highest: randomly choose from level 1 to the level of strongest party member. Close: "
                    + "same or below some levels of your strongest party member. True Random Level: anything "
                    + "from 1 to 100. Match Highest: the same level as your strongest party member.";

    private static final List<Integer> MIN_LEVELS = List.of(1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40, 50);
    private static final List<Integer> CLOSE_RANGES = List.of(1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30);
    private static final List<Integer> LEVEL_RANGE =
            List.of(0, 1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100);
    private static final List<String> LEVEL_RANGE_LABELS =
            List.of("Any", "1", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55", "60", "65",
                    "70", "75", "80", "85", "90", "95", "100");
    private static final List<Integer> DISTANCE_START_LEVELS =
            List.of(1, 2, 3, 5, 8, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70);
    private static final List<Integer> BLOCKS_PER_LEVEL =
            List.of(10, 16, 25, 32, 50, 64, 75, 100, 128, 150, 200, 250, 300, 400, 500, 750, 1000);
    private static final String DISTANCE_RULES = "levelMode=distance";

    public static final String SPECIES_GROUP_TIP = "A form on its own: cobblemon:pikachu form=alola-bias. "
            + "Groups: #cobblemon:legendary, #cobblemon:mythical, "
            + "#cobblemon:ultra_beast, #cobblemon:paradox, #cobblemon:baby, #cobblemon:fossil, "
            + "#cobblemon:starter, #cobblemon:gen1 to #cobblemon:gen9, and any other Cobblemon species label.";

    private static final List<String> ANIMATION_PRESET_IDS = List.of("minecraft", "retro", "custom");
    private static final List<String> ANIMATION_PRESET_LABELS =
            List.of("Cobblelocke Minecraft", "Retro", "Custom");

    public static final List<String> CLIP_IDS = List.of(
            "random", "default",
            "mc_grass", "mc_grass_flowers", "mc_grass_random", "mc_water", "mc_cave",
            "mc_nether_crimson", "mc_nether_warped", "mc_end",
            "retro_grass", "retro_water", "retro_cave", "retro_trainer", "retro_default");
    private static final List<String> CLIP_LABELS = List.of(
            "Random", "Default Transition",
            "MC Grass", "MC Grass & Flowers", "MC Grass (Random)", "MC Water", "MC Cave",
            "MC Crimson Nether", "MC Warped Nether", "MC End",
            "Retro Grass", "Retro Water", "Retro Cave", "Retro Trainer", "Retro Default");

    public static final List<String> ANIMATION_KEYS = List.of(
            "animOverworld", "animWater", "animCave", "animNetherCrimson", "animNetherWarped",
            "animEnd", "animDefault");

    static {
        tab(Tab.ENCOUNTERS);
        heading("Triggers");
        toggle("wildZones", null, "Wild Zones",
                "Walking inside a Wild Zone can start an encounter: the animation plays and a battle with a wild "
                        + "Cobblemon begins. Hold a Bone (as an operator, or with cheats on in single player) to "
                        + "see and make zones: right click sets one corner, left click the other, and crouching "
                        + "twice turns the selection into a Wild Zone. Right click a zone to rename it, change "
                        + "its color or size, or delete it. Crouch while right clicking to set a corner inside "
                        + "another zone.");
        slider("zoneEncounterRate", "wildZones", "Encounter Chance",
                "The chance of an encounter for every block walked inside a Wild Zone. 1 in 48 is about one "
                        + "encounter every 48 blocks.", "in", ENCOUNTER_RATES, List.of());
        slider("zoneCooldownSeconds", "wildZones", "Encounter Cooldown",
                "How long after a battle (or after an encounter that never became one) before the next "
                        + "encounter can start. Value is in seconds.", "s", COOLDOWNS, List.of());
        toggle("zoneGrassOnly", "wildZones", "Grass Only",
                "Encounters only happen while walking through one of the Encounter Blocks below, like the tall "
                        + "grass of the games. When off, every block of a zone counts.");
        list("encounterBlocks", "zoneGrassOnly", "Encounter Blocks",
                "Block ids that count as grass for Grass Only, e.g. minecraft:short_grass. Block tags work "
                        + "too, e.g. #minecraft:flowers.");
        toggle("blockNaturalSpawnsInZones", "wildZones", "No Natural Spawns In Zones",
                "Cobblemon do not spawn on their own inside Wild Zones, so the only Cobblemon there are the ones "
                        + "you run into.");
        toggle("zoneBanner", null, "Zone Banner",
                "Walking into a Wild Zone shows its name at the top of the screen, with the Cobblemon that can "
                        + "turn up there, for every player, with or without a Bone.");
        toggle("zoneBannerRates", "zoneBanner", "Show Rates",
                "The Zone Banner shows each Cobblemon's chance under its icon. When off, it only shows which "
                        + "Cobblemon can turn up.");
        toggle("everyWildBattle", null, "Every Wild Battle",
                "The encounter animation plays before each battle with a wild Cobblemon. This includes the "
                        + "battles that you start yourself, for example when you throw out your Cobblemon. The "
                        + "battle starts when the animation covers the screen.");
        toggle("encounterEvents", null, "Encounter Events",
                "When you go into a biome for the first time and stay there, the animation plays and a battle "
                        + "with a wild Cobblemon starts. This occurs one time in each biome for each player. To "
                        + "start the events again, use /wildencounter reset events.");
        slider("eventRegion", "encounterEvents", "Event Region",
                "The event triggers when entering a new square region of this many blocks instead of a new "
                        + "biome. Off uses biomes.", "", REGION_SIZES, REGION_SIZE_LABELS);
        slider("eventDwellSeconds", "encounterEvents", "Time Before The Event",
                "How long you have to stay in a new biome or region before its event starts. Value is in "
                        + "seconds.", "s", DWELL_SECONDS, List.of());

        heading("Encounters");
        toggle("autoStartBattle", null, "Start The Battle",
                "The battle starts on its own once the animation covers the screen. When off, the Cobblemon only "
                        + "appears in front of you and the battle is up to you.");
        toggle("removeAfterBattle", null, "Remove After Battle",
                "A Cobblemon from an encounter that was neither caught nor defeated leaves once the battle ends "
                        + "(or after a minute and a half without a battle) instead of staying in the world.");
        slider("shinyRate", null, "Shiny Rarity",
                "How rare shiny encounters are. Default uses Cobblemon's own shiny rate.", "in", SHINY_RATES,
                SHINY_RATE_LABELS);
        toggle("chatMessages", null, "Chat Messages",
                "Tells you in chat when an encounter starts and which Cobblemon appeared.");

        heading("Compatibility");
        toggle("respectRepels", null, "Respect Repels",
                "Encounters do not start near a Repel block from the Cobblemon Repel mod. They also do not "
                        + "start when you have a repel effect or the repel tag (/tag @s add repel). This setting "
                        + "does not change Every Wild Battle, because you start those battles yourself.");

        tab(Tab.SPECIES);
        heading("Species");
        option("speciesSource", null, "Encounter Species",
                "Natural Spawns: the Cobblemon that can spawn where you stand now (biome, time, weather, "
                        + "height). Each one has its usual rarity. If no Cobblemon can spawn there, any species "
                        + "can appear. Any Species: any Cobblemon can appear, at random.",
                List.of("natural", "random"), List.of("Natural Spawns", "Any Species"));
        toggle("allowLegendaries", null, "Legendaries",
                "Lets legendary, mythical, paradox and ultra beast Cobblemon turn up among Any Species picks. "
                        + "Natural Spawns always follow Cobblemon's own spawns.");
        option("filterSpeciesMode", null, "Filter Species",
                "This Dimension: one Species Whitelist and Blacklist for every biome these rules cover (every "
                        + "dimension when Affected Dimensions is ALL). Per Biome: add biomes that get their own "
                        + "lists; Every Other Biome uses the general ones.",
                List.of("dimension", "biome"), List.of("This Dimension", "Per Biome"));
        option("groupBiomeRestriction", null, "Biome Restriction",
                "This setting changes how groups on a whitelist work (for example #cobblemon:starter). It "
                        + "applies to the lists in this menu and in each Wild Zone. Natural Biome Species: only "
                        + "the group members that spawn naturally in the biome can appear. For example, with "
                        + "#cobblemon:starter, Mudkip does not appear in a forest. Any Cobblemon: all the members "
                        + "of a group can appear in all biomes. Species that you add one by one can always "
                        + "appear.",
                List.of("natural", "any"), List.of("Natural Biome Species", "Any Cobblemon"));
        list("speciesWhitelist", "filterSpeciesMode=dimension", "Cobblemon Species Whitelist",
                "Only the Cobblemon on this list can appear, for example cobblemon:pikachu. An empty list "
                        + "lets all species appear. Groups on this list follow Biome Restriction: with Natural "
                        + "Biome Species, only the group members that spawn naturally in the biome can appear. "
                        + SPECIES_GROUP_TIP);
        list("speciesBlacklist", "filterSpeciesMode=dimension", "Cobblemon Species Blacklist",
                "These species never turn up in encounters. " + SPECIES_GROUP_TIP);
        ALL.add(new Spec("biomeSpeciesFilters", "Species Filters Per Biome",
                "Biomes with their own Species Whitelist and Blacklist. Biomes not listed use Every Other "
                        + "Biome's.",
                current, "filterSpeciesMode=biome", Kind.BIOME_FILTERS, List.of(), List.of(), "", List.of()));

        heading("Levels");
        option("levelMode", null, "Encounter Level",
                "Default: the level Cobblemon gives that species when it spawns there. " + LEVEL_MODE_HELP
                        + " Stronger By Distance: the further from the center the encounter happens, the higher "
                        + "its level, with the Starting Level, Blocks Per Level and Center below.",
                LEVEL_MODE_IDS, LEVEL_MODE_LABELS);
        range("randomLvlMin", "randomLvlMax", "levelMode=random", "Random Level Range",
                "The lowest and highest level a True Random Level encounter can be. Any leaves that end open "
                        + "(1 or 100), so 5 - Any means level 5 to 100.", LEVEL_RANGE, LEVEL_RANGE_LABELS);
        slider("minimumLvl", "levelMode=lower,close", "Minimum Level",
                "The lowest level an encounter can be with Lower than Highest or Close, whatever the level of "
                        + "your strongest party member. 1 means no minimum.", "", MIN_LEVELS, List.of());
        slider("closeLvlRange", "levelMode=close", "Close Level Range",
                "How many levels below your strongest party member a Close encounter can be. For example at "
                        + "5, with your best at level 30, encounters are level 25 to 30.", "", CLOSE_RANGES,
                List.of());
        slider("distanceStartLvl", DISTANCE_RULES, "Starting Level",
                "The level of an encounter at the center. Levels grow from here with distance and never go "
                        + "above 100.", "", DISTANCE_START_LEVELS, List.of());
        slider("blocksPerLevel", DISTANCE_RULES, "Blocks Per Level",
                "How many blocks away from the center add 1 level. For example at 100 with a Starting Level "
                        + "of 5, an encounter 1,000 blocks away is level 15.", "", BLOCKS_PER_LEVEL, List.of());
        point("distanceCenterX", "distanceCenterZ", DISTANCE_RULES, "Center",
                "The X and Z coordinates levels grow from. Negative values work; 0 0 by default.");

        tab(Tab.ANIMATIONS);
        heading("Encounter Animations");
        option("animationPreset", null, "Animation Preset",
                "Sets all the encounter animations below at the same time. Cobblelocke Minecraft: animations "
                        + "in Minecraft style. The name comes from my other mod, Cobblelocke: Cobblemon "
                        + "Randomlocke. Retro: simple black transitions, as in the old Pokémon games. When you "
                        + "change one animation below, this changes to Custom. Push ▶ next to an animation to "
                        + "see it.",
                ANIMATION_PRESET_IDS, ANIMATION_PRESET_LABELS);
        option("animOverworld", null, "Overworld Biomes",
                "Plays anywhere above ground in the overworld.", CLIP_IDS, CLIP_LABELS);
        option("animWater", null, "Water Biomes",
                "Plays in an ocean or river, and anywhere the player is actually in water.", CLIP_IDS, CLIP_LABELS);
        option("animCave", null, "Caves",
                "Plays underground, wherever there is no sky overhead.", CLIP_IDS, CLIP_LABELS);
        option("animNetherCrimson", null, "Crimson Nether",
                "Plays in the Nether, except in the warped half.", CLIP_IDS, CLIP_LABELS);
        option("animNetherWarped", null, "Warped Nether",
                "Plays in warped forests and soul sand valleys.", CLIP_IDS, CLIP_LABELS);
        option("animEnd", null, "The End",
                "Plays in the End.", CLIP_IDS, CLIP_LABELS);
        option("animDefault", null, "Everywhere Else",
                "Plays anywhere the settings above do not cover, including other mods' dimensions. Random picks "
                        + "any Retro animation here.", CLIP_IDS, CLIP_LABELS);

        heading("Custom Animations");
        action(OPEN_CUSTOM_FOLDER, "Custom Animations Folder",
                "Opens config/awildencounter/custom encounter animations. Put your GIF files there, then use "
                        + "Import A GIF. Each animation in this folder shows in the animation rows above, with "
                        + "the other animations. Custom animations are on your computer only. Other players "
                        + "need the same folder to see them. If they do not have it, they see the default "
                        + "animation.");
        action(IMPORT_GIF, "Import A GIF",
                "Changes a GIF from the custom animations folder into an encounter animation. You choose the "
                        + "frame where the Cobblemon appears, and you can see that frame before you save. The "
                        + "animation then gets its own folder, and you can choose it in the rows above. You can "
                        + "also change the appearing frame of an animation that is already in the folder.");
    }
}
