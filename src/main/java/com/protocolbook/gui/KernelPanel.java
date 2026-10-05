package com.protocolbook.gui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The GUI's "Kernel names" screen - the --init-kernel-labels job without the command line. The export
 * only has a number for each recon kernel; this lists every code the protocols use, a few recon names
 * using it (so "BONE" in a name gives it away), and a box to type the name the book should show.
 * Each name is saved to kernel-labels.json as soon as it's typed.
 */
final class KernelPanel extends JPanel implements ProtocolBuilderGui.Refreshable {
    private final ProtocolBuilderGui gui;
    private final Session session;
    private final List<String> codes = new ArrayList<String>();
    private final Model model = new Model();
    private final JLabel status = new JLabel();

    KernelPanel(ProtocolBuilderGui gui) {
        super(new BorderLayout());
        this.gui = gui;
        this.session = gui.session();
        codes.addAll(session.kernelSamples.keySet());
        Collections.sort(codes, (a, b) -> {
            try { return Long.compare(Long.parseLong(a), Long.parseLong(b)); } catch (NumberFormatException e) { return a.compareTo(b); }
        });

        JTable table = new JTable(model);
        Ui.setUp(table);
        Ui.widths(table, 80, 180, 700);
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                boolean blank = v == null || v.toString().trim().isEmpty();
                setText(blank ? "(type a name)" : v.toString());
                setFont(getFont().deriveFont(blank ? Font.ITALIC : Font.BOLD));
                if (!sel) setForeground(blank ? Ui.WARNING : t.getForeground());
                return this;
            }
        });

        JPanel top = new JPanel(new BorderLayout());
        top.add(Ui.help("The scanner export only gives each recon kernel as a number. Type the name the book should show for each one "
                + "(e.g. Standard, Detail, Bone, Lung) - the recon names next to it usually give it away. Names are saved to "
                + "kernel-labels.json as you type them and are kept for next time, so this only needs doing when a new kernel shows up. "
                + "A kernel left blank shows as its number."), BorderLayout.NORTH);
        top.add(status, BorderLayout.CENTER);

        JPanel screen = Ui.screen();
        screen.add(top, BorderLayout.NORTH);
        screen.add(new JScrollPane(table), BorderLayout.CENTER);
        add(screen, BorderLayout.CENTER);
    }

    /** How many kernel codes the protocols use that have no name yet. */
    static int unnamed(Session session) {
        int n = 0;
        for (String code : session.kernelSamples.keySet()) {
            String name = session.kernelNames.get(code);
            if (name == null || name.trim().isEmpty()) n++;
        }
        return n;
    }

    @Override public void refresh() {
        model.fireTableDataChanged();
        int blank = unnamed(session);
        status.setText(blank == 0 ? "All " + codes.size() + " kernels are named." : blank + " of " + codes.size() + " kernels still need a name.");
        status.setForeground(blank == 0 ? new Color(0x2e7d32) : Ui.WARNING);
    }

    private final class Model extends AbstractTableModel {
        private final String[] columns = {"Code", "Name in the book", "Used by recons such as"};

        @Override public int getRowCount() { return codes.size(); }

        @Override public int getColumnCount() { return columns.length; }

        @Override public String getColumnName(int c) { return columns[c]; }

        @Override public boolean isCellEditable(int r, int c) { return c == 1; }

        @Override public Object getValueAt(int r, int c) {
            String code = codes.get(r);
            if (c == 0) return code;
            if (c == 1) return session.kernelNames.get(code);
            List<String> samples = session.kernelSamples.get(code);
            return samples == null ? "" : String.join(",  ", samples);
        }

        @Override public void setValueAt(Object v, int r, int c) {
            try {
                session.nameKernel(codes.get(r), v == null ? "" : v.toString());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(KernelPanel.this, "Couldn't save kernel-labels.json:\n" + e.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
            }
            refresh();
            gui.stepsChanged();
        }
    }
}
