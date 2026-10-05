package com.protocolbook.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/** Small shared pieces of the GUI: how hand-set values are highlighted, table setup, wrapped help text. */
final class Ui {
    private Ui() {}

    private static Color edited = new Color(0xff8200);

    /** Text color for values set by hand - the book's accent color. */
    static Color editedColor() { return edited; }

    static void setEditedColor(Color c) { edited = c; }

    /** Pale background for a cell that differs from the scanner. */
    static Color editedBackground() {
        return new Color((edited.getRed() + 255 * 7) / 8, (edited.getGreen() + 255 * 7) / 8, (edited.getBlue() + 255 * 7) / 8);
    }

    static final Color WARNING = new Color(0xb3261e);

    /** Taller rows and a header that isn't reorderable - shared by every table. */
    static void setUp(JTable table) {
        table.setRowHeight(Math.max(table.getRowHeight(), 24));
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    }

    static void widths(JTable table, int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    }

    /** A paragraph of grey explanatory text that wraps to the window width. */
    static JComponent help(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setForeground(new Color(0x555555));
        a.setFont(UIManager.getFont("Label.font"));
        a.setBorder(new EmptyBorder(0, 0, 8, 0));
        return a;
    }

    /** Renders a SectionRow.Cell (or plain text): hand-set values bold in the accent color. */
    static final class CellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            boolean hand = value instanceof SectionRow.Cell && ((SectionRow.Cell) value).edited;
            setFont(getFont().deriveFont(hand ? Font.BOLD : Font.PLAIN));
            if (!selected) setForeground(hand ? editedColor() : table.getForeground());
            String text = value == null ? "" : value.toString();
            setToolTipText(text.isEmpty() ? null : hand ? text + "  (set by hand)" : text);
            return this;
        }
    }

    /** Wraps a component with a titled, padded border. */
    static JComponent titled(String title, JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title), new EmptyBorder(4, 6, 6, 6)));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    /** Padding around a whole screen. */
    static JPanel screen() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(14, 16, 6, 16));
        return p;
    }
}
