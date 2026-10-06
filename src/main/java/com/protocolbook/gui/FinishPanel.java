package com.protocolbook.gui;

import com.protocolbook.Main;
import com.protocolbook.changes.ManualChanges;
import com.protocolbook.html.BookTheme;
import com.protocolbook.html.ChangeReportWriter;
import com.protocolbook.html.Changelog;
import com.protocolbook.html.PdfLibrary;
import com.protocolbook.html.ProtocolBookHtmlWriter;
import com.protocolbook.html.ProtocolBookPdfWriter;
import com.protocolbook.html.ScanRangePictures;
import com.protocolbook.overrides.ProtocolOverrides;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Last step: every manual change in one list, then write the book PDF (and HTML if wanted) plus the changes PDF. */
final class FinishPanel extends JPanel implements ProtocolBuilderGui.Refreshable {
    private final ProtocolBuilderGui gui;
    private final Session session;
    private final ChangesModel changes = new ChangesModel();
    private final JLabel summary = new JLabel();
    private final JTextField folder = new JTextField(40);
    private final JTextField bookTitle = new JTextField(30);
    private final JTextField fileName = new JTextField(30);
    private final JCheckBox pdf = new JCheckBox("Protocol book (PDF)");
    private final JCheckBox html = new JCheckBox("Protocol book (HTML, for a browser or intranet)");
    private final JCheckBox changesPdf = new JCheckBox("List of manual changes (PDF)");
    private final JLabel files = new JLabel();
    private final JButton create = new JButton("Create");
    private final JButton openFolder = new JButton("Open folder");
    private final JTextArea log = new JTextArea(5, 60);

