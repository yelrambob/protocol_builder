package com.protocolbook.gui;

import com.protocolbook.html.BookTheme;

import java.util.prefs.Preferences;

/**
 * Remembers the last choices between runs (folders, title, file name, which outputs, colors) in the
 * user's Java preferences - nothing written next to the protocol data. Reading or writing them never
 * stops the program: if preferences can't be stored, the defaults are used.
 */
final class Prefs {
    private final Preferences node;

    Prefs() {
        Preferences p;
        try {
            p = Preferences.userRoot().node("protocol-builder");
        } catch (Exception e) {
            p = null;
        }
        node = p;
    }

    String get(String key, String fallback) {
        try {
            return node == null ? fallback : node.get(key, fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }

    void put(String key, String value) {
        try {
            if (node != null && value != null) node.put(key, value);
        } catch (Exception ignored) {
            // not being able to remember a setting is never worth interrupting anyone over
        }
    }

    void putBoolean(String key, boolean value) {
        put(key, String.valueOf(value));
    }

    BookTheme theme() {
        String primary = get("primaryColor", null), accent = get("accentColor", null);
        if (!BookTheme.isColor(primary) || !BookTheme.isColor(accent)) return BookTheme.DEFAULT;
        BookTheme saved = new BookTheme(get("themeName", "Custom"), primary, accent);
        for (BookTheme preset : BookTheme.PRESETS) if (preset.equals(saved)) return preset;
        return saved;
    }

    void theme(BookTheme theme) {
        put("themeName", theme.getName());
        put("primaryColor", theme.getPrimary());
        put("accentColor", theme.getAccent());
    }
}
