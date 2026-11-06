package com.awildencounter.client;

import com.awildencounter.client.cutscene.CutscenePlayer;
import com.awildencounter.client.gui.DimensionScope;
import com.awildencounter.client.gui.WildConfigScreen;
import com.awildencounter.client.zone.ClientZones;
import com.awildencounter.client.zone.ZoneBannerHud;
import com.awildencounter.client.zone.ZoneRenderer;
import com.awildencounter.client.zone.ZoneTool;
import com.awildencounter.net.CutscenePayload;
import com.awildencounter.net.OpenConfigPayload;
import com.awildencounter.net.SelectedPokemonPayload;
import com.awildencounter.net.ZonePreviewPayload;
import com.awildencounter.net.ZonesPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;

public class AWildEncounterClient implements ClientModInitializer {
    private static String lastSelected = null;

    private static void sendSelectedPokemon(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            lastSelected = null;
            return;
        }
        if (client.player.age % 5 != 0) {
            return;
        }
        String selected = "";
        try {
            var storage = com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getStorage();
            var pokemon = storage.getParty().get(storage.getSelectedSlot());
            if (pokemon != null) {
                selected = pokemon.getUuid().toString();
            }
        } catch (Exception ignored) {
        }
        if (selected.equals(lastSelected) || !ClientPlayNetworking.canSend(SelectedPokemonPayload.ID)) {
            return;
        }
        lastSelected = selected;
        ClientPlayNetworking.send(new SelectedPokemonPayload(selected));
    }

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenConfigPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    var parsed = DimensionScope.parse(payload.scopesJson());
                    MinecraftClient.getInstance().setScreen(new WildConfigScreen(parsed.scopes(), parsed.selected(),
                            payload.presetsJson(), payload.canEdit(), payload.repelInstalled()));
                }));

        ClientPlayNetworking.registerGlobalReceiver(CutscenePayload.ID, (payload, context) ->
                context.client().execute(() -> CutscenePlayer.play(payload.clipId(), true)));

        ClientPlayNetworking.registerGlobalReceiver(ZonesPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientZones.set(payload.zonesJson())));

        ClientPlayNetworking.registerGlobalReceiver(com.awildencounter.net.ZoneOddsPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientZones.setOdds(payload.zoneId(), payload.oddsJson())));
        ClientPlayNetworking.registerGlobalReceiver(com.awildencounter.net.ZoneBannerPayload.ID, (payload, context) ->
                context.client().execute(() -> ZoneBannerHud.show(payload.bannerJson())));
        ClientPlayNetworking.registerGlobalReceiver(ZonePreviewPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientZones.setSpawns(payload.zoneId(), payload.entriesJson(),
                        payload.total())));

        ZoneTool.register();

        ClientPlayNetworking.registerGlobalReceiver(com.awildencounter.net.ZoneCornerPayload.ID, (payload, context) ->
                context.client().execute(() -> ZoneTool.setCornerFromCommand(payload.first(), payload.pos())));
        ClientPlayNetworking.registerGlobalReceiver(com.awildencounter.net.ZoneEditPayload.ID, (payload, context) ->
                context.client().execute(() -> ZoneTool.setZoneEdit(payload.on())));

        ClientTickEvents.START_CLIENT_TICK.register(ZoneTool::beforeInput);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            sendSelectedPokemon(client);
            ZoneTool.tick(client);
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            ZoneBannerHud.render(drawContext);
            CutscenePlayer.renderHud(drawContext);
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterRender(screen).register((current, drawContext, mouseX, mouseY, delta) ->
                        CutscenePlayer.renderOverScreen(drawContext)));

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ZoneRenderer::render);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> {
                    CutscenePlayer.stop();
                    ClientZones.clear();
                    ZoneBannerHud.clear();
                    ZoneTool.reset();
                    lastSelected = null;
                }));
    }
}
