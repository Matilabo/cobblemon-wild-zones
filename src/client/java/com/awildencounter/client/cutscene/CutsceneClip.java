package com.awildencounter.client.cutscene;

import com.awildencounter.AWildEncounter;
import com.awildencounter.encounter.CustomClips;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CutsceneClip(
        String id,
        int fps,
        int width,
        int height,
        boolean transparent,
        boolean smooth,
        int durationMs,
        int battleAtMs,
        int coverAtMs,
        int fadeOutMs,
        List<Frame> frames) {
    public record Frame(byte[] colour, byte[] alpha) {
    }

    private interface FileReader {
        byte[] read(String name) throws IOException;
    }

    public int frameCount() {
        return frames.size();
    }

    public static CutsceneClip load(String id) throws IOException {
        if (CustomClips.isCustom(id)) {
            return loadCustom(id);
        }
        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();
        String base = "cutscenes/" + id + "/";

        JsonObject meta;
        try (InputStream stream = open(resources, AWildEncounter.id(base + "meta.json"))) {
            meta = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        return fromMeta(id, meta, name -> {
            try (InputStream stream = open(resources, AWildEncounter.id(base + name))) {
                return stream.readAllBytes();
            }
        });
    }

    public static CutsceneClip loadCustom(String id) throws IOException {
        Path dir = CustomClips.dirOf(id);
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IOException("No custom animation folder for " + id);
        }
        JsonObject meta;
        try {
            meta = JsonParser.parseString(Files.readString(dir.resolve(CustomClips.META), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("Could not read the meta.json of " + id + ": " + e);
        }
        return fromMeta(id, meta, name -> {
            Path file = CustomClips.fileIn(dir, name);
            if (file == null) {
                throw new IOException("Bad frame name " + name + " in " + id);
            }
            return Files.readAllBytes(file);
        });
    }

    private static CutsceneClip fromMeta(String id, JsonObject meta, FileReader reader) throws IOException {
        JsonArray names = meta.getAsJsonArray("frames");
        if (names == null || names.isEmpty()) {
            throw new IOException("The animation " + id + " has no frames");
        }
        List<Frame> frames = new ArrayList<>(names.size());
        Map<String, byte[]> cache = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String[] parts = names.get(i).getAsString().split("\\|");
            byte[] colour = read(reader, cache, parts[0]);
            byte[] alpha = parts.length > 1 ? read(reader, cache, parts[1]) : null;
            frames.add(new Frame(colour, alpha));
        }

        int fps = Math.max(1, meta.get("fps").getAsInt());
        int battleAt = meta.has("battleAtMs") ? meta.get("battleAtMs").getAsInt() : frames.size() * 500 / fps;
        return new CutsceneClip(
                id,
                fps,
                meta.get("width").getAsInt(),
                meta.get("height").getAsInt(),
                meta.has("transparent") && meta.get("transparent").getAsBoolean(),
                meta.has("smooth") && meta.get("smooth").getAsBoolean(),
                meta.has("durationMs") ? meta.get("durationMs").getAsInt() : frames.size() * 1000 / fps,
                battleAt,
                meta.has("coverAtMs") ? meta.get("coverAtMs").getAsInt() : battleAt,
                meta.has("fadeOutMs") ? meta.get("fadeOutMs").getAsInt() : 0,
                frames);
    }

    private static byte[] read(FileReader reader, Map<String, byte[]> cache, String name) throws IOException {
        byte[] cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = reader.read(name);
        cache.put(name, bytes);
        return bytes;
    }

    private static InputStream open(ResourceManager resources, Identifier id) throws IOException {
        Optional<Resource> resource = resources.getResource(id);
        if (resource.isEmpty()) {
            throw new IOException("Missing cutscene resource " + id);
        }
        return resource.get().getInputStream();
    }
}
