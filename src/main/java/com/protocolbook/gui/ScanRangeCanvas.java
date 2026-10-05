package com.protocolbook.gui;

import com.protocolbook.html.ScanRangePictures;
import com.protocolbook.overrides.ProtocolOverride;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A picture with scan range boxes on it. Drag on an empty part of the picture to draw a new box (each
 * new box takes the next color), drag a box to move it, drag one of its corners to resize it, and press
 * Delete to remove the selected one. Box positions are kept as fractions of the picture's size.
 */
final class ScanRangeCanvas extends JComponent {
    private static final int PAD = 10, HANDLE = 9, MIN_DRAG = 4;

    private BufferedImage image;
    private List<ProtocolOverride.Box> boxes = new ArrayList<ProtocolOverride.Box>();
    private ProtocolOverride.Box selected;
    private final Runnable onChange;
    private final Consumer<ProtocolOverride.Box> onSelect;

    // drag state, in picture fractions
    private enum Mode { NONE, NEW, MOVE, RESIZE }
    private Mode mode = Mode.NONE;
    private double anchorX, anchorY, grabX, grabY, startX, startY;
    private Point pressPoint;
    private ProtocolOverride.Box drawing; // the box being drawn right now, if any

    ScanRangeCanvas(Runnable onChange, Consumer<ProtocolOverride.Box> onSelect) {
        this.onChange = onChange;
        this.onSelect = onSelect;
        setFocusable(true);
        setPreferredSize(new Dimension(520, 520));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { press(e.getPoint()); }
            @Override public void mouseDragged(MouseEvent e) { drag(e.getPoint()); }
            @Override public void mouseReleased(MouseEvent e) { release(); }
            @Override public void mouseMoved(MouseEvent e) { updateCursor(e.getPoint()); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) && selected != null) deleteSelected();
            }
        });
    }

    /** Shows a picture and the (live, edited in place) list of boxes drawn on it. */
    void show(BufferedImage image, List<ProtocolOverride.Box> boxes) {
        this.image = image;
        this.boxes = boxes == null ? new ArrayList<ProtocolOverride.Box>() : boxes;
        select(null);
        repaint();
    }

    ProtocolOverride.Box selected() { return selected; }

    void select(ProtocolOverride.Box b) {
        selected = b;
        repaint();
        if (onSelect != null) onSelect.accept(b);
    }

    void deleteSelected() {
        if (selected == null) return;
        boxes.remove(selected);
        select(null);
        onChange.run();
    }

    /** Where the picture is drawn: scaled to fit, centered. */
    private Rectangle imageRect() {
        if (image == null) return new Rectangle(0, 0, 0, 0);
        double scale = Math.min((getWidth() - 2.0 * PAD) / image.getWidth(), (getHeight() - 2.0 * PAD) / image.getHeight());
        scale = Math.max(scale, 0.01);
        int w = (int) Math.round(image.getWidth() * scale), h = (int) Math.round(image.getHeight() * scale);
        return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
    }

    private double fx(Point p, Rectangle r) { return clamp((p.x - r.x) / (double) r.width); }

    private double fy(Point p, Rectangle r) { return clamp((p.y - r.y) / (double) r.height); }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    private Rectangle screen(ProtocolOverride.Box b, Rectangle r) {
        return new Rectangle(r.x + (int) Math.round(b.getX() * r.width), r.y + (int) Math.round(b.getY() * r.height),
                (int) Math.round(b.getW() * r.width), (int) Math.round(b.getH() * r.height));
    }

    /** Index of the corner of the selected box under p (0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right), or -1. */
    private int cornerAt(Point p) {
        if (selected == null) return -1;
        Rectangle s = screen(selected, imageRect());
        int[][] corners = {{s.x, s.y}, {s.x + s.width, s.y}, {s.x, s.y + s.height}, {s.x + s.width, s.y + s.height}};
        for (int i = 0; i < 4; i++) if (Math.abs(p.x - corners[i][0]) <= HANDLE && Math.abs(p.y - corners[i][1]) <= HANDLE) return i;
        return -1;
    }

    private ProtocolOverride.Box boxAt(Point p) {
        Rectangle r = imageRect();
        for (int i = boxes.size() - 1; i >= 0; i--) if (screen(boxes.get(i), r).contains(p)) return boxes.get(i);
        return null;
    }

    private void press(Point p) {
        requestFocusInWindow();
        if (image == null) return;
        Rectangle r = imageRect();
        pressPoint = p;
        int corner = cornerAt(p);
        if (corner >= 0) {
            // resize: the opposite corner stays put
            mode = Mode.RESIZE;
            anchorX = corner == 0 || corner == 2 ? selected.getX() + selected.getW() : selected.getX();
            anchorY = corner == 0 || corner == 1 ? selected.getY() + selected.getH() : selected.getY();
            return;
        }
        ProtocolOverride.Box hit = boxAt(p);
        if (hit != null) {
            mode = Mode.MOVE;
            select(hit);
            grabX = fx(p, r);
            grabY = fy(p, r);
            startX = hit.getX();
            startY = hit.getY();
            return;
        }
        if (!r.contains(p)) {
            select(null);
            mode = Mode.NONE;
            return;
        }
        mode = Mode.NEW;
        anchorX = fx(p, r);
        anchorY = fy(p, r);
        select(null);
    }

    private void drag(Point p) {
        if (image == null || mode == Mode.NONE) return;
        Rectangle r = imageRect();
        double x = fx(p, r), y = fy(p, r);
        if (mode == Mode.NEW) {
            if (pressPoint.distance(p) < MIN_DRAG) return;
            ProtocolOverride.Box b = new ProtocolOverride.Box("", ScanRangePictures.nextColor(boxes), anchorX, anchorY, 0, 0);
            boxes.add(b);
            drawing = b;
            select(b);
            mode = Mode.RESIZE;
        }
        if (mode == Mode.RESIZE) {
            selected.setX(Math.min(anchorX, x));
            selected.setY(Math.min(anchorY, y));
            selected.setW(Math.abs(x - anchorX));
            selected.setH(Math.abs(y - anchorY));
        } else if (mode == Mode.MOVE) {
            selected.setX(Math.max(0, Math.min(1 - selected.getW(), startX + x - grabX)));
            selected.setY(Math.max(0, Math.min(1 - selected.getH(), startY + y - grabY)));
        }
        repaint();
    }

    private void release() {
        if (mode == Mode.NONE) return;
        // a click that barely moved isn't a box
        if (drawing != null && (drawing.getW() < 0.01 || drawing.getH() < 0.01)) {
            boxes.remove(drawing);
            select(null);
        }
        drawing = null;
        mode = Mode.NONE;
        onChange.run();
        repaint();
    }

    private void updateCursor(Point p) {
        int corner = cornerAt(p);
        if (corner == 0 || corner == 3) setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
        else if (corner == 1 || corner == 2) setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
        else if (boxAt(p) != null) setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        else if (image != null && imageRect().contains(p)) setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        else setCursor(Cursor.getDefaultCursor());
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(new Color(0x2b2b2b));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (image == null) {
            g.setColor(new Color(0xbbbbbb));
            g.setFont(getFont().deriveFont(14f));
            String text = "Add a picture on the left, then drag on it to draw the scan range.";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
        } else {
            Rectangle r = imageRect();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, r.x, r.y, r.width, r.height, null);
            ScanRangePictures.drawBoxes(g, boxes, r.x, r.y, r.width, r.height, selected);
        }
        g.dispose();
    }
}
