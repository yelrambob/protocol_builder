package com.protocolbook.io;

import com.protocolbook.model.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/** Writes one normalized, self-contained JSON file per Protocol - a cache/snapshot of a parse, not a new source of truth. */
public class ProtocolJsonWriter {

    public List<File> writeAll(List<Protocol> protocols, File outDir) throws IOException {
        if (!outDir.isDirectory() && !outDir.mkdirs()) throw new IOException("Could not create output directory: " + outDir);
        List<File> written = new java.util.ArrayList<File>();
        for (Protocol p : protocols) written.add(write(p, outDir));
        return written;
    }

    public File write(Protocol p, File outDir) throws IOException {
        File out = new File(outDir, fileNameFor(p));
        try (FileWriter w = new FileWriter(out)) { w.write(toJson(p).toString(2)); }
        return out;
    }

    private String fileNameFor(Protocol p) {
        Metadata m = p.getMetadata();
        String slot = m != null && m.getProtocolNumber() != null ? m.getProtocolNumber() + "_" : "";
        String name = m != null && m.getName() != null ? m.getName() : "protocol";
        return (slot + name).replaceAll("[^a-zA-Z0-9._-]+", "_") + ".json";
    }

    private JSONObject toJson(Protocol p) {
        JSONObject j = new JSONObject();
        j.put("metadata", metadataJson(p.getMetadata()));
        j.put("patientSetup", patientSetupJson(p.getPatientSetup()));
        j.put("contrast", contrastJson(p.getContrast()));
        j.put("dose", doseJson(p.getDose()));
        JSONArray series = new JSONArray();
        for (Series s : p.getSeries()) series.put(seriesJson(s));
        j.put("series", series);
        j.put("notes", new JSONArray(p.getNotes()));
        j.put("advanced", new JSONObject(p.getAdvanced()));
        return j;
    }

    private JSONObject metadataJson(Metadata m) {
        JSONObject j = new JSONObject();
        if (m == null) return j;
        put(j, "name", m.getName()); put(j, "protocolNumber", m.getProtocolNumber());
        put(j, "patientType", m.getPatientType()); put(j, "bodyPart", m.getBodyPart());
        put(j, "category", m.getCategory()); put(j, "version", m.getVersion());
        put(j, "scanner", m.getScanner()); put(j, "clinicalIndication", m.getClinicalIndication());
        put(j, "library", m.getLibrary()); put(j, "uuid", m.getUuid()); put(j, "lastUpdated", m.getLastUpdated());
        return j;
    }

    private JSONObject patientSetupJson(PatientSetup ps) {
        JSONObject j = new JSONObject();
        if (ps == null) return j;
        put(j, "position", ps.getPosition()); put(j, "orientation", ps.getOrientation());
        put(j, "breathing", ps.getBreathing()); put(j, "arms", ps.getArms()); put(j, "scanRange", ps.getScanRange());
        return j;
    }

    private JSONObject doseJson(Dose d) {
        JSONObject j = new JSONObject();
        if (d == null) return j;
        put(j, "ctdi", d.getCtdi()); put(j, "dlp", d.getDlp()); put(j, "doseModulation", d.getDoseModulation());
        return j;
    }

    private JSONObject contrastJson(Contrast c) {
        JSONObject j = new JSONObject();
        if (c == null) return j;
        j.put("iv", c.isIv()); j.put("oral", c.isOral()); j.put("rectal", c.isRectal());
        put(j, "ivVolume", c.getIvVolume()); put(j, "ivConcentration", c.getIvConcentration());
        put(j, "oralVolume", c.getOralVolume()); put(j, "oralConcentration", c.getOralConcentration());
        put(j, "flowRate", c.getFlowRate()); put(j, "injectionDelay", c.getInjectionDelay());
        put(j, "injectionDuration", c.getInjectionDuration()); put(j, "mediaRatio", c.getMediaRatio());
        put(j, "timing", c.getTiming()); put(j, "notes", c.getNotes());
        return j;
    }

    private JSONObject seriesJson(Series s) {
        JSONObject j = new JSONObject();
        j.put("number", s.getNumber());
        put(j, "name", s.getName()); put(j, "description", s.getDescription()); put(j, "scanType", s.getScanType());
        j.put("derived", s.isDerived());
        if (s.getContrast() != null) j.put("contrast", contrastJson(s.getContrast()));
        JSONArray groups = new JSONArray();
        for (Group g : s.getGroups()) groups.put(groupJson(g));
        j.put("groups", groups);
        return j;
    }

    private JSONObject groupJson(Group g) {
        JSONObject j = new JSONObject();
        j.put("acquisition", acquisitionJson(g.getAcquisition()));
        j.put("dose", doseJson(g.getDose()));
        JSONArray recons = new JSONArray();
        for (Reconstruction r : g.getReconstructions()) recons.put(reconstructionJson(r));
        j.put("reconstructions", recons);
        return j;
    }

    private JSONObject acquisitionJson(Acquisition a) {
        JSONObject j = new JSONObject();
        if (a == null) return j;
        put(j, "kv", a.getKv()); put(j, "ma", a.getMa()); put(j, "minMa", a.getMinMa()); put(j, "maxMa", a.getMaxMa());
        put(j, "noiseIndex", a.getNoiseIndex()); put(j, "rotationTime", a.getRotationTime()); put(j, "pitch", a.getPitch());
        put(j, "detector", a.getDetector()); put(j, "sliceThickness", a.getSliceThickness()); put(j, "interval", a.getInterval());
        put(j, "fieldOfView", a.getFieldOfView()); put(j, "matrix", a.getMatrix());
        return j;
    }

    private JSONObject reconstructionJson(Reconstruction r) {
        JSONObject j = new JSONObject();
        put(j, "name", r.getName()); put(j, "kernel", r.getKernel()); put(j, "thickness", r.getThickness());
        put(j, "interval", r.getInterval()); put(j, "plane", r.getPlane()); put(j, "matrix", r.getMatrix());
        put(j, "dfov", r.getDfov()); put(j, "windowLevel", r.getWindowLevel()); put(j, "windowWidth", r.getWindowWidth());
        put(j, "iterativeConfig", r.getIterativeConfig()); put(j, "startLocation", r.getStartLocation());
        put(j, "endLocation", r.getEndLocation()); put(j, "numberOfImages", r.getNumberOfImages());
        j.put("derived", r.isDerived());
        return j;
    }

    private void put(JSONObject j, String key, Object value) { j.put(key, value == null ? JSONObject.NULL : value); }
}
