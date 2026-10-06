package com.protocolbook.gui;

import java.io.File;

/** Where the program is running from and which version it is. */
final class AppInfo {
    private AppInfo() {}

    /** True when started from the installed / packaged app (jpackage's launcher sets this property). */
    static boolean packaged() {
        return System.getProperty("jpackage.app-path") != null;
    }

    /**
     * Where the changes file, labels, logo and pictures go by default: Documents\Protocol Builder for the
     * installed app (it can't write next to itself), the folder it was started from otherwise (run-gui.bat).
     */
    static File defaultFolder() {
        if (!packaged()) return new File(System.getProperty("user.dir"));
        File folder = new File(new File(System.getProperty("user.home"), "Documents"), "Protocol Builder");
        if (!folder.isDirectory()) folder.mkdirs();
        return folder;
    }

    /** The version from the jar's manifest, or "development" when run from the source. */
    static String version() {
        String v = AppInfo.class.getPackage() == null ? null : AppInfo.class.getPackage().getImplementationVersion();
        return v == null ? "development" : v;
    }
}
