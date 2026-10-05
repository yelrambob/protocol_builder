package com.protocolbook.gui;

import com.protocolbook.html.ScanRangePictures;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The scan range picture library: thumbnails of every picture in the "scan range images" folder, with
 * Import to copy more in. Used to pick a picture for a protocol, and from the File menu just to add pictures.
 */
final class PicturePicker extends JDialog {
    private static final int THUMB = 150;
    private static final Map<String, ImageIcon> THUMBNAILS = new HashMap<String, ImageIcon>();

    private final File folder;
    private final JPanel grid = new JPanel(new GridLayout(0, 4, 8, 8));
    private final ButtonGroup group = new ButtonGroup();
    private final JButton ok = new JButton("Use this picture");
    private String chosen;

    private PicturePicker(Window owner, File folder, boolean choosing) {
        super(owner, choosing ? "Choose a scan range picture" : "Scan range pictures", ModalityType.APPLICATION_MODAL);
        this.folder = folder;
        setSize(760, 560);
        setLocationRelativeTo(owner);

        JButton importButton = new JButton("Import pictures…");
        importButton.addActionListener(e -> importPictures());
        JButton cancel = new JButton(choosing ? "Cancel" : "Close");
        cancel.addActionListener(e -> dispose());
        ok.setEnabled(false);
        ok.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(importButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(cancel);
        if (choosing) right.add(ok);
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(new EmptyBorder(10, 12, 4, 12));
        content.add(Ui.help("Pictures here can be used by any protocol - each protocol gets its own boxes. Import copies the files into "
                + "the \"" + ScanRangePictures.FOLDER + "\" folder next to your changes file."), BorderLayout.NORTH);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(grid, BorderLayout.NORTH);
        content.add(new JScrollPane(wrap), BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        fill(null);
    }

    /** Shows the library and returns the picture picked (its name in the folder), or null. */
    static String choose(Window owner, File folder) {
        PicturePicker p = new PicturePicker(owner, folder, true);
        p.setVisible(true);
        return p.chosen;
    }

    /** Shows the library just to look at it and import pictures. */
    static void manage(Window owner, File folder) {
        new PicturePicker(owner, folder, false).setVisible(true);
    }

    private void fill(String select) {
        grid.removeAll();
        for (AbstractButton b : java.util.Collections.list(group.getElements())) group.remove(b);
        List<String> names = ScanRangePictures.library(folder);
        if (names.isEmpty()) grid.add(new JLabel("No pictures yet - press Import pictures…"));
        for (String name : names) {
            JToggleButton b = new JToggleButton(name, thumbnail(new File(folder, name)));
            b.setVerticalTextPosition(SwingConstants.BOTTOM);
            b.setHorizontalTextPosition(SwingConstants.CENTER);
            b.addActionListener(e -> { chosen = name; ok.setEnabled(true); });
            b.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) { chosen = name; dispose(); }
                }
            });
            group.add(b);
            grid.add(b);
            if (name.equals(select)) { b.setSelected(true); chosen = name; ok.setEnabled(true); }
        }
        grid.revalidate();
        grid.repaint();
    }

    private void importPictures() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Pictures (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
        chooser.setDialogTitle("Choose pictures to add");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String last = null;
        List<String> failed = new ArrayList<String>();
        for (File f : chooser.getSelectedFiles()) {
            try {
                if (ImageIO.read(f) == null) { failed.add(f.getName() + " (not a picture this can read)"); continue; }
                last = ScanRangePictures.importPicture(f, folder);
            } catch (IOException e) {
                failed.add(f.getName() + " (" + e.getMessage() + ")");
            }
        }
        if (!failed.isEmpty()) JOptionPane.showMessageDialog(this, "Couldn't add:\n" + String.join("\n", failed), "Import", JOptionPane.WARNING_MESSAGE);
        fill(last);
    }

    static ImageIcon thumbnail(File f) {
        String key = f.getAbsolutePath() + "|" + f.lastModified();
        ImageIcon icon = THUMBNAILS.get(key);
        if (icon != null) return icon;
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) return null;
            double scale = Math.min(THUMB / (double) img.getWidth(), THUMB / (double) img.getHeight());
            icon = new ImageIcon(img.getScaledInstance(Math.max(1, (int) (img.getWidth() * scale)), Math.max(1, (int) (img.getHeight() * scale)), Image.SCALE_SMOOTH));
            THUMBNAILS.put(key, icon);
            return icon;
        } catch (IOException e) {
            return null;
        }
    }
}
