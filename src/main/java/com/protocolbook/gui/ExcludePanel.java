package com.protocolbook.gui;

import com.protocolbook.model.Protocol;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Step 2: every protocol with a "Leave out" box - ticking one sets "excluded" in the overrides file. */
final class ExcludePanel extends JPanel implements ProtocolBuilderGui.Refreshable {
    private final ProtocolBuilderGui gui;
    private final Session session;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JLabel count = new JLabel();
    private final Map<Protocol, String> duplicateNotes;

    ExcludePanel(ProtocolBuilderGui gui) {
        super(new BorderLayout());
        this.gui = gui;
        this.session = gui.session();
        this.duplicateNotes = session.duplicateNotes();

        Ui.setUp(table);
        table.setDefaultRenderer(Object.class, new Ui.CellRenderer());
        Ui.widths(table, 70, 60, 380, 160, 160, 260);
        TableRowSorter<Model> sorter = new TableRowSorter<Model>(model);
        sorter.setComparator(1, (a, b) -> com.protocolbook.html.ProtocolNumbers.compare(String.valueOf(a), String.valueOf(b)));
        table.setRowSorter(sorter);

        JTextField search = new JTextField(24);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }

            private void filter() {
                String text = search.getText().trim();
                sorter.setRowFilter(text.isEmpty() ? null : RowFilter.<Model, Integer>regexFilter("(?i)" + Pattern.quote(text), 1, 2, 3, 5));
            }
        });

        JButton duplicates = new JButton("Leave out suggested duplicates");
        duplicates.setToolTipText("For protocols with identical settings, keeps the most recently updated copy and leaves out the rest");
        duplicates.addActionListener(e -> {
            List<Protocol> extra = session.suggestedExclusions();
            if (extra.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No protocols with identical settings were found.");
                return;
            }
            for (Protocol p : extra) session.overrideFor(Session.number(p)).setExcluded(true);
            gui.save();
            refresh();
            JOptionPane.showMessageDialog(this, "Left out " + extra.size() + " duplicate cop" + (extra.size() == 1 ? "y" : "ies")
                    + ". Untick any you want to keep.");
        });
        JButton includeAll = new JButton("Include all");
        includeAll.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Put every protocol back in the book?", "Include all", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            for (Protocol p : session.protocols) {
                if (session.existingOverride(p) != null) session.existingOverride(p).setExcluded(false);
            }
            gui.save();
            refresh();
        });

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(new JLabel("Search:"));
        tools.add(search);
        tools.add(duplicates);
        tools.add(includeAll);
        tools.add(count);

        JPanel top = new JPanel(new BorderLayout());
        top.add(Ui.help("Tick Leave out for protocols that shouldn't be in the book. Protocols with the same settings or the same name "
                + "are marked in the last column. Protocols marked \"not in book\" have a number with no section (e.g. 10.x QA) "
                + "and are always left out."), BorderLayout.NORTH);
        top.add(tools, BorderLayout.CENTER);

        JPanel screen = Ui.screen();
        screen.add(top, BorderLayout.NORTH);
        screen.add(new JScrollPane(table), BorderLayout.CENTER);
        add(screen, BorderLayout.CENTER);
    }

    @Override public void refresh() {
        model.fireTableDataChanged();
        int in = 0;
        for (Protocol p : session.protocols) if (!session.isExcluded(p) && session.sectionOf(p) != null) in++;
        count.setText("   " + in + " of " + session.protocols.size() + " protocols in the book");
    }

    private final class Model extends AbstractTableModel {
        private final String[] columns = {"Leave out", "#", "Protocol", "Section", "Last updated", "Duplicate?"};

        @Override public int getRowCount() { return session.sortedProtocols.size(); }

        @Override public int getColumnCount() { return columns.length; }

        @Override public String getColumnName(int c) { return columns[c]; }

        @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }

        @Override public boolean isCellEditable(int r, int c) { return c == 0; }

        @Override public Object getValueAt(int r, int c) {
            Protocol p = session.sortedProtocols.get(r);
            switch (c) {
                case 0: return session.isExcluded(p);
                case 1: return Session.number(p);
                case 2: return p.getMetadata() == null ? "" : p.getMetadata().getName();
                case 3: {
                    Session.Section s = session.sectionOf(p);
                    return s == null ? "(not in book)" : s.title();
                }
                case 4: {
                    String updated = p.getMetadata() == null ? null : p.getMetadata().getLastUpdated();
                    return updated == null ? "" : updated.replace('T', ' ').replaceAll("\\.\\d+.*$|Z$", "");
                }
                default: {
                    String note = duplicateNotes.get(p);
                    return note == null ? "" : note;
                }
            }
        }

        @Override public void setValueAt(Object value, int r, int c) {
            Protocol p = session.sortedProtocols.get(r);
            session.overrideFor(Session.number(p)).setExcluded(Boolean.TRUE.equals(value));
            gui.save();
            refresh();
        }
    }
}
