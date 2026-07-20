package com.protocolbook.model;

import java.util.*;

public class Protocol {

    private Metadata metadata;

    private PatientSetup patientSetup;

    private Contrast contrast;

    private Acquisition acquisition;

    private Dose dose;

    private List<Series> series = new ArrayList<>();

    private List<Reconstruction> reconstructions = new ArrayList<>();

    private Timing timing;

    private List<String> notes = new ArrayList<>();

    private Map<String,String> advanced = new LinkedHashMap<>();

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public PatientSetup getPatientSetup() {
        return patientSetup;
    }

    public void setPatientSetup(PatientSetup patientSetup) {
        this.patientSetup = patientSetup;
    }

    public Contrast getContrast() {
        return contrast;
    }

    public void setContrast(Contrast contrast) {
        this.contrast = contrast;
    }

    public Acquisition getAcquisition() {
        return acquisition;
    }

    public void setAcquisition(Acquisition acquisition) {
        this.acquisition = acquisition;
    }

    public Dose getDose() {
        return dose;
    }

    public void setDose(Dose dose) {
        this.dose = dose;
    }

    public List<Series> getSeries() {
        return series;
    }

    public List<Reconstruction> getReconstructions() {
        return reconstructions;
    }

    public Timing getTiming() {
        return timing;
    }

    public void setTiming(Timing timing) {
        this.timing = timing;
    }

    public List<String> getNotes() {
        return notes;
    }

    public Map<String, String> getAdvanced() {
        return advanced;
    }
}
