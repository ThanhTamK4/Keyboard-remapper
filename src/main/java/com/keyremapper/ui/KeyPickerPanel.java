package com.keyremapper.ui;

import com.keyremapper.util.KeyUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Four-column panel (Comm / Modify / Adv / Keypad) where each key is a small
 * clickable button.  The currently-highlighted button shows which key the
 * selected keyboard key is mapped to.
 */
public class KeyPickerPanel extends JPanel {

    private int highlightedVk = -1;
    private Consumer<Integer> onKeyPicked;
    private final List<PickerButton> allButtons = new ArrayList<>();

    /* ------------------------------------------------------------------ */
    /*  Category data                                                     */
    /* ------------------------------------------------------------------ */

    private static final int[][] COMM = {
            {0x41,0x42,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4A},
            {0x4B,0x4C,0x4D,0x4E,0x4F,0x50,0x51,0x52,0x53,0x54},
            {0x55,0x56,0x57,0x58,0x59,0x5A},
            {0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,0x38,0x39},
            {0xBD,0xBB,0xDD,0xDB,0xDC,0xBA},
            {0xDE,0xC0,0xBC,0xBE,0xBF}
    };

    private static final int[][] MODIFY = {
            {0xA2,0xA0,0xA4,0x5B},
            {0xA3,0xA1,0xA5,0x5C}
    };

    private static final int[][] ADV = {
            {0x70,0x71,0x72,0x73,0x74,0x75,0x76},
            {0x77,0x78,0x79,0x7A,0x7B,0x1B},
            {0x09,0x5D,0x2D,0x23,0x2E,0x0D},
            {0x24,0x13,0x20,0x2C,0x91},
            {0x22,0x21,0x14,0x08},
            {0x25,0x27,0x26,0x28}
    };

    private static final int[][] KEYPAD = {
            {0x60,0x61,0x62,0x63},
            {0x64,0x65,0x66,0x67},
            {0x68,0x69},
            {0x6B,0x6D,0x6A,0x6F},
            {0x6E,0x90}
    };

    /* ------------------------------------------------------------------ */
    /*  Constructor                                                       */
    /* ------------------------------------------------------------------ */

    public KeyPickerPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(6, 0, 0, 0));

        JLabel title = new JLabel("Key setting");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setBorder(new EmptyBorder(0, 4, 6, 0));

        ScrollableGrid grid = new ScrollableGrid(new GridBagLayout());
        grid.setOpaque(false);

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 14);

        gc.gridx = 0; gc.weightx = 0.38;
        grid.add(buildCategory("Comm", COMM), gc);

        gc.gridx = 1; gc.weightx = 0.18;
        grid.add(buildCategory("Modify", MODIFY), gc);

        gc.gridx = 2; gc.weightx = 0.30;
        grid.add(buildCategory("Adv", ADV), gc);

        gc.gridx = 3; gc.weightx = 0.14; gc.insets = new Insets(0, 0, 0, 0);
        grid.add(buildCategory("Keypad", KEYPAD), gc);

        JScrollPane scroll = new JScrollPane(grid,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        add(title, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /* ------------------------------------------------------------------ */
    /*  Public API                                                        */
    /* ------------------------------------------------------------------ */

    public void setOnKeyPicked(Consumer<Integer> cb) { this.onKeyPicked = cb; }

    public void setHighlightedKey(int vk) {
        this.highlightedVk = vk;
        for (PickerButton b : allButtons) {
            b.setHighlight(b.vk == vk);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Build helpers                                                     */
    /* ------------------------------------------------------------------ */

    private JPanel buildCategory(String name, int[][] rows) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        JLabel lbl = new JLabel(name);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        lbl.setBorder(new EmptyBorder(0, 2, 4, 0));
        panel.add(lbl);

        for (int[] row : rows) {
            JPanel rp = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
            rp.setOpaque(false);
            rp.setAlignmentX(LEFT_ALIGNMENT);
            for (int vk : row) {
                PickerButton btn = new PickerButton(vk);
                allButtons.add(btn);
                rp.add(btn);
            }
            panel.add(rp);
        }
        return panel;
    }

    /* ------------------------------------------------------------------ */
    /*  Picker button                                                     */
    /* ------------------------------------------------------------------ */

    /** Fills viewport width so GridBagLayout distributes columns, but scrolls vertically. */
    private static class ScrollableGrid extends JPanel implements Scrollable {
        ScrollableGrid(LayoutManager lm) { super(lm); }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 72; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private class PickerButton extends JButton {
        final int vk;
        private boolean highlighted;
        private final Color HL_BG = new Color(33, 150, 243);

        PickerButton(int vk) {
            super(KeyUtils.getPickerLabel(vk));
            this.vk = vk;
            setFocusPainted(false);
            setFont(getFont().deriveFont(11f));
            setMargin(new Insets(2, 6, 2, 6));
            putClientProperty("JButton.buttonType", "roundRect");

            addActionListener(e -> {
                if (onKeyPicked != null) onKeyPicked.accept(vk);
            });
        }

        void setHighlight(boolean on) {
            if (on == highlighted) return;
            highlighted = on;
            if (on) {
                setBackground(HL_BG);
                setForeground(Color.WHITE);
            } else {
                setBackground(UIManager.getColor("Button.background"));
                setForeground(UIManager.getColor("Button.foreground"));
            }
        }
    }
}
