package com.protocolbook.gui;

import com.protocolbook.Main;
import com.protocolbook.duplicates.DuplicateFinder;
import com.protocolbook.html.ProtocolBookHtmlWriter;
import com.protocolbook.labels.CodeLabels;
import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;
import com.protocolbook.overrides.ProtocolOverrides;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the GUI works on: the parsed protocols, the overrides being edited, and the label files.
 * The overrides file's folder plays the part the working directory plays on the command line - the
 * label files, logo, changelog, manual protocols and "reference workbooks" folder are looked for there.
 */
final class Session {
    /** One screen of the review: e.g. Adult - Head (every "1.x" adult protocol). */
    static final class Section {
        final String bucket, label;
        final int prefix;
        final List<Protocol> protocols;

        Section(String bucket, int prefix, String label, List<Protocol> protocols) {
            this.bucket = bucket;
            this.prefix = prefix;
            this.label = label;
            this.protocols = protocols;
        }

        String title() { return bucket + " — " + label; }
    }

    final File input, overridesFile, settingsDir;
    final List<Protocol> protocols;
    /** The same protocols in protocol-number order, for lists. */
    final List<Protocol> sortedProtocols;
    final Map<String, ProtocolOverride> overrides;
    LabelConfig labels;
    /** kernel-labels.json as it stands: code -> name ("" while not named yet). */
    final Map<String, String> kernelNames = new java.util.TreeMap<String, String>();
    /** Kernel codes used by the loaded protocols, with a few recon names using each. */
    final Map<String, List<String>> kernelSamples;
    final Map<String, String> scannerNames = new LinkedHashMap<String, String>();
    final List<Section> sections = new ArrayList<Section>();
    final DuplicateFinder.Result duplicates;
    final Set<String> reviewedSections = new LinkedHashSet<String>();

