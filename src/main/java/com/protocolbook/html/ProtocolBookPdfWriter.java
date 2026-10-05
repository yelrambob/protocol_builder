package com.protocolbook.html;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Printable PDF of the protocol book: cover, a contents list with page numbers, then one protocol
 * per page in the same order and with the same content as the HTML book (it reuses
 * {@link ProtocolBookHtmlWriter#appendProtocol}), so the two never drift apart. Protocol reference
 * images (--protocol-images-base) are left out - they're fetched from a server at view time.
 *
 * The renderer (openhtmltopdf) only understands CSS 2.1 plus paged media, so this has its own
 * stylesheet: no CSS variables or flexbox, but page headers/footers and contents page numbers.
 */
public class ProtocolBookPdfWriter {
    // The renderer logs timings and PDFBox logs font-cache housekeeping to the console on every run -
    // none of it actionable for someone generating a book. Held statically so the setting sticks.
    private static final Logger PDFBOX_LOG = Logger.getLogger("org.apache.pdfbox");
    static {
        XRLog.setLoggingEnabled(false);
        PDFBOX_LOG.setLevel(Level.SEVERE);
    }

    private BookTheme theme = BookTheme.DEFAULT;
    private File pictureFolder;

    /** The scan range picture library (see ScanRangePictures); without it no pictures are shown. */
    public ProtocolBookPdfWriter withPictureFolder(File folder) {
        this.pictureFolder = folder;
        return this;
    }

    /** The colors to draw the book in; the default is the original blue and orange. */
    public ProtocolBookPdfWriter withTheme(BookTheme theme) {
        this.theme = theme == null ? BookTheme.DEFAULT : theme;
        return this;
    }

    public File write(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, LabelConfig labels,
                      String logoDataUri, String bookTitle, File outFile) throws IOException {
        String title = bookTitle == null || bookTitle.trim().isEmpty() ? "Protocol Book" : bookTitle;
        ProtocolBookHtmlWriter book = new ProtocolBookHtmlWriter().withPictureFolder(pictureFolder);
        Map<String, Map<Integer, List<Protocol>>> tree = book.tree(protocols, overrides, labels);

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>").append(HtmlSupport.esc(title)).append("</title>\n");
        html.append("<style>").append(css(theme).replace("@TITLE@", cssString(title))).append("</style>\n</head>\n<body>\n");

        html.append("<div class=\"cover\">\n");
        if (logoDataUri != null) html.append("<img class=\"cover-logo\" src=\"").append(logoDataUri).append("\" alt=\"logo\">\n");
        html.append("<h1>").append(HtmlSupport.esc(title)).append("</h1>\n<p class=\"generated\">Generated ")
                .append(new SimpleDateFormat("MMMM d, yyyy").format(new Date())).append("</p>\n</div>\n");

        html.append("<div class=\"contents\">\n<h1>Contents</h1>\n");
        int index = 0;
        for (Map.Entry<String, Map<Integer, List<Protocol>>> bucket : tree.entrySet()) {
            if (bucket.getValue().isEmpty()) continue;
            html.append("<h2>").append(HtmlSupport.esc(bucket.getKey())).append("</h2>\n");
            for (Map.Entry<Integer, List<Protocol>> group : bucket.getValue().entrySet()) {
                html.append("<h3>").append(HtmlSupport.esc(labels.categoryForNumber(group.getKey()))).append("</h3>\n<ul>\n");
                for (Protocol p : group.getValue()) {
                    String id = book.protocolId(p, index++);
                    html.append("<li><a href=\"#").append(id).append("\">")
                            .append(HtmlSupport.esc(p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber())).append(" &mdash; ")
                            .append(HtmlSupport.esc(book.displayName(p, overrides))).append("</a></li>\n");
                }
                html.append("</ul>\n");
            }
        }
        html.append("</div>\n");

        index = 0;
        for (Map<Integer, List<Protocol>> byGroup : tree.values())
            for (List<Protocol> group : byGroup.values())
                for (Protocol p : group) {
                    html.append("<div class=\"protocol\" id=\"").append(book.protocolId(p, index++)).append("\">\n");
                    book.appendProtocol(html, p, overrides, labels, logoDataUri, null);
                    html.append("</div>\n");
                }
        html.append("</body>\n</html>\n");

        render(html.toString(), outFile);
        return outFile;
    }

    /** Renders a complete HTML document (CSS 2.1 + paged media only - see the class comment) to a PDF file. */
    static void render(String html, File outFile) throws IOException {
        if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) outFile.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(outFile)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html)), outFile.getAbsoluteFile().getParentFile().toURI().toString());
            builder.toStream(out);
            builder.run();
        }
    }

    private static String cssString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // BLUE/ORANGE/TINT are placeholders for the theme's main, accent and notes-box colors.
    private static final String BLUE = "@PRIMARY@", ORANGE = "@ACCENT@";

    static String css(BookTheme theme) {
        return CSS.replace(BLUE, theme.getPrimary()).replace(ORANGE, theme.getAccent()).replace("@TINT@", theme.getAccentTint());
    }

    private static final String CSS =
            "@page{size:letter;margin:.6in .55in .7in;" +
            "@top-right{content:@TITLE@;font-family:sans-serif;font-size:8pt;color:#777;}" +
            "@bottom-center{content:'Page ' counter(page) ' of ' counter(pages);font-family:sans-serif;font-size:8pt;color:#777;}}" +
            "@page:first{@top-right{content:none;}@bottom-center{content:none;}}" +
            "body{font-family:sans-serif;font-size:9.5pt;color:#222;}" +
            "p{margin:4px 0;}" +
            ".cover{text-align:center;padding-top:2.2in;page-break-after:always;}" +
            ".cover-logo{max-height:90px;max-width:300px;}" +
            ".cover h1{color:" + BLUE + ";font-size:28pt;margin:.4in 0 .1in;}" +
            ".generated{color:#666;}" +
            ".contents h1{color:" + BLUE + ";font-size:18pt;border-bottom:2px solid " + ORANGE + ";padding-bottom:4px;}" +
            ".contents h2{color:" + BLUE + ";font-size:13pt;margin:14px 0 2px;}" +
            ".contents h3{font-size:10pt;margin:8px 0 2px;color:#444;}" +
            ".contents ul{list-style:none;margin:0;padding:0 0 0 12px;}" +
            ".contents li{margin:1px 0;}" +
            ".contents a{color:#222;text-decoration:none;}" +
            ".contents a::after{content:leader('.') target-counter(attr(href), page);}" +
            ".protocol{page-break-before:always;}" +
            ".protocol-header{border-bottom:3px solid " + ORANGE + ";padding-bottom:4px;margin-bottom:8px;}" +
            ".protocol-logo{float:right;max-height:32px;max-width:120px;}" +
            ".protocol h2{color:" + BLUE + ";font-size:14pt;margin:0;}" +
            ".protocol h3{color:" + BLUE + ";font-size:10.5pt;margin:10px 0 3px;}" +
            ".meta,.dose,.destination{color:#555;}" +
            ".scan-range{margin:4px 0;}" +
            ".notes{background:@TINT@;border:1px solid " + ORANGE + ";padding:5px 8px;margin:5px 0;}" +
            ".series{margin:8px 0 8px 6px;padding-left:8px;border-left:3px solid #dbe7f3;page-break-inside:avoid;}" +
            "table{border-collapse:collapse;width:100%;margin:3px 0 8px;}" +
            "th,td{border:1px solid #ccd5df;padding:2px 5px;text-align:left;font-size:8.5pt;}" +
            "th{background:" + BLUE + ";color:#fff;}" +
            "tr.reformat td{color:#555;font-style:italic;}" +
            "tr.reformat td:first-child{padding-left:14px;}" +
            ".override{border-bottom:1px dotted #000;font-weight:bold;}" +
            ".override-note{font-size:7.5pt;color:#555;}" +
            ".added-field{margin:3px 0;}" +
            ".scan-picture{margin:5px 0;page-break-inside:avoid;}" +
            ".scan-picture img{max-width:100%;max-height:3.6in;}" +
            "table.box-key{width:auto;margin:2px 0 6px;}" +
            "table.box-key td{border:none;padding:1px 4px;}" +
            "td.swatch{width:10px;}";
}
