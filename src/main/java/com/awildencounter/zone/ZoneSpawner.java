package com.awildencounter.zone;

import com.awildencounter.AWildEncounter;
import com.awildencounter.compat.RepelCompat;
import com.awildencounter.config.EncounterConfig;
import com.awildencounter.data.WildState;
import com.awildencounter.encounter.EncounterService;
import com.awildencounter.encounter.SpeciesPicker;
import com.awildencounter.util.Worlds;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ZoneSpawner {
    public static final String KEY_ZONE = "awildencounter_zone";
    private static final String KEY_BORN = "awildencounter_zone_born";

    private static final int CHECK_INTERVAL = 20;
    private static final double ACTIVE_RANGE = 64.0;
    private static final double MIN_PLAYER_DISTANCE = 3.0;
    private static final double DESPAWN_PLAYER_RANGE = 24.0;
    private static final long LIFETIME_TICKS = 5 * 60 * 20;
    private static final int POSITION_ATTEMPTS = 12;
    private static final Random RANDOM = new Random();

    private static final Map<String, Long> nextSpawn = new HashMap<>();
    private static int tickCounter = 0;

    private ZoneSpawner() {
    }

    public static void reset() {
        nextSpawn.clear();
        tickCounter = 0;
    }

    public static void tick(MinecraftServer server) {
        if (++tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        WildState state = WildState.get(server);
        long now = server.getTicks();
        for (WildZone zone : List.copyOf(state.zones())) {
            if (!zone.spawning) {
                continue;
            }
            try {
                tickZone(server, state, zone, now);
            } catch (Exception e) {
                AWildEncounter.LOGGER.debug("Wild Zone {} could not spawn: {}", zone.name, e.toString());
            }
        }
    }

    private static void tickZone(MinecraftServer server, WildState state, WildZone zone, long now) {
        ServerWorld world = ZoneService.worldOf(server, zone);
        if (world == null) {
            return;
        }
        ServerPlayerEntity player = nearestPlayer(world, zone);
        List<PokemonEntity> spawned = spawnedBy(world, zone);
        long time = world.getTime();
        for (PokemonEntity entity : spawned) {
            despawnIfStale(world, entity, time);
        }
        if (player == null || now < nextSpawn.getOrDefault(zone.id, 0L)) {
            return;
        }
        nextSpawn.put(zone.id, now + zone.spawnCooldown * 20L);
        long alive = spawned.stream().filter(Entity::isAlive).count();
        if (alive >= zone.spawnCap) {
            return;
        }
        EncounterConfig config = state.rulesFor(world);
        BlockPos pos = findSpot(world, zone, player);
        if (pos == null || (config.respectRepels && RepelCompat.repelBlockNearby(world, pos))) {
            return;
        }
        SpeciesPicker.Pick pick = SpeciesPicker.pickAt(player, world, pos, config, Worlds.biomeId(world, pos), zone,
                RANDOM);
        if (pick == null) {
            return;
        }
        Pokemon pokemon = EncounterService.createPokemon(player, config, pick);
        NbtCompound data = pokemon.getPersistentData();
        data.putString(KEY_ZONE, zone.id);
        data.putLong(KEY_BORN, time);
        PokemonEntity entity = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RANDOM.nextFloat() * 360f,
                0.0f);
        world.spawnEntity(entity);
    }

    private static List<PokemonEntity> spawnedBy(ServerWorld world, WildZone zone) {
        Box box = new Box(zone.minX - 16, zone.minY - 16, zone.minZ - 16, zone.maxX + 17, zone.maxY + 17,
                zone.maxZ + 17);
        return world.getEntitiesByClass(PokemonEntity.class, box, entity -> fromZone(entity, zone.id));
    }

    private static boolean fromZone(PokemonEntity entity, String zoneId) {
        try {
            Pokemon pokemon = entity.getPokemon();
            return pokemon.isWild() && entity.getOwnerUuid() == null
                    && zoneId.equals(pokemon.getPersistentData().getString(KEY_ZONE));
        } catch (Exception e) {
            return false;
        }
    }

    private static void despawnIfStale(ServerWorld world, PokemonEntity entity, long time) {
        long born = entity.getPokemon().getPersistentData().getLong(KEY_BORN);
        if (time - born < LIFETIME_TICKS || entity.isBattling() || entity.getPokemon().getCurrentHealth() <= 0) {
            return;
        }
        if (world.getClosestPlayer(entity, DESPAWN_PLAYER_RANGE) == null) {
            entity.discard();
        }
    }

    private static ServerPlayerEntity nearestPlayer(ServerWorld world, WildZone zone) {
        double cx = (zone.minX + zone.maxX + 1) / 2.0;
        double cz = (zone.minZ + zone.maxZ + 1) / 2.0;
        double reachX = zone.width() / 2.0 + ACTIVE_RANGE;
        double reachZ = zone.length() / 2.0 + ACTIVE_RANGE;
        ServerPlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayerEntity player : world.getPlayers()) {
            double dx = Math.abs(player.getX() - cx);
            double dz = Math.abs(player.getZ() - cz);
            if (player.isSpectator() || dx > reachX || dz > reachZ) {
                continue;
            }
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static BlockPos findSpot(ServerWorld world, WildZone zone, ServerPlayerEntity player) {
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            int x = zone.minX + RANDOM.nextInt(zone.width());
            int z = zone.minZ + RANDOM.nextInt(zone.length());
            if (!world.isChunkLoaded(new BlockPos(x, zone.minY, z))) {
                continue;
            }
            for (int y = zone.maxY + 1; y >= zone.minY; y--) {
                BlockPos feet = new BlockPos(x, y, z);
                if (canStand(world, feet)
                        && player.squaredDistanceTo(x + 0.5, y, z + 0.5) > MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE) {
                    return feet;
                }
            }
        }
        return null;
    }

    private static boolean canStand(ServerWorld world, BlockPos feet) {
        BlockPos below = feet.down();
        BlockState ground = world.getBlockState(below);
        if (!ground.isSideSolidFullSquare(world, below, Direction.UP)) {
            return false;
        }
        for (BlockPos pos : new BlockPos[]{feet, feet.up()}) {
            BlockState state = world.getBlockState(pos);
            if (!state.getCollisionShape(world, pos).isEmpty() || state.getFluidState().isIn(FluidTags.LAVA)) {
                return false;
            }
        }
        return true;
    }

    public static void clear(MinecraftServer server, WildZone zone) {
        nextSpawn.remove(zone.id);
        ServerWorld world = ZoneService.worldOf(server, zone);
        if (world == null) {
            return;
        }
        for (PokemonEntity entity : spawnedBy(world, zone)) {
            if (!entity.isBattling()) {
                entity.discard();
            }
        }
    }
}
