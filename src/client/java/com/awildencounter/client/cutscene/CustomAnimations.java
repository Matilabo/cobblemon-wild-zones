package com.awildencounter.client.cutscene;

import com.awildencounter.AWildEncounter;
import com.awildencounter.encounter.CustomClips;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class CustomAnimations {
    public record Entry(String id, String name, int frames, int fps, int durationMs, int appearFrame) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private CustomAnimations() {
    }

    public static Path folder() {
        Path folder = CustomClips.folder();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            AWildEncounter.LOGGER.warn("Could not make {}: {}", folder, e.toString());
        }
        return folder;
    }

    public static void openFolder() {
        Util.getOperatingSystem().open(folder().toFile());
    }

    public static List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.list(folder())) {
            for (Path dir : paths.sorted(Comparator.comparing(path -> path.getFileName().toString()
                    .toLowerCase(Locale.ROOT))).toList()) {
                String name = dir.getFileName().toString();
                if (!Files.isDirectory(dir) || !CustomClips.safeName(name)
                        || !Files.isRegularFile(dir.resolve(CustomClips.META))) {
                    continue;
                }
                JsonObject meta = CustomClips.readMeta(CustomClips.id(name));
                if (meta == null || !meta.has("frames") || !meta.has("fps")) {
                    continue;
                }
                int frames = meta.getAsJsonArray("frames").size();
                int fps = Math.max(1, meta.get("fps").getAsInt());
                int duration = meta.has("durationMs") ? meta.get("durationMs").getAsInt() : frames * 1000 / fps;
                entries.add(new Entry(CustomClips.id(name), name, frames, fps, duration, appearFrame(meta, frames)));
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not list the custom animations: {}", e.toString());
        }
        return entries;
    }

    public static List<Path> gifs() {
        List<Path> gifs = new ArrayList<>();
        try (Stream<Path> paths = Files.list(folder())) {
            paths.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(gifs::add);
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not list the GIFs to import: {}", e.toString());
        }
        return gifs;
    }

    public static boolean exists(String name) {
        Path dir = CustomClips.dirOf(CustomClips.id(name));
        return dir != null && Files.isDirectory(dir);
    }

    public static String nameFor(Path gif) {
        String file = gif.getFileName().toString();
        String base = file.toLowerCase(Locale.ROOT).endsWith(".gif") ? file.substring(0, file.length() - 4) : file;
        StringBuilder out = new StringBuilder();
        for (char c : base.toCharArray()) {
            out.append(c < 32 || "/\\:*?\"<>|".indexOf(c) >= 0 ? '_' : c);
        }
        String name = out.toString().strip();
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1).strip();
        }
        if (name.length() > 64) {
            name = name.substring(0, 64).strip();
        }
        return CustomClips.safeName(name) ? name : "animation";
    }

    public static ChromaKey savedKey(String name) {
        try {
            JsonObject meta = CustomClips.readMeta(CustomClips.id(name));
            if (meta == null || !meta.has("chromaKey")) {
                return null;
            }
            Integer rgb = ChromaKey.parseHex(meta.get("chromaKey").getAsString());
            int intensity = meta.has("chromaIntensity") ? meta.get("chromaIntensity").getAsInt()
                    : ChromaKey.DEFAULT_INTENSITY;
            return rgb == null ? null : new ChromaKey(rgb, intensity);
        } catch (Exception e) {
            return null;
        }
    }

    public static int appearFrame(JsonObject meta, int frames) {
        try {
            if (meta.has("appearFrame")) {
                return clamp(meta.get("appearFrame").getAsInt(), frames);
            }
            int fps = Math.max(1, meta.get("fps").getAsInt());
            int coverAt = meta.has("coverAtMs") ? meta.get("coverAtMs").getAsInt()
                    : meta.has("battleAtMs") ? meta.get("battleAtMs").getAsInt() : frames * 500 / fps;
            return clamp(coverAt * fps / 1000, frames);
        } catch (Exception e) {
            return frames / 2;
        }
    }

    private static int clamp(int frame, int frames) {
        return Math.max(0, Math.min(frames - 1, frame));
    }

    public static String save(String name, GifConverter.Converted converted, int appearFrame, boolean smooth,
                              ChromaKey key) throws IOException {
        String id = CustomClips.id(name);
        Path dir = CustomClips.dirOf(id);
        if (dir == null) {
            throw new IOException("Not a folder name: " + name);
        }
        removeFrames(dir);
        Files.createDirectories(dir);
        List<String> fileNames = new ArrayList<>();
        for (int i = 0; i < converted.files().size(); i++) {
            String fileName = String.format(Locale.ROOT, "%03d.png", i);
            Files.write(dir.resolve(fileName), converted.files().get(i));
            fileNames.add(fileName);
        }
        JsonObject meta = new JsonObject();
        meta.addProperty("id", id);
        meta.addProperty("source", converted.source());
        meta.addProperty("fps", converted.fps());
        meta.addProperty("width", converted.width());
        meta.addProperty("height", converted.height());
        meta.addProperty("transparent", converted.transparent());
        meta.addProperty("smooth", smooth);
        if (key != null) {
            meta.addProperty("chromaKey", key.hex());
            meta.addProperty("chromaIntensity", key.intensity());
        }
        JsonArray frames = new JsonArray();
        for (int index : converted.sequence()) {
            frames.add(fileNames.get(index));
        }
        applyTiming(meta, converted.sequence().size(), converted.fps(), appearFrame);
        meta.add("frames", frames);
        Files.writeString(dir.resolve(CustomClips.META), GSON.toJson(meta), StandardCharsets.UTF_8);
        AWildEncounter.LOGGER.info("Imported {} as the custom animation {}", converted.source(), id);
        return id;
    }

    public static void saveTiming(String id, int appearFrame, boolean smooth) throws IOException {
        Path dir = CustomClips.dirOf(id);
        JsonObject meta = CustomClips.readMeta(id);
        if (dir == null || meta == null || !meta.has("frames") || !meta.has("fps")) {
            throw new IOException("No custom animation " + id);
        }
        meta.addProperty("smooth", smooth);
        applyTiming(meta, meta.getAsJsonArray("frames").size(), Math.max(1, meta.get("fps").getAsInt()),
                appearFrame);
        Files.writeString(dir.resolve(CustomClips.META), GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    private static void applyTiming(JsonObject meta, int frames, int fps, int appearFrame) {
        int appear = clamp(appearFrame, frames);
        int durationMs = (frames * 1000 + fps - 1) / fps;
        int appearMs = (appear * 1000 + fps - 1) / fps;
        meta.addProperty("appearFrame", appear);
        meta.addProperty("durationMs", durationMs);
        meta.addProperty("battleAtMs", appearMs);
        meta.addProperty("coverAtMs", appearMs);
        meta.addProperty("coverUntilMs", durationMs);
        meta.addProperty("fadeOutMs", Math.max(0, Math.min(250, (durationMs - appearMs) / 2)));
    }

    private static void removeFrames(Path dir) {
        Path metaFile = dir.resolve(CustomClips.META);
        if (!Files.isRegularFile(metaFile)) {
            return;
        }
        try {
            JsonObject meta = com.google.gson.JsonParser.parseString(
                    Files.readString(metaFile, StandardCharsets.UTF_8)).getAsJsonObject();
            if (meta.has("frames")) {
                for (JsonElement element : meta.getAsJsonArray("frames")) {
                    for (String part : element.getAsString().split("\\|")) {
                        Path file = CustomClips.fileIn(dir, part);
                        if (file != null) {
                            Files.deleteIfExists(file);
                        }
                    }
                }
            }
            Files.deleteIfExists(metaFile);
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not clear the old frames of {}: {}", dir, e.toString());
        }
    }
}
