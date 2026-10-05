package com.protocolbook.gui;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Step 1: where the exported protocols are and which overrides file the changes go into, then load. */
final class SetupPanel extends JPanel {
    private final ProtocolBuilderGui gui;
    private final JTextField input = new JTextField();
    private final JTextField overrides = new JTextField();
    private final JButton load = new JButton("Load protocols");
    private final JTextArea log = new JTextArea(8, 60);

    SetupPanel(ProtocolBuilderGui gui) {
        super(new BorderLayout());
        this.gui = gui;
        File cwd = new File(System.getProperty("user.dir"));
        input.setText(gui.prefs.get("input", new File(cwd, "protocol data").getPath()));
        overrides.setText(gui.prefs.get("overrides", new File(cwd, "protocol-overrides.json").getPath()));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 4);
        c.anchor = GridBagConstraints.WEST;
        row(form, c, 0, "Protocols folder:", input, browse(input, true),
                "The folder of protocols exported from the scanner (any nesting is fine), or a Protocols.xlsm workbook.");
        row(form, c, 2, "Changes file:", overrides, browse(overrides, false),
                "protocol-overrides.json - where everything you change is saved. It's created if it doesn't exist. "
                        + "Label files, logo.png and changelog.json are read from the same folder.");

        load.setFont(load.getFont().deriveFont(Font.BOLD, 14f));
        load.addActionListener(e -> load());
        c.gridx = 1; c.gridy = 4; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        form.add(load, c);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel screen = Ui.screen();
        JPanel top = new JPanel(new BorderLayout());
        top.add(Ui.help("Pick the scanner export and the file your changes are saved in, then press Load protocols. "
                + "Next you'll choose which protocols to leave out, then go through the book one section at a time."), BorderLayout.NORTH);
        top.add(form, BorderLayout.CENTER);
        screen.add(top, BorderLayout.NORTH);
        screen.add(Ui.titled("Messages", new JScrollPane(log)), BorderLayout.CENTER);
        add(screen, BorderLayout.CENTER);
    }

    private static void row(JPanel form, GridBagConstraints c, int y, String label, JTextField field, JButton browse, String help) {
        c.gridx = 0; c.gridy = y; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        form.add(l, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        field.setColumns(50);
        form.add(field, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(browse, c);
        c.gridx = 1; c.gridy = y + 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        JLabel h = new JLabel("<html>" + help + "</html>");
        h.setForeground(new Color(0x555555));
        form.add(h, c);
        c.gridwidth = 1;
    }

    private JButton browse(JTextField field, boolean protocols) {
        JButton b = new JButton("Browse…");
        b.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            File current = new File(field.getText().trim());
            chooser.setCurrentDirectory(current.isDirectory() ? current : current.getAbsoluteFile().getParentFile());
            if (protocols) {
                chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                chooser.setDialogTitle("Choose the exported protocols folder (or a Protocols.xlsm workbook)");
            } else {
                chooser.setSelectedFile(current);
                chooser.setDialogTitle("Choose (or name) the changes file");
            }
            int result = protocols ? chooser.showOpenDialog(this) : chooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                if (!protocols && !f.getName().toLowerCase().endsWith(".json")) f = new File(f.getPath() + ".json");
                field.setText(f.getPath());
            }
        });
        return b;
    }

    private void load() {
        File in = new File(input.getText().trim());
        File ov = new File(overrides.getText().trim());
        if (!in.exists()) {
            JOptionPane.showMessageDialog(this, "Can't find " + in.getAbsolutePath(), "Protocols folder not found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (ov.isDirectory()) ov = new File(ov, "protocol-overrides.json");
        final File overridesFile = ov;
        gui.prefs.put("input", in.getPath());
        gui.prefs.put("overrides", overridesFile.getPath());
        load.setEnabled(false);
        log.setText("Loading " + in.getAbsolutePath() + " ...\n");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Session, Void>() {
            private final ByteArrayOutputStream out = new ByteArrayOutputStream();

            @Override protected Session doInBackground() throws Exception {
                try (PrintStream ps = new PrintStream(out, true, "UTF-8")) {
                    return Session.load(in, overridesFile, ps);
                }
            }

            @Override protected void done() {
                load.setEnabled(true);
                setCursor(Cursor.getDefaultCursor());
                log.append(new String(out.toByteArray(), StandardCharsets.UTF_8));
                try {
                    Session s = get();
                    if (s.protocols.isEmpty()) {
                        log.append("No protocols found there.\n");
                        return;
                    }
                    log.append("Loaded " + s.protocols.size() + " protocols in " + s.sections.size() + " sections.\n");
                    gui.loaded(s);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.append("ERROR: " + cause.getMessage() + "\n");
                    JOptionPane.showMessageDialog(SetupPanel.this, cause.getMessage(), "Couldn't load protocols", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
