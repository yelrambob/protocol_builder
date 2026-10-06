package com.protocolbook.parser;

import com.protocolbook.model.*;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.InputStream;
import java.util.*;

/**
 * Reads a Siemens SOMATOM protocol export that has been opened into Excel (.xlsm/.xlsx): the scanner's
 * protocol XML flattened to one row per value, with the hierarchy repeated on every row - FolderName,
 * BodySize (Adult/Child), RegionName, ProtocolName, ScanType (Topo/Scan) - then one column per setting.
 * A row with a Range value starts a new range (series); the rows after it carry that range's settings
 * one per row; a row with a SeriesDescription is one reconstruction of the current range.
 *
 * Column names carry a running-number suffix ("Voltage4", "Kernel34"), so they're matched by name with
 * the suffix ignored. Columns before FolderName, if any, are only label copies and are ignored. The
 * workbook is read as a stream, since these exports run to tens of thousands of rows.
 *
 * Siemens doesn't number protocols, so each protocol's key in protocol-overrides.json is
 * "Region/Protocol name" (the same name can be filed under two regions); nothing is shown as its number.
 * The region picks the book section (see {@link #section}). The export carries no contrast settings; the
 * SAFIRE strength is read from the series description (see {@link #safire}).
 */
public class SiemensWorkbookParser implements ProtocolParser {
    public static final String SCANNER = "Siemens SOMATOM";

    // Setting names as they appear before the running-number suffix. Longest match wins
    // ("CustomMAsA7" is CustomMAsA, not CustomMAs; "Comment136" is Comment1).
    private static final List<String> FIELDS = Arrays.asList(
            "FolderName", "BodySize", "RegionName", "ProtocolName", "ScanType", "Range", "RefKV", "Voltage", "QualityRefMAs",
            "CustomMAs", "CustomMAsA", "CustomMAsB", "CAREkV", "OptimizeSliderPosition", "Care", "CareDoseType", "CTDIw",
            "FastAdjustLimitScanTime", "FastAdjustLimitMaxMAs", "DoseNotificationValueCTDIvol", "DoseNotificationValueDLP",
            "RotTime", "ScanTime", "Delay", "PitchFactor", "Feed", "Acq.", "ApiId", "ScanStart", "ScanEnd", "Pulsing",
            "PulsingStart", "PulsingEnd", "SeriesDescription", "ReconSliceEffective", "ReconIncr", "NoOfImages", "Kernel",
            "Window", "Comment1", "Comment2", "Transfer1", "Transfer2", "Transfer3", "SyngoViaTaskflow", "SyngoViaProcessingID",
            "BestPhase", "PhaseStart", "Multiphase", "SliceEffective");

    // Range settings kept as extra fields (readable label), since the book has no slot for them.
    private static final Map<String, String> EXTRA = new LinkedHashMap<String, String>();
    static {
        EXTRA.put("RefKV", "Ref. kV");
        EXTRA.put("CustomMAsA", "Eff. mAs (tube A)");
        EXTRA.put("CustomMAsB", "Eff. mAs (tube B)");
        EXTRA.put("OptimizeSliderPosition", "Tissue of interest (slider)");
        EXTRA.put("FastAdjustLimitScanTime", "FAST Adjust: upper limit scan time");
        EXTRA.put("FastAdjustLimitMaxMAs", "FAST Adjust: lower limit max. mAs");
        EXTRA.put("DoseNotificationValueCTDIvol", "Dose notification CTDIvol (mGy)");
        EXTRA.put("DoseNotificationValueDLP", "Dose notification DLP (mGy*cm)");
        EXTRA.put("ScanTime", "Scan time (s)");
        EXTRA.put("Feed", "Feed per scan (mm)");
        EXTRA.put("ApiId", "API");
        EXTRA.put("ScanStart", "Scan start");
        EXTRA.put("ScanEnd", "Scan end");
        EXTRA.put("Pulsing", "Pulsing");
        EXTRA.put("PulsingStart", "Pulsing start");
        EXTRA.put("PulsingEnd", "Pulsing end");
        EXTRA.put("PhaseStart", "Phase start");
        EXTRA.put("Multiphase", "Multi phase");
    }