    FinishPanel(ProtocolBuilderGui gui) {
        super(new BorderLayout());
        this.gui = gui;
        this.session = gui.session();

        JTable table = new JTable(changes);
        Ui.setUp(table);
        Ui.widths(table, 60, 260, 200, 160, 180, 220);

        folder.setText(gui.prefs.get("outputFolder", session.settingsDir.getPath()));
        bookTitle.setText(gui.prefs.get("bookTitle", "Protocol Book"));
        fileName.setText(gui.prefs.get("fileName", "Protocol Book"));
        pdf.setSelected(gui.prefs.getBoolean("makePdf", true));
        html.setSelected(gui.prefs.getBoolean("makeHtml", false));
        changesPdf.setSelected(gui.prefs.getBoolean("makeChanges", true));
        javax.swing.event.DocumentListener update = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateFiles(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateFiles(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateFiles(); }
        };
        folder.getDocument().addDocumentListener(update);
        fileName.getDocument().addDocumentListener(update);
        pdf.addActionListener(e -> updateFiles());
        html.addActionListener(e -> updateFiles());
        changesPdf.addActionListener(e -> updateFiles());

        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File(folder.getText().trim()));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Where to save the book");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) folder.setText(chooser.getSelectedFile().getPath());
        });
        create.setFont(create.getFont().deriveFont(Font.BOLD, 14f));
        create.addActionListener(e -> create());
        openFolder.setEnabled(false);
        openFolder.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File(folder.getText().trim()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Couldn't open the folder: " + ex.getMessage());
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Book title:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; form.add(bookTitle, c);
        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0; form.add(new JLabel("Save as:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; form.add(fileName, c);
        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0; form.add(new JLabel("In folder:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; form.add(folder, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0; form.add(browse, c);
        JPanel boxes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        boxes.add(pdf);
        boxes.add(Box.createHorizontalStrut(14));
        boxes.add(html);
        boxes.add(Box.createHorizontalStrut(14));
        boxes.add(changesPdf);
        c.gridx = 1; c.gridy = 3; c.gridwidth = 2; form.add(boxes, c);
        files.setForeground(new Color(0x555555));
        c.gridy = 4; form.add(files, c);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(create);
        actions.add(Box.createHorizontalStrut(10));
        actions.add(openFolder);
        c.gridy = 5; form.add(actions, c);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(Ui.titled("Create the book", form), BorderLayout.NORTH);
        bottom.add(new JScrollPane(log), BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.add(Ui.help("Every value set by hand, next to what the scanner has. These only change the book - the scanner keeps its own "
                + "settings, so the changes PDF doubles as the list of what to update at the console."), BorderLayout.NORTH);
        top.add(summary, BorderLayout.CENTER);

        JPanel screen = Ui.screen();
        screen.add(top, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), bottom);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        screen.add(split, BorderLayout.CENTER);
        add(screen, BorderLayout.CENTER);
        updateFiles();
    }

    @Override public void refresh() {
        changes.rows = ManualChanges.all(session.protocols, session.overrides, session.labels);
        changes.fireTableDataChanged();
        java.util.Set<String> protocols = new java.util.HashSet<String>();
        for (ManualChanges.Change ch : changes.rows) protocols.add(ch.protocolNumber);
        summary.setText(changes.rows.size() + " change(s) on " + protocols.size() + " protocol(s)");
    }

    private String baseName() {
        String name = fileName.getText().trim().replaceAll("(?i)\\.(pdf|html?)$", "");
        return name.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    private List<File> outputs() {
        List<File> out = new ArrayList<File>();
        File dir = new File(folder.getText().trim());
        String base = baseName().isEmpty() ? "Protocol Book" : baseName();
        if (pdf.isSelected()) out.add(new File(dir, base + ".pdf"));
        if (html.isSelected()) out.add(new File(dir, base + ".html"));
        if (changesPdf.isSelected()) out.add(new File(dir, base + " - changes.pdf"));
        return out;
    }

    private void updateFiles() {
        List<String> names = new ArrayList<String>();
        for (File f : outputs()) names.add(f.getName());
        files.setText(names.isEmpty() ? "Tick at least one thing to create." : "Creates: " + String.join(",  ", names));
        create.setEnabled(!names.isEmpty());
    }

    private void create() {
        final File dir = new File(folder.getText().trim());
        final String title = bookTitle.getText().trim();
        final boolean makePdf = pdf.isSelected(), makeHtml = html.isSelected(), makeChanges = changesPdf.isSelected();
        final List<File> targets = outputs();
        final File pdfFile = makePdf ? targets.get(0) : null;
        final File htmlFile = makeHtml ? targets.get(makePdf ? 1 : 0) : null;
        final File changesFile = makeChanges ? targets.get(targets.size() - 1) : null;
        final BookTheme theme = gui.theme();
        gui.prefs.put("outputFolder", dir.getPath());
        gui.prefs.put("bookTitle", title);
        gui.prefs.put("fileName", fileName.getText().trim());
        gui.prefs.putBoolean("makePdf", makePdf);
        gui.prefs.putBoolean("makeHtml", makeHtml);
        gui.prefs.putBoolean("makeChanges", makeChanges);
        if (!gui.save()) return;

        create.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        log.setText("Creating...\n");
        new SwingWorker<List<String>, String>() {
            @Override protected List<String> doInBackground() throws Exception {
                List<String> lines = new ArrayList<String>();
                for (String problem : ProtocolOverrides.problems(session.protocols, session.overrides)) lines.add("WARN: " + problem);
                File pictures = session.file(ScanRangePictures.FOLDER);
                for (String problem : ScanRangePictures.problems(session.protocols, session.overrides, pictures)) lines.add("WARN: " + problem);
                String logo = Main.loadLogoDataUri(session.file("logo.png"));
                if (pdfFile != null) {
                    new ProtocolBookPdfWriter().withTheme(theme).withPictureFolder(pictures).write(session.protocols, session.overrides, session.labels, logo, title, pdfFile);
                    lines.add("Wrote " + pdfFile.getAbsolutePath());
                }
                if (htmlFile != null) {
                    new ProtocolBookHtmlWriter().withTheme(theme).withPictureFolder(pictures).write(session.protocols, session.overrides, session.labels, logo,
                            PdfLibrary.load(session.file("pdf-library.json")), PdfLibrary.load(session.file("reference-library.json")),
                            null, title, Changelog.load(session.file("changelog.json")), htmlFile);
                    lines.add("Wrote " + htmlFile.getAbsolutePath());
                }
                if (changesFile != null) {
                    new ChangeReportWriter().withTheme(theme).write(session.protocols, session.overrides, session.labels, title, changesFile);
                    lines.add("Wrote " + changesFile.getAbsolutePath());
                }
                return lines;
            }

            @Override protected void done() {
                create.setEnabled(true);
                setCursor(Cursor.getDefaultCursor());
                try {
                    for (String line : get()) log.append(line + "\n");
                    log.append("Done.\n");
                    openFolder.setEnabled(Desktop.isDesktopSupported());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.append("ERROR: " + cause.getMessage() + "\n");
                    JOptionPane.showMessageDialog(FinishPanel.this, cause.getMessage()
                            + "\n\nIf the file is open (e.g. the PDF in a viewer), close it and try again.", "Couldn't create the book", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static final class ChangesModel extends AbstractTableModel {
        List<ManualChanges.Change> rows = new ArrayList<ManualChanges.Change>();
        private final String[] columns = {"#", "Protocol", "Where", "Setting", "Scanner", "Changed to"};

        @Override public int getRowCount() { return rows.size(); }

        @Override public int getColumnCount() { return columns.length; }

        @Override public String getColumnName(int c) { return columns[c]; }

        @Override public Object getValueAt(int r, int c) {
            ManualChanges.Change ch = rows.get(r);
            switch (c) {
                case 0: return ch.displayNumber;
                case 1: return ch.protocolName;
                case 2: return ch.where;
                case 3: return ch.setting;
                case 4: return ch.scannerValue == null ? "—" : ch.scannerValue;
                default: return ch.newValue;
            }
        }
    }
}
