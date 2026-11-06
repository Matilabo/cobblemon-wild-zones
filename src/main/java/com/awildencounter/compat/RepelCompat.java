package com.awildencounter.compat;

import com.awildencounter.AWildEncounter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

import java.lang.reflect.Method;
import java.util.Locale;

public final class RepelCompat {
    public static final String REPEL_MOD = "cobblemonrepel";
    private static final String REPEL_CLASS = "deuli.cobblemonrepel.CobblemonRepel";
    public static final String REPEL_TAG = "repel";

    private static boolean looked = false;
    private static Method isRepelNearby = null;
    private static GameRules.Key<GameRules.IntRule> repelRange = null;

    private RepelCompat() {
    }

    public static boolean repelModInstalled() {
        return FabricLoader.getInstance().isModLoaded(REPEL_MOD);
    }

    public static boolean isRepelled(ServerPlayerEntity player) {
        if (player.getCommandTags().contains(REPEL_TAG)) {
            return true;
        }
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            if (isRepelEffect(effect.getEffectType())) {
                return true;
            }
        }
        return repelBlockNearby(player.getServerWorld(), player.getBlockPos());
    }

    private static boolean isRepelEffect(RegistryEntry<StatusEffect> effect) {
        return effect.getKey()
                .map(key -> key.getValue().getPath().toLowerCase(Locale.ROOT).endsWith("repel"))
                .orElse(false);
    }

    @SuppressWarnings("unchecked")
    public static boolean repelBlockNearby(ServerWorld world, BlockPos pos) {
        if (!repelModInstalled()) {
            return false;
        }
        if (!looked) {
            looked = true;
            try {
                Class<?> repel = Class.forName(REPEL_CLASS);
                isRepelNearby = repel.getMethod("isRepelNearby", ServerWorld.class, BlockPos.class);
                repelRange = (GameRules.Key<GameRules.IntRule>) repel.getField("REPEL_RANGE").get(null);
            } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
                AWildEncounter.LOGGER.warn("Cobblemon Repel is installed but could not be read, so its repels "
                        + "do not stop encounters: {}", e.toString());
            }
        }
        if (isRepelNearby == null) {
            return false;
        }
        try {
            if (repelRange != null && world.getGameRules().getInt(repelRange) == 0) {
                return false;
            }
            return (boolean) isRepelNearby.invoke(null, world, pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            AWildEncounter.LOGGER.debug("Could not check for repels: {}", e.toString());
            return false;
        }
    }
}
