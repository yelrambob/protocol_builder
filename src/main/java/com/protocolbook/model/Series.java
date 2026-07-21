package com.protocolbook.model;
import java.util.ArrayList; import java.util.List;
public class Series {
    private int number; private String name, description; private boolean derived; private Acquisition acquisition;
    private final List<Reconstruction> reconstructions = new ArrayList<Reconstruction>();
    public int getNumber(){return number;} public void setNumber(int v){number=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public boolean isDerived(){return derived;} public void setDerived(boolean v){derived=v;}
    public Acquisition getAcquisition(){return acquisition;} public void setAcquisition(Acquisition v){acquisition=v;}
    public List<Reconstruction> getReconstructions(){return reconstructions;}
}
