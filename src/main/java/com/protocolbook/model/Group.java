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

    /**
     * Factor that turns the exported dose into the worst case at max mA. The console calculates
     * CTDI/DLP at the group's milliAmps value, and both scale linearly with mA (kV, rotation,
     * pitch and collimation fixed), so under auto-mA it's maxMa / milliAmps. 1 for fixed mA, or
     * when either value is missing.
     */
    public double maxMaDoseFactor() {
        if (!acquisition.isAutoMa()) return 1;
        Double ma = Acquisition.number(acquisition.getMa()), max = Acquisition.number(acquisition.getMaxMa());
        return ma == null || max == null || ma <= 0 ? 1 : max / ma;
    }

    /** CTDIvol at max mA (see {@link #maxMaDoseFactor}); null if the export has none. */
    public Double maxCtdi() {
        return dose == null || dose.getCtdi() == null ? null : dose.getCtdi() * maxMaDoseFactor();
    }

    /** DLP at max mA (see {@link #maxMaDoseFactor}); null if the export has none. */
    public Double maxDlp() {
        return dose == null || dose.getDlp() == null ? null : dose.getDlp() * maxMaDoseFactor();
    }
}
