package com.protocolbook.parser;

import com.protocolbook.model.*;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses one protocol folder exported from a GE Revolution scanner
 * (protocolmetadata.json + UIRx.xml, with session.xml supplying the
 * human-readable series/recon names) into a single {@link Protocol}.
 */
public class UIRxProtocolParser {
    private static final Pattern GROUP_PATH = Pattern.compile("series\\[(\\d+)\\]\\.group\\[(\\d+)\\]\\.recon\\[(\\d+)\\]");

    public Protocol parse(File folder) throws Exception {
        File metaFile = new File(folder, "protocolmetadata.json");
        File uirxFile = new File(folder, "UIRx.xml");
        File sessionFile = new File(folder, "session.xml");
        if (!metaFile.isFile() || !uirxFile.isFile())
            throw new IllegalArgumentException("Not a protocol folder (missing protocolmetadata.json/UIRx.xml): " + folder);

        Protocol p = new Protocol();
        p.setMetadata(new Metadata());
        p.setPatientSetup(new PatientSetup());
        p.setContrast(new Contrast());
        p.setDose(new Dose());

        applyMetadataJson(metaFile, p);
        Map<String, String> reconNames = Collections.emptyMap();
        List<String> seriesScanTypes = Collections.emptyList();
        if (sessionFile.isFile()) {
            Document sessionDoc = parseXml(sessionFile);
            reconNames = readReconDisplayNames(sessionDoc);
            seriesScanTypes = readSeriesScanTypes(sessionDoc);
        }
        parseUirx(uirxFile, p, reconNames, seriesScanTypes);
        return p;
    }

    private void applyMetadataJson(File metaFile, Protocol p) throws Exception {
        JSONObject json = new JSONObject(new String(Files.readAllBytes(metaFile.toPath()), StandardCharsets.UTF_8));
        Metadata m = p.getMetadata();
        m.setName(json.optString("name", null));
        m.setProtocolNumber(json.optString("slotNumber", null));
        m.setPatientType(json.optString("humanoid", null));
        m.setBodyPart(json.optString("anatomyRegion", null));
        m.setLibrary(json.optString("library", null));
        m.setUuid(json.optString("uuid", null));
    }

