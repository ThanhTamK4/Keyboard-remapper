package com.keyremapper.ui;

import com.keyremapper.hook.MacroPlayer;
import com.keyremapper.hook.MacroRecorder;
import com.keyremapper.model.Macro;
import com.keyremapper.model.MacroAction;
import com.keyremapper.model.Profile;
import com.keyremapper.util.KeyUtils;
import com.keyremapper.util.ProfileManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Macro tab: macro list, recorded key list, cycle/delay settings, and
 * record/playback controls.  Layout mirrors the reference CIY driver UI.
 */
public class MacroPanel extends JPanel {

    private final ProfileManager profileManager;
    private final MacroRecorder recorder = new MacroRecorder();
    private final MacroPlayer player = new MacroPlayer();

    private final DefaultListModel<String> macroListModel = new DefaultListModel<>();
    private final JList<String> macroList;
    private final ActionTableModel tableModel = new ActionTableModel();
    private final JTable actionTable;

    private JCheckBox autoDelayCheck;
    private JRadioButton cycleUntilReleasedRadio;
    private JRadioButton specifiedTimesRadio;
    private JSpinner cycleCountSpinner;
    private JComboBox<String> insertEventCombo;
    private JButton recordBtn;
    private JButton playBtn;

    /* ================================================================== */
    /*  Constructor                                                       */
    /* ================================================================== */

    public MacroPanel(ProfileManager pm) {
        this.profileManager = pm;
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(8, 8, 0, 0));

