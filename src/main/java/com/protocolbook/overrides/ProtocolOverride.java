package com.protocolbook.overrides;

import com.protocolbook.model.Reconstruction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private String referenceSheet;
    private String scanRange;
    private Map<String, String> reconSendDestinations = new LinkedHashMap<String, String>();
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public boolean isExcluded(){return excluded;} public void setExcluded(boolean v){excluded=v;}
    public String getSendDestination(){return sendDestination;} public void setSendDestination(String v){sendDestination=v;}
    public String getContrastVolume(){return contrastVolume;} public void setContrastVolume(String v){contrastVolume=v;}
    public String getContrastRate(){return contrastRate;} public void setContrastRate(String v){contrastRate=v;}
    // Which reference-workbook sheet to take the scan range from, when matching by name picks the wrong one or none.
    public String getReferenceSheet(){return referenceSheet;} public void setReferenceSheet(String v){referenceSheet=v;}
    // Hand-typed scan range; wins over any reference-workbook sheet.
    public String getScanRange(){return scanRange;} public void setScanRange(String v){scanRange=v;}
    // Recon name -> comma-separated auto-send hosts, for when the export lists fewer hosts than the scanner really uses.
    public Map<String, String> getReconSendDestinations(){return reconSendDestinations;}

    /**
     * The auto-send hosts to show for a recon: the hand-typed list from "reconSendDestinations" when one
     * matches the recon's name (ignoring case and repeated spaces), otherwise what the export listed.
     */
    public List<String> sendDestinationsFor(Reconstruction r) {
        String typed = typedDestinations(r.getName());
        if (typed == null) return r.getSendDestinations();
        List<String> out = new ArrayList<String>();
        for (String host : typed.split(",")) if (!host.trim().isEmpty() && !out.contains(host.trim())) out.add(host.trim());
        return out;
    }

    /** reconSendDestinations names that match no recon in the given list - almost always a typo. */
    public List<String> unmatchedReconNames(List<Reconstruction> recons) {
        List<String> out = new ArrayList<String>();
        for (String name : reconSendDestinations.keySet()) {
            boolean found = false;
            for (Reconstruction r : recons) if (normalize(name).equals(normalize(r.getName()))) found = true;
            if (!found) out.add(name);
        }
        return out;
    }

    private String typedDestinations(String reconName) {
        for (Map.Entry<String, String> e : reconSendDestinations.entrySet())
            if (normalize(e.getKey()).equals(normalize(reconName)) && e.getValue() != null && !e.getValue().trim().isEmpty()) return e.getValue();
        return null;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
