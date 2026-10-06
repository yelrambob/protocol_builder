package com.protocolbook.gui;

import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One section of the book (e.g. Adult - Head): every protocol on one line with the settings worth a
 * glance, a "Change" box to pick the ones to edit, and a column flagging anything that looks off.
 */
final class SectionPanel extends JPanel implements ProtocolBuilderGui.Refreshable {
    private final ProtocolBuilderGui gui;
    private final Session session;
    private final Session.Section section;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JButton editChecked = new JButton();
    private final JButton contrastChecked = new JButton();
    private final JCheckBox reviewed = new JCheckBox("Section reviewed");
    private final JLabel excludedNote = new JLabel();
    private final Set<Protocol> checked = Collections.newSetFromMap(new IdentityHashMap<Protocol, Boolean>());
    private List<SectionRow> rows = new ArrayList<SectionRow>();

    SectionPanel(ProtocolBuilderGui gui, Session.Section section) {
        super(new BorderLayout());
        this.gui = gui;
        this.session = gui.session();
        this.section = section;

        Ui.setUp(table);
        table.setDefaultRenderer(Object.class, new Ui.CellRenderer());
        table.getColumnModel().getColumn(model.getColumnCount() - 1).setCellRenderer(new WarningRenderer());
        Ui.widths(table, 38, 45, 250, 42, 75, 50, 135, 45, 105, 90, 90, 180);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0 && table.columnAtPoint(e.getPoint()) != 0) {
                    List<Protocol> one = new ArrayList<Protocol>();
                    one.add(rows.get(table.convertRowIndexToModel(row)).protocol);
                    edit(one);
                }
            }
        });

        editChecked.addActionListener(e -> edit(checkedInOrder()));
        contrastChecked.addActionListener(e -> setContrast(checkedInOrder()));
        JButton checkAll = new JButton("Check all");
        checkAll.addActionListener(e -> {
            for (SectionRow r : rows) checked.add(r.protocol);
            refresh();
        });
        JButton uncheck = new JButton("Uncheck all");
        uncheck.addActionListener(e -> {
            checked.clear();
            refresh();
        });
        reviewed.addActionListener(e -> {
            if (reviewed.isSelected()) session.reviewedSections.add(section.title());
            else session.reviewedSections.remove(section.title());
            gui.stepsChanged();
        });

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(editChecked);
        tools.add(contrastChecked);
        tools.add(checkAll);
        tools.add(uncheck);
        tools.add(Box.createHorizontalStrut(20));
        tools.add(reviewed);

        JPanel top = new JPanel(new BorderLayout());
        top.add(Ui.help("Tick Edit on the protocols you want to change, then press Edit checked - or double-click a row. "
                + "Values set by hand show in the accent color. \"Worth a look\" flags things that may need fixing; it's a hint, not an error."),
                BorderLayout.NORTH);
        top.add(tools, BorderLayout.CENTER);
        excludedNote.setForeground(new Color(0x555555));

        JPanel screen = Ui.screen();
        screen.add(top, BorderLayout.NORTH);
        screen.add(new JScrollPane(table), BorderLayout.CENTER);
        screen.add(excludedNote, BorderLayout.SOUTH);
        add(screen, BorderLayout.CENTER);
    }

    @Override public void refresh() {
        List<Protocol> shown = new ArrayList<Protocol>();
        int left = 0;
        for (Protocol p : section.protocols) {
            if (session.isExcluded(p)) left++;
            else shown.add(p);
        }
        checked.retainAll(new LinkedHashSet<Protocol>(shown));
        rows = SectionRow.forSection(shown, session);
        model.fireTableDataChanged();
        reviewed.setSelected(session.reviewedSections.contains(section.title()));
        excludedNote.setText(left == 0 ? " " : left + " protocol" + (left == 1 ? " is" : "s are") + " left out of this section (see step 2).");
        updateButtons();
    }

    private void updateButtons() {
        int n = checked.size();
        editChecked.setText("Edit checked (" + n + ")…");
        contrastChecked.setText("Set contrast for checked (" + n + ")…");
        editChecked.setEnabled(n > 0);
        contrastChecked.setEnabled(n > 0);
    }

    private List<Protocol> checkedInOrder() {
        List<Protocol> out = new ArrayList<Protocol>();
        for (SectionRow r : rows) if (checked.contains(r.protocol)) out.add(r.protocol);
        return out;
    }

    private void edit(List<Protocol> protocols) {
        if (protocols.isEmpty()) return;
        new ProtocolEditorDialog(gui, protocols).setVisible(true);
        refresh();
    }

    // The same contrast volume/rate/delay on several protocols at once - the export's figures are often off the same way.
    private void setContrast(List<Protocol> protocols) {
        JTextField volume = new JTextField(8), rate = new JTextField(8), delay = new JTextField(8);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel("Contrast volume (mL):"));
        form.add(volume);
        form.add(new JLabel("Injection rate (mL/s):"));
        form.add(rate);
        form.add(new JLabel("Contrast delay (s):"));
        form.add(delay);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("<html>Applies to the " + protocols.size() + " checked protocol(s).<br>Leave a box empty to leave that value as it is.</html>"),
                BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, panel, "Set contrast", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
            return;
        for (Protocol p : protocols) {
            ProtocolOverride o = session.overrideFor(Session.number(p));
            if (!volume.getText().trim().isEmpty()) o.setContrastVolume(volume.getText().trim());
            if (!rate.getText().trim().isEmpty()) o.setContrastRate(rate.getText().trim());
            if (!delay.getText().trim().isEmpty()) o.setContrastDelay(delay.getText().trim());
        }
        gui.save();
        refresh();
    }

    private final class Model extends AbstractTableModel {
        private final String[] columns = {"Edit", "#", "Protocol", "kV", "mA / mAs", "Pitch", "Contrast (mL @ mL/s)", "Delay", "Main recon", "CTDIvol", "Notes", "Worth a look"};

        @Override public int getRowCount() { return rows.size(); }

        @Override public int getColumnCount() { return columns.length; }

        @Override public String getColumnName(int c) { return columns[c]; }

        @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : Object.class; }

        @Override public boolean isCellEditable(int r, int c) { return c == 0; }

        @Override public Object getValueAt(int r, int c) {
            SectionRow row = rows.get(r);
            switch (c) {
                case 0: return checked.contains(row.protocol);
                case 1: return row.number;
                case 2: return row.name;
                case 3: return row.kv;
                case 4: return row.ma;
                case 5: return row.pitch;
                case 6: return row.contrast;
                case 7: return row.delay;
                case 8: return row.recon;
                case 9: return row.ctdi;
                case 10: return row.extras;
                default: return String.join("; ", row.warnings);
            }
        }

        @Override public void setValueAt(Object value, int r, int c) {
            if (Boolean.TRUE.equals(value)) checked.add(rows.get(r).protocol);
            else checked.remove(rows.get(r).protocol);
            updateButtons();
        }
    }

    private static final class WarningRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            String text = value == null ? "" : value.toString();
            if (!selected) setForeground(Ui.WARNING);
            setText(text.isEmpty() ? "" : "⚠ " + text);
            setToolTipText(text.isEmpty() ? null : text);
            return this;
        }
    }
}
