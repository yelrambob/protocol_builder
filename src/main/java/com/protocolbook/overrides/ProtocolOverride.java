package com.protocolbook.overrides;

/**
 * Hand-authored addition to one protocol: a display title override, scanning notes, exclusion
 * from the generated book, where its images are sent, and/or a contrast volume/rate override.
 * Send destination isn't reliably derivable from the scanner export - session.xml logs which
 * network job actually ran for a given historical scan, not what the protocol template always
 * does, so a person has to state it here when it matters. Contrast volume/rate overrides exist
 * for the same reason a title override does: to correct what's shown in the book without needing
 * to touch (or being able to touch) the underlying scanner export.
 */
public class ProtocolOverride {
    private String title;
    private String notes;
    private boolean excluded;
    private String sendDestination;
    private String contrastVolume;
    private String contrastRate;
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public boolean isExcluded(){return excluded;} public void setExcluded(boolean v){excluded=v;}
    public String getSendDestination(){return sendDestination;} public void setSendDestination(String v){sendDestination=v;}
    public String getContrastVolume(){return contrastVolume;} public void setContrastVolume(String v){contrastVolume=v;}
    public String getContrastRate(){return contrastRate;} public void setContrastRate(String v){contrastRate=v;}
}
