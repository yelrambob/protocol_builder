package com.protocolbook.gui;

import com.protocolbook.html.BookTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Step-by-step window for building the protocol book: pick the export folder, choose which protocols
 * to leave out, go through the sections one screen at a time (notes, contrast, any setting, added
 * fields), then review every change and write the book (PDF, optionally HTML) plus a PDF listing the
 * manual changes. Everything typed is saved to protocol-overrides.json as you go - the same file the
 * command line reads - so a review can be stopped and picked up later, and the .bat scripts keep working.
 */
public class ProtocolBuilderGui extends JFrame {
    /** One entry in the step list on the left. */
    private static final class Step {
        final String title;
        final JComponent panel;
        final Session.Section section;

        Step(String title, JComponent panel, Session.Section section) {
            this.title = title;
            this.panel = panel;
            this.section = section;
        }
    }

    final Prefs prefs = new Prefs();
    private BookTheme theme = prefs.theme();
    private Session session;

    private final List<Step> steps = new ArrayList<Step>();
    private final DefaultListModel<Step> stepModel = new DefaultListModel<Step>();
    private final JList<Step> stepList = new JList<Step>(stepModel);
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JLabel headerTitle = new JLabel();
    private final JLabel headerStep = new JLabel();
    private final JPanel header = new JPanel(new BorderLayout());
    private final JButton back = new JButton("◀ Back");
    private final JButton next = new JButton("Next ▶");
    private final JMenu colorsMenu = new JMenu("Colors");
    private final SetupPanel setupPanel;
    private boolean switching;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look works fine
        }
        SwingUtilities.invokeLater(() -> new ProtocolBuilderGui().setVisible(true));
    }

    public ProtocolBuilderGui() {
        super("Protocol Builder");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(960, 600));
        setLocationRelativeTo(null);

        headerTitle.setFont(headerTitle.getFont().deriveFont(Font.BOLD, 20f));
        headerStep.setFont(headerStep.getFont().deriveFont(14f));
        header.add(headerTitle, BorderLayout.WEST);
        header.add(headerStep, BorderLayout.EAST);

        stepList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stepList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                Step step = (Step) value;
                String mark = step.section != null && session != null && session.reviewedSections.contains(step.section.title()) ? "  ✓" : "";
                if (step.panel instanceof KernelPanel) {
                    int blank = KernelPanel.unnamed(session);
                    mark = blank == 0 ? "  ✓" : "  (" + blank + " to name)";
                }
                JLabel l = (JLabel) super.getListCellRendererComponent(list, step.title + mark, index, selected, focus);
                l.setBorder(new EmptyBorder(6, 10, 6, 10));
                if (step.section != null) l.setBorder(new EmptyBorder(4, 22, 4, 10));
                return l;
            }
        });
        stepList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !switching && stepList.getSelectedIndex() >= 0) show(stepList.getSelectedIndex());
        });
        JScrollPane stepScroll = new JScrollPane(stepList);
        stepScroll.setPreferredSize(new Dimension(230, 100));

        back.addActionListener(e -> show(stepList.getSelectedIndex() - 1));
        next.addActionListener(e -> show(stepList.getSelectedIndex() + 1));
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        nav.add(back);
        nav.add(next);

        JPanel center = new JPanel(new BorderLayout());
        center.add(cardPanel, BorderLayout.CENTER);
        center.add(nav, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(header, BorderLayout.NORTH);
        getContentPane().add(stepScroll, BorderLayout.WEST);
        getContentPane().add(center, BorderLayout.CENTER);

        setupPanel = new SetupPanel(this);
        setJMenuBar(menuBar());
        rebuildSteps();
        applyTheme();
        show(0);
    }

    BookTheme theme() { return theme; }

    Session session() { return session; }

    /** Called by the setup screen once protocols are loaded: one step per section, then the finish screen. */
    void loaded(Session s) {
        session = s;
        rebuildSteps();
        show(1);
    }

    private void rebuildSteps() {
        steps.clear();
        cardPanel.removeAll();
        steps.add(new Step("1. Choose protocols", setupPanel, null));
        if (session != null) {
            steps.add(new Step("2. Leave out protocols", new ExcludePanel(this), null));
            if (!session.kernelSamples.isEmpty()) steps.add(new Step("3. Kernel names", new KernelPanel(this), null));
            for (Session.Section section : session.sections)
                steps.add(new Step(section.title() + " (" + section.protocols.size() + ")", new SectionPanel(this, section), section));
            steps.add(new Step("Review & create book", new FinishPanel(this), null));
        }
        switching = true;
        stepModel.clear();
        for (int i = 0; i < steps.size(); i++) {
            stepModel.addElement(steps.get(i));
            cardPanel.add(steps.get(i).panel, String.valueOf(i));
        }
        switching = false;
    }

    /** Switches to step i, refreshing it first so it reflects edits made on other screens. */
    void show(int i) {
        if (i < 0 || i >= steps.size()) return;
        Step step = steps.get(i);
        if (step.panel instanceof Refreshable) ((Refreshable) step.panel).refresh();
        cards.show(cardPanel, String.valueOf(i));
        switching = true;
        stepList.setSelectedIndex(i);
        stepList.ensureIndexIsVisible(i);
        switching = false;
        headerTitle.setText(step.section != null ? step.section.title() : step.title.replaceFirst("^\\d+\\. ", ""));
        headerStep.setText(session == null ? "" : "Step " + (i + 1) + " of " + steps.size());
        back.setEnabled(i > 0);
        next.setEnabled(i < steps.size() - 1);
    }

    /** Repaints the step list (e.g. after a section is marked reviewed). */
    void stepsChanged() {
        stepList.repaint();
    }

    /** Saves the overrides file, reporting (not throwing) a failure - every edit autosaves through here. */
    boolean save() {
        if (session == null) return true;
        try {
            session.save();
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Couldn't save " + session.overridesFile + ":\n" + e.getMessage()
                    + "\n\nIf the file is open in another program, close it and try again.", "Save failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        JMenuItem open = new JMenuItem("Choose another protocols folder…");
        open.addActionListener(e -> show(0));
        JMenuItem save = new JMenuItem("Save changes");
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        save.addActionListener(e -> {
            if (session != null && save()) JOptionPane.showMessageDialog(this, "Saved to " + session.overridesFile.getAbsolutePath());
        });
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());
        file.add(open);
        file.add(save);
        file.addSeparator();
        file.add(exit);
        bar.add(file);

        rebuildColorsMenu();
        bar.add(colorsMenu);

        JMenu help = new JMenu("Help");
        JMenuItem how = new JMenuItem("How this works");
        how.addActionListener(e -> JOptionPane.showMessageDialog(this, HELP, "How this works", JOptionPane.INFORMATION_MESSAGE));
        help.add(how);
        bar.add(help);
        return bar;
    }

    private void rebuildColorsMenu() {
        colorsMenu.removeAll();
        ButtonGroup group = new ButtonGroup();
        boolean matched = false;
        for (BookTheme preset : BookTheme.PRESETS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(preset.getName(), new Swatch(preset));
            item.setSelected(preset.equals(theme));
            matched |= preset.equals(theme);
            item.addActionListener(e -> setTheme(preset));
            group.add(item);
            colorsMenu.add(item);
        }
        if (!matched) {
            JRadioButtonMenuItem custom = new JRadioButtonMenuItem("Custom", new Swatch(theme));
            custom.setSelected(true);
            group.add(custom);
            colorsMenu.add(custom);
        }
        colorsMenu.addSeparator();
        JMenuItem primary = new JMenuItem("Choose main color…");
        primary.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Main color (headings, table headers, page background)", Color.decode(theme.getPrimary()));
            if (c != null) setTheme(theme.withPrimary(hex(c)));
        });
        JMenuItem accent = new JMenuItem("Choose accent color…");
        accent.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Accent color (sidebar, underlines, notes box)", Color.decode(theme.getAccent()));
            if (c != null) setTheme(theme.withAccent(hex(c)));
        });
        colorsMenu.add(primary);
        colorsMenu.add(accent);
    }

    private void setTheme(BookTheme t) {
        theme = t;
        prefs.theme(t);
        rebuildColorsMenu();
        applyTheme();
    }

    // The header bar is drawn in the book's colors, so the choice is visible right away.
    private void applyTheme() {
        Color primary = Color.decode(theme.getPrimary()), accent = Color.decode(theme.getAccent());
        header.setBackground(primary);
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 5, 0, accent), new EmptyBorder(12, 16, 10, 16)));
        headerTitle.setForeground(Color.WHITE);
        headerStep.setForeground(Color.WHITE);
        Ui.setEditedColor(accent);
        repaint();
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** A two-color square shown next to each color choice. */
    private static final class Swatch implements Icon {
        private final BookTheme theme;

        Swatch(BookTheme theme) { this.theme = theme; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.decode(theme.getPrimary()));
            g.fillRect(x, y, 10, 14);
            g.setColor(Color.decode(theme.getAccent()));
            g.fillRect(x + 10, y, 10, 14);
            g.setColor(Color.GRAY);
            g.drawRect(x, y, 19, 13);
        }

        @Override public int getIconWidth() { return 20; }

        @Override public int getIconHeight() { return 14; }
    }

    /** A screen that redraws itself from the session each time it's shown. */
    interface Refreshable {
        void refresh();
    }

    private static final String HELP = "<html><body style='width:420px'>"
            + "<p><b>1. Choose protocols</b> &mdash; the folder of exported protocols (or a Protocols.xlsm workbook) and the "
            + "protocol-overrides.json file your changes are saved in. Label files, logo and changelog are read from that file's folder.</p><br>"
            + "<p><b>2. Leave out protocols</b> &mdash; tick the ones that shouldn't be in the book. Duplicates are marked.</p><br>"
            + "<p><b>3. Kernel names</b> &mdash; the export only has a number for each recon kernel; type the name the book "
            + "should show (saved to kernel-labels.json). New kernels are added to that list automatically each time you load.</p><br>"
            + "<p><b>Sections</b> &mdash; one screen per body area. Tick <i>Edit</i> on the protocols to change and press "
            + "<i>Edit checked</i>, or double-click a row. Values you've set are shown in the accent color. "
            + "The <i>Worth a look</i> column flags things that may be wrong.</p><br>"
            + "<p><b>Review &amp; create book</b> &mdash; every change in one list, then the book PDF (and HTML if you want it) "
            + "plus a PDF of the manual changes.</p><br>"
            + "<p>Every edit is saved to protocol-overrides.json straight away (the previous version is kept as .bak). "
            + "Changes only affect the book &mdash; the scanner keeps its own settings.</p></body></html>";
}
