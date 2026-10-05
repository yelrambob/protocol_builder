package com.protocolbook.gui;

import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Copies one picture and its boxes to other protocols in the book: a checklist (the current section
 * first) of every protocol not left out. Protocols that already have exactly these boxes are skipped;
 * ones using the same picture with different boxes are replaced after a warning.
 */
final class ApplyBoxesDialog extends JDialog {
    private enum Status { NONE, SAME, DIFFERENT }

    private final Session session;
    private final ProtocolOverride.ScanRangePicture picture;
    private final List<Protocol> rows = new ArrayList<Protocol>();
    private final List<Status> status = new ArrayList<Status>();
    private final Set<Protocol> ticked = Collections.newSetFromMap(new IdentityHashMap<Protocol, Boolean>());
    private final Model model = new Model();
    private final JButton apply = new JButton();
    private final List<Protocol> applied = new ArrayList<Protocol>();

    private ApplyBoxesDialog(Window owner, Session session, Protocol current, ProtocolOverride.ScanRangePicture picture) {
        super(owner, "Apply " + picture.getImage() + " and its boxes to other protocols", ModalityType.APPLICATION_MODAL);
        this.session = session;
        this.picture = picture;
        setSize(900, 600);
        setLocationRelativeTo(owner);

        Session.Section home = session.sectionOf(current);
        List<Protocol> others = new ArrayList<Protocol>();
        for (Protocol p : session.sortedProtocols) {
            if (p == current || session.isExcluded(p) || session.sectionOf(p) == null) continue;
            if (home != null && session.sectionOf(p) == home) rows.add(p);
            else others.add(p);
        }
        rows.addAll(others);
        for (Protocol p : rows) status.add(statusOf(p));

        JTable table = new JTable(model);
        Ui.setUp(table);
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                Status s = status.get(t.convertRowIndexToModel(r));
                if (!sel) setForeground(s == Status.SAME ? Color.GRAY : c == 4 && s == Status.DIFFERENT ? Ui.WARNING : t.getForeground());
                return this;
            }
        });
        Ui.widths(table, 55, 55, 330, 160, 260);
        TableRowSorter<Model> sorter = new TableRowSorter<Model>(model);
        sorter.setSortable(0, false);
        table.setRowSorter(sorter);

        JTextField search = new JTextField(20);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }

            private void filter() {
                String text = search.getText().trim();
                sorter.setRowFilter(text.isEmpty() ? null : RowFilter.<Model, Integer>regexFilter("(?i)" + Pattern.quote(text), 1, 2, 3));
            }
        });
        JButton section = new JButton("Tick this section");
        section.setEnabled(home != null);
        section.addActionListener(e -> {
            for (int i = 0; i < rows.size(); i++) if (session.sectionOf(rows.get(i)) == home && status.get(i) != Status.SAME) ticked.add(rows.get(i));
            changed();
        });
        JButton shown = new JButton("Tick all shown");
        shown.addActionListener(e -> {
            for (int r = 0; r < table.getRowCount(); r++) {
                int i = table.convertRowIndexToModel(r);
                if (status.get(i) != Status.SAME) ticked.add(rows.get(i));
            }
            changed();
        });
        JButton none = new JButton("Untick all");
        none.addActionListener(e -> { ticked.clear(); changed(); });

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(new JLabel("Search:"));
        tools.add(search);
        tools.add(section);
        tools.add(shown);
        tools.add(none);

        apply.addActionListener(e -> applyTicked());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(apply);

        int n = picture.getBoxes().size();
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(Ui.help("Copies " + picture.getImage() + " with its " + n + " box" + (n == 1 ? "" : "es") + " to the protocols you tick. "
                + "Each gets its own copy, so you can still adjust one later. Protocols left out of the book aren't listed; "
                + "ones that already have exactly these boxes are greyed out and skipped."), BorderLayout.NORTH);
        top.add(tools, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(10, 12, 4, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        changed();
    }

    /** Shows the checklist; returns the protocols the picture was applied to (empty if cancelled). */
    static List<Protocol> show(Window owner, Session session, Protocol current, ProtocolOverride.ScanRangePicture picture) {
        ApplyBoxesDialog d = new ApplyBoxesDialog(owner, session, current, picture);
        d.setVisible(true);
        return d.applied;
    }

    private Status statusOf(Protocol p) {
        ProtocolOverride o = session.existingOverride(p);
        if (o == null) return Status.NONE;
        for (ProtocolOverride.ScanRangePicture pic : o.getScanRangePictures())
            if (picture.getImage().equals(pic.getImage())) return sameBoxes(pic.getBoxes(), picture.getBoxes()) ? Status.SAME : Status.DIFFERENT;
        return Status.NONE;
    }

    static boolean sameBoxes(List<ProtocolOverride.Box> a, List<ProtocolOverride.Box> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ProtocolOverride.Box x = a.get(i), y = b.get(i);
            if (!String.valueOf(x.getLabel()).equals(String.valueOf(y.getLabel())) || !String.valueOf(x.getColor()).equalsIgnoreCase(String.valueOf(y.getColor()))
                    || Math.abs(x.getX() - y.getX()) > 1e-4 || Math.abs(x.getY() - y.getY()) > 1e-4
                    || Math.abs(x.getW() - y.getW()) > 1e-4 || Math.abs(x.getH() - y.getH()) > 1e-4) return false;
        }
        return true;
    }

    /** Copies the picture onto one protocol's overrides, replacing that picture's boxes if it already had it. */
    static void applyTo(ProtocolOverride o, ProtocolOverride.ScanRangePicture picture) {
        ProtocolOverride.ScanRangePicture copy = new ProtocolOverride.ScanRangePicture(picture.getImage());
        for (ProtocolOverride.Box b : picture.getBoxes()) copy.getBoxes().add(b.copy());
        List<ProtocolOverride.ScanRangePicture> pics = o.getScanRangePictures();
        for (int i = 0; i < pics.size(); i++)
            if (picture.getImage().equals(pics.get(i).getImage())) { pics.set(i, copy); return; }
        pics.add(copy);
    }

    private void changed() {
        model.fireTableDataChanged();
        apply.setText("Apply to " + ticked.size() + " protocol" + (ticked.size() == 1 ? "" : "s"));
        apply.setEnabled(!ticked.isEmpty());
    }

    private void applyTicked() {
        List<String> replaced = new ArrayList<String>();
        for (int i = 0; i < rows.size(); i++)
            if (ticked.contains(rows.get(i)) && status.get(i) == Status.DIFFERENT) replaced.add(Session.number(rows.get(i)) + " " + name(rows.get(i)));
        if (!replaced.isEmpty() && JOptionPane.showConfirmDialog(this, "These already use " + picture.getImage() + " with different boxes, "
                + "which will be replaced:\n\n" + String.join("\n", replaced), "Replace boxes?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.OK_OPTION) return;
        for (Protocol p : rows) {
            if (!ticked.contains(p)) continue;
            applyTo(session.overrideFor(Session.number(p)), picture);
            applied.add(p);
        }
        dispose();
    }

    private static String name(Protocol p) {
        return p.getMetadata() == null ? "" : p.getMetadata().getName();
    }

    private final class Model extends AbstractTableModel {
        private final String[] columns = {"Apply", "#", "Protocol", "Section", "Status"};

        @Override public int getRowCount() { return rows.size(); }

        @Override public int getColumnCount() { return columns.length; }

        @Override public String getColumnName(int c) { return columns[c]; }

        @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }

        @Override public boolean isCellEditable(int r, int c) { return c == 0 && status.get(r) != Status.SAME; }

        @Override public Object getValueAt(int r, int c) {
            Protocol p = rows.get(r);
            switch (c) {
                case 0: return ticked.contains(p);
                case 1: return Session.number(p);
                case 2: return name(p);
                case 3: return session.sectionOf(p).title();
                default:
                    return status.get(r) == Status.SAME ? "Already applied - skipped"
                            : status.get(r) == Status.DIFFERENT ? "Different boxes - will be replaced" : "";
            }
        }

        @Override public void setValueAt(Object v, int r, int c) {
            if (Boolean.TRUE.equals(v)) ticked.add(rows.get(r)); else ticked.remove(rows.get(r));
            changed();
        }
    }
}
