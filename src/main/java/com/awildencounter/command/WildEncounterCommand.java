package com.awildencounter.command;

import com.awildencounter.config.DimensionNames;
import com.awildencounter.config.EncounterConfig;
import com.awildencounter.data.WildState;
import com.awildencounter.encounter.EncounterService;
import com.awildencounter.net.Networking;
import com.awildencounter.util.Lang;
import com.awildencounter.zone.WildZone;
import com.awildencounter.zone.ZoneService;
import com.awildencounter.net.ZoneCornerPayload;
import com.awildencounter.net.ZoneEditPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.util.math.BlockPos;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class WildEncounterCommand {
    private WildEncounterCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> root = dispatcher.register(CommandManager.literal("wildencounter")
                .then(corner("pos1", true))
                .then(corner("pos2", false))
                .then(CommandManager.literal("zonedit")
                        .requires(source -> source.hasPermissionLevel(ZoneService.EDIT_PERMISSION))
                        .executes(context -> zoneEdit(context, null))
                        .then(CommandManager.literal("on").executes(context -> zoneEdit(context, true)))
                        .then(CommandManager.literal("off").executes(context -> zoneEdit(context, false))))
                .then(CommandManager.literal("config")
                        .executes(context -> {
                            Networking.sendOpenConfig(context.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                .then(CommandManager.literal("zones")
                        .requires(source -> source.hasPermissionLevel(ZoneService.EDIT_PERMISSION))
                        .executes(WildEncounterCommand::listZones))
                .then(CommandManager.literal("zone")
                        .requires(source -> source.hasPermissionLevel(ZoneService.EDIT_PERMISSION))
                        .then(CommandManager.literal("delete")
                                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                                WildState.get(context.getSource().getServer()).zones().stream()
                                                        .map(zone -> zone.name), builder))
                                        .executes(WildEncounterCommand::deleteZone))))
                .then(CommandManager.literal("test")
                        .requires(source -> source.hasPermissionLevel(ZoneService.EDIT_PERMISSION))
                        .executes(WildEncounterCommand::test))
                .then(CommandManager.literal("reset")
                        .requires(source -> source.hasPermissionLevel(ZoneService.EDIT_PERMISSION))
                        .then(CommandManager.literal("events")
                                .executes(context -> resetEvents(context, null))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(context -> resetEvents(context,
                                                EntityArgumentType.getPlayer(context, "player")))))));
        dispatcher.register(CommandManager.literal("awildencounter").redirect(root));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> corner(String name, boolean first) {
        return CommandManager.literal(name)
                .requires(source -> source.hasPermissionLevel(ZoneService.EDIT_PERMISSION))
                .executes(context -> setCorner(context, first, null))
                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .executes(context -> setCorner(context, first,
                                BlockPosArgumentType.getBlockPos(context, "pos"))));
    }

    private static int setCorner(CommandContext<ServerCommandSource> context, boolean first, BlockPos pos)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!ServerPlayNetworking.canSend(player, ZoneCornerPayload.ID)) {
            context.getSource().sendError(noClient());
            return 0;
        }
        BlockPos corner = pos != null ? pos : player.getBlockPos();
        ServerPlayNetworking.send(player, new ZoneCornerPayload(first, corner));
        return 1;
    }

    private static int zoneEdit(CommandContext<ServerCommandSource> context, Boolean wanted)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!ServerPlayNetworking.canSend(player, ZoneEditPayload.ID)) {
            context.getSource().sendError(noClient());
            return 0;
        }
        boolean on = wanted != null ? wanted : !ZoneService.inZoneEdit(player);
        ZoneService.setZoneEdit(player, on);
        ServerPlayNetworking.send(player, new ZoneEditPayload(on));
        context.getSource().sendFeedback(() -> on
                ? Lang.tr("command.zonedit.on", "§aZone edit mode on. §7The zones show, and right click opens the "
                + "menu of the zone you look at, without a Bone. Use /awildencounter pos1 and pos2 to select, then "
                + "crouch twice to make a zone. /awildencounter zonedit again turns it off.")
                : Lang.tr("command.zonedit.off", "§7Zone edit mode off."), false);
        return 1;
    }

    private static Text noClient() {
        return Lang.tr("command.no_client", "A Wild Cobblemon Appeared! is not installed on your client, so this "
                + "command cannot work.");
    }

    private static int listZones(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        List<WildZone> zones = WildState.get(source.getServer()).zones();
        if (zones.isEmpty()) {
            source.sendFeedback(() -> Lang.tr("command.zones.none", "§7There are no Wild Zones yet. Hold a Bone: "
                    + "right click sets one corner, left click the other, and crouching twice makes the zone."),
                    false);
            return 0;
        }
        source.sendFeedback(() -> Lang.tr("command.zones.header", "§6Wild Zones (%s):", zones.size()), false);
        for (WildZone zone : zones) {
            int x = (zone.minX + zone.maxX) / 2;
            int z = (zone.minZ + zone.maxZ) / 2;
            String teleport = "/execute in " + zone.dimension + " run tp @s " + x + " " + (zone.maxY + 1) + " " + z;
            MutableText line = Text.literal(" - ").formatted(Formatting.GOLD)
                    .append(Text.literal(zone.name).styled(style -> style.withColor(zone.color)))
                    .append(Text.literal(" " + DimensionNames.defaultName(zone.dimension) + " " + zone.corners()
                            + " (" + zone.size() + ") ").formatted(Formatting.GRAY))
                    .append(Lang.tr("command.zones.tp", "[teleport]").styled(style -> style
                            .withColor(Formatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, teleport))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(teleport)))));
            source.sendFeedback(() -> line, false);
        }
        return zones.size();
    }

    private static int deleteZone(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String name = StringArgumentType.getString(context, "name").strip();
        for (WildZone zone : WildState.get(player.getServer()).zones()) {
            if (zone.name.equalsIgnoreCase(name) || zone.id.equals(name)) {
                ZoneService.delete(player, zone.id);
                return 1;
            }
        }
        context.getSource().sendError(Lang.tr("command.zone.unknown", "There is no Wild Zone called %s.", name));
        return 0;
    }

    private static int test(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (EncounterService.hasEncounterUnderway(player.getUuid())) {
            context.getSource().sendError(Lang.tr("command.test.busy", "An encounter is already starting."));
            return 0;
        }
        EncounterConfig config = WildState.get(player.getServer()).rulesFor(player.getServerWorld());
        EncounterService.begin(player, config, EncounterService.Kind.TEST, null, null);
        return 1;
    }

    private static int resetEvents(CommandContext<ServerCommandSource> context, ServerPlayerEntity target) {
        int count = WildState.get(context.getSource().getServer()).resetEvents(target == null ? null : target.getUuid());
        context.getSource().sendFeedback(() -> target == null
                ? Lang.tr("command.reset.all", "§aEncounter Events start over for everyone (%s cleared).", count)
                : Lang.tr("command.reset.player", "§aEncounter Events start over for %1$s (%2$s cleared).",
                        target.getName(), count), true);
        return 1;
    }
}
