package com.protocolbook.html;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The two colors the book is drawn in: the main color (page background, headings, table headers)
 * and the accent color (sidebar, header underline, scanning-notes box). The darker hover shade and
 * the pale notes-box fill are worked out from the accent, so two picks are all it takes.
 */
public final class BookTheme {
    /** The book's original look - Atlantic Health System blue and orange. */
    public static final BookTheme DEFAULT = new BookTheme("Blue & Orange", "#044281", "#ff8200");

    /** Ready-made pairs offered in the GUI's Colors menu. */
    public static final List<BookTheme> PRESETS = Arrays.asList(
            DEFAULT,
            new BookTheme("Navy & Teal", "#1f3a5f", "#138d90"),
            new BookTheme("Forest & Gold", "#1e5631", "#d4a017"),
            new BookTheme("Charcoal & Red", "#2f3437", "#c0392b"),
            new BookTheme("Purple & Gray", "#4b2e83", "#7a7f87"),
            new BookTheme("Black & White", "#222222", "#666666"));

    private final String name, primary, accent;

    public BookTheme(String name, String primary, String accent) {
        this.name = name;
        this.primary = normalize(primary);
        this.accent = normalize(accent);
    }

    public BookTheme(String primary, String accent) { this("Custom", primary, accent); }

    public String getName() { return name; }
    /** "#rrggbb" */
    public String getPrimary() { return primary; }
    /** "#rrggbb" */
    public String getAccent() { return accent; }
    /** The accent darkened by a fifth, for hover/selected sidebar items. */
    public String getAccentDark() { return mix(accent, "#000000", 0.2); }
    /** The accent at a tenth strength over white, for the scanning-notes box. */
    public String getAccentTint() { return mix(accent, "#ffffff", 0.9); }

    /** Same colors under another name - e.g. a preset once one of its colors is changed. */
    public BookTheme withPrimary(String hex) { return new BookTheme(primary.equals(normalize(hex)) ? name : "Custom", hex, accent); }
    public BookTheme withAccent(String hex) { return new BookTheme(accent.equals(normalize(hex)) ? name : "Custom", primary, hex); }

    /** True for "#rgb" / "#rrggbb" (the "#" optional). */
    public static boolean isColor(String hex) {
        return hex != null && hex.trim().matches("#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})");
    }

    private static String normalize(String hex) {
        if (!isColor(hex)) throw new IllegalArgumentException("Not a color: \"" + hex + "\" - use a hex value like #044281");
        String h = hex.trim().replace("#", "").toLowerCase(Locale.ROOT);
        if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        return "#" + h;
    }

    // a moved toward b by amount (0 = a, 1 = b).
    private static String mix(String a, String b, double amount) {
        int ca = Integer.parseInt(a.substring(1), 16), cb = Integer.parseInt(b.substring(1), 16);
        StringBuilder out = new StringBuilder("#");
        for (int shift = 16; shift >= 0; shift -= 8) {
            int x = (ca >> shift) & 0xff, y = (cb >> shift) & 0xff;
            out.append(String.format(Locale.ROOT, "%02x", (int) Math.round(x + (y - x) * amount)));
        }
        return out.toString();
    }

    @Override public boolean equals(Object o) {
        return o instanceof BookTheme && ((BookTheme) o).primary.equals(primary) && ((BookTheme) o).accent.equals(accent);
    }

    @Override public int hashCode() { return primary.hashCode() * 31 + accent.hashCode(); }

    @Override public String toString() { return name; }
}
