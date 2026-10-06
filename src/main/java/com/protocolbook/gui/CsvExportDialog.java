package com.protocolbook.gui;

import com.protocolbook.io.ParameterCsvWriter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File > Export parameters to CSV: tick the settings to compare across protocols (e.g. ASIR and kernel)
 * and get a spreadsheet with one row per recon, series or protocol - see ParameterCsvWriter.
 */
final class CsvExportDialog extends JDialog {
    private static final String[] LEVEL_TITLES = {"Exam", "Series (acquisition)", "Recon"};

    private final Map<ParameterCsvWriter.Column, JCheckBox> boxes = new LinkedHashMap<ParameterCsvWriter.Column, JCheckBox>();

    private CsvExportDialog(ProtocolBuilderGui gui) {
        super(gui, "Export parameters to CSV", ModalityType.APPLICATION_MODAL);
        Session session = gui.session();
        List<String> saved = Arrays.asList(gui.prefs.get("csvColumns", "kernel,asir").split(","));

        JPanel columns = new JPanel(new GridLayout(1, 3, 12, 0));
        int level = 0;
        for (Map.Entry<ParameterCsvWriter.Level, List<ParameterCsvWriter.Column>> e : ParameterCsvWriter.byLevel().entrySet()) {
            JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
            for (ParameterCsvWriter.Column c : e.getValue()) {
                JCheckBox box = new JCheckBox(c.label, saved.contains(c.id));
                boxes.put(c, box);
                list.add(box);
            }
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.add(list, BorderLayout.NORTH);
            columns.add(Ui.titled(LEVEL_TITLES[level++], wrap));
        }

        JCheckBox leftOut = new JCheckBox("Include protocols left out of the book", gui.prefs.getBoolean("csvLeftOut", false));
        JButton none = new JButton("Untick all");
        none.addActionListener(e -> { for (JCheckBox b : boxes.values()) b.setSelected(false); });
        JButton export = new JButton("Save CSV…");
        JButton cancel = new JButton("Close");
        cancel.addActionListener(e -> dispose());
        export.addActionListener(e -> {
            List<ParameterCsvWriter.Column> chosen = new ArrayList<ParameterCsvWriter.Column>();
            List<String> ids = new ArrayList<String>();
            for (Map.Entry<ParameterCsvWriter.Column, JCheckBox> b : boxes.entrySet())
                if (b.getValue().isSelected()) { chosen.add(b.getKey()); ids.add(b.getKey().id); }
            gui.prefs.put("csvColumns", String.join(",", ids));
            gui.prefs.putBoolean("csvLeftOut", leftOut.isSelected());
            JFileChooser chooser = new JFileChooser(new File(gui.prefs.get("outputFolder", session.settingsDir.getPath())));
            chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), "protocol parameters.csv"));
            chooser.setDialogTitle("Save the parameters as");
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File out = chooser.getSelectedFile();
            if (!out.getName().toLowerCase().endsWith(".csv")) out = new File(out.getPath() + ".csv");
            try {
                int rows = ParameterCsvWriter.write(session.protocols, session.overrides, session.labels, chosen, leftOut.isSelected(), out);
                Object[] options = Desktop.isDesktopSupported() ? new Object[] {"Open it", "OK"} : new Object[] {"OK"};
                int answer = JOptionPane.showOptionDialog(this, "Saved " + rows + " row(s) to\n" + out.getAbsolutePath(), "Exported",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
                if (answer == 0 && options.length == 2) Desktop.getDesktop().open(out);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Couldn't save " + out.getName() + ":\n" + ex.getMessage()
                        + "\n\nIf it's open in Excel, close it and try again.", "Export failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel south = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(leftOut);
        left.add(none);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(cancel);
        right.add(export);
        south.add(left, BorderLayout.WEST);
        south.add(right, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(10, 12, 6, 12));
        content.add(Ui.help("Tick the settings to compare across all protocols. Each row is a recon if you tick any recon setting, "
                + "otherwise a series, otherwise a protocol - with its section, number and name. Values are what the book shows, "
                + "including anything you've changed by hand. Opens in Excel; sort or filter a column to spot the odd ones out."), BorderLayout.NORTH);
        content.add(columns, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        // the help text wraps once the width is set, which needs more height than pack() allowed for
        setSize(Math.max(getWidth(), 780), getHeight() + 90);
        setLocationRelativeTo(gui);
    }

    static void show(ProtocolBuilderGui gui) {
        if (gui.session() == null) {
            JOptionPane.showMessageDialog(gui, "Load the protocols first.");
            return;
        }
        new CsvExportDialog(gui).setVisible(true);
    }
}
