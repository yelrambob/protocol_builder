package com.protocolbook.html;

/**
 * Convention-based per-protocol image lookup: {@code <baseUrl>/<protocolNumber>.<extension>} -
 * no per-protocol list to maintain, since the filename convention alone tells us where to look.
 * Not every protocol number needs to have a file; the generated <img> hides itself client-side
 * (onerror) if that particular one 404s, so nothing needs to be known in advance about which
 * protocols actually have an image on the server.
 */
public final class ProtocolImages {
    private final String baseUrl;
    private final String extension;

    public ProtocolImages(String baseUrl, String extension) {
        this.baseUrl = baseUrl;
        this.extension = extension == null || extension.isEmpty() ? "png" : extension;
    }

    public String urlFor(String protocolNumber) {
        if (baseUrl == null || baseUrl.isEmpty() || protocolNumber == null || protocolNumber.isEmpty()) return null;
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/" + protocolNumber + "." + extension;
    }
}
