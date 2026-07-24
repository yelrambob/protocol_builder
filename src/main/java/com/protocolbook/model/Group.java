package com.protocolbook.model;
import java.util.ArrayList; import java.util.List;
/** One acquisition pass within a series (GE's "group") and its reconstructions. */
public class Group {
    private Acquisition acquisition = new Acquisition();
    private Dose dose = new Dose();
    private final List<Reconstruction> reconstructions = new ArrayList<Reconstruction>();
    public Acquisition getAcquisition(){return acquisition;} public void setAcquisition(Acquisition v){acquisition=v;}
    public Dose getDose(){return dose;} public void setDose(Dose v){dose=v;}
    public List<Reconstruction> getReconstructions(){return reconstructions;}
}
