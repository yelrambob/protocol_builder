package com.protocolbook.overrides;

import com.protocolbook.model.Group;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hand-authored addition to one protocol: a display title override, scanning notes, exclusion
 * from the generated book, where its images are sent, and/or a contrast volume/rate override.
 * Send destination isn't reliably derivable from the scanner export - session.xml logs which
 * network job actually ran for a given historical scan, not what the protocol template always
 * does, so a person has to state it here when it matters. Contrast volume/rate overrides exist
 * for the same reason a title override does: to correct what's shown in the book without needing
 * to touch (or being able to touch) the underlying scanner export.
 */
public class ProtocolOverride {
    private String title;
    private String notes;
    private boolean excluded;
    private String sendDestination;
    private String contrastVolume;
    private String contrastRate;
    private String referenceSheet;
    private String scanRange;
    private Map<String, String> reconSendDestinations = new LinkedHashMap<String, String>();
    private Boolean threeD;
    private String contrastDelay, examCtdi, examDlp;
    private Map<String, Map<String, String>> recons = new LinkedHashMap<String, Map<String, String>>();
    private Map<String, Map<String, String>> series = new LinkedHashMap<String, Map<String, String>>();
    private final List<AddedField> addedFields = new ArrayList<AddedField>();
    private final List<ScanRangePicture> scanRangePictures = new ArrayList<ScanRangePicture>();
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public boolean isExcluded(){return excluded;} public void setExcluded(boolean v){excluded=v;}
    public String getSendDestination(){return sendDestination;} public void setSendDestination(String v){sendDestination=v;}
    public String getContrastVolume(){return contrastVolume;} public void setContrastVolume(String v){contrastVolume=v;}
    public String getContrastRate(){return contrastRate;} public void setContrastRate(String v){contrastRate=v;}
    // Which reference-workbook sheet to take the scan range from, when matching by name picks the wrong one or none.
    public String getReferenceSheet(){return referenceSheet;} public void setReferenceSheet(String v){referenceSheet=v;}
    // Hand-typed scan range; wins over any reference-workbook sheet.
    public String getScanRange(){return scanRange;} public void setScanRange(String v){scanRange=v;}
    // Recon name -> comma-separated auto-send hosts, for when the export lists fewer hosts than the scanner really uses.
    public Map<String, String> getReconSendDestinations(){return reconSendDestinations;}
    // true/false forces the 3D MIP/VR series on or off; null (not set) decides from whether the protocol sends to AW Server.
    public Boolean getThreeD(){return threeD;} public void setThreeD(Boolean v){threeD=v;}

    // Delay/dose figures typed in to replace the scanner's.
    public String getContrastDelay(){return contrastDelay;} public void setContrastDelay(String v){contrastDelay=v;}
    public String getExamCtdi(){return examCtdi;} public void setExamCtdi(String v){examCtdi=v;}
    public String getExamDlp(){return examDlp;} public void setExamDlp(String v){examDlp=v;}
    /** Recon name -> field -> typed value; see {@link #RECON_FIELDS}. */
    public Map<String, Map<String, String>> getRecons(){return recons;}
    /** Series number (as shown in the book, e.g. "2") -> field -> typed value; see {@link #SERIES_FIELDS}. */
    public Map<String, Map<String, String>> getSeries(){return series;}

    /** Fields the scanner has no slot for (e.g. "Oral contrast"), added by hand to the whole exam or to one series. */
    public List<AddedField> getAddedFields(){return addedFields;}

    /** The added fields for one series number, or for the exam as a whole when seriesNumber is null. */
    public List<AddedField> addedFieldsFor(Integer seriesNumber) {
        List<AddedField> out = new ArrayList<AddedField>();
        for (AddedField f : addedFields) {
            if (f.isBlank()) continue;
            if (seriesNumber == null ? f.isExam() : String.valueOf(seriesNumber).equals(f.getSeries())) out.add(f);
        }
        return out;
    }

    /** Pictures (from the "scan range images" folder) with the scan range drawn on as colored boxes. */
    public List<ScanRangePicture> getScanRangePictures(){return scanRangePictures;}

    /** One picture from the scan range picture library and the boxes drawn on it for this protocol. */
    public static class ScanRangePicture {
        private String image;
        private final List<Box> boxes = new ArrayList<Box>();
        public ScanRangePicture() {}
        public ScanRangePicture(String image) { this.image = image; }
        /** File name inside the "scan range images" folder (next to protocol-overrides.json). */
        public String getImage(){return image;} public void setImage(String v){image=v;}
        public List<Box> getBoxes(){return boxes;}
    }

    /**
     * One box drawn on a picture. Position and size are fractions of the picture's width/height (0-1),
     * so the box stays put whatever size the picture is shown at.
     */
    public static class Box {
        private String label, color;
        private double x, y, w, h;
        public Box() {}
        public Box(String label, String color, double x, double y, double w, double h) {
            this.label = label; this.color = color; this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public Box copy() { return new Box(label, color, x, y, w, h); }
        public String getLabel(){return label;} public void setLabel(String v){label=v;}
        /** "#rrggbb" */
        public String getColor(){return color;} public void setColor(String v){color=v;}
        public double getX(){return x;} public void setX(double v){x=v;}
        public double getY(){return y;} public void setY(double v){y=v;}
        public double getW(){return w;} public void setW(double v){w=v;}
        public double getH(){return h;} public void setH(double v){h=v;}
    }

