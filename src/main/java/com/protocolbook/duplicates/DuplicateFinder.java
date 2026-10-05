package com.protocolbook.duplicates;

import com.protocolbook.model.*;

import java.util.*;

/**
 * Finds protocols that are effectively the same thing filed twice - typically the same exam saved
 * under two category numbers (e.g. "CT LWR EXT HIP" as both 8.2 and 9.4). Two kinds:
 *
 *  - identical settings: every series, acquisition group, contrast setting and reconstruction
 *    matches, whatever the protocols are named or numbered - safe candidates to drop one of.
 *  - same name, different settings: worth a look, since one may be a stale copy of the other.
 *
 * It only reports; hiding one from the book is an "excluded": true in protocol-overrides.json.
 */
public final class DuplicateFinder {
    private DuplicateFinder() {}

    public static final class Result {
        /** Groups of 2+ protocols whose settings are identical. */
        public final List<List<Protocol>> identical = new ArrayList<List<Protocol>>();
        /** Groups of 2+ protocols sharing a name whose settings differ. */
        public final List<List<Protocol>> sameNameDifferent = new ArrayList<List<Protocol>>();
    }

    /**
     * The copy to keep out of a group of duplicates: the most recently updated (the one someone has been
     * maintaining), or the lowest-numbered when there are no dates. lastUpdated is an ISO-8601 timestamp
     * from protocolmetadata.json, so text order is date order.
     */
    public static Protocol suggestedKeep(List<Protocol> group) {
        List<Protocol> sorted = new ArrayList<Protocol>(group);
        sorted.sort((a, b) -> com.protocolbook.html.ProtocolNumbers.compare(number(a), number(b)));
        Protocol best = null;
        for (Protocol p : sorted) {
            String updated = p.getMetadata() == null ? null : p.getMetadata().getLastUpdated();
            String bestUpdated = best == null || best.getMetadata() == null ? null : best.getMetadata().getLastUpdated();
            if (best == null || (updated != null && (bestUpdated == null || updated.compareTo(bestUpdated) > 0))) best = p;
        }
        return best;
    }

    private static String number(Protocol p) {
        return p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
    }

    public static Result find(List<Protocol> protocols) {
        Map<String, List<Protocol>> bySettings = new LinkedHashMap<String, List<Protocol>>();
        Map<String, List<Protocol>> byName = new LinkedHashMap<String, List<Protocol>>();
        for (Protocol p : protocols) {
            String fp = fingerprint(p);
            // nothing to compare (e.g. a hand-written manual protocol with no series) is never "identical"
            if (!fp.isEmpty()) bySettings.computeIfAbsent(fp, k -> new ArrayList<Protocol>()).add(p);
            String name = p.getMetadata() == null || p.getMetadata().getName() == null ? "" : p.getMetadata().getName().trim().toUpperCase(Locale.ROOT);
            if (!name.isEmpty()) byName.computeIfAbsent(name, k -> new ArrayList<Protocol>()).add(p);
        }
        Result r = new Result();
        Set<Protocol> inIdentical = Collections.newSetFromMap(new IdentityHashMap<Protocol, Boolean>());
        for (List<Protocol> group : bySettings.values())
            if (group.size() > 1) { r.identical.add(group); inIdentical.addAll(group); }
        for (List<Protocol> group : byName.values()) {
            if (group.size() < 2) continue;
            // a same-name group already fully explained by identical settings adds nothing new
            Set<String> fingerprints = new HashSet<String>();
            for (Protocol p : group) fingerprints.add(fingerprint(p));
            if (fingerprints.size() > 1) r.sameNameDifferent.add(group);
        }
        return r;
    }

    /** Everything that changes how the exam is scanned or reconstructed - not its name, number or filing. */
    static String fingerprint(Protocol p) {
        Map<String, String> f = settings(p);
        return f.isEmpty() ? "" : f.toString();
    }

    /**
     * Settings that differ between protocols, as "Series 2, group 1: pitch 33.0 / 63.0" (values in
     * the order given) - so a same-name pair can be judged at a glance.
     */
    public static List<String> differences(List<Protocol> protocols) {
        List<Map<String, String>> all = new ArrayList<Map<String, String>>();
        Set<String> keys = new LinkedHashSet<String>();
        for (Protocol p : protocols) { Map<String, String> m = settings(p); all.add(m); keys.addAll(m.keySet()); }
        List<String> out = new ArrayList<String>();
        for (String key : keys) {
            Set<String> distinct = new HashSet<String>();
            List<String> values = new ArrayList<String>();
            for (Map<String, String> m : all) {
                String v = m.containsKey(key) ? String.valueOf(m.get(key)) : "(none)";
                distinct.add(v); values.add(v);
            }
            if (distinct.size() > 1) out.add(key + ": " + String.join(" / ", values));
        }
        return out;
    }

    private static Map<String, String> settings(Protocol p) {
        Map<String, String> f = new LinkedHashMap<String, String>();
        for (Series s : p.getSeries()) {
            String sk = "Series " + s.getNumber();
            f.put(sk + ": scan type", s.getScanType());
            Contrast c = s.getContrast();
            if (c != null) {
                f.put(sk + ": IV contrast", c.isIv() ? c.getIvVolume() + " mL @ " + c.getFlowRate() + " mL/s" : "none");
                f.put(sk + ": oral contrast", c.isOral() ? c.getOralVolume() + " mL" : "none");
            }
            for (int gi = 0; gi < s.getGroups().size(); gi++) {
                Group g = s.getGroups().get(gi);
                Acquisition a = g.getAcquisition();
                String gk = sk + ", group " + (gi + 1) + ": ";
                f.put(gk + "kV", a.getKv());
                f.put(gk + "mA", a.getMa());
                f.put(gk + "mA range", a.getMinMa() + "-" + a.getMaxMa());
                f.put(gk + "mA mode", a.getMaMode());
                f.put(gk + "noise index", a.getNoiseIndex());
                f.put(gk + "pitch", a.getPitch());
                f.put(gk + "rotation", a.getRotationTime());
                f.put(gk + "detector rows", a.getDetector());
                f.put(gk + "scan delay", a.getScanDelay());
                for (int ri = 0; ri < g.getReconstructions().size(); ri++) {
                    Reconstruction r = g.getReconstructions().get(ri);
                    String rk = gk + "recon " + (ri + 1) + " ";
                    f.put(rk + "name", r.getName());
                    f.put(rk + "kernel", r.getKernel());
                    f.put(rk + "thickness/interval", r.getThickness() + "/" + r.getInterval());
                    f.put(rk + "plane", r.getPlane());
                    f.put(rk + "ASIR", r.getIterativeConfig());
                    f.put(rk + "matrix", r.getMatrix());
                    f.put(rk + "DFOV", r.getDfov());
                    f.put(rk + "WW/WL", r.getWindowWidth() + "/" + r.getWindowLevel());
                }
            }
        }
        return f;
    }
}
