package com.awildencounter.encounter;

import com.awildencounter.AWildEncounter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

public final class CustomClips {
    public static final String PREFIX = "custom:";
    public static final String FOLDER = "custom encounter animations";
    public static final String META = "meta.json";

    private CustomClips() {
    }

    public static boolean isCustom(String clipId) {
        return clipId != null && clipId.startsWith(PREFIX);
    }

    public static String id(String folderName) {
        return PREFIX + folderName;
    }

    public static String nameOf(String clipId) {
        return isCustom(clipId) ? clipId.substring(PREFIX.length()) : clipId;
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve(AWildEncounter.MOD_ID).resolve(FOLDER);
    }

    public static boolean safeName(String name) {
        if (name == null || name.isBlank() || name.length() > 64 || !name.equals(name.strip())
                || name.endsWith(".") || name.equals("..")) {
            return false;
        }
        for (char c : name.toCharArray()) {
            if (c < 32 || "/\\:*?\"<>|".indexOf(c) >= 0) {
                return false;
            }
        }
        String upper = name.toUpperCase(Locale.ROOT);
        int dot = upper.indexOf('.');
        String base = dot >= 0 ? upper.substring(0, dot) : upper;
        return !base.matches("CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9]");
    }

    public static Path dirOf(String clipId) {
        if (!isCustom(clipId)) {
            return null;
        }
        String name = nameOf(clipId);
        if (!safeName(name)) {
            return null;
        }
        try {
            Path base = folder().toAbsolutePath().normalize();
            Path dir = base.resolve(name).normalize();
            return base.equals(dir.getParent()) ? dir : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static Path fileIn(Path dir, String name) {
        if (dir == null || !safeName(name)) {
            return null;
        }
        try {
            Path file = dir.resolve(name).normalize();
            return dir.equals(file.getParent()) ? file : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static JsonObject readMeta(String clipId) {
        Path dir = dirOf(clipId);
        if (dir == null) {
            return null;
        }
        Path meta = dir.resolve(META);
        if (!Files.isReadable(meta)) {
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read {}: {}", meta, e.toString());
            return null;
        }
    }
}
