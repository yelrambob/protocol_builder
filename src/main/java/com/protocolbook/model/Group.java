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
     * Factor that turns the exported dose into the dose at another mA. The console calculates
     * CTDI/DLP at the group's milliAmps value, and both scale linearly with mA (kV, rotation,
     * pitch and collimation fixed), so under auto-mA the dose at minMa/maxMa is the exported
     * figure x minMa (or maxMa) / milliAmps. 1 for fixed mA, or when a value is missing.
     */
    public double doseFactor(boolean max) {
        if (!acquisition.isAutoMa()) return 1;
        Double ma = Acquisition.number(acquisition.getMa());
        Double target = Acquisition.number(max ? acquisition.getMaxMa() : acquisition.getMinMa());
        return ma == null || target == null || ma <= 0 ? 1 : target / ma;
    }

    /** CTDIvol at min or max mA (see {@link #doseFactor}); null if the export has none. */
    public Double ctdi(boolean max) {
        return dose == null || dose.getCtdi() == null ? null : dose.getCtdi() * doseFactor(max);
    }

    /** DLP at min or max mA (see {@link #doseFactor}); null if the export has none. */
    public Double dlp(boolean max) {
        return dose == null || dose.getDlp() == null ? null : dose.getDlp() * doseFactor(max);
    }
}
