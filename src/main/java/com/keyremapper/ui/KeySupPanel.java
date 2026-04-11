package com.keyremapper.ui;

import com.keyremapper.ai.KeySupBot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.regex.Pattern;

/**
 * Chat-style UI for the KeySup assistant.
 * Supports streaming token-by-token display when Ollama is connected,
 * and falls back to instant regex responses in offline mode.
 */
public class KeySupPanel extends JPanel {

    private static final Color USER_BG = UIManager.getColor("Component.focusColor") != null
            ? UIManager.getColor("Component.focusColor") : new Color(33, 150, 243);
    private static final Color BOT_BG  = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background").darker() : new Color(60, 63, 65);
    private static final Color INFO_BG = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background") : new Color(45, 48, 50);
    private static final Pattern CMD_STRIP =
            Pattern.compile("`?\\[CMD:\\w+\\([^)]*\\)]`?\\s*");

    private final KeySupBot bot;
    private final JPanel chatArea;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private final JButton sendBtn;

    public KeySupPanel(KeySupBot bot) {
        this.bot = bot;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel header = new JLabel("KeySup \u2014 Keyboard Support");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        header.setBorder(new EmptyBorder(0, 4, 8, 0));

        chatArea = new JPanel();
        chatArea.setLayout(new BoxLayout(chatArea, BoxLayout.Y_AXIS));
        chatArea.setOpaque(false);

        JPanel chatWrapper = new JPanel(new BorderLayout());
        chatWrapper.setOpaque(false);
        chatWrapper.add(chatArea, BorderLayout.NORTH);

        scrollPane = new JScrollPane(chatWrapper);
        scrollPane.setBorder(BorderFactory.createLineBorder(
                UIManager.getColor("Component.borderColor")));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        inputField = new JTextField();
        inputField.setFont(inputField.getFont().deriveFont(13f));
        inputField.putClientProperty("JTextField.placeholderText", "Type a message\u2026");
        inputField.addActionListener(this::onSend);

        sendBtn = new JButton("Send");
        sendBtn.setFocusPainted(false);
        sendBtn.setBackground(USER_BG);
        sendBtn.setForeground(Color.WHITE);
        sendBtn.addActionListener(this::onSend);

        JPanel inputBar = new JPanel(new BorderLayout(6, 0));
        inputBar.setBorder(new EmptyBorder(8, 0, 0, 0));
        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(sendBtn, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(inputBar, BorderLayout.SOUTH);

        addBubble(bot.getGreeting(), false);

        bot.checkAvailability(available -> SwingUtilities.invokeLater(() -> {
            if (available) {
                addInfoBubble("\u2705 AI mode active  (model: " + bot.getModelName() + ")");
            } else {
                addInfoBubble("\u26A0 Ollama not detected \u2014 running in offline mode.\n" +
                        "Start Ollama for AI-powered responses.");
            }
        }));
    }

    /* ================================================================== */
    /*  Send handling                                                     */
    /* ================================================================== */

    private void onSend(ActionEvent ignored) {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText("");

        addBubble(text, true);

        if (bot.isOllamaAvailable()) {
            setInputEnabled(false);
            ChatBubble streaming = addStreamingBubble();

            StringBuilder accumulated = new StringBuilder();
            bot.processAsync(text,
                    token -> SwingUtilities.invokeLater(() -> {
                        accumulated.append(token);
                        String display = CMD_STRIP.matcher(accumulated).replaceAll("");
                        streaming.updateText(display.isEmpty() ? "Thinking\u2026" : display);
                        scrollToBottom();
                    }),
                    finalText -> SwingUtilities.invokeLater(() -> {
                        streaming.updateText(finalText);
                        scrollToBottom();
                        setInputEnabled(true);
                    })
            );
        } else {
            String reply = bot.process(text);
            addBubble(reply, false);
        }
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendBtn.setEnabled(enabled);
        if (enabled) inputField.requestFocusInWindow();
    }

    /* ================================================================== */
    /*  Bubble helpers                                                    */
    /* ================================================================== */

    private void addBubble(String text, boolean isUser) {
        chatArea.add(new ChatBubble(text, isUser ? USER_BG : BOT_BG,
                isUser ? FlowLayout.RIGHT : FlowLayout.LEFT));
        chatArea.revalidate();
        scrollToBottom();
    }

    private void addInfoBubble(String text) {
        chatArea.add(new ChatBubble(text, INFO_BG, FlowLayout.CENTER));
        chatArea.revalidate();
        scrollToBottom();
    }

    /** Creates an empty bot bubble that will be updated via streaming. */
    private ChatBubble addStreamingBubble() {
        ChatBubble bubble = new ChatBubble("Thinking\u2026", BOT_BG, FlowLayout.LEFT);
        chatArea.add(bubble);
        chatArea.revalidate();
        scrollToBottom();
        return bubble;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar sb = scrollPane.getVerticalScrollBar();
            sb.setValue(sb.getMaximum());
        });
    }

    /* ================================================================== */
    /*  Chat bubble component                                             */
    /* ================================================================== */

    static class ChatBubble extends JPanel {
        private final JLabel label;

        ChatBubble(String text, Color bg, int alignment) {
            setLayout(new FlowLayout(alignment, 12, 2));
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            setBorder(new EmptyBorder(3, 0, 3, 0));

            JPanel bubble = new RoundPanel(bg);
            bubble.setLayout(new BorderLayout());
            bubble.setBorder(new EmptyBorder(10, 14, 10, 14));

            label = new JLabel(formatHtml(text));
            label.setForeground(Color.WHITE);
            label.setFont(new Font("SansSerif", Font.PLAIN, 13));
            bubble.add(label);

            add(bubble);
        }

        void updateText(String text) {
            label.setText(formatHtml(text));
            revalidate();
            repaint();
            if (getParent() != null) {
                getParent().revalidate();
            }
        }

        private static String formatHtml(String text) {
            String safe = text.replace("&", "&amp;")
                              .replace("<", "&lt;")
                              .replace(">", "&gt;")
                              .replace("\n", "<br>");
            return "<html><div style='width:380px'>" + safe + "</div></html>";
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /* ================================================================== */
    /*  Round-cornered panel                                              */
    /* ================================================================== */

    private static class RoundPanel extends JPanel {
        private final Color bg;

        RoundPanel(Color bg) {
            this.bg = bg;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
        }
    }
}
