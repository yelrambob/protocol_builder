package com.protocolbook.parser;

import com.protocolbook.model.*;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.util.*;

/** Tolerant parser for GE protocol workbooks stored as XLSX/XLSM. */
public class GEWorkbookParser implements ProtocolParser {
    private final DataFormatter formatter = new DataFormatter(Locale.US);
    private final FormulaEvaluator[] evaluator = new FormulaEvaluator[1];

    public List<Protocol> parse(File file) throws Exception {
        validateFile(file);
        List<Protocol> result = new ArrayList<Protocol>();
        try (InputStream in = new BufferedInputStream(new FileInputStream(file)); Workbook wb = WorkbookFactory.create(in)) {
            evaluator[0] = wb.getCreationHelper().createFormulaEvaluator();
            for (Sheet sheet : wb) if (isProtocolSheet(sheet)) result.add(parseSheet(sheet));
        } catch (EncryptedDocumentException e) {
            throw new IllegalArgumentException("Workbook is password protected. Save an unprotected copy and try again.", e);
        } catch (IOException e) {
            throw new IOException("Could not read '" + file + "'. Close it in Excel, verify it is a valid .xlsx/.xlsm file, and try again.", e);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No protocol worksheets were detected. Expected populated sheets containing protocol fields such as Protocol Name, kV, Series, or Reconstruction.");
        return result;
    }

    private void validateFile(File f) {
        if (f == null) throw new IllegalArgumentException("No workbook path was supplied.");
        if (!f.isFile()) throw new IllegalArgumentException("Workbook not found: " + f.getAbsolutePath() + ". Pass the path explicitly, for example: gradlew run --args=\"C:\\\\path\\\\Protocols.xlsm\"");
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (!(n.endsWith(".xlsm") || n.endsWith(".xlsx") || n.endsWith(".xls"))) throw new IllegalArgumentException("Unsupported input '" + f.getName() + "'. Expected .xlsm, .xlsx, or .xls.");
    }

    private boolean isProtocolSheet(Sheet s) {
        String name = norm(s.getSheetName());
        if (s.getWorkbook().isSheetHidden(s.getWorkbook().getSheetIndex(s)) || name.matches(".*(cover|instruction|contents|index|lookup|config|template).*")) return false;
        int populated = 0, signals = 0;
        for (Row row : s) for (Cell cell : row) {
            String v = norm(text(cell)); if (v.isEmpty()) continue;
            populated++;
            if (v.matches(".*(protocol|scan type|kv|mas|pitch|series|recon|patient position|contrast|ctdi|dlp).*")) signals++;
            if (populated >= 8 && signals >= 1) return true;
        }
        return populated >= 3 && (signals >= 2 || name.matches(".*(protocol|ct|ge).*"));
    }

    private Protocol parseSheet(Sheet sheet) {
        Protocol p = new Protocol();
        p.setMetadata(new Metadata()); p.getMetadata().setName(sheet.getSheetName());
        p.setPatientSetup(new PatientSetup()); p.setContrast(new Contrast()); p.setAcquisition(new Acquisition()); p.setDose(new Dose()); p.setTiming(new Timing());
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex); if (row == null) continue;
            List<Cell> cells = nonEmpty(row); if (cells.isEmpty()) continue;
            if (looksLikeTableHeader(cells)) { rowIndex = parseTable(sheet, row, cells, p); continue; }
            String label = text(cells.get(0));
            String value = cells.size() > 1 ? joinValues(cells, 1) : "";
            if (!value.isEmpty()) map(p, label, value, sheet.getSheetName() + "!" + cells.get(0).getAddress());
            else if (norm(label).matches(".*(note|comment|instruction).*")) p.getNotes().add(label);
        }
        return p;
    }

    private List<Cell> nonEmpty(Row row) {
        List<Cell> out = new ArrayList<Cell>(); if (row == null) return out;
        for (Cell c : row) if (!text(c).trim().isEmpty()) out.add(c); return out;
    }
    private boolean looksLikeTableHeader(List<Cell> c) {
        if (c.size() < 2) return false; String all = norm(joinValues(c, 0));
        return all.contains("series") && (all.contains("description") || all.contains("kernel") || all.contains("recon"));
    }
    private int parseTable(Sheet sheet, Row header, List<Cell> headerCells, Protocol p) {
        Map<Integer,String> headings = new LinkedHashMap<Integer,String>();
        for (Cell c : headerCells) headings.put(c.getColumnIndex(), norm(text(c)));
        int last = header.getRowNum();
        for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r); if (row == null) break;
            boolean any = false; for (Integer col : headings.keySet()) if (!text(row.getCell(col)).isEmpty()) any = true;
            if (!any) break;
            last = r;
            Reconstruction rec = new Reconstruction(); Series series = new Series(); boolean hasSeries = false, hasRec = false;
            for (Map.Entry<Integer,String> h : headings.entrySet()) {
                String v = text(row.getCell(h.getKey())); if (v.isEmpty()) continue; String k = h.getValue();
                if (k.matches(".*series.*(number|no|#).*")) { series.setNumber(integer(v, p, "series number")); hasSeries=true; }
                else if (k.contains("series") || k.contains("description")) { series.setName(v); hasSeries=true; }
                else if (k.contains("kernel") || k.contains("algorithm")) { rec.setKernel(v); hasRec=true; }
                else if (k.contains("thick")) { rec.setThickness(v); hasRec=true; }
                else if (k.contains("interval") || k.contains("increment")) { rec.setInterval(v); hasRec=true; }
                else if (k.contains("plane")) { rec.setPlane(v); hasRec=true; }
                else if (k.contains("recon")) { rec.setName(v); hasRec=true; }
                else advanced(p, sheet.getSheetName()+"!"+row.getCell(h.getKey()).getAddress()+" "+text(header.getCell(h.getKey())), v);
            }
            if (hasRec) { p.getReconstructions().add(rec); Group group = new Group(); group.getReconstructions().add(rec); series.getGroups().add(group); }
            if (hasSeries) p.getSeries().add(series);
        }
        return last;
    }

    private void map(Protocol p, String raw, String v, String location) {
        String k = norm(raw).replaceAll("[^a-z0-9]+", " ").trim();
        Metadata m=p.getMetadata(); PatientSetup ps=p.getPatientSetup(); Contrast c=p.getContrast(); Acquisition a=p.getAcquisition(); Dose d=p.getDose(); Timing t=p.getTiming();
        if (matches(k,"protocol name","protocol","exam name")) m.setName(v);
        else if (contains(k,"category")) m.setCategory(v); else if (contains(k,"body part","anatomy")) m.setBodyPart(v);
        else if (contains(k,"version","revision")) m.setVersion(v); else if (contains(k,"scanner","system","equipment")) m.setScanner(v);
        else if (contains(k,"indication")) m.setClinicalIndication(v); else if (contains(k,"patient position","position")) ps.setPosition(v);
        else if (contains(k,"orientation","patient entry")) ps.setOrientation(v); else if (contains(k,"breath")) ps.setBreathing(v);
        else if (contains(k,"arms")) ps.setArms(v); else if (contains(k,"scan range","coverage","landmark")) ps.setScanRange(v);
        else if (matches(k,"kv","kvp","tube voltage")) a.setKv(v); else if (matches(k,"ma","mas","smart ma","tube current")) a.setMa(v);
        else if (contains(k,"rotation")) a.setRotationTime(v); else if (contains(k,"pitch")) a.setPitch(v);
        else if (contains(k,"detector","collimation")) a.setDetector(v); else if (contains(k,"slice thickness","acq thickness")) a.setSliceThickness(v);
        else if (contains(k,"interval","increment")) a.setInterval(v); else if (matches(k,"fov","field of view","dfov")) a.setFieldOfView(v);
        else if (contains(k,"matrix")) a.setMatrix(v); else if (contains(k,"ctdi")) d.setCtdi(decimal(v,p,raw));
        else if (matches(k,"dlp","dose length product")) d.setDlp(decimal(v,p,raw)); else if (contains(k,"dose modulation","automA","smart ma")) d.setDoseModulation(v);
        else if (contains(k,"bolus","smart prep")) t.setBolusTracking(v); else if (contains(k,"roi")) t.setRoiLocation(v); else if (contains(k,"delay")) t.setDelay(v);
        else if (contains(k,"iv contrast","intravenous")) c.setIv(yes(v)); else if (contains(k,"oral contrast")) c.setOral(yes(v)); else if (contains(k,"rectal contrast")) c.setRectal(yes(v));
        else if (contains(k,"contrast timing")) c.setTiming(v); else if (contains(k,"contrast note")) c.setNotes(v);
        else if (contains(k,"note","comment","instruction")) p.getNotes().add(raw + ": " + v);
        else advanced(p, location + " " + raw, v);
    }

    private void advanced(Protocol p,String key,String value){ParseSupport.putAdvanced(p,key,value);}
    private String text(Cell c){if(c==null)return ""; try{return formatter.formatCellValue(c,evaluator[0]).trim();}catch(RuntimeException e){return formatter.formatCellValue(c).trim();}}
    private String joinValues(List<Cell> c,int from){StringBuilder b=new StringBuilder(); for(int i=from;i<c.size();i++){String v=text(c.get(i));if(!v.isEmpty()){if(b.length()>0)b.append(" | ");b.append(v);}}return b.toString();}
    private String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace('\u00a0',' ').replaceAll("\\s+"," ").trim();}
    private boolean contains(String k,String... terms){for(String t:terms)if(k.contains(norm(t)))return true;return false;}
    private boolean matches(String k,String... terms){for(String t:terms)if(k.equals(norm(t)))return true;return false;}
    private boolean yes(String s){String v=norm(s);return v.equals("yes")||v.equals("y")||v.equals("true")||v.equals("1")||v.contains("required");}
    private Double decimal(String s,Protocol p,String field){return ParseSupport.decimal(s,p,field);}
    private int integer(String s,Protocol p,String field){Integer v=ParseSupport.integer(s,p,field); return v==null?0:v;}
}
