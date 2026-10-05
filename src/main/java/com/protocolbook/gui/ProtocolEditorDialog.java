package com.protocolbook.gui;

import com.protocolbook.changes.ManualChanges;
import com.protocolbook.html.ScanRangePictures;
import com.protocolbook.html.ScannerValues;
import com.protocolbook.model.Group;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;
import com.protocolbook.overrides.ProtocolOverride;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Edits one protocol at a time - Previous/Next step through the ones checked on the section screen.
 * Every setting starts at what the book shows now; anything changed from the scanner's own value is
 * highlighted and saved to the overrides file, and putting a value back (or clearing it) undoes that.
 */
final class ProtocolEditorDialog extends JDialog {
    private final ProtocolBuilderGui gui;
    private final Session session;
    private final List<Protocol> queue;
    private int index;
    /** Protocols in the queue that got their scan range boxes applied from another one - Save & next skips them. */
    private final java.util.Set<Protocol> skip = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Protocol, Boolean>());
    private Editor editor;

    private final JPanel body = new JPanel(new BorderLayout());
    private final JButton previous = new JButton("◀ Save & previous");
    private final JButton next = new JButton("Save & next ▶");
    private final JButton done = new JButton("Save & close");
    private final JLabel position = new JLabel();

    ProtocolEditorDialog(ProtocolBuilderGui gui, List<Protocol> queue) {
        super(gui, "Edit protocol", true);
        this.gui = gui;
        this.session = gui.session();
        this.queue = queue;
        setSize(1150, 720);
        setLocationRelativeTo(gui);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { cancel(); }
        });

        previous.addActionListener(e -> { if (saveCurrent()) open(step(-1)); });
        next.addActionListener(e -> { if (saveCurrent()) open(step(1)); });
        done.addActionListener(e -> { if (saveCurrent()) dispose(); });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> cancel());
        getRootPane().setDefaultButton(queue.size() > 1 ? next : done);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(position);
        buttons.add(Box.createHorizontalStrut(16));
        if (queue.size() > 1) {
            buttons.add(previous);
            buttons.add(next);
        }
        buttons.add(cancel);
        buttons.add(done);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(body, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        open(0);
    }

    private void open(int i) {
        if (i < 0 || i >= queue.size()) return;
        index = i;
        editor = new Editor(queue.get(i));
        body.removeAll();
        body.add(editor.component(), BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
        updateNavigation();
    }

    /** The next (direction 1) or previous (-1) protocol in the queue that isn't skipped, or -1. */
    private int step(int direction) {
        for (int i = index + direction; i >= 0 && i < queue.size(); i += direction) if (!skip.contains(queue.get(i))) return i;
        return -1;
    }

    private void updateNavigation() {
        position.setText(queue.size() > 1 ? "Protocol " + (index + 1) + " of " + queue.size()
                + (skip.isEmpty() ? "" : " (" + skip.size() + " skipped - boxes applied)") : "");
        previous.setEnabled(step(-1) >= 0);
        next.setEnabled(step(1) >= 0);
    }

    private void cancel() {
        if (editor != null && editor.changed()
                && JOptionPane.showConfirmDialog(this, "Discard the changes to this protocol?", "Cancel", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
            return;
        dispose();
    }

    private boolean saveCurrent() {
        stopEditing(editor.seriesTable);
        stopEditing(editor.reconTable);
        stopEditing(editor.addedTable);
        editor.apply();
        return gui.save();
    }

    private static void stopEditing(JTable t) {
        if (t.isEditing() && t.getCellEditor() != null) t.getCellEditor().stopCellEditing();
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private static String blankToNull(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** One table row of settings: what the scanner has, and what's typed (starting from what the book shows). */
    private static final class GridRow {
        final String label;
        final String[] scanner, value;
        final boolean[] editable;
        final Object source;

        GridRow(String label, int size, Object source) {
            this.label = label;
            this.scanner = new String[size];
            this.value = new String[size];
            this.editable = new boolean[size];
            this.source = source;
        }
    }

    /** A grid of settings (series or recons) - first column the row's label, then one column per field. */
    private static final class GridModel extends AbstractTableModel {
        final String firstColumn;
        final List<String> fields;
        final List<GridRow> rows = new ArrayList<GridRow>();

        GridModel(String firstColumn, List<String> fields) {
            this.firstColumn = firstColumn;
            this.fields = fields;
        }

        @Override public int getRowCount() { return rows.size(); }

        @Override public int getColumnCount() { return fields.size() + 1; }

        @Override public String getColumnName(int c) { return c == 0 ? firstColumn : ManualChanges.label(fields.get(c - 1)); }

        @Override public boolean isCellEditable(int r, int c) { return c > 0 && rows.get(r).editable[c - 1]; }

        @Override public Object getValueAt(int r, int c) {
            GridRow row = rows.get(r);
            return c == 0 ? row.label : row.editable[c - 1] ? row.value[c - 1] : "";
        }

        @Override public void setValueAt(Object v, int r, int c) {
            rows.get(r).value[c - 1] = v == null ? "" : v.toString();
            fireTableCellUpdated(r, c);
        }

        boolean changedFromScanner(int r, int c) {
            GridRow row = rows.get(r);
            return c > 0 && row.editable[c - 1] && !trim(row.value[c - 1]).isEmpty() && !trim(row.value[c - 1]).equals(trim(row.scanner[c - 1]));
        }
    }

    /** Highlights a cell whose value differs from the scanner's, with the scanner's value as its tooltip. */
    private static final class GridRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(table, value, selected, focus, r, c);
            GridModel m = (GridModel) table.getModel();
            boolean changed = m.changedFromScanner(r, c);
            setFont(getFont().deriveFont(changed || c == 0 ? Font.BOLD : Font.PLAIN));
            if (!selected) {
                setForeground(changed ? Ui.editedColor() : table.getForeground());
                setBackground(changed ? Ui.editedBackground() : c > 0 && !m.rows.get(r).editable[c - 1] ? new Color(0xf2f2f2) : table.getBackground());
            }
            String scanner = c == 0 ? null : m.rows.get(r).scanner[c - 1];
            setToolTipText(c == 0 || !m.rows.get(r).editable[c - 1] ? null
                    : "Scanner: " + (trim(scanner).isEmpty() ? "(none)" : scanner) + (changed ? "  - changed" : ""));
            return this;
        }
    }

    /** The editing screen for one protocol. */
    private final class Editor {
        final Protocol p;
        final String number;
        final ProtocolOverride o;
        final JCheckBox excluded = new JCheckBox("Leave this protocol out of the book");
        final Map<String, JTextField> exam = new LinkedHashMap<String, JTextField>();
        final Map<String, String> examScanner = new LinkedHashMap<String, String>();
        final JComboBox<String> threeD = new JComboBox<String>(new String[] {"Automatic (when it sends to AW Server)", "Always show", "Never show"});
        final JTextArea notes = new JTextArea(5, 50);
        final GridModel seriesModel = new GridModel("Series", ProtocolOverride.SERIES_FIELDS);
        final GridModel reconModel = new GridModel("Series", ProtocolOverride.RECON_FIELDS);
        final AddedModel addedModel;
        final List<ProtocolOverride.ScanRangePicture> pictures = new ArrayList<ProtocolOverride.ScanRangePicture>();
        final JTable seriesTable = new JTable(seriesModel), reconTable = new JTable(reconModel), addedTable;
        final String startState;

        Editor(Protocol p) {
            this.p = p;
            this.number = Session.number(p);
            ProtocolOverride existing = session.existingOverride(p);
            this.o = existing != null ? existing : new ProtocolOverride();

            String name = p.getMetadata() == null ? null : p.getMetadata().getName();
            examField("title", "Title in the book", o.getTitle(), name);
            examField("contrastVolume", "Contrast volume (mL)", o.getContrastVolume(), ScannerValues.contrastVolume(p));
            examField("contrastRate", "Injection rate (mL/s)", o.getContrastRate(), ScannerValues.contrastRate(p));
            examField("contrastDelay", "Contrast delay (s)", o.getContrastDelay(), ScannerValues.contrastDelay(p));
            examField("sendDestination", "Sends to", o.getSendDestination(), autoSend());
            examField("scanRange", "Scan range", o.getScanRange(), p.getPatientSetup() == null ? null : p.getPatientSetup().getScanRange());
            examField("examCtdi", "Exam CTDIvol (mGy)", o.getExamCtdi(), ScannerValues.examCtdi(p));
            examField("examDlp", "Exam DLP (mGy·cm)", o.getExamDlp(), ScannerValues.examDlp(p));
            excluded.setSelected(o.isExcluded());
            threeD.setSelectedIndex(o.getThreeD() == null ? 0 : o.getThreeD() ? 1 : 2);
            notes.setText(o.getNotes() == null ? "" : o.getNotes());
            notes.setLineWrap(true);
            notes.setWrapStyleWord(true);

            for (Series s : p.getSeries()) {
                boolean scout = ScannerValues.isScout(s);
                GridRow row = new GridRow(s.getNumber() + " — " + (s.getName() != null ? s.getName() : s.getScanType()), seriesModel.fields.size(), s);
                for (int f = 0; f < seriesModel.fields.size(); f++) {
                    String field = seriesModel.fields.get(f);
                    row.editable[f] = !scout || "kv".equals(field) || "ma".equals(field);
                    row.scanner[f] = ScannerValues.seriesField(s, field);
                    String typed = o.seriesField(s.getNumber(), field);
                    row.value[f] = typed != null ? typed : trim(row.scanner[f]);
                }
                seriesModel.rows.add(row);
            }
            Set<String> seen = new HashSet<String>();
            for (Series s : p.getSeries()) {
                if (ScannerValues.isScout(s)) continue;
                for (Group g : s.getGroups())
                    for (Reconstruction r : g.getReconstructions()) {
                        if (!seen.add(normalize(r.getName()))) continue; // same-named recons share one override
                        GridRow row = new GridRow(String.valueOf(s.getNumber()) + (r.isDerived() ? "  (reformat)" : ""), reconModel.fields.size(), r);
                        for (int f = 0; f < reconModel.fields.size(); f++) {
                            String field = reconModel.fields.get(f);
                            row.editable[f] = true;
                            row.scanner[f] = ScannerValues.reconField(r, field, session.labels);
                            String typed = o.reconField(r, field);
                            row.value[f] = typed != null ? ("sendTo".equals(field) ? String.join(", ", o.sendDestinationsFor(r)) : typed) : trim(row.scanner[f]);
                        }
                        reconModel.rows.add(row);
                    }
            }
            addedModel = new AddedModel(o.getAddedFields());
            addedTable = new JTable(addedModel);
            for (ProtocolOverride.ScanRangePicture pic : o.getScanRangePictures()) {
                ProtocolOverride.ScanRangePicture copy = new ProtocolOverride.ScanRangePicture(pic.getImage());
                for (ProtocolOverride.Box b : pic.getBoxes()) copy.getBoxes().add(b.copy());
                pictures.add(copy);
            }
            startState = state();
        }

        private String autoSend() {
            Set<String> hosts = new java.util.LinkedHashSet<String>();
            for (Series s : p.getSeries()) for (Group g : s.getGroups()) for (Reconstruction r : g.getReconstructions()) hosts.addAll(r.getSendDestinations());
            return hosts.isEmpty() ? null : "auto-sends to " + String.join(", ", hosts);
        }

        private void examField(String key, String label, String typed, String scanner) {
            JTextField f = new JTextField(typed == null ? "" : typed, 30);
            f.setName(label);
            exam.put(key, f);
            examScanner.put(key, scanner);
        }

        JComponent component() {
            JPanel top = new JPanel(new BorderLayout());
            top.setBorder(new EmptyBorder(12, 14, 6, 14));
            JLabel title = new JLabel(number + " — " + (p.getMetadata() == null ? "" : p.getMetadata().getName()));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
            top.add(title, BorderLayout.WEST);
            top.add(excluded, BorderLayout.EAST);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Exam & contrast", examTab());
            tabs.addTab("Series settings", gridTab(seriesTable,
                    "kV, mA, pitch and so on per series, starting from what the scanner has. Change a cell to override it in the book; "
                            + "clear it (or type the scanner's value back) to use the scanner's. Hover a cell to see the scanner's value. "
                            + "Grey cells don't apply (scouts only show kV and mA).", 260, 70, 90, 90, 80, 90, 110));
            tabs.addTab("Recons", gridTab(reconTable,
                    "Each reconstruction, starting from what the scanner has. Kernel and ASIR show as typed (e.g. \"Bone\", \"40%\"). "
                            + "Sends to lists every destination, comma-separated.", 90, 300, 75, 75, 90, 60, 90, 160));
            tabs.addTab("Added fields", addedTab());
            tabs.addTab("Scan range pictures", picturesTab());

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(top, BorderLayout.NORTH);
            panel.add(tabs, BorderLayout.CENTER);
            return panel;
        }

        private JComponent examTab() {
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(new EmptyBorder(10, 12, 10, 12));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 8);
            c.anchor = GridBagConstraints.WEST;
            int y = 0;
            for (Map.Entry<String, JTextField> e : exam.entrySet()) {
                c.gridy = y++;
                c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
                form.add(new JLabel(e.getValue().getName() + ":"), c);
                c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
                form.add(e.getValue(), c);
                c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
                String scanner = examScanner.get(e.getKey());
                JLabel hint = new JLabel("Scanner: " + (trim(scanner).isEmpty() ? "(none)" : scanner.replace("\n", " / ")));
                hint.setForeground(new Color(0x777777));
                form.add(hint, c);
            }
            c.gridy = y++;
            c.gridx = 0; c.fill = GridBagConstraints.NONE;
            form.add(new JLabel("3D MIP / VR series:"), c);
            c.gridx = 1; c.fill = GridBagConstraints.NONE;
            form.add(threeD, c);
            c.gridy = y++;
            c.gridx = 0; c.anchor = GridBagConstraints.NORTHWEST;
            form.add(new JLabel("Scanning notes:"), c);
            c.gridx = 1; c.gridwidth = 2; c.weightx = 1; c.weighty = 1; c.fill = GridBagConstraints.BOTH;
            form.add(new JScrollPane(notes), c);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(Ui.help("Leave a box empty to use the scanner's value (shown on the right). Notes appear in a highlighted box "
                    + "near the top of the protocol's page."), BorderLayout.NORTH);
            panel.add(form, BorderLayout.CENTER);
            panel.setBorder(new EmptyBorder(8, 10, 8, 10));
            return panel;
        }

        private JComponent gridTab(JTable table, String help, int... widths) {
            Ui.setUp(table);
            table.setDefaultRenderer(Object.class, new GridRenderer());
            Ui.widths(table, widths);
            JButton reset = new JButton("Use the scanner's values for the selected row(s)");
            reset.addActionListener(e -> {
                stopEditing(table);
                GridModel m = (GridModel) table.getModel();
                for (int r : table.getSelectedRows()) {
                    GridRow row = m.rows.get(table.convertRowIndexToModel(r));
                    for (int f = 0; f < row.value.length; f++) row.value[f] = trim(row.scanner[f]);
                }
                m.fireTableDataChanged();
            });
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
            south.add(reset);
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(new EmptyBorder(8, 10, 8, 10));
            panel.add(Ui.help(help), BorderLayout.NORTH);
            panel.add(new JScrollPane(table), BorderLayout.CENTER);
            panel.add(south, BorderLayout.SOUTH);
            return panel;
        }

        // Pictures on the left, the selected one in the middle to draw on, its boxes (color + label) on the right.
        private JComponent picturesTab() {
            File folder = session.file(ScanRangePictures.FOLDER);
            DefaultListModel<ProtocolOverride.ScanRangePicture> listModel = new DefaultListModel<ProtocolOverride.ScanRangePicture>();
            for (ProtocolOverride.ScanRangePicture pic : pictures) listModel.addElement(pic);
            JList<ProtocolOverride.ScanRangePicture> list = new JList<ProtocolOverride.ScanRangePicture>(listModel);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                    ProtocolOverride.ScanRangePicture pic = (ProtocolOverride.ScanRangePicture) v;
                    int n = pic.getBoxes().size();
                    JLabel label = (JLabel) super.getListCellRendererComponent(l, "<html><b>" + pic.getImage() + "</b><br>" + n + " box" + (n == 1 ? "" : "es") + "</html>", i, sel, focus);
                    label.setIcon(PicturePicker.thumbnail(new File(folder, pic.getImage())) == null ? null
                            : new ImageIcon(PicturePicker.thumbnail(new File(folder, pic.getImage())).getImage().getScaledInstance(48, -1, Image.SCALE_SMOOTH)));
                    label.setBorder(new EmptyBorder(4, 4, 4, 4));
                    return label;
                }
            });

            BoxModel boxModel = new BoxModel();
            JTable boxTable = new JTable(boxModel);
            Ui.setUp(boxTable);
            boxTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            Ui.widths(boxTable, 50, 190);
            boxTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                    super.getTableCellRendererComponent(t, "", sel, focus, r, c);
                    setBackground(ScanRangePictures.color((String) v));
                    setToolTipText("Double-click to change the color");
                    return this;
                }
            });
            final boolean[] syncing = {false};
            ScanRangeCanvas canvas = new ScanRangeCanvas(() -> {
                boxModel.fireTableDataChanged();
                list.repaint();
            }, box -> {
                if (syncing[0]) return;
                syncing[0] = true;
                int row = box == null ? -1 : boxModel.boxes.indexOf(box);
                if (row >= 0) boxTable.setRowSelectionInterval(row, row); else boxTable.clearSelection();
                syncing[0] = false;
            });
            boxModel.onEdit = canvas::repaint;
            boxTable.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting() || syncing[0]) return;
                syncing[0] = true;
                int row = boxTable.getSelectedRow();
                canvas.select(row >= 0 && row < boxModel.boxes.size() ? boxModel.boxes.get(row) : null);
                syncing[0] = false;
            });
            Runnable recolor = () -> {
                ProtocolOverride.Box b = canvas.selected();
                if (b == null) { JOptionPane.showMessageDialog(ProtocolEditorDialog.this, "Select a box first."); return; }
                Color c = JColorChooser.showDialog(ProtocolEditorDialog.this, "Box color", ScanRangePictures.color(b.getColor()));
                if (c == null) return;
                b.setColor(String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()));
                boxModel.fireTableDataChanged();
                canvas.repaint();
            };
            boxTable.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2 && boxTable.columnAtPoint(e.getPoint()) == 0) recolor.run();
                }
            });

            list.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                ProtocolOverride.ScanRangePicture pic = list.getSelectedValue();
                java.awt.image.BufferedImage img = null;
                if (pic != null) {
                    try { img = javax.imageio.ImageIO.read(new File(folder, pic.getImage())); } catch (java.io.IOException ignored) {}
                    if (img == null) JOptionPane.showMessageDialog(ProtocolEditorDialog.this,
                            "Can't open " + new File(folder, pic.getImage()) + " - it may have been moved or renamed.");
                }
                boxModel.boxes = pic == null ? new ArrayList<ProtocolOverride.Box>() : pic.getBoxes();
                boxModel.fireTableDataChanged();
                canvas.show(img, pic == null ? null : pic.getBoxes());
            });

            JButton add = new JButton("Add picture…");
            add.addActionListener(e -> {
                String name = PicturePicker.choose(ProtocolEditorDialog.this, folder);
                if (name == null) return;
                ProtocolOverride.ScanRangePicture pic = new ProtocolOverride.ScanRangePicture(name);
                pictures.add(pic);
                listModel.addElement(pic);
                list.setSelectedValue(pic, true);
            });
            JButton remove = new JButton("Remove picture");
            remove.addActionListener(e -> {
                ProtocolOverride.ScanRangePicture pic = list.getSelectedValue();
                if (pic == null) return;
                if (!pic.getBoxes().isEmpty() && JOptionPane.showConfirmDialog(ProtocolEditorDialog.this,
                        "Remove " + pic.getImage() + " and its boxes from this protocol? (The picture stays in the library.)",
                        "Remove picture", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
                pictures.remove(pic);
                listModel.removeElement(pic);
            });
            JButton applyOthers = new JButton("Apply to other protocols…");
            applyOthers.setToolTipText("Copy this picture and its boxes to other protocols in the book");
            applyOthers.addActionListener(e -> {
                ProtocolOverride.ScanRangePicture pic = list.getSelectedValue();
                if (pic == null || pic.getBoxes().isEmpty()) {
                    JOptionPane.showMessageDialog(ProtocolEditorDialog.this, "Pick a picture and draw its boxes first.");
                    return;
                }
                List<Protocol> done = ApplyBoxesDialog.show(ProtocolEditorDialog.this, session, p, pic);
                if (done.isEmpty() || !gui.save()) return;
                int skipped = 0;
                for (Protocol q : done) if (queue.contains(q) && queue.indexOf(q) != index && skip.add(q)) skipped++;
                updateNavigation();
                JOptionPane.showMessageDialog(ProtocolEditorDialog.this, "Applied " + pic.getImage() + " to " + done.size() + " protocol"
                        + (done.size() == 1 ? "" : "s") + "."
                        + (skipped == 0 ? "" : "\n" + skipped + " of them " + (skipped == 1 ? "was" : "were")
                        + " waiting in this editor and will be skipped by Save & next (double-click one on the section screen to edit it)."));
            });
            JButton color = new JButton("Change color…");
            color.addActionListener(e -> recolor.run());
            JButton delete = new JButton("Delete box");
            delete.addActionListener(e -> canvas.deleteSelected());

            JPanel left = new JPanel(new BorderLayout(0, 6));
            left.add(new JScrollPane(list), BorderLayout.CENTER);
            JPanel leftButtons = new JPanel(new GridLayout(0, 1, 0, 4));
            leftButtons.add(add);
            leftButtons.add(remove);
            leftButtons.add(applyOthers);
            left.add(leftButtons, BorderLayout.SOUTH);
            left.setPreferredSize(new Dimension(220, 100));

            JPanel right = new JPanel(new BorderLayout(0, 6));
            right.add(new JScrollPane(boxTable), BorderLayout.CENTER);
            JPanel rightButtons = new JPanel(new GridLayout(0, 1, 0, 4));
            rightButtons.add(color);
            rightButtons.add(delete);
            right.add(rightButtons, BorderLayout.SOUTH);
            right.setPreferredSize(new Dimension(260, 100));

            JPanel panel = new JPanel(new BorderLayout(8, 6));
            panel.setBorder(new EmptyBorder(8, 10, 8, 10));
            panel.add(Ui.help("Add a picture, then drag on it to draw a box - each new box gets the next color, and boxes can overlap. "
                    + "Click a box to select it, then drag it to move or drag a corner to resize; Delete removes it. "
                    + "Type a label for each box on the right (e.g. Arterial, Venous). "
                    + "The boxes are drawn into the picture in the book."), BorderLayout.NORTH);
            panel.add(left, BorderLayout.WEST);
            panel.add(canvas, BorderLayout.CENTER);
            panel.add(right, BorderLayout.EAST);
            if (!listModel.isEmpty()) list.setSelectedIndex(0);
            return panel;
        }

        /** The selected picture's boxes: Color / Label. */
        private final class BoxModel extends AbstractTableModel {
            List<ProtocolOverride.Box> boxes = new ArrayList<ProtocolOverride.Box>();
            Runnable onEdit;

            @Override public int getRowCount() { return boxes.size(); }

            @Override public int getColumnCount() { return 2; }

            @Override public String getColumnName(int c) { return c == 0 ? "Color" : "Label"; }

            @Override public boolean isCellEditable(int r, int c) { return c == 1; }

            @Override public Object getValueAt(int r, int c) { return c == 0 ? boxes.get(r).getColor() : boxes.get(r).getLabel(); }

            @Override public void setValueAt(Object v, int r, int c) {
                boxes.get(r).setLabel(v == null ? "" : v.toString().trim());
                fireTableRowsUpdated(r, r);
                if (onEdit != null) onEdit.run();
            }
        }

        private JComponent addedTab() {
            Ui.setUp(addedTable);
            Ui.widths(addedTable, 260, 220, 520);
            JComboBox<String> where = new JComboBox<String>(addedModel.choices.toArray(new String[0]));
            addedTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(where));
            JComboBox<String> titles = new JComboBox<String>(session.addedFieldTitles().toArray(new String[0]));
            titles.setEditable(true);
            addedTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(titles));

            JButton add = new JButton("Add a field…");
            add.addActionListener(e -> addField());
            JButton remove = new JButton("Remove selected");
            remove.addActionListener(e -> {
                stopEditing(addedTable);
                int[] selected = addedTable.getSelectedRows();
                for (int i = selected.length - 1; i >= 0; i--) addedModel.fields.remove(selected[i]);
                addedModel.fireTableDataChanged();
            });
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
            south.add(add);
            south.add(remove);
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(new EmptyBorder(8, 10, 8, 10));
            panel.add(Ui.help("Information the scanner has no place for - e.g. \"Oral contrast: 900 mL over 1 hour\" or \"Breath hold: Inspiration\". "
                    + "An exam field shows under the protocol's header; a series field shows under that series. "
                    + "Titles you've used before are offered again."), BorderLayout.NORTH);
            panel.add(new JScrollPane(addedTable), BorderLayout.CENTER);
            panel.add(south, BorderLayout.SOUTH);
            return panel;
        }

        // Applies to (exam or a series) -> title -> value, in that order.
        private void addField() {
            stopEditing(addedTable);
            JComboBox<String> where = new JComboBox<String>(addedModel.choices.toArray(new String[0]));
            JComboBox<String> title = new JComboBox<String>(session.addedFieldTitles().toArray(new String[0]));
            title.setEditable(true);
            title.setSelectedItem("");
            JTextArea value = new JTextArea(4, 36);
            value.setLineWrap(true);
            value.setWrapStyleWord(true);
            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.NORTHWEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.gridx = 0; c.gridy = 0; form.add(new JLabel("Applies to:"), c);
            c.gridx = 1; form.add(where, c);
            c.gridx = 0; c.gridy = 1; form.add(new JLabel("Title:"), c);
            c.gridx = 1; form.add(title, c);
            c.gridx = 0; c.gridy = 2; form.add(new JLabel("Value:"), c);
            c.gridx = 1; form.add(new JScrollPane(value), c);
            if (JOptionPane.showConfirmDialog(ProtocolEditorDialog.this, form, "Add a field", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                    != JOptionPane.OK_OPTION) return;
            String t = title.getEditor().getItem() == null ? "" : title.getEditor().getItem().toString().trim();
            if (t.isEmpty() && value.getText().trim().isEmpty()) return;
            addedModel.fields.add(new ProtocolOverride.AddedField(addedModel.seriesFor((String) where.getSelectedItem()), t, value.getText().trim()));
            addedModel.fireTableDataChanged();
        }

        /** Everything on screen as text, to tell whether anything changed since the protocol was opened. */
        String state() {
            StringBuilder s = new StringBuilder().append(excluded.isSelected()).append(threeD.getSelectedIndex()).append(notes.getText());
            for (JTextField f : exam.values()) s.append('|').append(f.getText());
            for (GridRow r : seriesModel.rows) s.append('|').append(String.join(",", r.value));
            for (GridRow r : reconModel.rows) s.append('|').append(String.join(",", r.value));
            for (ProtocolOverride.AddedField f : addedModel.fields) s.append('|').append(f.getSeries()).append(f.getTitle()).append(f.getValue());
            for (ProtocolOverride.ScanRangePicture pic : pictures) {
                s.append('|').append(pic.getImage());
                for (ProtocolOverride.Box b : pic.getBoxes())
                    s.append(',').append(b.getLabel()).append(b.getColor()).append(b.getX()).append(b.getY()).append(b.getW()).append(b.getH());
            }
            return s.toString();
        }

        boolean changed() { return !state().equals(startState); }

        /** Writes what's on screen into the session's overrides (only what differs from the scanner). */
        void apply() {
            ProtocolOverride target = session.overrideFor(number);
            target.setExcluded(excluded.isSelected());
            String name = p.getMetadata() == null ? null : p.getMetadata().getName();
            String title = blankToNull(exam.get("title").getText());
            target.setTitle(title != null && title.equals(trim(name)) ? null : title);
            target.setContrastVolume(typed("contrastVolume"));
            target.setContrastRate(typed("contrastRate"));
            target.setContrastDelay(typed("contrastDelay"));
            target.setSendDestination(blankToNull(exam.get("sendDestination").getText()));
            // not compared with the hint: with reference workbooks the protocol's scan range may already be this override
            target.setScanRange(blankToNull(exam.get("scanRange").getText()));
            target.setExamCtdi(typed("examCtdi"));
            target.setExamDlp(typed("examDlp"));
            target.setThreeD(threeD.getSelectedIndex() == 0 ? null : threeD.getSelectedIndex() == 1);
            target.setNotes(blankToNull(notes.getText()));

            for (GridRow row : seriesModel.rows) {
                Series s = (Series) row.source;
                Map<String, String> fields = changedFields(row, seriesModel.fields);
                if (fields.isEmpty()) target.getSeries().remove(String.valueOf(s.getNumber()));
                else target.getSeries().put(String.valueOf(s.getNumber()), fields);
            }
            for (GridRow row : reconModel.rows) {
                Reconstruction r = (Reconstruction) row.source;
                // drop whatever was typed under any spelling of this recon's name, then write it back under the scanner's spelling
                removeByName(target.getRecons(), r.getName());
                removeByName(target.getReconSendDestinations(), r.getName());
                Map<String, String> fields = changedFields(row, reconModel.fields);
                if (!fields.isEmpty()) target.getRecons().put(r.getName(), fields);
            }
            target.getScanRangePictures().clear();
            target.getScanRangePictures().addAll(pictures);
            target.getAddedFields().clear();
            for (ProtocolOverride.AddedField f : addedModel.fields) if (!f.isBlank()) target.getAddedFields().add(f);
        }

        // A typed value that's the same as the scanner's isn't an override - it would only hide later scanner changes.
        private String typed(String key) {
            String v = blankToNull(exam.get(key).getText());
            return v == null || v.equals(trim(examScanner.get(key))) ? null : v;
        }

        private Map<String, String> changedFields(GridRow row, List<String> fields) {
            Map<String, String> out = new LinkedHashMap<String, String>();
            for (int f = 0; f < fields.size(); f++) {
                String v = trim(row.value[f]);
                if (row.editable[f] && !v.isEmpty() && !v.equals(trim(row.scanner[f]))) out.put(fields.get(f), v);
            }
            return out;
        }

        private <V> void removeByName(Map<String, V> map, String name) {
            for (Iterator<String> it = map.keySet().iterator(); it.hasNext(); ) if (normalize(it.next()).equals(normalize(name))) it.remove();
        }

        /** The added fields table: Applies to / Title / Value. */
        private final class AddedModel extends AbstractTableModel {
            final List<ProtocolOverride.AddedField> fields = new ArrayList<ProtocolOverride.AddedField>();
            final List<String> choices = new ArrayList<String>();
            private final Map<String, String> seriesByChoice = new LinkedHashMap<String, String>();

            AddedModel(List<ProtocolOverride.AddedField> existing) {
                for (ProtocolOverride.AddedField f : existing) fields.add(new ProtocolOverride.AddedField(f.getSeries(), f.getTitle(), f.getValue()));
                choices.add("Exam");
                seriesByChoice.put("Exam", null);
                for (Series s : p.getSeries()) {
                    String label = "Series " + s.getNumber() + " — " + (s.getName() != null ? s.getName() : s.getScanType());
                    choices.add(label);
                    seriesByChoice.put(label, String.valueOf(s.getNumber()));
                }
            }

            String seriesFor(String choice) { return seriesByChoice.get(choice); }

            String choiceFor(ProtocolOverride.AddedField f) {
                if (f.isExam()) return "Exam";
                for (Map.Entry<String, String> e : seriesByChoice.entrySet()) if (f.getSeries().trim().equals(e.getValue())) return e.getKey();
                return "Series " + f.getSeries() + " (not in this protocol)";
            }

            @Override public int getRowCount() { return fields.size(); }

            @Override public int getColumnCount() { return 3; }

            @Override public String getColumnName(int c) { return c == 0 ? "Applies to" : c == 1 ? "Title" : "Value"; }

            @Override public boolean isCellEditable(int r, int c) { return true; }

            @Override public Object getValueAt(int r, int c) {
                ProtocolOverride.AddedField f = fields.get(r);
                return c == 0 ? choiceFor(f) : c == 1 ? f.getTitle() : f.getValue();
            }

            @Override public void setValueAt(Object v, int r, int c) {
                ProtocolOverride.AddedField f = fields.get(r);
                String text = v == null ? "" : v.toString();
                if (c == 0) { if (seriesByChoice.containsKey(text)) f.setSeries(seriesFor(text)); }
                else if (c == 1) f.setTitle(text.trim());
                else f.setValue(text.trim());
                fireTableRowsUpdated(r, r);
            }
        }
    }
}
