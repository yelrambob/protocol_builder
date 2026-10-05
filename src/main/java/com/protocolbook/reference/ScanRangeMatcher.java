package com.protocolbook.reference;

import com.protocolbook.html.ProtocolNumbers;
import com.protocolbook.model.Metadata;
import com.protocolbook.model.PatientSetup;
import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;

import java.util.*;

/**
 * Pairs scanner protocol names with reference-workbook sheet names, which are written by
 * different people for different audiences: "CT LWR EXT KNEE WITH CONTRAST" on the scanner is
 * "CT Lower Ext. Knee" in the workbook, "CT CERVICAL SPINE" is "CT C-spine". Both sides are
 * reduced to a set of normalized words (abbreviations expanded, contrast/peds wording and other
 * filler dropped) and the sheet with the highest word overlap wins - but only when it's a clear
 * winner, since a wrong scan range is worse than none. Anything this gets wrong or misses is
 * fixed by naming the sheet explicitly ("referenceSheet" in protocol-overrides.json).
 */
public final class ScanRangeMatcher {
    /** Minimum Jaccard overlap of the two word sets for an automatic match. */
    static final double MIN_SCORE = 0.6;

    private static final Map<String, String> ABBREVIATIONS = new HashMap<String, String>();
    static {
        String[][] pairs = {
                {"LWR", "LOWER"}, {"LOW", "LOWER"}, {"UP", "UPPER"}, {"UPP", "UPPER"},
                {"EXT", "EXTREMITY"}, {"EXTREMITIES", "EXTREMITY"}, {"EXTREM", "EXTREMITY"},
                {"HND", "HAND"}, {"WR", "WRIST"}, {"ELB", "ELBOW"},
                {"ABD", "ABDOMEN"}, {"ABDOMINAL", "ABDOMEN"}, {"PEL", "PELVIS"}, {"PELV", "PELVIS"},
                {"BILAT", "BILATERAL"}, {"ART", "ARTERIAL"}, {"MES", "MESENTERIC"}, {"ISCH", "ISCHEMIA"},
                {"ONC", "ONCOLOGY"}, {"PE", "PULMONARY EMBOLUS"}, {"CAP", "CHEST ABDOMEN PELVIS"},
                {"STN", "SOFT TISSUE NECK"}, {"TEMP", "TEMPORAL"}, {"IAC", "TEMPORAL"},
                {"CSPINE", "CERVICAL SPINE"}, {"TSPINE", "THORACIC SPINE"}, {"LSPINE", "LUMBAR SPINE"},
        };
        for (String[] p : pairs) ABBREVIATIONS.put(p[0], p[1]);
    }

    // Words that say nothing about which sheet a protocol is: contrast phase, population, filler.
    // "CTA"/"CTV" are deliberately NOT here - CTA Head and CT Head are different protocols.
    private static final Set<String> STOP_WORDS = new HashSet<String>(Arrays.asList(
            "CT", "WITH", "WITHOUT", "W", "WO", "AND", "OR", "CONTRAST", "NON", "CON", "IV", "ORAL",
            "PEDS", "PED", "PEDIATRIC", "ROUTINE", "PROTOCOL", "OF", "THE", "ONLY"));

    private final List<ReferenceSheets.Sheet> sheets;
    private final Map<ReferenceSheets.Sheet, Set<String>> sheetWords = new IdentityHashMap<ReferenceSheets.Sheet, Set<String>>();

    public ScanRangeMatcher(List<ReferenceSheets.Sheet> sheets) {
        this.sheets = sheets;
        for (ReferenceSheets.Sheet s : sheets) sheetWords.put(s, words(s.name));
    }

    /** The sheet with exactly this name (case/spacing-insensitive), regardless of population; null if none. */
    public ReferenceSheets.Sheet byName(String sheetName) {
        String want = squash(sheetName);
        for (ReferenceSheets.Sheet s : sheets) if (squash(s.name).equals(want)) return s;
        return null;
    }

