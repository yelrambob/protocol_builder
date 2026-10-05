package com.protocolbook.html;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Acquisition;
import com.protocolbook.model.Group;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;

import java.util.Locale;

/**
 * The scanner's own value for each setting protocol-overrides.json can replace, written the way the
 * book shows it (pitch as a ratio, mA as a min-max range under auto-mA, CTDIvol as a min-max range,
 * kernel and ASIR through the labels). The book, the GUI's tables/editor and the changes report all
 * read it from here so "what the scanner has" never differs between them. Values come back without
 * units ("0.992" not "0.992:1", "70" not "70 sec") - the form someone would type an override in.
 */
public final class ScannerValues {
    private ScannerValues() {}

    /** Whether a series is the scout/localizer, which only shows kV and mA. */
    public static boolean isScout(Series s) {
        return s.getScanType() != null && s.getScanType().equalsIgnoreCase("Scout");
    }

    /** One acquisition field (see ProtocolOverride.SERIES_FIELDS) for a series, from its first group. */
    public static String seriesField(Series s, String field) {
        if (s.getGroups().isEmpty()) return null;
        return groupField(s.getGroups().get(0), field);
    }

    static String groupField(Group g, String field) {
        Acquisition a = g.getAcquisition();
        boolean autoMa = a.isAutoMa();
        switch (field) {
            case "kv": return a.getKv();
            case "ma": return autoMa ? a.getMinMa() + "-" + a.getMaxMa() : a.getMa();
            // Noise Index only means anything under auto-mA - a fixed-mA group can carry a stale leftover value.
            case "noiseIndex": return autoMa ? a.getNoiseIndex() : null;
            case "pitch": {
                Double ratio = a.pitchRatio();
                return ratio != null ? String.format(Locale.ROOT, "%.3f", ratio) : a.getPitch();
            }
            case "rotationTime": return a.getRotationTime();
            case "ctdi": return g.ctdi(true) == null ? null : doseRange(g.ctdi(false), g.ctdi(true));
            default: return null;
        }
    }

    /** One recon field (see ProtocolOverride.RECON_FIELDS), with kernel/ASIR through the labels. */
    public static String reconField(Reconstruction r, String field, LabelConfig labels) {
        switch (field) {
            case "name": return r.getName();
            case "thickness": return r.getThickness();
            case "interval": return r.getInterval();
            case "kernel": return labels.kernel(r.getKernel());
            case "asir": return labels.asir(r.getIterativeConfig());
            case "wwwl": return windowWidthLevel(r);
            case "sendTo": return r.getSendDestinations().isEmpty() ? null : String.join(", ", r.getSendDestinations());
            default: return null;
        }
    }

    /** The first series given IV contrast, or null when the exam has none. */
    public static Series firstContrastSeries(Protocol p) {
        for (Series s : p.getSeries()) if (!isScout(s) && s.getContrast() != null && s.getContrast().isIv()) return s;
        return null;
    }

    public static String contrastVolume(Protocol p) {
        Series s = firstContrastSeries(p);
        return s == null ? null : s.getContrast().getIvVolume();
    }

    public static String contrastRate(Protocol p) {
        Series s = firstContrastSeries(p);
        return s == null ? null : s.getContrast().getFlowRate();
    }

    /** Seconds from injection to scan for the first contrast series ("70"), or null. */
    public static String contrastDelay(Protocol p) {
        Series s = firstContrastSeries(p);
        return s == null ? null : delaySeconds(s);
    }

    /** A series' delay in whole seconds when it's whole ("70"), from its first group that has one; null when none or zero. */
    public static String delaySeconds(Series s) {
        for (Group g : s.getGroups()) {
            String d = g.getAcquisition().getScanDelay();
            if (d == null || d.trim().isEmpty()) continue;
            try {
                double seconds = Double.parseDouble(d.trim());
                if (seconds <= 0) return null;
                return seconds % 1 == 0 ? String.valueOf((long) seconds) : String.valueOf(seconds);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Exam CTDIvol as a min-max mA range ("4.35-61.38"), or null. */
    public static String examCtdi(Protocol p) {
        return p.getDose() == null ? null : doseRange(examDose(p, true, false), examDose(p, true, true));
    }

    /** Exam DLP as a min-max mA range, or null. */
    public static String examDlp(Protocol p) {
        return p.getDose() == null ? null : doseRange(examDose(p, false, false), examDose(p, false, true));
    }

    // The exported exam totals are calculated at each group's milliAmps value; swap each group's
    // share for its min- or max-mA figure (see Group#doseFactor) so the total is a range too.
    private static Double examDose(Protocol p, boolean ctdi, boolean max) {
        Double total = ctdi ? p.getDose().getCtdi() : p.getDose().getDlp();
        if (total == null) return null;
        for (Series s : p.getSeries())
            for (Group g : s.getGroups()) {
                Double stored = ctdi ? g.getDose().getCtdi() : g.getDose().getDlp();
                if (stored != null) total += stored * (g.doseFactor(max) - 1);
            }
        return total;
    }

    // "4.35-61.38", or just "44.58" when min and max are the same (fixed mA).
    static String doseRange(Double min, Double max) {
        if (max == null) return null;
        String hi = String.format(Locale.ROOT, "%.2f", max);
        String lo = min == null ? hi : String.format(Locale.ROOT, "%.2f", min);
        return lo.equals(hi) ? hi : lo + "-" + hi;
    }

    // Display window as "width/level" (e.g. "1500/250"), the order it's dialed in at the console.
    static String windowWidthLevel(Reconstruction r) {
        String ww = r.getWindowWidth(), wl = r.getWindowLevel();
        if (ww == null && wl == null) return null;
        return (ww != null ? ww : "?") + "/" + (wl != null ? wl : "?");
    }
}