    /** Whether this workbook has the Siemens export's columns (ProtocolName and RegionName) in a sheet's first row. */
    public static boolean looksLikeSiemens(File file) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        if (!file.isFile() || !(n.endsWith(".xlsx") || n.endsWith(".xlsm"))) return false;
        final boolean[] found = {false};
        try {
            stream(file, new RowSink() {
                @Override public boolean row(int index, Map<Integer, String> cells) {
                    if (cells.containsValue("ProtocolName") && cells.containsValue("RegionName")) found[0] = true;
                    return false; // only the first row of each sheet is needed
                }
            });
        } catch (Exception e) {
            return false;
        }
        return found[0];
    }

    @Override public List<Protocol> parse(File file) throws Exception {
        String n = file.getName().toLowerCase(Locale.ROOT);
        if (!file.isFile()) throw new IllegalArgumentException("Siemens protocol workbook not found: " + file.getAbsolutePath());
        if (!(n.endsWith(".xlsx") || n.endsWith(".xlsm")))
            throw new IllegalArgumentException("A Siemens protocol export is read from an Excel workbook (.xlsm or .xlsx) - got '" + file.getName() + "'.");
        Builder builder = new Builder();
        stream(file, builder);
        if (builder.protocols.isEmpty())
            throw new IllegalArgumentException("No Siemens protocols found in " + file.getName()
                    + ". Expected a sheet whose first row has columns such as FolderName, BodySize, RegionName, ProtocolName, ScanType, Range, Voltage, Kernel.");
        List<Protocol> out = new ArrayList<Protocol>(builder.protocols.values());
        for (Protocol p : out) {
            builder.applyDoseModulation(p);
            finish(p);
        }
        return out;
    }

    /** Book section for a Siemens region, using the GE console's numbering (1 Head ... 9 Lower Ext., +10 for children). */
    static int section(String region, boolean child) {
        String r = region == null ? "" : region.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        int base;
        switch (r) {
            case "head": base = 1; break;
            case "neck": base = 3; break;
            case "shoulder": case "upperextremities": case "upperextremity": base = 4; break;
            case "thorax": case "chest": base = 5; break;
            case "abdomen": base = 6; break;
            case "spine": base = 7; break;
            case "pelvis": base = 8; break;
            case "lowerextremities": case "lowerextremity": base = 9; break;
            case "vascular": base = 21; break;
            case "private": return 23; // phantom/QA tests - no section label by default, so left out of the book like GE's 10.x
            default: base = 22; // Specials, and any region this doesn't know
        }
        return child ? base + 10 : base;
    }

    /** Receives each sheet's rows in order: column index -> formatted text. Return false to skip the rest of that sheet. */
    private interface RowSink {
        boolean row(int index, Map<Integer, String> cells);
    }

    /** Thrown to stop reading a sheet early. */
    private static final class StopSheet extends RuntimeException {
        StopSheet() { super(null, null, false, false); }
    }

    private static void stream(File file, RowSink sink) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ)) {
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            Iterator<InputStream> sheets = reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    XMLReader xml = XMLHelper.newXMLReader();
                    xml.setContentHandler(new XSSFSheetXMLHandler(styles, null, strings, new XSSFSheetXMLHandler.SheetContentsHandler() {
                        private Map<Integer, String> cells;
                        private int index;

                        @Override public void startRow(int rowNum) { cells = new HashMap<Integer, String>(); index = rowNum; }

                        @Override public void endRow(int rowNum) { if (!sink.row(index, cells)) throw new StopSheet(); }

                        @Override public void cell(String ref, String value, XSSFComment comment) {
                            if (ref == null || value == null || value.trim().isEmpty()) return;
                            cells.put((int) new CellReference(ref).getCol(), value.trim());
                        }
                    }, new DataFormatter(Locale.US), false));
                    try {
                        xml.parse(new InputSource(sheet));
                    } catch (StopSheet ignored) {
                        // that sheet wasn't wanted, or only its first row was
                    }
                }
            }
        }
    }

    /** Turns the stream of rows into protocols. */
    private static final class Builder implements RowSink {
        final Map<String, Protocol> protocols = new LinkedHashMap<String, Protocol>();
        private Map<Integer, String> columns; // column index -> field name, for the current sheet
        private Protocol protocol;
        private Series series;
        private final Map<Protocol, Map<String, String>> pending = new IdentityHashMap<Protocol, Map<String, String>>();

        @Override public boolean row(int index, Map<Integer, String> cells) {
            if (columns == null || index == 0) {
                columns = header(cells);
                return columns != null; // not a Siemens sheet: skip it
            }
            Map<String, String> v = new HashMap<String, String>();
            for (Map.Entry<Integer, String> c : cells.entrySet()) {
                String field = columns.get(c.getKey());
                if (field != null) v.put(field, c.getValue());
            }
            String name = v.get("ProtocolName");
            if (name == null) return true;
            String region = v.containsKey("RegionName") ? v.get("RegionName") : "";
            String key = region + "/" + name;
            if (protocol == null || !key.equals(protocol.getMetadata().getProtocolNumber())) {
                protocol = protocols.get(key);
                if (protocol == null) protocols.put(key, protocol = newProtocol(key, name, region, v.get("BodySize"), v.get("FolderName")));
                series = protocol.getSeries().isEmpty() ? null : protocol.getSeries().get(protocol.getSeries().size() - 1);
            }
            if (v.containsKey("Range")) series = newSeries(protocol, v.get("Range"), v.get("ScanType"));
            if (v.containsKey("SeriesDescription")) addRecon(v);
            else if (series != null) for (Map.Entry<String, String> e : v.entrySet()) setRangeField(e.getKey(), e.getValue());
            return true;
        }

        // The first row names the columns; null if this sheet isn't a Siemens export.
        private Map<Integer, String> header(Map<Integer, String> cells) {
            int start = -1;
            for (Map.Entry<Integer, String> c : cells.entrySet()) if ("FolderName".equals(c.getValue()) || "ProtocolName".equals(c.getValue()))
                start = start < 0 ? c.getKey() : Math.min(start, c.getKey());
            if (start < 0 || !cells.containsValue("ProtocolName")) return null;
            Map<Integer, String> out = new HashMap<Integer, String>();
            for (Map.Entry<Integer, String> c : cells.entrySet()) {
                if (c.getKey() < start) continue;
                String field = field(c.getValue());
                if (field != null) out.put(c.getKey(), field);
            }
            return out;
        }

        private Protocol newProtocol(String key, String name, String region, String bodySize, String folder) {
            Protocol p = new Protocol();
            Metadata m = new Metadata();
            boolean child = bodySize != null && bodySize.trim().equalsIgnoreCase("child");
            m.setProtocolNumber(key);
            m.setDisplayNumber("");
            m.setName(name);
            m.setBodyPart(region);
            m.setCategory(region);
            m.setPatientType(child ? "Pediatric" : "Adult");
            m.setSection(section(region, child));
            m.setLibrary(folder);
            m.setScanner(SCANNER);
            p.setMetadata(m);
            p.setPatientSetup(new PatientSetup());
            p.setAcquisition(new Acquisition());
            p.setTiming(new Timing());
            return p;
        }

        private Series newSeries(Protocol p, String range, String scanType) {
            Series s = new Series();
            s.setNumber(p.getSeries().size() + 1);
            s.setName(range);
            s.setScanType("topo".equalsIgnoreCase(scanType) ? "Scout" : "Scan");
            Group g = new Group();
            g.getAcquisition().setMaUnit("mAs");
            s.getGroups().add(g);
            p.getSeries().add(s);
            return s;
        }

        private void setRangeField(String field, String value) {
            Acquisition a = series.getGroups().get(0).getAcquisition();
            switch (field) {
                case "Voltage": a.setKv(value); break;
                case "CustomMAs": a.setMa(value); break;
                case "QualityRefMAs": a.setQualityRefMas(value); break;
                case "RotTime": a.setRotationTime(value); break;
                case "Delay": a.setScanDelay(value); break;
                case "PitchFactor": a.setPitch(value); break;
                case "Acq.": a.setDetector(value); break;
                case "SliceEffective": a.setSliceThickness(value); break;
                case "CTDIw":
                    try { series.getGroups().get(0).getDose().setCtdi(Double.valueOf(value)); } catch (NumberFormatException ignored) {}
                    break;
                case "Care": case "CareDoseType": case "CAREkV":
                    pending.computeIfAbsent(protocol, k -> new HashMap<String, String>()).put(series.getNumber() + "|" + field, value);
                    break;
                default:
                    String label = EXTRA.get(field);
                    if (label != null) ParseSupport.putAdvanced(protocol, "Series " + series.getNumber() + " (" + series.getName() + ") " + label, value);
            }
        }

        private void addRecon(Map<String, String> v) {
            if (series == null) series = newSeries(protocol, "Range", v.get("ScanType"));
            Reconstruction r = new Reconstruction();
            String description = v.get("SeriesDescription").trim().replaceAll("\\s+", " ");
            r.setName(description);
            r.setThickness(v.get("ReconSliceEffective"));
            r.setInterval(v.get("ReconIncr"));
            r.setKernel(v.get("Kernel"));
            r.setWindowName(v.get("Window"));
            r.setIterativeConfig(safire(description, r.getKernel()));
            r.setDerived(description.toUpperCase(Locale.ROOT).matches(".*\\bMPR\\b.*"));
            try { if (v.get("NoOfImages") != null) r.setNumberOfImages(Integer.valueOf(v.get("NoOfImages"))); } catch (NumberFormatException ignored) {}
            for (String t : new String[] {"Transfer1", "Transfer2", "Transfer3"}) {
                String host = v.get(t);
                if (host != null && !r.getSendDestinations().contains(host)) r.getSendDestinations().add(host);
            }
            String prefix = "Series " + series.getNumber() + " recon " + description + ": ";
            for (String f : new String[] {"Comment1", "Comment2", "SyngoViaTaskflow", "SyngoViaProcessingID", "BestPhase"})
                if (v.get(f) != null) ParseSupport.putAdvanced(protocol, prefix + f, v.get(f));
            series.getGroups().get(0).getReconstructions().add(r);
        }

        // "CARE Dose4D", "CARE Dose4D, CARE kV Semi" or "Off" per diagnostic range, from its Care / CareDoseType / CAREkV rows.
        void applyDoseModulation(Protocol p) {
            Map<String, String> values = pending.get(p);
            if (values == null) return;
            for (Series s : p.getSeries()) {
                if ("Scout".equals(s.getScanType())) continue;
                String care = values.get(s.getNumber() + "|Care"), type = values.get(s.getNumber() + "|CareDoseType");
                String careKv = values.get(s.getNumber() + "|CAREkV");
                String text = "on".equalsIgnoreCase(care) ? (type != null ? type : "CARE Dose") : care != null ? "Dose modulation off" : null;
                if (careKv != null && !"off".equalsIgnoreCase(careKv)) text = (text == null ? "" : text + ", ") + "CARE kV " + careKv;
                s.getGroups().get(0).getAcquisition().setDoseModulation(text);
            }
        }
    }

    /**
     * The SAFIRE strength, which the scanner adds to the series description right after the kernel when
     * SAFIRE is on ("Abdomen 4.0 Br40 3" is Br40 with SAFIRE 3); null when there's no strength there
     * ("Head 4.0 Hr38", "1.0 Hr60 ax").
     */
    static String safire(String description, String kernel) {
        if (description == null || kernel == null || kernel.trim().isEmpty()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(?:^|\\s)" + java.util.regex.Pattern.quote(kernel.trim()) + "\\s+([1-5])(?=\\s|$)")
                .matcher(description);
        return m.find() ? "SAFIRE " + m.group(1) : null;
    }

    private static String field(String header) {
        String best = null;
        for (String f : FIELDS)
            if (header.startsWith(f) && header.substring(f.length()).matches("\\d*") && (best == null || f.length() > best.length())) best = f;
        return best;
    }

    // Scan type from the pitch (a range with table feed is spiral, without is sequential) and the dose
    // modulation line from CARE Dose / CARE kV - only known once all of a range's rows are in.
    private static void finish(Protocol p) {
        // Recon descriptions repeat within a protocol (a "3.0 MPR cor" per kernel), but changes typed for a recon
        // are matched by its name - so a repeated name gets its kernel added, and a number if that's not enough.
        // (The kernel only helps when the repeats differ by kernel; identical repeats, like a protocol's two topograms, just get numbered.)
        Map<String, Set<String>> kernels = new HashMap<String, Set<String>>();
        for (Series s : p.getSeries()) for (Group g : s.getGroups()) for (Reconstruction r : g.getReconstructions())
            kernels.computeIfAbsent(r.getName().toUpperCase(Locale.ROOT), k -> new HashSet<String>()).add(String.valueOf(r.getKernel()));
        Set<String> used = new HashSet<String>();
        for (Series s : p.getSeries()) for (Group g : s.getGroups()) for (Reconstruction r : g.getReconstructions()) {
            String name = r.getName();
            if (kernels.get(name.toUpperCase(Locale.ROOT)).size() > 1 && r.getKernel() != null) name += " (" + r.getKernel() + ")";
            String unique = name;
            for (int n = 2; !used.add(unique.toUpperCase(Locale.ROOT)); n++) unique = name + " #" + n;
            r.setName(unique);
        }
        for (Series s : p.getSeries()) {
            Acquisition a = s.getGroups().get(0).getAcquisition();
            if (!"Scout".equals(s.getScanType())) {
                Double pitch = null;
                try { if (a.getPitch() != null) pitch = Double.valueOf(a.getPitch()); } catch (NumberFormatException ignored) {}
                s.setScanType(pitch != null && pitch > 0 ? "Spiral" : "Sequence");
                if (pitch == null || pitch <= 0) a.setPitch(null); // a sequence has no table feed, so no pitch
            } else {
                a.setPitch(null); // a topogram's "pitch" is always 0
            }
        }
    }
}
