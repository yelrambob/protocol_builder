package com.protocolbook.html;

import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scan range pictures: a library of images kept in the "scan range images" folder next to
 * protocol-overrides.json, and the colored boxes drawn on them per protocol (see
 * ProtocolOverride.ScanRangePicture). The boxes are drawn into the picture itself when the book is
 * made, so the HTML and PDF show exactly what the GUI showed while drawing - both use {@link #drawBoxes}.
 */
public final class ScanRangePictures {
    private ScanRangePictures() {}

    /** The library folder's name, next to protocol-overrides.json. */
    public static final String FOLDER = "scan range images";

    /** Box colors in the order new boxes get them - chosen to stay distinct from each other on a grey scout. */
    public static final List<String> COLORS = Arrays.asList(
            "#e53935", "#1e88e5", "#43a047", "#fb8c00", "#8e24aa", "#00acc1", "#fdd835", "#d81b60", "#6d4c41", "#3949ab");

    private static final List<String> EXTENSIONS = Arrays.asList("png", "jpg", "jpeg", "gif", "bmp");

    /** The first palette color no box uses yet (cycling once all are taken). */
    public static String nextColor(List<ProtocolOverride.Box> boxes) {
        for (String c : COLORS) {
            boolean used = false;
            for (ProtocolOverride.Box b : boxes) used |= c.equalsIgnoreCase(b.getColor());
            if (!used) return c;
        }
        return COLORS.get(boxes.size() % COLORS.size());
    }

    /** Picture files in the library, by name. */
    public static List<String> library(File folder) {
        List<String> out = new ArrayList<String>();
        File[] files = folder == null ? null : folder.listFiles();
        if (files == null) return out;
        for (File f : files) if (f.isFile() && isPicture(f.getName())) out.add(f.getName());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static boolean isPicture(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Copies a picture into the library (adding " (2)" etc. if the name is taken by a different file) and returns its name there. */
    public static String importPicture(File source, File folder) throws IOException {
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Couldn't create " + folder);
        if (source.getAbsoluteFile().getParentFile().equals(folder.getAbsoluteFile())) return source.getName();
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        File target = new File(folder, name);
        for (int n = 2; target.exists(); n++) {
            if (Arrays.equals(Files.readAllBytes(target.toPath()), Files.readAllBytes(source.toPath()))) return target.getName();
            target = new File(folder, stem + " (" + n + ")" + ext);
        }
        Files.copy(source.toPath(), target.toPath());
        return target.getName();
    }

    /**
     * Draws boxes over a picture shown at (left, top) with size width x height: a translucent fill, a
     * solid outline, and the label in a tag at the box's top-left corner. selected (may be null) gets
     * corner handles, for the GUI.
     */
    public static void drawBoxes(Graphics2D g, List<ProtocolOverride.Box> boxes, int left, int top, int width, int height, ProtocolOverride.Box selected) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        float stroke = Math.max(2f, width / 300f);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, Math.round(width / 26f)));
        for (ProtocolOverride.Box b : boxes) {
            Color c = color(b.getColor());
            int x = left + (int) Math.round(b.getX() * width), y = top + (int) Math.round(b.getY() * height);
            int w = (int) Math.round(b.getW() * width), h = (int) Math.round(b.getH() * height);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 45));
            g.fillRect(x, y, w, h);
            g.setColor(c);
            g.setStroke(new BasicStroke(stroke));
            g.drawRect(x, y, w, h);
            String label = b.getLabel() == null ? "" : b.getLabel().trim();
            if (!label.isEmpty()) {
                g.setFont(font);
                FontMetrics fm = g.getFontMetrics();
                int pad = Math.max(2, fm.getHeight() / 6), tw = fm.stringWidth(label) + pad * 2, th = fm.getHeight();
                int ty = y - th >= top ? y - th : y; // above the box when there's room, else just inside it
                g.fillRect(x, ty, tw, th);
                g.setColor(textOn(c));
                g.drawString(label, x + pad, ty + fm.getAscent());
            }
            if (b == selected) {
                int s = Math.max(6, (int) stroke * 3);
                g.setColor(Color.WHITE);
                for (int[] corner : new int[][] {{x, y}, {x + w, y}, {x, y + h}, {x + w, y + h}}) g.fillRect(corner[0] - s / 2, corner[1] - s / 2, s, s);
                g.setColor(c);
                g.setStroke(new BasicStroke(1.5f));
                for (int[] corner : new int[][] {{x, y}, {x + w, y}, {x, y + h}, {x + w, y + h}}) g.drawRect(corner[0] - s / 2, corner[1] - s / 2, s, s);
            }
        }
    }

    /** The picture with its boxes drawn in, at most maxWidth wide, as a JPEG data URI; null if the file is missing or unreadable. */
    public static String composedDataUri(File folder, ProtocolOverride.ScanRangePicture picture, int maxWidth) {
        try {
            BufferedImage source = picture.getImage() == null ? null : ImageIO.read(new File(folder, picture.getImage()));
            if (source == null) return null;
            double scale = Math.min(1.0, maxWidth / (double) source.getWidth());
            int w = Math.max(1, (int) Math.round(source.getWidth() * scale)), h = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source, 0, 0, w, h, null);
            drawBoxes(g, picture.getBoxes(), 0, 0, w, h, null);
            g.dispose();
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg(out));
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** One line per picture a protocol uses that isn't in the library folder. */
    public static List<String> problems(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, File folder) {
        List<String> out = new ArrayList<String>();
        for (Protocol p : protocols) {
            String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
            ProtocolOverride o = number == null ? null : overrides.get(number);
            if (o == null) continue;
            for (ProtocolOverride.ScanRangePicture pic : o.getScanRangePictures())
                if (folder == null || !new File(folder, pic.getImage()).isFile())
                    out.add("protocol " + number + " scanRangePictures: '" + pic.getImage() + "' isn't in " + (folder == null ? FOLDER : folder.getPath()));
        }
        return out;
    }

    public static Color color(String hex) {
        try {
            return Color.decode(hex == null ? COLORS.get(0) : hex.trim());
        } catch (NumberFormatException e) {
            return Color.decode(COLORS.get(0));
        }
    }

    /** Black or white, whichever reads better on c. */
    public static Color textOn(Color c) {
        return c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114 > 160 ? Color.BLACK : Color.WHITE;
    }
}