    /**
     * Best-matching sheet for a scanner protocol name, among sheets for the same population
     * (adult protocols never pick up a PEDS sheet and vice versa). Null when nothing clears
     * {@link #MIN_SCORE} or the top two candidates tie.
     */
    public ReferenceSheets.Sheet match(String protocolName, boolean pediatric) {
        if (protocolName == null) return null;
        Set<String> want = words(protocolName);
        if (want.isEmpty()) return null;
        ReferenceSheets.Sheet best = null;
        double bestScore = 0, runnerUp = 0;
        for (ReferenceSheets.Sheet s : sheets) {
            if (s.pediatric != pediatric) continue;
            double score = jaccard(want, sheetWords.get(s));
            if (score > bestScore) { runnerUp = bestScore; bestScore = score; best = s; }
            else if (score > runnerUp) runnerUp = score;
        }
        return bestScore >= MIN_SCORE && bestScore > runnerUp ? best : null;
    }

    /**
     * Fills in each protocol's scan range. First match wins: a hand-typed "scanRange" override,
     * then the sheet a "referenceSheet" override names, then a range the input itself already
     * carried (workbook input), then the best-matching sheet by name. Returns a human-readable
     * report - which sheet each protocol got, and which got none - so a wrong or missing match is
     * easy to spot and pin down with a "referenceSheet" override.
     */
    public List<String> apply(List<Protocol> protocols, Map<String, ProtocolOverride> overrides) {
        List<String> matched = new ArrayList<String>(), unmatched = new ArrayList<String>(), problems = new ArrayList<String>();
        for (Protocol p : protocols) {
            Metadata m = p.getMetadata();
            String number = m == null ? null : m.getProtocolNumber();
            ProtocolOverride o = overrides.get(number);
            if (o != null && o.isExcluded()) continue;
            if (p.getPatientSetup() == null) p.setPatientSetup(new PatientSetup());
            String label = number + " " + (m == null ? "" : m.getName());
            if (o != null && notBlank(o.getScanRange())) {
                p.getPatientSetup().setScanRange(o.getScanRange().trim());
                matched.add(label + "  <-  scanRange override");
                continue;
            }
            ReferenceSheets.Sheet sheet = null;
            if (o != null && notBlank(o.getReferenceSheet())) {
                sheet = byName(o.getReferenceSheet());
                if (sheet == null) problems.add(label + ": referenceSheet \"" + o.getReferenceSheet().trim() + "\" not found in the reference workbooks");
            } else if (notBlank(p.getPatientSetup().getScanRange())) {
                continue;
            } else {
                sheet = match(m == null ? null : m.getName(), ProtocolNumbers.isPediatricProtocol(p));
            }
            if (sheet == null) { unmatched.add(label); continue; }
            p.getPatientSetup().setScanRange(String.join("\n", sheet.lines()));
            matched.add(label + "  <-  " + sheet.workbook + " > " + sheet.name);
        }
        List<String> report = new ArrayList<String>();
        report.add("Scan ranges from " + sheets.size() + " reference sheet(s): " + matched.size() + " protocol(s) matched, " + unmatched.size() + " without one");
        for (String line : matched) report.add("  " + line);
        if (!unmatched.isEmpty()) {
            report.add("No scan range found for (set \"referenceSheet\" or \"scanRange\" in protocol-overrides.json):");
            for (String line : unmatched) report.add("  " + line);
        }
        for (String line : problems) report.add("WARNING: " + line);
        return report;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    static Set<String> words(String name) {
        String s = name.toUpperCase(Locale.ROOT)
                // C-spine / C SPINE / CSPINE all mean the same thing
                .replaceAll("\\b([CTL])\\s*-?\\s*SPINE\\b", "$1SPINE")
                // "W/O" and "W/" (without / with contrast) before "/" becomes a separator and strands an "O"
                .replaceAll("\\bW\\s*/\\s*O\\b", " WITHOUT ").replaceAll("\\bW\\s*/", " WITH ")
                .replaceAll("[^A-Z0-9]+", " ");
        Set<String> out = new LinkedHashSet<String>();
        for (String w : s.trim().split(" ")) {
            if (w.isEmpty()) continue;
            String expanded = ABBREVIATIONS.containsKey(w) ? ABBREVIATIONS.get(w) : w;
            for (String e : expanded.split(" ")) {
                if (STOP_WORDS.contains(e)) continue;
                out.add(e.length() > 4 && e.endsWith("S") ? e.substring(0, e.length() - 1) : e); // ORBITS ~ ORBIT
            }
        }
        return out;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int common = 0;
        for (String w : a) if (b.contains(w)) common++;
        return (double) common / (a.size() + b.size() - common);
    }

    private static String squash(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
