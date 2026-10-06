package com.protocolbook.model;
public class Acquisition {
    private String kv, ma, rotationTime, pitch, detector, sliceThickness, interval, fieldOfView, matrix;
    private String minMa, maxMa, noiseIndex, maMode, scanDelay;
    private String qualityRefMas, doseModulation, maUnit;
    public String getKv(){return kv;} public void setKv(String v){kv=v;}
    public String getMa(){return ma;} public void setMa(String v){ma=v;}
    public String getRotationTime(){return rotationTime;} public void setRotationTime(String v){rotationTime=v;}
    public String getPitch(){return pitch;} public void setPitch(String v){pitch=v;}
    public String getDetector(){return detector;} public void setDetector(String v){detector=v;}
    public String getSliceThickness(){return sliceThickness;} public void setSliceThickness(String v){sliceThickness=v;}
    public String getInterval(){return interval;} public void setInterval(String v){interval=v;}
    public String getFieldOfView(){return fieldOfView;} public void setFieldOfView(String v){fieldOfView=v;}
    public String getMatrix(){return matrix;} public void setMatrix(String v){matrix=v;}
    public String getMinMa(){return minMa;} public void setMinMa(String v){minMa=v;}
    public String getMaxMa(){return maxMa;} public void setMaxMa(String v){maxMa=v;}
    public String getNoiseIndex(){return noiseIndex;} public void setNoiseIndex(String v){noiseIndex=v;}
    // Presence (any value) means SmartmA/auto-mA is active for this group, and "ma" is a stale
    // fallback the console keeps around - minMa/maxMa is the real setting when this is non-null.
    public String getMaMode(){return maMode;} public void setMaMode(String v){maMode=v;}
    // Seconds from the start of the series (i.e. from injection, for a contrast series) to this group's scan - GE's groupDelay.
    public String getScanDelay(){return scanDelay;} public void setScanDelay(String v){scanDelay=v;}
    // Siemens: CARE Dose4D's quality reference mAs, the dose modulation in use (e.g. "CARE Dose4D", "Off"),
    // and "mAs" as the unit of "ma" (Siemens gives effective mAs, GE gives mA). maUnit null means mA.
    public String getQualityRefMas(){return qualityRefMas;} public void setQualityRefMas(String v){qualityRefMas=v;}
    public String getDoseModulation(){return doseModulation;} public void setDoseModulation(String v){doseModulation=v;}
    public String getMaUnit(){return maUnit == null ? "mA" : maUnit;} public void setMaUnit(String v){maUnit=v;}

    /**
     * milliAmpsMode is a mode code, not a flag - "0" means SmartmA/auto-mA is off (a fixed-dose
     * group can still carry populated min/max fields the console records regardless), and any
     * other value means some auto-mA mode is active.
     */
    public boolean isAutoMa() {
        return maMode != null && !"0".equals(maMode) && minMa != null && maxMa != null;
    }

    /**
     * The real helical pitch (e.g. 0.992 for "0.992:1"). GE exports pitch as table travel per
     * rotation in detector rows, so the ratio is that value over the row count (macroRowNumber):
     * 127/128 = 0.992, 88/64 = 1.375, 33/64 = 0.516. A value that already looks like a ratio
     * (under 4 - no CT pitch is higher) is taken as-is, e.g. from a workbook. Null if unknown.
     */
    public Double pitchRatio() {
        Double raw = number(pitch);
        if (raw == null || raw <= 0) return null;
        if (raw < 4) return raw;
        Double rows = number(detector);
        return rows == null || rows <= 0 ? null : raw / rows;
    }

    static Double number(String v) {
        if (v == null) return null;
        try { return Double.valueOf(v.trim()); } catch (NumberFormatException e) { return null; }
    }
}
