package com.awildencounter.client.gui;

import com.awildencounter.config.ConfigOptions;
import net.minecraft.client.resource.language.I18n;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Tr {
    private static final String PREFIX = "awildencounter.";

    private static final Pattern ONE_IN = Pattern.compile("1 in (\\d+)");

    private Tr() {
    }

    public static String get(String key, String english, Object... args) {
        String full = PREFIX + key;
        if (I18n.hasTranslation(full)) {
            return I18n.translate(full, args);
        }
        if (args.length == 0) {
            return english;
        }
        try {
            return String.format(Locale.ROOT, english, args);
        } catch (RuntimeException e) {
            return english;
        }
    }

    public static String label(ConfigOptions.Spec spec) {
        if (spec.isHeading()) {
            return get("heading." + slug(spec.label()), spec.label());
        }
        return get("option." + spec.key(), spec.label());
    }

    public static String description(ConfigOptions.Spec spec) {
        if (spec.key() == null || spec.description() == null) {
            return spec.description();
        }
        return get("option." + spec.key() + ".desc", spec.description());
    }

    public static String tab(ConfigOptions.Tab tab) {
        return get("tab." + tab.name().toLowerCase(Locale.ROOT), tab.title);
    }

    public static String value(String english) {
        if (english == null || english.isEmpty() || english.chars().allMatch(Character::isDigit)) {
            return english;
        }
        Matcher oneIn = ONE_IN.matcher(english);
        if (oneIn.matches()) {
            return get("value.one_in", "1 in %s", oneIn.group(1));
        }
        return get("value." + slug(english), english);
    }

    public static String slug(String english) {
        StringBuilder out = new StringBuilder();
        boolean underscore = false;
        for (char letter : english.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(letter)) {
                out.append(letter);
                underscore = false;
            } else if (!underscore && out.length() > 0) {
                out.append('_');
                underscore = true;
            }
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == '_') {
            end--;
        }
        return out.substring(0, end);
    }
}
