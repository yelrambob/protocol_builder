package com.protocolbook.overrides;

/**
 * Hand-authored addition to one protocol: a display title override, scanning notes, exclusion
 * from the generated book, and/or where its images are sent. Send destination isn't reliably
 * derivable from the scanner export - session.xml logs which network job actually ran for a given
 * historical scan, not what the protocol template always does, so a person has to state it here
 * when it matters.
 */
public class ProtocolOverride {
    private String title;
    private String notes;
    private boolean excluded;
    private String sendDestination;
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public boolean isExcluded(){return excluded;} public void setExcluded(boolean v){excluded=v;}
    public String getSendDestination(){return sendDestination;} public void setSendDestination(String v){sendDestination=v;}
}
