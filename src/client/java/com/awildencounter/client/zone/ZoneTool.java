package com.awildencounter.client.zone;

import com.awildencounter.client.gui.ZoneScreen;
import com.awildencounter.net.CreateZonePayload;
import com.awildencounter.zone.WildZone;
import com.awildencounter.zone.ZoneService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.InputUtil;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

public final class ZoneTool {
    public static final double REACH = 64.0;
    private static final int DOUBLE_CROUCH_TICKS = 12;

    private static BlockPos first = null;
    private static BlockPos second = null;
    private static String dimension = null;

    private static boolean zoneEdit = false;
    private static boolean commandSelection = false;

    private static boolean useHeld = false;
    private static boolean sneakDown = false;
    private static int lastCrouchTick = -100;
    private static int ticks = 0;

    private ZoneTool() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient() || !isActive(player) || !player.getStackInHand(hand).isOf(Items.BONE)) {
                return ActionResult.PASS;
            }
            use();
            return ActionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient() || !isActive(player) || !stack.isOf(Items.BONE)) {
                return TypedActionResult.pass(stack);
            }
            use();
            return TypedActionResult.fail(stack);
        });
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (!isActive(player) || !player.getMainHandStack().isOf(Items.BONE) || client.currentScreen != null) {
                return false;
            }
            if (clickCount > 0) {
                setCorner(false, target(client, player), false);
            }
            return true;
        });
    }

    public static boolean isActive(PlayerEntity player) {
        return player != null && player.hasPermissionLevel(ZoneService.EDIT_PERMISSION) && holdsBone(player);
    }

    public static boolean showing(PlayerEntity player) {
        return player != null && player.hasPermissionLevel(ZoneService.EDIT_PERMISSION)
                && (holdsBone(player) || zoneEdit || (commandSelection && (first != null || second != null)));
    }

    public static void setZoneEdit(boolean on) {
        zoneEdit = on;
    }

    public static void setCornerFromCommand(boolean isFirst, BlockPos pos) {
        commandSelection = true;
        setCorner(isFirst, pos, true);
    }

    public static void reset() {
        clear();
        zoneEdit = false;
    }

    public static void beforeInput(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (!zoneEdit || player == null || client.currentScreen != null || holdsBone(player) || player.isSneaking()
                || !player.hasPermissionLevel(ZoneService.EDIT_PERMISSION)) {
            return;
        }
        WildZone zone = zoneLookedThrough(client, player);
        if (zone == null) {
            return;
        }
        boolean pressed = false;
        while (client.options.useKey.wasPressed()) {
            pressed = true;
        }
        if (pressed) {
            client.setScreen(new ZoneScreen(null, zone));
        }
    }

    public static boolean holdsBone(PlayerEntity player) {
        return player.getMainHandStack().isOf(Items.BONE) || player.getOffHandStack().isOf(Items.BONE);
    }

    public static BlockPos first() {
        return first;
    }

    public static BlockPos second() {
        return second;
    }

    public static String dimension() {
        return dimension;
    }

    public static void clear() {
        first = null;
        second = null;
        dimension = null;
        commandSelection = false;
    }

    public static void tick(MinecraftClient client) {
        ticks++;
        ClientPlayerEntity player = client.player;
        if (player == null) {
            clear();
            return;
        }
        if (!client.options.useKey.isPressed()) {
            useHeld = false;
        }
        if (dimension != null && !dimension.equals(currentDimension(client))) {
            clear();
        }
        boolean down = sneakKeyDown(client);
        boolean tapped = down && !sneakDown;
        sneakDown = down;
        if (tapped && client.currentScreen == null && showing(player)) {
            if (ticks - lastCrouchTick <= DOUBLE_CROUCH_TICKS && first != null && second != null) {
                lastCrouchTick = -100;
                createZone(client);
            } else {
                lastCrouchTick = ticks;
            }
        }
    }

    private static boolean sneakKeyDown(MinecraftClient client) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(client.options.sneakKey);
        long window = client.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
        }
        return key.getCode() != InputUtil.UNKNOWN_KEY.getCode() && InputUtil.isKeyPressed(window, key.getCode());
    }

    private static void use() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || useHeld || client.currentScreen != null) {
            return;
        }
        useHeld = true;
        BlockPos target = target(client, player);
        if (!player.isSneaking()) {
            WildZone zone = zoneLookedThrough(client, player);
            if (zone != null) {
                client.setScreen(new ZoneScreen(null, zone));
                return;
            }
        }
        setCorner(true, target, false);
    }

    private static WildZone zoneLookedThrough(MinecraftClient client, ClientPlayerEntity player) {
        float delta = client.getRenderTickCounter().getTickDelta(true);
        Vec3d eye = player.getCameraPosVec(delta);
        Vec3d look = player.getRotationVec(delta);
        HitResult hit = player.raycast(REACH, delta, false);
        double distance = hit.getType() == HitResult.Type.MISS ? REACH : hit.getPos().distanceTo(eye) + 0.01;
        return ClientZones.zoneAlong(currentDimension(client), eye, look, distance);
    }

    private static BlockPos target(MinecraftClient client, ClientPlayerEntity player) {
        HitResult hit = player.raycast(REACH, client.getRenderTickCounter().getTickDelta(true), false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return player.getBlockPos();
    }

    private static String currentDimension(MinecraftClient client) {
        return client.world == null ? null : client.world.getRegistryKey().getValue().toString();
    }

    private static void setCorner(boolean isFirst, BlockPos pos, boolean fromCommand) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        String here = currentDimension(client);
        if (dimension == null || !dimension.equals(here)) {
            first = null;
            second = null;
            dimension = here;
        }
        lastCrouchTick = -100;
        if (isFirst) {
            first = pos.toImmutable();
        } else {
            second = pos.toImmutable();
        }
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.6f, isFirst ? 1.2f : 1.5f);
        Text message;
        if (first != null && second != null) {
            WildZone selection = WildZone.between(here, first, second);
            message = Text.translatableWithFallback("awildencounter.tool.selected",
                    "Selected %s blocks. Crouch twice to make it a Wild Zone.", selection.size())
                    .formatted(Formatting.GREEN);
        } else if (fromCommand) {
            String corner = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            message = isFirst
                    ? Text.translatableWithFallback("awildencounter.tool.first_command",
                    "Corner 1 set at %s. /awildencounter pos2 sets corner 2.", corner).formatted(Formatting.YELLOW)
                    : Text.translatableWithFallback("awildencounter.tool.second_command",
                    "Corner 2 set at %s. /awildencounter pos1 sets corner 1.", corner).formatted(Formatting.YELLOW);
        } else {
            String corner = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            message = isFirst
                    ? Text.translatableWithFallback("awildencounter.tool.first",
                    "Corner 1 set at %s. Left click sets corner 2.", corner).formatted(Formatting.YELLOW)
                    : Text.translatableWithFallback("awildencounter.tool.second",
                    "Corner 2 set at %s. Right click sets corner 1.", corner).formatted(Formatting.YELLOW);
        }
        player.sendMessage(message, !fromCommand);
    }

    private static void createZone(MinecraftClient client) {
        if (!ClientPlayNetworking.canSend(CreateZonePayload.ID) || client.player == null) {
            return;
        }
        ClientPlayNetworking.send(new CreateZonePayload(first, second));
        client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.7f, 1.4f);
        clear();
    }
}