    // series[i].group[j].recon[k] -> friendly display name pulled from session.xml's CTReconTask DESCRIPTION
    private Map<String, String> readReconDisplayNames(Document doc) {
        Map<String, String> out = new HashMap<String, String>();
        for (Element task : descendants(doc.getDocumentElement(), "task")) {
            String type = task.getAttribute("type");
            if (type == null || !type.contains("ReconTask")) continue;
            String description = null, path = null;
            for (Element prop : children(task, "property")) {
                String name = prop.getAttribute("name");
                if ("DESCRIPTION".equals(name)) description = prop.getAttribute("value");
                else if ("SELECTED_GROUP_PATHS".equals(name)) path = prop.getAttribute("value");
            }
            if (description == null || description.isEmpty() || path == null) continue;
            Matcher matcher = GROUP_PATH.matcher(path);
            if (matcher.find()) out.put(matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3), description);
        }
        return out;
    }

    // CTSeriesTask elements appear in the same document order as jrx:series, so position i pairs with series[i]
    private List<String> readSeriesScanTypes(Document doc) {
        List<String> out = new ArrayList<String>();
        for (Element task : descendants(doc.getDocumentElement(), "task")) {
            if (!task.getAttribute("type").contains("CTSeriesTask")) continue;
            String scanType = null;
            for (Element prop : children(task, "property")) if ("scanType".equals(prop.getAttribute("name"))) scanType = prop.getAttribute("value");
            out.add(scanType);
        }
        return out;
    }

    private void parseUirx(File uirxFile, Protocol p, Map<String, String> reconNames, List<String> seriesScanTypes) throws Exception {
        Document doc = parseXml(uirxFile);
        Element examEl = children(doc.getDocumentElement(), "exam").get(0);
        Map<String, String> examVals = ulements(examEl);
        p.getDose().setCtdi(ParseSupport.decimal(examVals.remove("ExamCtdi"), p, "ExamCtdi"));
        p.getDose().setDlp(ParseSupport.decimal(examVals.remove("ExamDLP"), p, "ExamDLP"));
        applyContrast(examVals, p.getContrast());
        remainder(p, "exam", examVals);

        Element protoEl = children(examEl, "proto").get(0);
        Map<String, String> protoVals = ulements(protoEl);
        p.getMetadata().setVersion(protoVals.remove("protocolVersion"));
        String name = protoVals.remove("name");
        if (name != null && !name.isEmpty()) p.getMetadata().setName(name);
        String anatomy = protoVals.remove("anatomy");
        if (anatomy != null && !anatomy.isEmpty()) p.getMetadata().setBodyPart(anatomy);
        String category = protoVals.remove("category");
        if (category != null && !category.isEmpty()) p.getMetadata().setPatientType(category);
        String notes = protoVals.remove("protocolNotes");
        if (notes != null && !notes.isEmpty()) p.getNotes().add(notes);
        remainder(p, "proto", protoVals);

        List<Element> seriesEls = children(protoEl, "series");
        for (int si = 0; si < seriesEls.size(); si++) {
            String scanType = si < seriesScanTypes.size() ? seriesScanTypes.get(si) : null;
            p.getSeries().add(parseSeries(seriesEls.get(si), si, reconNames, scanType, p));
        }
    }

    private Series parseSeries(Element seriesEl, int si, Map<String, String> reconNames, String scanType, Protocol p) {
        Series series = new Series();
        series.setNumber(si + 1);
        series.setScanType(scanType);
        Map<String, String> vals = ulements(seriesEl);
        series.setContrast(new Contrast());
        applyContrast(vals, series.getContrast());
        remainder(p, "series[" + si + "]", vals);

        List<Element> groupEls = children(seriesEl, "group");
        for (int gi = 0; gi < groupEls.size(); gi++) series.getGroups().add(parseGroup(groupEls.get(gi), si, gi, reconNames, p));
        for (Element injectorEl : children(seriesEl, "injector")) applyInjector(injectorEl, series, p, si);

        if (series.getName() == null && !series.getGroups().isEmpty() && !series.getGroups().get(0).getReconstructions().isEmpty())
            series.setName(series.getGroups().get(0).getReconstructions().get(0).getName());
        return series;
    }

    private Group parseGroup(Element groupEl, int si, int gi, Map<String, String> reconNames, Protocol p) {
        Group group = new Group();
        Map<String, String> vals = ulements(groupEl);
        Acquisition a = group.getAcquisition();
        a.setKv(vals.remove("kiloVolts"));
        a.setMa(vals.remove("milliAmps"));
        a.setMinMa(vals.remove("milliAmpsMin"));
        a.setMaxMa(vals.remove("milliAmpsMax"));
        a.setNoiseIndex(vals.remove("referenceNoiseIndex"));
        a.setPitch(vals.remove("pitch"));
        a.setRotationTime(vals.remove("rotationTime"));
        a.setDetector(vals.remove("macroRowNumber"));
        a.setFieldOfView(vals.remove("scanFieldOfViewType"));
        group.getDose().setCtdi(ParseSupport.decimal(vals.remove("CTDI"), p, "series[" + si + "].group[" + gi + "] CTDI"));
        group.getDose().setDlp(ParseSupport.decimal(vals.remove("DLP"), p, "series[" + si + "].group[" + gi + "] DLP"));
        String plane = vals.remove("scoutPlane");

        List<Element> reconEls = children(groupEl, "recon");
        for (int ri = 0; ri < reconEls.size(); ri++) {
            Reconstruction rec = parseRecon(reconEls.get(ri), plane, reconNames.get(si + "." + gi + "." + ri), p, si, gi, ri);
            group.getReconstructions().add(rec);
        }
        remainder(p, "series[" + si + "].group[" + gi + "]", vals);
        return group;
    }

    private Reconstruction parseRecon(Element reconEl, String plane, String displayName, Protocol p, int si, int gi, int ri) {
        Reconstruction rec = new Reconstruction();
        Map<String, String> vals = ulements(reconEl);
        String shortLabel = vals.remove("seriesDescriptionRecon");
        rec.setName(displayName != null ? displayName : shortLabel);
        rec.setKernel(vals.remove("reconKernel"));
        rec.setThickness(vals.remove("reconImageThickness"));
        rec.setInterval(vals.remove("reconInterval"));
        rec.setPlane(plane);
        rec.setMatrix(vals.remove("reconMatrix"));
        rec.setDfov(vals.remove("displayFieldOfView"));
        rec.setWindowLevel(vals.remove("windowLevel"));
        rec.setWindowWidth(vals.remove("windowWidth"));
        rec.setIterativeConfig(vals.remove("iterativeConfig"));
        rec.setStartLocation(vals.remove("reconStartLocation"));
        rec.setEndLocation(vals.remove("reconEndLocation"));
        rec.setNumberOfImages(ParseSupport.integer(vals.remove("reconNumberOfImages"), p, "series[" + si + "].group[" + gi + "].recon[" + ri + "] images"));
        remainder(p, "series[" + si + "].group[" + gi + "].recon[" + ri + "]", vals);
        return rec;
    }

    private void applyInjector(Element injectorEl, Series series, Protocol p, int si) {
        List<Element> phases = children(injectorEl, "phase");
        if (phases.isEmpty()) return;
        Map<String, String> vals = ulements(phases.get(0));
        Contrast c = series.getContrast();
        c.setFlowRate(vals.remove("totalFlowRate"));
        c.setInjectionDelay(vals.remove("injectionDelay"));
        c.setInjectionDuration(vals.remove("injectionDuration"));
        c.setMediaRatio(vals.remove("contrastMediaRatio"));
        remainder(p, "series[" + si + "].injector.phase[0]", vals);
        for (int i = 1; i < phases.size(); i++) remainder(p, "series[" + si + "].injector.phase[" + i + "]", ulements(phases.get(i)));
    }

    private void applyContrast(Map<String, String> vals, Contrast c) {
        String ivVolume = vals.remove("contrast_iv_volume");
        String ivConcentration = vals.remove("contrast_iv_concentration");
        String oralVolume = vals.remove("contrast_oral_volume");
        String oralConcentration = vals.remove("contrast_oral_concentration");
        vals.remove("contrast_iv"); vals.remove("contrast_oral");
        vals.remove("contrast_iv_series_number"); vals.remove("contrast_oral_series_number");
        if (ivVolume != null && !ivVolume.isEmpty()) { c.setIvVolume(ivVolume); c.setIv(true); }
        if (ivConcentration != null && !ivConcentration.isEmpty()) c.setIvConcentration(ivConcentration);
        if (oralVolume != null && !oralVolume.isEmpty()) { c.setOralVolume(oralVolume); c.setOral(true); }
        if (oralConcentration != null && !oralConcentration.isEmpty()) c.setOralConcentration(oralConcentration);
    }

    private void remainder(Protocol p, String prefix, Map<String, String> vals) {
        for (Map.Entry<String, String> e : vals.entrySet()) ParseSupport.putAdvanced(p, prefix + " " + e.getKey(), e.getValue());
    }

    // ---- DOM helpers: namespace-agnostic (GE mixes unprefixed / "session:" / "ses:" for the same elements) ----

    private static Document parseXml(File file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        return dbf.newDocumentBuilder().parse(file);
    }

    private static String localName(Node n) {
        String tag = n.getNodeName();
        int i = tag.indexOf(':');
        return i < 0 ? tag : tag.substring(i + 1);
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<Element>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && localName(n).equals(localName)) out.add((Element) n);
        }
        return out;
    }

    private static List<Element> descendants(Element root, String localName) {
        List<Element> out = new ArrayList<Element>();
        collectDescendants(root, localName, out);
        return out;
    }

    private static void collectDescendants(Node n, String localName, List<Element> out) {
        NodeList nl = n.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node c = nl.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                if (localName(c).equals(localName)) out.add((Element) c);
                collectDescendants(c, localName, out);
            }
        }
    }

    private static Map<String, String> ulements(Element parent) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (Element e : children(parent, "ulement")) m.put(e.getAttribute("name"), e.getAttribute("value"));
        return m;
    }
}