    private Session(File input, File overridesFile, List<Protocol> protocols) throws IOException {
        this.input = input;
        this.overridesFile = overridesFile;
        this.settingsDir = overridesFile.getAbsoluteFile().getParentFile();
        this.protocols = protocols;
        this.sortedProtocols = new ArrayList<Protocol>(protocols);
        sortedProtocols.sort((a, b) -> com.protocolbook.html.ProtocolNumbers.compare(number(a), number(b)));
        this.overrides = ProtocolOverrides.load(overridesFile);
        // The --init-kernel-labels step, done on every load: any kernel code not in the file yet is added blank,
        // to be named on the "Kernel names" screen. Existing names are never touched.
        // only numeric codes need naming (GE); kernels that already have names (Siemens "Br40") are left alone
        List<String> codes = new ArrayList<String>();
        for (String code : Main.collectReconCodes(protocols, true)) if (code.matches("\\d+")) codes.add(code);
        Map<String, String> existing = CodeLabels.load(file("kernel-labels.json"));
        if (!existing.keySet().containsAll(codes) && settingsDir.isDirectory()) CodeLabels.mergeTemplate(codes, file("kernel-labels.json"));
        this.kernelNames.putAll(CodeLabels.load(file("kernel-labels.json")));
        for (String code : codes) if (!kernelNames.containsKey(code)) kernelNames.put(code, "");
        this.kernelSamples = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> e : Main.sampleReconNamesByKernelCode(protocols).entrySet())
            if (codes.contains(e.getKey())) kernelSamples.put(e.getKey(), e.getValue());
        this.labels = loadLabels();
        for (Protocol p : protocols) if (number(p) != null) scannerNames.put(number(p), p.getMetadata().getName());
        this.duplicates = DuplicateFinder.find(protocols);
        // Same grouping and order as the book, but nothing left out yet - excluding is part of the review.
        Map<String, Map<Integer, List<Protocol>>> tree =
                new ProtocolBookHtmlWriter().tree(protocols, Collections.<String, ProtocolOverride>emptyMap(), labels);
        for (Map.Entry<String, Map<Integer, List<Protocol>>> bucket : tree.entrySet())
            for (Map.Entry<Integer, List<Protocol>> group : bucket.getValue().entrySet())
                sections.add(new Section(bucket.getKey(), group.getKey(), labels.categoryForNumber(group.getKey()), group.getValue()));
    }

    static Session load(File input, File overridesFile, PrintStream log) throws Exception {
        return load(input, null, overridesFile, log);
    }

    static Session load(File input, com.protocolbook.parser.ScannerFormat format, File overridesFile, PrintStream log) throws Exception {
        File dir = overridesFile.getAbsoluteFile().getParentFile();
        List<Protocol> protocols = Main.loadProtocols(input, format, new File(dir, "manual-protocols.json"), new ArrayList<File>(),
                new File(dir, "reference workbooks"), overridesFile, log);
        return new Session(input, overridesFile, protocols);
    }

    private LabelConfig loadLabels() throws IOException {
        return LabelConfig.load(file("kernel-labels.json"), file("plane-labels.json"), file("category-labels.json"));
    }

    /** Names one kernel code, writes kernel-labels.json and re-reads the labels so every screen shows the new name. */
    void nameKernel(String code, String name) throws IOException {
        kernelNames.put(code, name == null ? "" : name.trim());
        if (!settingsDir.isDirectory()) settingsDir.mkdirs();
        CodeLabels.save(kernelNames, file("kernel-labels.json"));
        labels = loadLabels();
    }

    /** A file next to the overrides file (labels, logo, changelog, ...). */
    File file(String name) {
        return new File(settingsDir, name);
    }

    /** This protocol's overrides, created (empty) on first use. */
    ProtocolOverride overrideFor(String number) {
        ProtocolOverride o = overrides.get(number);
        if (o == null) {
            o = new ProtocolOverride();
            overrides.put(number, o);
        }
        return o;
    }

    /** The overrides for a protocol if it has any, without creating them. */
    ProtocolOverride existingOverride(Protocol p) {
        return number(p) == null ? null : overrides.get(number(p));
    }

    boolean isExcluded(Protocol p) {
        ProtocolOverride o = existingOverride(p);
        return o != null && o.isExcluded();
    }

    /** Which section (screen) a protocol is on, or null when its number has no category (e.g. 10.x QA) and it never reaches the book. */
    Section sectionOf(Protocol p) {
        for (Section s : sections) for (Protocol q : s.protocols) if (q == p) return s;
        return null;
    }

    /** Writes the overrides file (keeping a .bak of the previous one). */
    void save() throws IOException {
        ProtocolOverrides.save(overrides, scannerNames, overridesFile);
    }

    /** Every title already used for an added field, so the next one can be picked instead of retyped. */
    List<String> addedFieldTitles() {
        Set<String> titles = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (ProtocolOverride o : overrides.values())
            for (ProtocolOverride.AddedField f : o.getAddedFields())
                if (f.getTitle() != null && !f.getTitle().trim().isEmpty()) titles.add(f.getTitle().trim());
        return new ArrayList<String>(titles);
    }

    /** "Same settings as 8.4" / "Same name as 9.6 (settings differ)" - blank when not a duplicate. */
    Map<Protocol, String> duplicateNotes() {
        Map<Protocol, String> notes = new HashMap<Protocol, String>();
        for (List<Protocol> group : duplicates.identical) describe(group, "Same settings as ", "", notes);
        for (List<Protocol> group : duplicates.sameNameDifferent) describe(group, "Same name as ", " (settings differ)", notes);
        return notes;
    }

    private static void describe(List<Protocol> group, String prefix, String suffix, Map<Protocol, String> notes) {
        for (Protocol p : group) {
            List<String> others = new ArrayList<String>();
            for (Protocol q : group) if (q != p) {
                String shown = com.protocolbook.html.ProtocolNumbers.displayNumber(q);
                others.add(shown.isEmpty() ? q.getMetadata().getName() : shown);
            }
            if (!notes.containsKey(p)) notes.put(p, prefix + String.join(", ", others) + suffix);
        }
    }

    /** The extra copies of identical protocols - every one but the most recently updated in each group. */
    List<Protocol> suggestedExclusions() {
        List<Protocol> out = new ArrayList<Protocol>();
        for (List<Protocol> group : duplicates.identical) {
            Protocol keep = DuplicateFinder.suggestedKeep(group);
            for (Protocol p : group) if (p != keep) out.add(p);
        }
        return out;
    }

    static String number(Protocol p) {
        return p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
    }
}
