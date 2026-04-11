package com.keyremapper.ui;

import com.keyremapper.model.Profile;
import com.keyremapper.util.ProfileManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.function.Consumer;

/**
 * Sidebar panel that shows the profile list and management buttons
 * (add / delete / rename / export / import).
 */
public class ProfilePanel extends JPanel {

    private final ProfileManager pm;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> profileList;
    private Consumer<Integer> onProfileChanged;

    public ProfilePanel(ProfileManager pm) {
        this.pm = pm;
        setLayout(new BorderLayout(0, 6));
        setBorder(new EmptyBorder(0, 0, 0, 10));
        setPreferredSize(new Dimension(170, 0));

        profileList = new JList<>(listModel);
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.setFont(profileList.getFont().deriveFont(12f));
        profileList.setFixedCellHeight(28);
        refreshList();
        profileList.setSelectedIndex(pm.getActiveIndex());
        profileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = profileList.getSelectedIndex();
                if (idx >= 0) {
                    pm.setActiveIndex(idx);
                    if (onProfileChanged != null) onProfileChanged.accept(idx);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(profileList);
        scroll.setBorder(BorderFactory.createLineBorder(
                UIManager.getColor("Component.borderColor")));

        JButton addBtn  = smallBtn("+",  "Add a new profile");
        JButton delBtn  = smallBtn("\u2212", "Delete selected profile");
        JButton menuBtn = smallBtn("\u2026", "Rename / Export / Import");

        addBtn.addActionListener(e -> addProfile());
        delBtn.addActionListener(e -> deleteProfile());
        menuBtn.addActionListener(e -> showMenu(menuBtn));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        btns.add(addBtn);
        btns.add(delBtn);
        btns.add(menuBtn);

        add(scroll, BorderLayout.CENTER);
        add(btns, BorderLayout.SOUTH);
    }

    public void setOnProfileChanged(Consumer<Integer> cb) { this.onProfileChanged = cb; }

    /* ------------------------------------------------------------------ */
    /*  Actions                                                           */
    /* ------------------------------------------------------------------ */

    private void addProfile() {
        String name = JOptionPane.showInputDialog(this,
                "Choose a name:", "New Profile", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        pm.addProfile(name.trim());
        refreshList();
        profileList.setSelectedIndex(pm.getProfiles().size() - 1);
    }

    private void deleteProfile() {
        int idx = profileList.getSelectedIndex();
        if (idx < 0) return;
        if (pm.getProfiles().size() <= 1) {
            JOptionPane.showMessageDialog(this,
                    "At least one profile must remain.",
                    "Cannot Delete", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this,
                "Delete \"" + pm.getProfiles().get(idx).getName() + "\"?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        pm.deleteProfile(idx);
        refreshList();
        profileList.setSelectedIndex(pm.getActiveIndex());
    }

    private void renameProfile() {
        int idx = profileList.getSelectedIndex();
        if (idx < 0) return;
        String cur = pm.getProfiles().get(idx).getName();
        String name = (String) JOptionPane.showInputDialog(this,
                "New name:", "Rename Profile",
                JOptionPane.PLAIN_MESSAGE, null, null, cur);
        if (name == null || name.trim().isEmpty()) return;
        pm.renameProfile(idx, name.trim());
        refreshList();
        profileList.setSelectedIndex(idx);
    }

    private void exportProfile() {
        int idx = profileList.getSelectedIndex();
        if (idx < 0) return;
        Profile p = pm.getProfiles().get(idx);
        JFileChooser fc = bkmChooser();
        fc.setSelectedFile(new File(p.getName() + ".bkm"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = ensureExtension(fc.getSelectedFile(), ".bkm");
        try {
            pm.exportProfile(p, file);
            JOptionPane.showMessageDialog(this,
                    "Exported to " + file.getName(), "Export", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importProfile() {
        JFileChooser fc = bkmChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Profile p = pm.importProfile(fc.getSelectedFile());
            refreshList();
            profileList.setSelectedIndex(pm.getProfiles().size() - 1);
            JOptionPane.showMessageDialog(this,
                    "Imported \"" + p.getName() + "\"", "Import", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Import failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Popup menu                                                        */
    /* ------------------------------------------------------------------ */

    private void showMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem ren = new JMenuItem("Rename");
        JMenuItem exp = new JMenuItem("Export (.bkm)");
        JMenuItem imp = new JMenuItem("Import (.bkm)");
        ren.addActionListener(e -> renameProfile());
        exp.addActionListener(e -> exportProfile());
        imp.addActionListener(e -> importProfile());
        menu.add(ren);
        menu.addSeparator();
        menu.add(exp);
        menu.add(imp);
        menu.show(anchor, 0, anchor.getHeight());
    }

    /* ------------------------------------------------------------------ */
    /*  Public refresh (called by the bot / external actions)             */
    /* ------------------------------------------------------------------ */

    public void refresh() {
        refreshList();
        profileList.setSelectedIndex(pm.getActiveIndex());
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    private void refreshList() {
        listModel.clear();
        for (Profile p : pm.getProfiles()) {
            listModel.addElement(p.getName());
        }
    }

    private static JButton smallBtn(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(36, 28));
        b.setMargin(new Insets(0, 0, 0, 0));
        return b;
    }

    private static JFileChooser bkmChooser() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Key Remap Profile (*.bkm)", "bkm"));
        return fc;
    }

    private static File ensureExtension(File f, String ext) {
        if (!f.getName().toLowerCase().endsWith(ext)) {
            return new File(f.getAbsolutePath() + ext);
        }
        return f;
    }
}
