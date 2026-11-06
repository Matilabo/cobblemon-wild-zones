package com.awildencounter.encounter;

import com.awildencounter.AWildEncounter;
import com.awildencounter.compat.CobblelockeCompat;
import com.awildencounter.compat.RepelCompat;
import com.awildencounter.config.EncounterConfig;
import com.awildencounter.data.WildState;
import com.awildencounter.net.Networking;
import com.awildencounter.util.Lang;
import com.awildencounter.util.Worlds;
import com.awildencounter.zone.WildZone;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.ErroredBattleStart;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class EncounterService {
    public static final String KEY_ENCOUNTER = "awildencounter_encounter";

    private static final int BATTLE_START_DELAY_TICKS = 10;
    private static final int MIN_BATTLE_DELAY_TICKS = 2;
    private static final int CLIENT_REPORT_GRACE_MS = 1500;
    private static final int NO_ANIMATION_WAIT_MS = 2500;
    private static final int TRACK_INTERVAL = 10;
    private static final int UNBATTLED_TIMEOUT_TICKS = 90 * 20;
    private static final double MAX_STEP = 4.0;

    private static final Random RANDOM = new Random();

    public enum Kind {
        ZONE,
        BIOME_EVENT,
        WILD_BATTLE,
        TEST
    }

    private record PendingCutscene(UUID playerId, Kind kind, String clipId, UUID entityId, UUID lead,
                                   int[] ticksLeft) {
    }

    private record PendingBattle(UUID playerId, UUID entityId, UUID lead, int[] ticksLeft) {
    }

    private static final class Tracked {
        final UUID entityId;
        int age = 0;
        boolean battled = false;

        Tracked(UUID entityId) {
            this.entityId = entityId;
        }
    }

    private static final class PlayerTrack {
        double lastX;
        double lastZ;
        boolean placed = false;
        double walked = 0;
        int cooldownUntil = 0;
        String arrivalArea = null;
        int arrivalTick = 0;
    }

    private static final List<PendingCutscene> cutscenes = new ArrayList<>();
    private static final List<PendingBattle> battles = new ArrayList<>();
    private static final List<Tracked> tracked = new ArrayList<>();
    private static final Map<UUID, PlayerTrack> players = new HashMap<>();
    private static final Set<UUID> bypass = new HashSet<>();

    private EncounterService() {
    }

    public static void reset() {
        cutscenes.clear();
        battles.clear();
        tracked.clear();
        players.clear();
        bypass.clear();
    }

    public static void forget(UUID playerId) {
        players.remove(playerId);
        cutscenes.removeIf(cutscene -> cutscene.playerId().equals(playerId));
        battles.removeIf(battle -> battle.playerId().equals(playerId));
    }

    public static void tick(MinecraftServer server) {
        advanceCutscenes(server);
        advanceBattles(server);
        if (server.getTicks() % TRACK_INTERVAL == 0) {
            advanceTracked(server);
        }
        WildState state = WildState.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                tickPlayer(server, state, player);
            } catch (Exception e) {
                AWildEncounter.LOGGER.warn("Encounter check failed for {}: {}", player.getName().getString(),
                        e.toString());
            }
        }
    }

    private static void tickPlayer(MinecraftServer server, WildState state, ServerPlayerEntity player) {
        EncounterConfig config = state.rulesFor(player.getServerWorld());
        PlayerTrack track = players.computeIfAbsent(player.getUuid(), id -> new PlayerTrack());

        double moved = 0;
        if (track.placed) {
            double dx = player.getX() - track.lastX;
            double dz = player.getZ() - track.lastZ;
            moved = Math.sqrt(dx * dx + dz * dz);
            if (moved > MAX_STEP) {
                moved = 0;
            }
        }
        track.lastX = player.getX();
        track.lastZ = player.getZ();
        track.placed = true;

        if (inBattle(player)) {
            track.cooldownUntil = Math.max(track.cooldownUntil, server.getTicks() + config.zoneCooldownSeconds * 20);
            track.walked = 0;
            return;
        }

        if (config.wildZones && moved > 0) {
            zoneStep(server, state, player, config, track, moved);
        }
        if (config.encounterEvents && server.getTicks() % 20 == 0) {
            biomeEventCheck(server, state, player, config, track);
        }
    }

    private static void zoneStep(MinecraftServer server, WildState state, ServerPlayerEntity player,
                                 EncounterConfig config, PlayerTrack track, double moved) {
        if (player.isSpectator() || player.getAbilities().flying || player.isFallFlying()) {
            return;
        }
        WildZone zone = state.zoneAt(Worlds.dimensionId(player.getServerWorld()), player.getBlockPos());
        if (zone == null) {
            track.walked = 0;
            return;
        }
        if (config.zoneGrassOnly && !onEncounterBlock(player, config)) {
            return;
        }
        track.walked += moved;
        int steps = (int) track.walked;
        if (steps <= 0) {
            return;
        }
        track.walked -= steps;
        if (server.getTicks() < track.cooldownUntil || hasEncounterUnderway(player.getUuid()) || !canBattle(player)
                || CobblelockeCompat.encounterUnderway(player)) {
            return;
        }
        int rate = Math.max(1, config.zoneEncounterRate);
        boolean encounter = false;
        for (int i = 0; i < steps && !encounter; i++) {
            encounter = RANDOM.nextInt(rate) == 0;
        }
        if (!encounter || (config.respectRepels && RepelCompat.isRepelled(player))) {
            return;
        }
        if (!SpeciesPicker.canPick(player, config, Worlds.biomeId(player), zone)) {
            return;
        }
        track.cooldownUntil = server.getTicks() + config.zoneCooldownSeconds * 20;
        if (config.chatMessages) {
            player.sendMessage(Lang.tr("encounter.zone", "§d★ Something stirs in %s§d!",
                    Lang.hl(zone.name, Formatting.YELLOW)), true);
        }
        begin(player, config, Kind.ZONE, null, null);
    }

    private static boolean onEncounterBlock(ServerPlayerEntity player, EncounterConfig config) {
        ServerWorld world = player.getServerWorld();
        BlockPos feet = player.getBlockPos();
        return isEncounterBlock(world.getBlockState(feet), config)
                || isEncounterBlock(world.getBlockState(feet.up()), config);
    }

    private static boolean isEncounterBlock(BlockState state, EncounterConfig config) {
        if (state.isAir() || config.encounterBlocks == null) {
            return false;
        }
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        for (String entry : config.encounterBlocks) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String wanted = entry.trim().toLowerCase(Locale.ROOT);
            if (wanted.startsWith("#")) {
                Identifier tag = Identifier.tryParse(wanted.substring(1));
                if (tag != null && state.isIn(TagKey.of(RegistryKeys.BLOCK, tag))) {
                    return true;
                }
            } else if (wanted.equals(id) || ("minecraft:" + wanted).equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static void onSpawn(SpawnEvent<?> event) {
        if (event.isCanceled()) {
            return;
        }
        try {
            ServerWorld world = event.getSpawnablePosition().getWorld();
            WildState state = WildState.get(world.getServer());
            EncounterConfig config = state.rulesFor(world);
            if (!config.wildZones || !config.blockNaturalSpawnsInZones) {
                return;
            }
            if (state.zoneAt(Worlds.dimensionId(world), event.getSpawnablePosition().getPosition()) != null) {
                event.cancel();
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.debug("Could not check a spawn against the Wild Zones: {}", e.toString());
        }
    }

    public static String areaAt(ServerPlayerEntity player, EncounterConfig config) {
        String dimension = Worlds.dimensionId(player.getServerWorld());
        if (config.eventRegion > 0) {
            int size = config.eventRegion;
            return dimension + "|region|" + size + "|" + Math.floorDiv(player.getBlockX(), size) + "|"
                    + Math.floorDiv(player.getBlockZ(), size);
        }
        String biome = Worlds.biomeId(player);
        return biome == null ? null : dimension + "|" + biome;
    }

    public static MutableText areaName(String area) {
        String[] parts = area.split("\\|");
        if (parts.length == 6 && "region".equals(parts[1])) {
            return Lang.tr("area.region", "Region (%1$s, %2$s)", parts[4], parts[5]);
        }
        return Lang.biome(parts.length > 1 ? parts[1] : area);
    }

    private static void biomeEventCheck(MinecraftServer server, WildState state, ServerPlayerEntity player,
                                        EncounterConfig config, PlayerTrack track) {
        String area = areaAt(player, config);
        if (area == null || state.hasFired(player.getUuid(), area)) {
            track.arrivalArea = null;
            return;
        }
        if (!area.equals(track.arrivalArea)) {
            track.arrivalArea = area;
            track.arrivalTick = server.getTicks();
            return;
        }
        if (server.getTicks() - track.arrivalTick < config.eventDwellSeconds * 20) {
            return;
        }
        if (hasEncounterUnderway(player.getUuid()) || !canBattle(player) || player.isSpectator()) {
            return;
        }
        if (CobblelockeCompat.encounterUnderway(player) || CobblelockeCompat.eventLockActive(player)) {
            return;
        }
        if (config.respectRepels && RepelCompat.isRepelled(player)) {
            return;
        }
        track.arrivalArea = null;
        if (!SpeciesPicker.canPick(player, config, Worlds.biomeId(player),
                state.zoneAt(Worlds.dimensionId(player.getServerWorld()), player.getBlockPos()))) {
            return;
        }
        state.markFired(player.getUuid(), area);
        if (config.chatMessages) {
            player.sendMessage(Lang.tr("encounter.biome", "§d★ A wild encounter blocks your path in %s§d!",
                    Lang.hl(areaName(area), Formatting.YELLOW)));
        }
        begin(player, config, Kind.BIOME_EVENT, null, null);
    }

    public static void onBattleStarting(BattleStartedEvent.Pre event) {
        try {
            PokemonBattle battle = event.getBattle();
            if (event.isCanceled() || !battle.isPvW() || battle.getPlayers().size() != 1) {
                return;
            }
            ServerPlayerEntity player = battle.getPlayers().get(0);
            EncounterConfig config = WildState.get(player.getServer()).rulesFor(player.getServerWorld());
            if (!config.everyWildBattle) {
                return;
            }
            PokemonEntity wild = null;
            UUID lead = null;
            for (BattleActor actor : battle.getActors()) {
                if (actor.getType() == ActorType.WILD) {
                    for (BattlePokemon pokemon : actor.getPokemonList()) {
                        if (wild != null || pokemon.getEntity() == null) {
                            return;
                        }
                        wild = pokemon.getEntity();
                    }
                } else if (actor.getType() == ActorType.PLAYER && lead == null && !actor.getPokemonList().isEmpty()) {
                    lead = actor.getPokemonList().get(0).getOriginalPokemon().getUuid();
                }
            }
            if (wild == null || bypass.remove(wild.getUuid()) || isEncounter(wild)
                    || CobblelockeCompat.isEventPokemon(wild)) {
                return;
            }
            if (hasEncounterUnderway(player.getUuid()) || !Networking.canPlayCutscene(player)) {
                return;
            }
            event.cancel();
            event.setReason(Lang.tr("encounter.appeared", "§d★ A wild %s§d appeared!",
                    Lang.hl(Lang.species(wild.getPokemon().getSpecies()), Formatting.AQUA)));
            begin(player, config, Kind.WILD_BATTLE, wild.getUuid(), lead);
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not hold a wild battle back for its animation: {}", e.toString());
        }
    }

    public static void begin(ServerPlayerEntity player, EncounterConfig config, Kind kind, UUID entityId, UUID lead) {
        String clip = EncounterAnimations.choose(player, config);
        boolean playing = Networking.sendCutscene(player, clip);
        int coverAtMs = EncounterAnimations.coverAtMs(clip);
        int deadlineMs = playing ? coverAtMs + CLIENT_REPORT_GRACE_MS : Math.min(coverAtMs, NO_ANIMATION_WAIT_MS);
        cutscenes.add(new PendingCutscene(player.getUuid(), kind, clip, entityId, lead,
                new int[]{Math.max(1, deadlineMs / 50)}));
    }

    public static void onScreenCovered(ServerPlayerEntity player, String clipId) {
        if (player == null || clipId == null) {
            return;
        }
        Iterator<PendingCutscene> iterator = cutscenes.iterator();
        while (iterator.hasNext()) {
            PendingCutscene cutscene = iterator.next();
            if (!cutscene.playerId().equals(player.getUuid()) || !clipId.equals(cutscene.clipId())) {
                continue;
            }
            iterator.remove();
            covered(player, cutscene);
            return;
        }
    }

    private static void advanceCutscenes(MinecraftServer server) {
        if (cutscenes.isEmpty()) {
            return;
        }
        List<PendingCutscene> due = new ArrayList<>();
        Iterator<PendingCutscene> iterator = cutscenes.iterator();
        while (iterator.hasNext()) {
            PendingCutscene cutscene = iterator.next();
            if (--cutscene.ticksLeft()[0] <= 0) {
                iterator.remove();
                due.add(cutscene);
            }
        }
        for (PendingCutscene cutscene : due) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(cutscene.playerId());
            if (player != null) {
                covered(player, cutscene);
            }
        }
    }

    private static void covered(ServerPlayerEntity player, PendingCutscene cutscene) {
        int delay = battleDelayTicks(cutscene.clipId());
        try {
            if (cutscene.kind() == Kind.WILD_BATTLE) {
                battles.add(new PendingBattle(player.getUuid(), cutscene.entityId(), cutscene.lead(), new int[]{delay}));
            } else {
                spawnEncounter(player, delay);
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.error("Could not start the encounter for {}: {}", player.getName().getString(),
                    e.toString());
        }
    }

    private static int battleDelayTicks(String clipId) {
        int window = EncounterAnimations.coverUntilMs(clipId) - EncounterAnimations.coverAtMs(clipId);
        return Math.max(MIN_BATTLE_DELAY_TICKS, Math.min(BATTLE_START_DELAY_TICKS, window / 50));
    }

    private static void spawnEncounter(ServerPlayerEntity player, int battleDelayTicks) {
        WildState state = WildState.get(player.getServer());
        EncounterConfig config = state.rulesFor(player.getServerWorld());
        String biomeId = Worlds.biomeId(player);
        WildZone zone = state.zoneAt(Worlds.dimensionId(player.getServerWorld()), player.getBlockPos());
        SpeciesPicker.Pick pick = SpeciesPicker.pick(player, config, biomeId, zone, RANDOM);
        if (pick == null) {
            AWildEncounter.LOGGER.warn("No species is allowed for an encounter at {} in {}", player.getBlockPos(),
                    biomeId);
            return;
        }
        Pokemon pokemon = createPokemon(player, config, pick);
        NbtCompound data = pokemon.getPersistentData();
        data.putBoolean(KEY_ENCOUNTER, true);

        ServerWorld world = player.getServerWorld();
        PokemonEntity entity = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
        Vec3d spot = spawnSpot(player);
        entity.refreshPositionAndAngles(spot.x, spot.y, spot.z, player.getYaw() + 180.0f, 0.0f);
        if (!world.spawnEntity(entity)) {
            AWildEncounter.LOGGER.warn("Could not spawn the encounter Cobblemon");
            return;
        }
        tracked.add(new Tracked(entity.getUuid()));
        if (config.chatMessages) {
            MutableText name = Lang.hl(Lang.species(pick.species()), Formatting.AQUA);
            player.sendMessage(pokemon.getShiny()
                    ? Lang.tr("encounter.appeared_shiny", "§d★ A wild §6shiny§d %s§d appeared!", name)
                    : Lang.tr("encounter.appeared", "§d★ A wild %s§d appeared!", name));
        }
        if (config.autoStartBattle) {
            battles.add(new PendingBattle(player.getUuid(), entity.getUuid(), null,
                    new int[]{Math.max(MIN_BATTLE_DELAY_TICKS, battleDelayTicks)}));
        } else {
            bypass.add(entity.getUuid());
        }
    }

    public static Pokemon createPokemon(ServerPlayerEntity player, EncounterConfig config, SpeciesPicker.Pick pick) {
        PokemonProperties properties = pick.properties().copy();
        properties.setLevel(LevelRules.level(config, player, pick.species(), pick.levelRange(), RANDOM));
        boolean ownForm = pick.form() != null && !SpeciesEntry.isStandard(pick.species(), pick.form());
        if (ownForm) {
            Set<String> aspects = new HashSet<>(properties.getAspects());
            aspects.addAll(pick.form().getAspects());
            properties.setAspects(aspects);
        }
        Pokemon pokemon = properties.create();
        if (ownForm && !pokemon.getForm().getName().equalsIgnoreCase(pick.form().getName())) {
            pokemon.setForm(pick.form());
        }
        if (properties.getShiny() == null) {
            pokemon.setShiny(rollShiny(config));
        }
        pokemon.heal();
        return pokemon;
    }

    private static boolean rollShiny(EncounterConfig config) {
        if (config.shinyRate > 0) {
            return RANDOM.nextInt(config.shinyRate) == 0;
        }
        try {
            float rate = Cobblemon.INSTANCE.getConfig().getShinyRate();
            return rate <= 1 || RANDOM.nextFloat() * rate < 1.0f;
        } catch (Exception e) {
            return false;
        }
    }

    private static void advanceBattles(MinecraftServer server) {
        if (battles.isEmpty()) {
            return;
        }
        List<PendingBattle> due = new ArrayList<>();
        Iterator<PendingBattle> iterator = battles.iterator();
        while (iterator.hasNext()) {
            PendingBattle battle = iterator.next();
            if (--battle.ticksLeft()[0] <= 0) {
                iterator.remove();
                due.add(battle);
            }
        }
        for (PendingBattle battle : due) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(battle.playerId());
            if (player != null) {
                startBattle(player, battle);
            }
        }
    }

    private static void startBattle(ServerPlayerEntity player, PendingBattle battle) {
        Entity entity = player.getServerWorld().getEntity(battle.entityId());
        if (!(entity instanceof PokemonEntity pokemonEntity) || !pokemonEntity.isAlive()) {
            return;
        }
        if (pokemonEntity.isBattling() || inBattle(player)) {
            return;
        }
        UUID lead = LeadSelection.usable(player, battle.lead());
        if (lead == null) {
            lead = LeadSelection.lead(player);
        }
        bypass.add(pokemonEntity.getUuid());
        try {
            BattleStartResult result = BattleBuilder.INSTANCE.pve(player, pokemonEntity, lead);
            if (result instanceof ErroredBattleStart) {
                bypass.remove(pokemonEntity.getUuid());
            }
        } catch (Exception e) {
            bypass.remove(pokemonEntity.getUuid());
            AWildEncounter.LOGGER.error("Could not start the encounter battle for {}: {}",
                    player.getName().getString(), e.toString());
        }
    }

    private static void advanceTracked(MinecraftServer server) {
        if (tracked.isEmpty()) {
            return;
        }
        Iterator<Tracked> iterator = tracked.iterator();
        while (iterator.hasNext()) {
            Tracked entry = iterator.next();
            entry.age += TRACK_INTERVAL;
            Entity entity = findEntity(server, entry.entityId);
            if (!(entity instanceof PokemonEntity pokemonEntity) || !pokemonEntity.isAlive()) {
                iterator.remove();
                bypass.remove(entry.entityId);
                continue;
            }
            if (!pokemonEntity.getPokemon().isWild() || pokemonEntity.getPokemon().getCurrentHealth() <= 0
                    || pokemonEntity.isDead()) {
                iterator.remove();
                bypass.remove(entry.entityId);
                continue;
            }
            if (pokemonEntity.isBattling()) {
                entry.battled = true;
                continue;
            }
            boolean waitingForBattle = battles.stream().anyMatch(battle -> battle.entityId().equals(entry.entityId));
            if (waitingForBattle || (!entry.battled && entry.age < UNBATTLED_TIMEOUT_TICKS)) {
                continue;
            }
            iterator.remove();
            bypass.remove(entry.entityId);
            EncounterConfig config = WildState.get(server).rulesFor(pokemonEntity.getWorld());
            if (config.removeAfterBattle && pokemonEntity.getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ParticleTypes.POOF, pokemonEntity.getX(), pokemonEntity.getY() + 0.5,
                        pokemonEntity.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
                pokemonEntity.discard();
            }
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    public static boolean isEncounter(PokemonEntity entity) {
        try {
            return entity.getPokemon().getPersistentData().getBoolean(KEY_ENCOUNTER);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasEncounterUnderway(UUID playerId) {
        return cutscenes.stream().anyMatch(cutscene -> cutscene.playerId().equals(playerId))
                || battles.stream().anyMatch(battle -> battle.playerId().equals(playerId));
    }

    private static boolean inBattle(ServerPlayerEntity player) {
        try {
            return BattleRegistry.getBattleByParticipatingPlayer(player) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean canBattle(ServerPlayerEntity player) {
        try {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                if (pokemon.getCurrentHealth() > 0) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Vec3d spawnSpot(ServerPlayerEntity player) {
        Vec3d facing = player.getRotationVec(1.0f).multiply(3.0);
        Vec3d target = player.getPos().add(facing.x, 0.0, facing.z);
        ServerWorld world = player.getServerWorld();
        BlockPos pos = BlockPos.ofFloored(target);

        BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos);
        if (Math.abs(ground.getY() - player.getBlockY()) > 6) {
            return player.getPos().add(facing.x, 0.0, facing.z);
        }
        return new Vec3d(target.x, ground.getY(), target.z);
    }
}
