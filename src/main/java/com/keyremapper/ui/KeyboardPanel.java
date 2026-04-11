package com.keyremapper.ui;

import com.keyremapper.util.KeyUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A visual 87 % (TKL) keyboard layout. Each key is a clickable rounded rectangle
 * painted with a position-based rainbow gradient.  When a key is selected its border
 * turns white; when remapped the label changes to show the target key.
 */
public class KeyboardPanel extends JPanel {

    /* ------------------------------------------------------------------ */
    /*  Key definition                                                    */
    /* ------------------------------------------------------------------ */

    static class KeyDef {
        final String label;
        final int vk;
        final double x, y, w, h;

        KeyDef(String label, int vk, double x, double y, double w) {
            this(label, vk, x, y, w, 1.0);
        }

        KeyDef(String label, int vk, double x, double y, double w, double h) {
            this.label = label;
            this.vk = vk;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Layout constants                                                  */
    /* ------------------------------------------------------------------ */

    static final double LW = 18.5;
    static final double LH = 6.5;
    private static final double GAP = 0.05;
    private static final int ARC = 6;

    private static final List<KeyDef> LAYOUT = buildLayout();

    /* ------------------------------------------------------------------ */
    /*  State                                                             */
    /* ------------------------------------------------------------------ */

    private int selectedVk = -1;
    private final Map<Integer, Integer> mappings = new HashMap<>();
    private Consumer<Integer> onKeySelected;

    /* ------------------------------------------------------------------ */
    /*  Constructor                                                       */
    /* ------------------------------------------------------------------ */

    public KeyboardPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(820, 310));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                KeyDef hit = hitTest(e.getX(), e.getY());
                if (hit != null && hit.vk > 0) {
                    selectedVk = hit.vk;
                    repaint();
                    if (onKeySelected != null) onKeySelected.accept(hit.vk);
                }
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Public API                                                        */
    /* ------------------------------------------------------------------ */

    public void setOnKeySelected(Consumer<Integer> cb) { this.onKeySelected = cb; }

    public void setSelectedKey(int vk) {
        this.selectedVk = vk;
        repaint();
    }

    public void updateMappings(Map<Integer, Integer> m) {
        mappings.clear();
        mappings.putAll(m);
        repaint();
    }

    public int getSelectedVk() { return selectedVk; }

    /* ------------------------------------------------------------------ */
    /*  Painting                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int pad = 12;
        double unit = Math.min(
                (getWidth() - pad * 2) / LW,
                (getHeight() - pad * 2) / LH);
        double ox = (getWidth() - unit * LW) / 2;
        double oy = (getHeight() - unit * LH) / 2;

        drawCase(g2, ox, oy, unit);

        for (KeyDef k : LAYOUT) {
            drawKey(g2, k, unit, ox, oy);
        }
        g2.dispose();
    }

    private void drawCase(Graphics2D g, double ox, double oy, double u) {
        double margin = u * 0.18;
        RoundRectangle2D r = new RoundRectangle2D.Double(
                ox - margin, oy - margin,
                LW * u + margin * 2, LH * u + margin * 2,
                14, 14);
        g.setColor(new Color(38, 38, 42));
        g.fill(r);
        g.setColor(new Color(58, 58, 62));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(r);
    }

    private void drawKey(Graphics2D g, KeyDef k, double u, double ox, double oy) {
        double gap = GAP * u;
        int kx = (int) (ox + k.x * u + gap);
        int ky = (int) (oy + k.y * u + gap);
        int kw = (int) (k.w * u - gap * 2);
        int kh = (int) (k.h * u - gap * 2);

        boolean sel = k.vk == selectedVk && k.vk > 0;
        boolean mapped = mappings.containsKey(k.vk);

        Color bg = keyColor(k.x + k.w / 2, sel, mapped);
        g.setColor(bg);
        g.fillRoundRect(kx, ky, kw, kh, ARC, ARC);

        if (sel) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.2f));
            g.drawRoundRect(kx, ky, kw, kh, ARC, ARC);
            g.setStroke(new BasicStroke(1));
        }

        String label = mapped ? KeyUtils.getKeyLabel(mappings.get(k.vk)) : k.label;
        float fontSize = (float) Math.max(9, u * (k.w >= 1.4 ? 0.22 : 0.26));
        g.setFont(g.getFont().deriveFont(Font.PLAIN, fontSize));
        g.setColor(Color.WHITE);

        FontMetrics fm = g.getFontMetrics();
        int tx = kx + (kw - fm.stringWidth(label)) / 2;
        int ty = ky + (kh + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(label, tx, ty);

        if (mapped && !sel) {
            g.setColor(new Color(255, 200, 50, 180));
            g.fillOval(kx + kw - 8, ky + 3, 5, 5);
        }
    }

    private Color keyColor(double cx, boolean sel, boolean mapped) {
        float hue = 0.80f - (float) (cx / LW) * 0.80f;
        float sat = sel ? 0.75f : 0.60f;
        float bri = sel ? 0.72f : (mapped ? 0.52f : 0.38f);
        return Color.getHSBColor(hue, sat, bri);
    }

    /* ------------------------------------------------------------------ */
    /*  Hit testing                                                       */
    /* ------------------------------------------------------------------ */