        macroList = new JList<>(macroListModel);
        macroList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        macroList.setFont(macroList.getFont().deriveFont(12f));
        macroList.setFixedCellHeight(28);
        macroList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onMacroSelected();
        });

        actionTable = new JTable(tableModel);
        actionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionTable.setRowHeight(24);
        actionTable.getColumnModel().getColumn(0).setMaxWidth(40);
        actionTable.getColumnModel().getColumn(0).setMinWidth(32);

        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.gridy = 0;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 8);

        gc.gridx = 0; gc.weightx = 0.28;
        content.add(buildMacroListPanel(), gc);

        gc.gridx = 1; gc.weightx = 0.40;
        content.add(buildKeyListPanel(), gc);

        gc.gridx = 2; gc.weightx = 0.32;
        gc.insets = new Insets(0, 0, 0, 0);
        content.add(buildSettingsPanel(), gc);

        add(content, BorderLayout.CENTER);
        loadMacros();
    }

    /* ================================================================== */
    /*  Panel builders                                                    */
    /* ================================================================== */

    private JPanel buildMacroListPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));

        JLabel header = new JLabel("Macro list");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setBorder(new EmptyBorder(0, 2, 0, 0));

        JScrollPane scroll = new JScrollPane(macroList);
        scroll.setBorder(BorderFactory.createLineBorder(
                UIManager.getColor("Component.borderColor")));

        JButton newBtn = new JButton("+ New");
        newBtn.setFocusPainted(false);
        newBtn.addActionListener(e -> onNewMacro());

        JButton delBtn = new JButton("\u2212 Delete");
        delBtn.setFocusPainted(false);
        delBtn.addActionListener(e -> onDeleteMacro());

        JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
        btns.add(newBtn);
        btns.add(delBtn);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(btns, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildKeyListPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));

        JLabel header = new JLabel("Key list");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setBorder(new EmptyBorder(0, 2, 0, 0));

        JScrollPane scroll = new JScrollPane(actionTable);
        scroll.setBorder(BorderFactory.createLineBorder(
                UIManager.getColor("Component.borderColor")));

        JButton modifyBtn = new JButton("\u270E Modify");
        modifyBtn.setFocusPainted(false);
        modifyBtn.addActionListener(e -> onModifyAction());

        JButton delBtn = new JButton("\u2212 Delete");
        delBtn.setFocusPainted(false);
        delBtn.addActionListener(e -> onDeleteAction());

        JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
        btns.add(modifyBtn);
        btns.add(delBtn);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(btns, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(0, 4, 0, 0));

        autoDelayCheck = new JCheckBox("Automatic insert delay");
        autoDelayCheck.setSelected(true);
        autoDelayCheck.setAlignmentX(LEFT_ALIGNMENT);
        autoDelayCheck.addActionListener(e -> syncSettingsToMacro());

        panel.add(autoDelayCheck);
        panel.add(Box.createVerticalStrut(12));

        cycleUntilReleasedRadio = new JRadioButton("Cycle until the key released");
        specifiedTimesRadio = new JRadioButton("Specified cycle times");
        specifiedTimesRadio.setSelected(true);
        ButtonGroup cycleGroup = new ButtonGroup();
        cycleGroup.add(cycleUntilReleasedRadio);
        cycleGroup.add(specifiedTimesRadio);
        cycleUntilReleasedRadio.setAlignmentX(LEFT_ALIGNMENT);
        specifiedTimesRadio.setAlignmentX(LEFT_ALIGNMENT);
        cycleUntilReleasedRadio.addActionListener(e -> syncSettingsToMacro());
        specifiedTimesRadio.addActionListener(e -> syncSettingsToMacro());

        panel.add(cycleUntilReleasedRadio);
        panel.add(Box.createVerticalStrut(4));
        panel.add(specifiedTimesRadio);
        panel.add(Box.createVerticalStrut(4));

        cycleCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 65535, 1));
        cycleCountSpinner.setMaximumSize(new Dimension(120, 28));
        cycleCountSpinner.setAlignmentX(LEFT_ALIGNMENT);
        cycleCountSpinner.addChangeListener(e -> syncSettingsToMacro());

        JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        countRow.setAlignmentX(LEFT_ALIGNMENT);
        countRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        countRow.add(Box.createHorizontalStrut(20));
        countRow.add(cycleCountSpinner);
        countRow.add(new JLabel("1 ~ 65535"));
        panel.add(countRow);

        panel.add(Box.createVerticalStrut(16));

        JLabel insertLabel = new JLabel("Insert event");
        insertLabel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(insertLabel);
        panel.add(Box.createVerticalStrut(4));

        insertEventCombo = new JComboBox<>(new String[]{"Key Down", "Key Up", "Delay"});
        insertEventCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        insertEventCombo.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(insertEventCombo);

        panel.add(Box.createVerticalStrut(6));

        JButton insertBtn = new JButton("Insert");
        insertBtn.setFocusPainted(false);
        insertBtn.setAlignmentX(LEFT_ALIGNMENT);
        insertBtn.addActionListener(e -> onInsertEvent());
        panel.add(insertBtn);

        panel.add(Box.createVerticalGlue());

        recordBtn = new JButton("\u25CF  Start record");
        recordBtn.setFocusPainted(false);
        recordBtn.setAlignmentX(LEFT_ALIGNMENT);
        recordBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        recordBtn.addActionListener(e -> onToggleRecord());

        playBtn = new JButton("\u25B6  Play");
        playBtn.setFocusPainted(false);
        playBtn.setAlignmentX(LEFT_ALIGNMENT);
        playBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        playBtn.addActionListener(e -> onTogglePlay());

        panel.add(recordBtn);
        panel.add(Box.createVerticalStrut(6));
        panel.add(playBtn);

        return panel;
    }

    /* ================================================================== */
    /*  Public API (called by MainFrame on profile switch)                */
    /* ================================================================== */

    public void loadMacros() {
        macroListModel.clear();
        for (Macro m : getActiveMacros()) {
            macroListModel.addElement(m.getName());
        }
        if (!macroListModel.isEmpty()) {
            macroList.setSelectedIndex(0);
        } else {
            tableModel.setActions(new ArrayList<>());
        }
    }

    /* ================================================================== */
    /*  Macro list actions                                                */
    /* ================================================================== */

    private void onNewMacro() {
        String name = JOptionPane.showInputDialog(this,
                "Macro name:", "New Macro", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        Macro macro = new Macro(name.trim());
        macro.setAutoInsertDelay(autoDelayCheck.isSelected());
        macro.setCycleMode(specifiedTimesRadio.isSelected()
                ? Macro.CycleMode.SPECIFIED_TIMES : Macro.CycleMode.UNTIL_RELEASED);
        macro.setCycleCount((int) cycleCountSpinner.getValue());

        getActiveMacros().add(macro);
        profileManager.save();
        macroListModel.addElement(macro.getName());
        macroList.setSelectedIndex(macroListModel.size() - 1);
    }

    private void onDeleteMacro() {
        int idx = macroList.getSelectedIndex();
        if (idx < 0) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Delete \"" + getActiveMacros().get(idx).getName() + "\"?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        getActiveMacros().remove(idx);
        profileManager.save();
        macroListModel.remove(idx);
        if (!macroListModel.isEmpty()) {
            macroList.setSelectedIndex(Math.min(idx, macroListModel.size() - 1));
        } else {
            tableModel.setActions(new ArrayList<>());
        }
    }

    private void onMacroSelected() {
        Macro macro = getSelectedMacro();
        if (macro == null) {
            tableModel.setActions(new ArrayList<>());
            return;
        }
        tableModel.setActions(macro.getActions());
        autoDelayCheck.setSelected(macro.isAutoInsertDelay());
        if (macro.getCycleMode() == Macro.CycleMode.UNTIL_RELEASED) {
            cycleUntilReleasedRadio.setSelected(true);
        } else {
            specifiedTimesRadio.setSelected(true);
        }
        cycleCountSpinner.setValue(macro.getCycleCount());
    }

    /* ================================================================== */
    /*  Key list actions                                                  */
    /* ================================================================== */

    private void onModifyAction() {
        int row = actionTable.getSelectedRow();
        Macro macro = getSelectedMacro();
        if (row < 0 || macro == null) return;

        MacroAction action = macro.getActions().get(row);
        String input = JOptionPane.showInputDialog(this,
                "Delay (ms):", String.valueOf(action.getDelayMs()));
        if (input == null) return;
        try {
            action.setDelayMs(Long.parseLong(input.trim()));
            tableModel.fireTableRowsUpdated(row, row);
            profileManager.save();
        } catch (NumberFormatException ignored) {}
    }

    private void onDeleteAction() {
        int row = actionTable.getSelectedRow();
        Macro macro = getSelectedMacro();
        if (row < 0 || macro == null) return;

        macro.getActions().remove(row);
        tableModel.fireTableRowsDeleted(row, row);
        profileManager.save();
    }

    private void onInsertEvent() {
        Macro macro = getSelectedMacro();
        if (macro == null) return;

        String type = (String) insertEventCombo.getSelectedItem();
        MacroAction action;

        if ("Delay".equals(type)) {
            String input = JOptionPane.showInputDialog(this, "Delay (ms):", "100");
            if (input == null) return;
            try {
                action = MacroAction.delay(Long.parseLong(input.trim()));
            } catch (NumberFormatException e) { return; }
        } else {
            String input = JOptionPane.showInputDialog(this,
                    "Virtual key code (decimal or 0xHex):", "0x41");
            if (input == null) return;
            try {
                int vk = input.trim().startsWith("0x")
                        ? Integer.parseInt(input.trim().substring(2), 16)
                        : Integer.parseInt(input.trim());
                action = "Key Down".equals(type)
                        ? MacroAction.keyDown(vk, 0)
                        : MacroAction.keyUp(vk, 0);
            } catch (NumberFormatException e) { return; }
        }

        int insertIdx = actionTable.getSelectedRow();
        if (insertIdx < 0) insertIdx = macro.getActions().size();
        else insertIdx++;

        macro.getActions().add(insertIdx, action);
        tableModel.fireTableRowsInserted(insertIdx, insertIdx);
        profileManager.save();
    }

    /* ================================================================== */
    /*  Record / Play                                                     */
    /* ================================================================== */

    private void onToggleRecord() {
        if (recorder.isRecording()) {
            List<MacroAction> recorded = recorder.stopRecording();
            recordBtn.setText("\u25CF  Start record");
            recordBtn.setBackground(UIManager.getColor("Button.background"));
            recordBtn.setForeground(UIManager.getColor("Button.foreground"));

            Macro macro = getSelectedMacro();
            if (macro != null) {
                macro.setActions(recorded);
                tableModel.setActions(macro.getActions());
                profileManager.save();
            }
        } else {
            Macro macro = getSelectedMacro();
            if (macro == null) {
                JOptionPane.showMessageDialog(this,
                        "Select or create a macro first.",
                        "No macro selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            recordBtn.setText("\u25A0  Stop record");
            recordBtn.setBackground(new Color(211, 47, 47));
            recordBtn.setForeground(Color.WHITE);

            tableModel.setActions(new ArrayList<>());

            recorder.startRecording(autoDelayCheck.isSelected(), action -> {
                SwingUtilities.invokeLater(() -> {
                    int sz = tableModel.getRowCount();
                    tableModel.addAction(action);
                    tableModel.fireTableRowsInserted(sz, sz);
                });
            });
        }
    }

    private void onTogglePlay() {
        if (player.isPlaying()) {
            player.stop();
            playBtn.setText("\u25B6  Play");
            return;
        }
        Macro macro = getSelectedMacro();
        if (macro == null || macro.getActions().isEmpty()) return;
        playBtn.setText("\u25A0  Stop");
        player.play(macro, () -> playBtn.setText("\u25B6  Play"));
    }

    /* ================================================================== */
    /*  Settings sync                                                     */
    /* ================================================================== */

    private void syncSettingsToMacro() {
        Macro macro = getSelectedMacro();
        if (macro == null) return;
        macro.setAutoInsertDelay(autoDelayCheck.isSelected());
        macro.setCycleMode(specifiedTimesRadio.isSelected()
                ? Macro.CycleMode.SPECIFIED_TIMES : Macro.CycleMode.UNTIL_RELEASED);
        macro.setCycleCount((int) cycleCountSpinner.getValue());
        profileManager.save();
    }

    /* ================================================================== */
    /*  Helpers                                                           */
    /* ================================================================== */

    private List<Macro> getActiveMacros() {
        return profileManager.getActiveProfile().getMacros();
    }

    private Macro getSelectedMacro() {
        int idx = macroList.getSelectedIndex();
        List<Macro> macros = getActiveMacros();
        return (idx >= 0 && idx < macros.size()) ? macros.get(idx) : null;
    }

    /* ================================================================== */
    /*  Table model for the key/action list                               */
    /* ================================================================== */

    private static class ActionTableModel extends AbstractTableModel {
        private static final String[] COLS = {"#", "Event", "Key", "Delay (ms)"};
        private List<MacroAction> actions = new ArrayList<>();

        void setActions(List<MacroAction> a) {
            this.actions = a;
            fireTableDataChanged();
        }

        void addAction(MacroAction a) { actions.add(a); }

        @Override public int getRowCount() { return actions.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override
        public Object getValueAt(int row, int col) {
            MacroAction a = actions.get(row);
            switch (col) {
                case 0: return row + 1;
                case 1:
                    switch (a.getType()) {
                        case KEY_DOWN: return "Key Down";
                        case KEY_UP:   return "Key Up";
                        case DELAY:    return "Delay";
                    }
                    return "";
                case 2:
                    return a.getType() == MacroAction.Type.DELAY
                            ? "\u2014"
                            : KeyUtils.getKeyLabel(a.getKeyCode());
                case 3: return a.getDelayMs();
                default: return "";
            }
        }
    }
}
