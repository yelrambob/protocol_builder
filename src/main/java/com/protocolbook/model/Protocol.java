package com.protocolbook.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Protocol {
    private Metadata metadata;
    private PatientSetup patientSetup;
    private Contrast contrast;
    private Acquisition acquisition;
    private Dose dose;
    private final List<Series> series = new ArrayList<Series>();
    private final List<Reconstruction> reconstructions = new ArrayList<Reconstruction>();
    private Timing timing;
    private final List<String> notes = new ArrayList<String>();
    private final Map<String, String> advanced = new LinkedHashMap<String, String>();

    public Metadata getMetadata() { return metadata; }
    public void setMetadata(Metadata value) { metadata = value; }
    public PatientSetup getPatientSetup() { return patientSetup; }
    public void setPatientSetup(PatientSetup value) { patientSetup = value; }
    public Contrast getContrast() { return contrast; }
    public void setContrast(Contrast value) { contrast = value; }
    public Acquisition getAcquisition() { return acquisition; }
    public void setAcquisition(Acquisition value) { acquisition = value; }
    public Dose getDose() { return dose; }
    public void setDose(Dose value) { dose = value; }
    public List<Series> getSeries() { return series; }
    public List<Reconstruction> getReconstructions() { return reconstructions; }
    public Timing getTiming() { return timing; }
    public void setTiming(Timing value) { timing = value; }
    public List<String> getNotes() { return notes; }
    public Map<String, String> getAdvanced() { return advanced; }
}
