package com.awildencounter.client.gui;

import com.awildencounter.AWildEncounter;
import com.awildencounter.encounter.SpeciesEntry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SpeciesIcons {
    public static final int SIZE = 64;

    private static Map<String, String> index = null;
    private static final Set<Identifier> smoothed = new HashSet<>();

    private SpeciesIcons() {
    }

    public static Map<String, String> index() {
        if (index == null) {
            index = load();
        }
        return index;
    }

    private static Map<String, String> load() {
        Map<String, String> loaded = new HashMap<>();
        try {
            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager()
                    .getResource(AWildEncounter.id("icons/index.json"));
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().getInputStream()) {
                    JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        loaded.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read the Cobblemon icons: {}", e.toString());
        }
        return loaded;
    }

    public static Identifier icon(String entryText) {
        SpeciesEntry entry = SpeciesEntry.parse(entryText);
        if (entry.group()) {
            return null;
        }
        String file = index().get(entry.key());
        if (file == null && entry.form() != null) {
            file = index().get(entry.speciesId());
        }
        return file == null ? null : AWildEncounter.id("textures/icons/" + file + ".png");
    }

    public static boolean draw(DrawContext context, String entry, int x, int y, int size) {
        Identifier icon = icon(entry);
        if (icon == null) {
            return false;
        }
        smooth(icon);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.drawTexture(icon, x, y, size, size, 0.0f, 0.0f, SIZE, SIZE, SIZE, SIZE);
        RenderSystem.disableBlend();
        return true;
    }

    public static void smooth(Identifier icon) {
        if (smoothed.add(icon)) {
            MinecraftClient.getInstance().getTextureManager().getTexture(icon).setFilter(true, false);
        }
    }
}
