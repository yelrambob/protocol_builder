package com.protocolbook.model;
import java.util.ArrayList; import java.util.List;
public class Series {
    private int number; private String name, description, scanType; private boolean derived;
    private Contrast contrast;
    private final List<Group> groups = new ArrayList<Group>();
    public int getNumber(){return number;} public void setNumber(int v){number=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getScanType(){return scanType;} public void setScanType(String v){scanType=v;}
    public boolean isDerived(){return derived;} public void setDerived(boolean v){derived=v;}
    public Contrast getContrast(){return contrast;} public void setContrast(Contrast v){contrast=v;}
    public List<Group> getGroups(){return groups;}
}
