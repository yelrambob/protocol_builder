package com.protocolbook.html;

/** Shared look-and-feel for every generated page (protocol book, pediatric weight sheet, etc.) - one place to change the design. */
final class HtmlSupport {
    private HtmlSupport() {}

    static String esc(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static final String BASE_CSS =
            "body{font-family:sans-serif;max-width:900px;margin:2rem auto;padding:0 1rem;}" +
            "h1{margin-bottom:.25rem;}" +
            ".subtitle{color:#555;margin-top:0;}" +
            "table{border-collapse:collapse;width:100%;margin:.25rem 0 .75rem;}" +
            "th,td{border:1px solid #ddd;padding:.4rem .6rem;font-size:.9rem;text-align:left;}" +
            "th{background:#f0f0f0;}" +
            "@media print{body{margin:0;max-width:none;}tr{break-inside:avoid;}}";
}