    /** One hand-added "Title: value" line, shown under the exam header or under one series. */
    public static class AddedField {
        private String series, title, value;
        public AddedField() {}
        public AddedField(String series, String title, String value) { this.series = series; this.title = title; this.value = value; }
        /** Series number as shown in the book (e.g. "2"), or null/blank for the whole exam. */
        public String getSeries(){return series;} public void setSeries(String v){series=v;}
        public String getTitle(){return title;} public void setTitle(String v){title=v;}
        public String getValue(){return value;} public void setValue(String v){value=v;}
        public boolean isExam() { return series == null || series.trim().isEmpty(); }
        public boolean isBlank() { return (title == null || title.trim().isEmpty()) && (value == null || value.trim().isEmpty()); }
    }

    /** Recon settings that can be typed in per recon, under "recons". */
    public static final List<String> RECON_FIELDS = Arrays.asList("name", "thickness", "interval", "kernel", "asir", "wwwl", "sendTo");
    /** Acquisition settings that can be typed in per series, under "series". */
    public static final List<String> SERIES_FIELDS = Arrays.asList("kv", "ma", "noiseIndex", "pitch", "rotationTime", "ctdi");

    /**
     * The typed value of one recon field (see RECON_FIELDS) for a recon, matched by name ignoring case and
     * repeated spaces, or null when nothing is typed for it. "sendTo" also falls back to the older
     * "reconSendDestinations" list.
     */
    public String reconField(Reconstruction r, String field) {
        for (Map.Entry<String, Map<String, String>> e : recons.entrySet())
            if (normalize(e.getKey()).equals(normalize(r.getName()))) {
                String v = e.getValue().get(field);
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        if ("sendTo".equals(field)) return typedDestinations(r.getName());
        return null;
    }

    /** The typed value of one acquisition field (see SERIES_FIELDS) for a series number, or null. */
    public String seriesField(int seriesNumber, String field) {
        Map<String, String> fields = series.get(String.valueOf(seriesNumber));
        String v = fields == null ? null : fields.get(field);
        return v != null && !v.trim().isEmpty() ? v.trim() : null;
    }

    /**
     * The auto-send hosts to show for a recon: the hand-typed list ("sendTo" under "recons", or the older
     * "reconSendDestinations") when one matches the recon's name, otherwise what the export listed.
     */
    public List<String> sendDestinationsFor(Reconstruction r) {
        String typed = reconField(r, "sendTo");
        if (typed == null) return r.getSendDestinations();
        List<String> out = new ArrayList<String>();
        for (String host : typed.split(",")) if (!host.trim().isEmpty() && !out.contains(host.trim())) out.add(host.trim());
        return out;
    }

    /**
     * Problems with what's typed for this protocol: recon names or series numbers that match nothing,
     * and field names that aren't recognized - almost always typos, which would otherwise do nothing silently.
     */
    public List<String> problems(List<Series> protocolSeries) {
        List<Reconstruction> all = new ArrayList<Reconstruction>();
        List<String> numbers = new ArrayList<String>();
        for (Series s : protocolSeries) {
            numbers.add(String.valueOf(s.getNumber()));
            for (Group g : s.getGroups()) all.addAll(g.getReconstructions());
        }
        List<String> out = new ArrayList<String>();
        for (String name : reconSendDestinations.keySet())
            if (!matchesAny(name, all)) out.add("reconSendDestinations: no recon named '" + name + "' - check the spelling against the book");
        for (Map.Entry<String, Map<String, String>> e : recons.entrySet()) {
            if (!matchesAny(e.getKey(), all)) out.add("recons: no recon named '" + e.getKey() + "' - check the spelling against the book");
            for (String field : e.getValue().keySet())
                if (!RECON_FIELDS.contains(field)) out.add("recons '" + e.getKey() + "': unknown field '" + field + "' (use one of " + RECON_FIELDS + ")");
        }
        for (Map.Entry<String, Map<String, String>> e : series.entrySet()) {
            if (!numbers.contains(e.getKey())) out.add("series: no series " + e.getKey() + " (this protocol has series " + numbers + ")");
            for (String field : e.getValue().keySet())
                if (!SERIES_FIELDS.contains(field)) out.add("series " + e.getKey() + ": unknown field '" + field + "' (use one of " + SERIES_FIELDS + ")");
        }
        for (AddedField f : addedFields)
            if (!f.isExam() && !numbers.contains(f.getSeries().trim()))
                out.add("addedFields '" + f.getTitle() + "': no series " + f.getSeries() + " (this protocol has series " + numbers + ")");
        return out;
    }

    private static boolean matchesAny(String name, List<Reconstruction> recons) {
        for (Reconstruction r : recons) if (normalize(name).equals(normalize(r.getName()))) return true;
        return false;
    }

    private String typedDestinations(String reconName) {
        for (Map.Entry<String, String> e : reconSendDestinations.entrySet())
            if (normalize(e.getKey()).equals(normalize(reconName)) && e.getValue() != null && !e.getValue().trim().isEmpty()) return e.getValue();
        return null;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
