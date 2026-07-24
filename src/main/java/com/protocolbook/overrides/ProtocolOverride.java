package com.protocolbook.overrides;

/** Hand-authored addition to one protocol: scanning notes and/or exclusion from the generated book. */
public class ProtocolOverride {
    private String notes;
    private boolean excluded;
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public boolean isExcluded(){return excluded;} public void setExcluded(boolean v){excluded=v;}
}