    private KeyDef hitTest(int mx, int my) {
        int pad = 12;
        double unit = Math.min(
                (getWidth() - pad * 2) / LW,
                (getHeight() - pad * 2) / LH);
        double ox = (getWidth() - unit * LW) / 2;
        double oy = (getHeight() - unit * LH) / 2;

        for (KeyDef k : LAYOUT) {
            double gap = GAP * unit;
            double kx = ox + k.x * unit + gap;
            double ky = oy + k.y * unit + gap;
            double kw = k.w * unit - gap * 2;
            double kh = k.h * unit - gap * 2;
            if (mx >= kx && mx <= kx + kw && my >= ky && my <= ky + kh) {
                return k;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /*  87 % (TKL) keyboard layout definition                             */
    /* ------------------------------------------------------------------ */

    private static List<KeyDef> buildLayout() {
        List<KeyDef> k = new ArrayList<>();
        double x;

        // Row 0 — function row (y = 0)
        a(k, "Esc",   0x1B, 0, 0, 1);
        a(k, "F1",    0x70, 2, 0, 1);
        a(k, "F2",    0x71, 3, 0, 1);
        a(k, "F3",    0x72, 4, 0, 1);
        a(k, "F4",    0x73, 5, 0, 1);
        a(k, "F5",    0x74, 6.5, 0, 1);
        a(k, "F6",    0x75, 7.5, 0, 1);
        a(k, "F7",    0x76, 8.5, 0, 1);
        a(k, "F8",    0x77, 9.5, 0, 1);
        a(k, "F9",    0x78, 11, 0, 1);
        a(k, "F10",   0x79, 12, 0, 1);
        a(k, "F11",   0x7A, 13, 0, 1);
        a(k, "F12",   0x7B, 14, 0, 1);
        a(k, "PrtSc", 0x2C, 15.5, 0, 1);
        a(k, "ScrLk", 0x91, 16.5, 0, 1);
        a(k, "Pause", 0x13, 17.5, 0, 1);

        // Row 1 — number row (y = 1.5)
        x = 0;
        x = a(k, "`",    0xC0, x, 1.5, 1);
        x = a(k, "1",    0x31, x, 1.5, 1);
        x = a(k, "2",    0x32, x, 1.5, 1);
        x = a(k, "3",    0x33, x, 1.5, 1);
        x = a(k, "4",    0x34, x, 1.5, 1);
        x = a(k, "5",    0x35, x, 1.5, 1);
        x = a(k, "6",    0x36, x, 1.5, 1);
        x = a(k, "7",    0x37, x, 1.5, 1);
        x = a(k, "8",    0x38, x, 1.5, 1);
        x = a(k, "9",    0x39, x, 1.5, 1);
        x = a(k, "0",    0x30, x, 1.5, 1);
        x = a(k, "-",    0xBD, x, 1.5, 1);
        x = a(k, "=",    0xBB, x, 1.5, 1);
            a(k, "Bksp", 0x08, x, 1.5, 2);
            a(k, "Ins",  0x2D, 15.5, 1.5, 1);
            a(k, "Home", 0x24, 16.5, 1.5, 1);
            a(k, "PgUp", 0x21, 17.5, 1.5, 1);

        // Row 2 — QWERTY (y = 2.5)
        x = 0;
        x = a(k, "Tab",  0x09, x, 2.5, 1.5);
        x = a(k, "Q",    0x51, x, 2.5, 1);
        x = a(k, "W",    0x57, x, 2.5, 1);
        x = a(k, "E",    0x45, x, 2.5, 1);
        x = a(k, "R",    0x52, x, 2.5, 1);
        x = a(k, "T",    0x54, x, 2.5, 1);
        x = a(k, "Y",    0x59, x, 2.5, 1);
        x = a(k, "U",    0x55, x, 2.5, 1);
        x = a(k, "I",    0x49, x, 2.5, 1);
        x = a(k, "O",    0x4F, x, 2.5, 1);
        x = a(k, "P",    0x50, x, 2.5, 1);
        x = a(k, "[",    0xDB, x, 2.5, 1);
        x = a(k, "]",    0xDD, x, 2.5, 1);
            a(k, "\\",   0xDC, x, 2.5, 1.5);
            a(k, "Del",  0x2E, 15.5, 2.5, 1);
            a(k, "End",  0x23, 16.5, 2.5, 1);
            a(k, "PgDn", 0x22, 17.5, 2.5, 1);

        // Row 3 — home row (y = 3.5)
        x = 0;
        x = a(k, "Caps", 0x14, x, 3.5, 1.75);
        x = a(k, "A",    0x41, x, 3.5, 1);
        x = a(k, "S",    0x53, x, 3.5, 1);
        x = a(k, "D",    0x44, x, 3.5, 1);
        x = a(k, "F",    0x46, x, 3.5, 1);
        x = a(k, "G",    0x47, x, 3.5, 1);
        x = a(k, "H",    0x48, x, 3.5, 1);
        x = a(k, "J",    0x4A, x, 3.5, 1);
        x = a(k, "K",    0x4B, x, 3.5, 1);
        x = a(k, "L",    0x4C, x, 3.5, 1);
        x = a(k, ";",    0xBA, x, 3.5, 1);
        x = a(k, "'",    0xDE, x, 3.5, 1);
            a(k, "Enter",0x0D, x, 3.5, 2.25);

        // Row 4 — shift row (y = 4.5)
        x = 0;
        x = a(k, "Shift",0xA0, x, 4.5, 2.25);
        x = a(k, "Z",    0x5A, x, 4.5, 1);
        x = a(k, "X",    0x58, x, 4.5, 1);
        x = a(k, "C",    0x43, x, 4.5, 1);
        x = a(k, "V",    0x56, x, 4.5, 1);
        x = a(k, "B",    0x42, x, 4.5, 1);
        x = a(k, "N",    0x4E, x, 4.5, 1);
        x = a(k, "M",    0x4D, x, 4.5, 1);
        x = a(k, ",",    0xBC, x, 4.5, 1);
        x = a(k, ".",    0xBE, x, 4.5, 1);
        x = a(k, "/",    0xBF, x, 4.5, 1);
            a(k, "Shift",0xA1, x, 4.5, 2.75);
            a(k, "\u2191",0x26, 16.5, 4.5, 1);

        // Row 5 — bottom row + arrows (y = 5.5)
        x = 0;
        x = a(k, "Ctrl", 0xA2, x, 5.5, 1.25);
        x = a(k, "Win",  0x5B, x, 5.5, 1.25);
        x = a(k, "Alt",  0xA4, x, 5.5, 1.25);
        x = a(k, "Space",0x20, x, 5.5, 6.25);
        x = a(k, "Alt",  0xA5, x, 5.5, 1.25);
        x = a(k, "Fn",   0,    x, 5.5, 1.25);
        x = a(k, "App",  0x5D, x, 5.5, 1.25);
            a(k, "Ctrl", 0xA3, x, 5.5, 1.25);
            a(k, "\u2190",0x25, 15.5, 5.5, 1);
            a(k, "\u2193",0x28, 16.5, 5.5, 1);
            a(k, "\u2192",0x27, 17.5, 5.5, 1);

        return k;
    }

    private static double a(List<KeyDef> list, String label, int vk,
                             double x, double y, double w) {
        list.add(new KeyDef(label, vk, x, y, w));
        return x + w;
    }
}
