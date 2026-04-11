package com.keyremapper.ui;

import com.keyremapper.ai.KeySupBot;
import com.keyremapper.hook.KeyboardHook;
import com.keyremapper.hook.MacroPlayer;
import com.keyremapper.hook.MacroRecorder;
import com.keyremapper.model.KeyMapping;
import com.keyremapper.model.Macro;
import com.keyremapper.model.MacroAction;
import com.keyremapper.model.Profile;
import com.keyremapper.util.KeyUtils;
import com.keyremapper.util.ProfileManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class MainFrame extends JFrame {

    private final ProfileManager profileManager = new ProfileManager();
    private final KeyboardHook hook = new KeyboardHook();

    private KeyboardPanel keyboardPanel;
    private KeyPickerPanel pickerPanel;
    private ProfilePanel profilePanel;
    private MacroPanel macroPanel;
    private JLabel statusLabel;

    private final Map<Integer, Integer> pendingMappings = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean hookActive = false;
    private HttpServer bridgeServer;
    private final MacroRecorder bridgeRecorder = new MacroRecorder();
    private final MacroPlayer bridgePlayer = new MacroPlayer();

    private static final int BRIDGE_PORT = 8230;

    public MainFrame() {
        super("Key Remapper");
        initUI();
        loadActiveProfile();
        startBridgeServer();
    }

    /* ================================================================== */
    /*  UI construction                                                   */
    /* ================================================================== */

    private void initUI() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });
        setSize(980, 700);
        setMinimumSize(new Dimension(840, 580));
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 13f));
        tabs.addTab("Key Settings", buildKeySettingsTab());
        macroPanel = new MacroPanel(profileManager);
        tabs.addTab("Macro", macroPanel);
        tabs.addTab("KeySup", buildKeySupTab());

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(6, 8, 8, 8));
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
    }

    /* ------------------------------------------------------------------ */
    /*  Tab 1 — Key Settings                                              */
    /* ------------------------------------------------------------------ */

    private JPanel buildKeySettingsTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 0));
        tab.setBorder(new EmptyBorder(8, 8, 4, 8));

        profilePanel = new ProfilePanel(profileManager);
        profilePanel.setOnProfileChanged(idx -> {
            loadActiveProfile();
            macroPanel.loadMacros();
        });

        keyboardPanel = new KeyboardPanel();
        keyboardPanel.setOnKeySelected(this::onKeyboardKeySelected);

        pickerPanel = new KeyPickerPanel();
        pickerPanel.setOnKeyPicked(this::onPickerKeyClicked);

        JPanel bottomBar = buildBottomBar();

        JPanel centre = new JPanel(new BorderLayout(0, 8));
        centre.add(keyboardPanel, BorderLayout.CENTER);

        JPanel lower = new JPanel(new BorderLayout(0, 6));
        lower.add(pickerPanel, BorderLayout.CENTER);
        lower.add(bottomBar, BorderLayout.SOUTH);
        centre.add(lower, BorderLayout.SOUTH);

        tab.add(profilePanel, BorderLayout.WEST);
        tab.add(centre, BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildBottomBar() {
        statusLabel = new JLabel("\u25CB  Inactive");
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JLabel version = new JLabel("Driver version: 1.0");
        version.setForeground(UIManager.getColor("Label.disabledForeground"));
        version.setFont(version.getFont().deriveFont(11f));

        JButton restoreBtn = new JButton("Restore");
        restoreBtn.setFocusPainted(false);
        restoreBtn.addActionListener(e -> onRestore());

        JButton disableBtn = new JButton("Disable");
        disableBtn.setFocusPainted(false);
        disableBtn.setBackground(new Color(211, 47, 47));
        disableBtn.setForeground(Color.WHITE);
        disableBtn.addActionListener(e -> onDisable());

        JButton applyBtn = new JButton("Apply");
        applyBtn.setFocusPainted(false);
        applyBtn.setBackground(new Color(33, 150, 243));
        applyBtn.setForeground(Color.WHITE);
        applyBtn.addActionListener(e -> onApply());

        JPanel left  = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(version);
        JPanel mid   = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        mid.add(statusLabel);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(restoreBtn);
        right.add(disableBtn);
        right.add(applyBtn);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new EmptyBorder(8, 0, 0, 0));
        bar.add(left, BorderLayout.WEST);
        bar.add(mid, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /* ------------------------------------------------------------------ */
    /*  Tab 2 — KeySup (AI chat)                                          */
    /* ------------------------------------------------------------------ */

    private JPanel buildKeySupTab() {
        KeySupBot bot = new KeySupBot(botActions);
        return new KeySupPanel(bot);
    }

    /* ================================================================== */
    /*  Bot Actions implementation                                        */
    /* ================================================================== */

    private final KeySupBot.Actions botActions = new KeySupBot.Actions() {

        @Override
        public String createProfile(String name) {
            profileManager.addProfile(name);
            profilePanel.refresh();
            return "Created profile \"" + name + "\". Use \"switch to profile " + name + "\" to activate it.";
        }

        @Override
        public String deleteProfile(String identifier) {
            int idx = findProfileIndex(identifier);
            if (idx < 0) return "Profile \"" + identifier + "\" not found.";
            if (profileManager.getProfiles().size() <= 1)
                return "Can't delete the last remaining profile.";
            String n = profileManager.getProfiles().get(idx).getName();
            profileManager.deleteProfile(idx);
            profilePanel.refresh();
            loadActiveProfile();
            return "Deleted profile \"" + n + "\".";
        }

        @Override
        public String renameProfile(String identifier, String newName) {
            int idx = findProfileIndex(identifier);
            if (idx < 0) return "Profile \"" + identifier + "\" not found.";
            String old = profileManager.getProfiles().get(idx).getName();
            profileManager.renameProfile(idx, newName);
            profilePanel.refresh();
            return "Renamed \"" + old + "\" to \"" + newName + "\".";
        }

        @Override
        public String listProfiles() {
            StringBuilder sb = new StringBuilder("Profiles:\n");
            for (int i = 0; i < profileManager.getProfiles().size(); i++) {
                Profile p = profileManager.getProfiles().get(i);
                boolean active = (i == profileManager.getActiveIndex());
                sb.append("  ").append(i + 1).append(". ").append(p.getName());
                if (active) sb.append("  (active)");
                sb.append('\n');
            }
            return sb.toString().trim();
        }

        @Override
        public String switchProfile(String identifier) {
            int idx = findProfileIndex(identifier);
            if (idx < 0) return "Profile \"" + identifier + "\" not found.";
            profileManager.setActiveIndex(idx);
            profilePanel.refresh();
            loadActiveProfile();
            return "Switched to \"" + profileManager.getActiveProfile().getName() + "\".";
        }

        @Override
        public String mapKey(int fromVk, int toVk) {
            pendingMappings.put(fromVk, toVk);
            syncUI();
            return "Mapped " + KeyUtils.getKeyLabel(fromVk) + " \u2192 " +
                    KeyUtils.getKeyLabel(toVk) + ". Click Apply (or type \"apply\") to activate.";
        }

        @Override
        public String swapKeys(int vk1, int vk2) {
            pendingMappings.put(vk1, vk2);
            pendingMappings.put(vk2, vk1);
            syncUI();
            return "Swapped " + KeyUtils.getKeyLabel(vk1) + " \u2194 " +
                    KeyUtils.getKeyLabel(vk2) + ". Click Apply to activate.";
        }

        @Override
        public String removeMapping(int fromVk) {
            if (!pendingMappings.containsKey(fromVk))
                return KeyUtils.getKeyLabel(fromVk) + " has no mapping to remove.";
            pendingMappings.remove(fromVk);
            syncUI();
            return "Removed mapping for " + KeyUtils.getKeyLabel(fromVk) + ".";
        }

        @Override
        public String clearMappings() {
            if (pendingMappings.isEmpty()) return "There are no mappings to clear.";
            int n = pendingMappings.size();
            pendingMappings.clear();
            syncUI();
            return "Cleared " + n + " mapping(s).";
        }

        @Override
        public String showMappings() {
            if (pendingMappings.isEmpty()) return "No key mappings are configured.";
            StringBuilder sb = new StringBuilder("Current mappings:\n");
            for (Map.Entry<Integer, Integer> e : pendingMappings.entrySet()) {
                sb.append("  ").append(KeyUtils.getKeyLabel(e.getKey()))
                  .append(" \u2192 ").append(KeyUtils.getKeyLabel(e.getValue()))
                  .append('\n');
            }
            sb.append("\nStatus: ").append(hookActive ? "Active" : "Inactive");
            return sb.toString().trim();
        }

        @Override
        public String apply() {
            if (pendingMappings.isEmpty())
                return "No mappings to apply. Map some keys first!";
            onApply();
            return "Mappings applied! " + pendingMappings.size() + " key(s) remapped system-wide.";
        }

        @Override
        public String restore() {
            onRestore();
            return "Restored defaults. All mappings cleared and hook disabled.";
        }

        @Override
        public String createMacro(String name) {
            if (name == null || name.trim().isEmpty()) return "Macro name cannot be empty.";
            Macro macro = new Macro(name.trim());
            profileManager.getActiveProfile().getMacros().add(macro);
            profileManager.save();
            macroPanel.loadMacros();
            return "Created macro \"" + name.trim() + "\".";
        }

        @Override
        public String deleteMacro(String name) {
            java.util.List<Macro> macros = profileManager.getActiveProfile().getMacros();
            for (int i = 0; i < macros.size(); i++) {
                if (macros.get(i).getName().equalsIgnoreCase(name.trim())) {
                    String n = macros.get(i).getName();
                    macros.remove(i);
                    profileManager.save();
                    macroPanel.loadMacros();
                    return "Deleted macro \"" + n + "\".";
                }
            }
            return "Macro \"" + name + "\" not found.";
        }

        @Override
        public String listMacros() {
            java.util.List<Macro> macros = profileManager.getActiveProfile().getMacros();
            if (macros.isEmpty()) return "No macros in the active profile.";
            StringBuilder sb = new StringBuilder("Macros:\n");
            for (int i = 0; i < macros.size(); i++) {
                Macro m = macros.get(i);
                sb.append("  ").append(i + 1).append(". ").append(m.getName())
                  .append(" (").append(m.getActions().size()).append(" actions)\n");
            }
            return sb.toString().trim();
        }

        @Override
        public String addMacroKeyPress(String macroName, int vk) {
            java.util.List<Macro> macros = profileManager.getActiveProfile().getMacros();
            for (Macro m : macros) {
                if (m.getName().equalsIgnoreCase(macroName.trim())) {
                    m.getActions().add(MacroAction.keyDown(vk, 0));
                    m.getActions().add(MacroAction.keyUp(vk, 10));
                    profileManager.save();
                    macroPanel.loadMacros();
                    return "Added " + KeyUtils.getKeyLabel(vk) + " press to \"" + m.getName() + "\".";
                }
            }
            return "Macro \"" + macroName + "\" not found.";
        }

        @Override
        public String clearMacro(String name) {
            java.util.List<Macro> macros = profileManager.getActiveProfile().getMacros();
            for (Macro m : macros) {
                if (m.getName().equalsIgnoreCase(name.trim())) {
                    int count = m.getActions().size();
                    m.getActions().clear();
                    profileManager.save();
                    macroPanel.loadMacros();
                    return "Cleared " + count + " action(s) from \"" + m.getName() + "\".";
                }
            }
            return "Macro \"" + name + "\" not found.";
        }
    };

    /* ================================================================== */
    /*  Key Settings event handlers                                       */
    /* ================================================================== */

    private void onKeyboardKeySelected(int vk) {
        Integer mapped = pendingMappings.get(vk);
        pickerPanel.setHighlightedKey(mapped != null ? mapped : -1);
    }

    private void onPickerKeyClicked(int targetVk) {
        int sel = keyboardPanel.getSelectedVk();
        if (sel <= 0) return;
        if (targetVk == sel) {
            pendingMappings.remove(sel);
        } else {
            pendingMappings.put(sel, targetVk);
        }
        syncUI();
        pickerPanel.setHighlightedKey(targetVk == sel ? -1 : targetVk);
    }

    private void onDisable() {
        if (hookActive) { hook.stop(); hookActive = false; }
        updateStatus();
    }

    private void onApply() {
        Profile p = profileManager.getActiveProfile();
        p.setMappings(ProfileManager.fromVkMap(pendingMappings));
        profileManager.save();
        hook.updateMappings(new HashMap<>(pendingMappings));
        if (!hookActive) { hook.start(); hookActive = true; }
        updateStatus();
    }

    private void onRestore() {
        pendingMappings.clear();
        keyboardPanel.updateMappings(pendingMappings);
        keyboardPanel.setSelectedKey(-1);
        pickerPanel.setHighlightedKey(-1);
        Profile p = profileManager.getActiveProfile();
        p.getMappings().clear();
        profileManager.save();
        if (hookActive) { hook.stop(); hookActive = false; }
        updateStatus();
    }

    /* ================================================================== */
    /*  Helpers                                                           */
    /* ================================================================== */

    private void syncUI() {
        keyboardPanel.updateMappings(pendingMappings);
    }

    private void loadActiveProfile() {
        Profile p = profileManager.getActiveProfile();
        pendingMappings.clear();
        for (KeyMapping m : p.getMappings()) {
            pendingMappings.put(m.getFromKeyCode(), m.getToKeyCode());
        }
        keyboardPanel.updateMappings(pendingMappings);
        keyboardPanel.setSelectedKey(-1);
        pickerPanel.setHighlightedKey(-1);
        if (hookActive) hook.updateMappings(new HashMap<>(pendingMappings));
    }

    private void updateStatus() {
        if (hookActive) {
            statusLabel.setText("\u25CF  Active \u2014 " + pendingMappings.size() + " mapping(s)");
            statusLabel.setForeground(new Color(46, 125, 50));
        } else {
            statusLabel.setText("\u25CB  Inactive");
            statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        }
    }

    private int findProfileIndex(String id) {
        id = id.trim();
        try {
            int num = Integer.parseInt(id);
            if (num >= 1 && num <= profileManager.getProfiles().size()) return num - 1;
        } catch (NumberFormatException ignored) { }
        for (int i = 0; i < profileManager.getProfiles().size(); i++) {
            if (profileManager.getProfiles().get(i).getName().equalsIgnoreCase(id)) return i;
        }
        return -1;
    }

    /* ================================================================== */
    /*  Bridge HTTP server (for web UI control)                           */
    /* ================================================================== */

    private void startBridgeServer() {
        try {
            bridgeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", BRIDGE_PORT), 0);
            bridgeServer.setExecutor(Executors.newFixedThreadPool(4));

            bridgeServer.createContext("/api/status", ex -> {
                cors(ex);
                if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 204, ""); return; }
                // Reload profiles if changed externally (e.g. by web server)
                profileManager.reloadIfChanged();
                String json = buildStatusJson();
                sendJson(ex, 200, json);
            });

            bridgeServer.createContext("/api/apply", ex -> {
                cors(ex);
                if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 204, ""); return; }
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                final String[] result = { "" };
                final int[] code = { 200 };
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        applyMappingsFromJson(body);
                        result[0] = buildStatusJson();
                    });
                } catch (Exception e) {
                    code[0] = 500;
                    result[0] = "{\"error\":\"" + e.getMessage() + "\"}";
                }
                sendJson(ex, code[0], result[0]);
            });

            bridgeServer.createContext("/api/disable", ex -> {
                cors(ex);
                if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 204, ""); return; }
                final String[] result = { "" };
                final int[] code = { 200 };
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        onDisable();
                        result[0] = buildStatusJson();
                    });
                } catch (Exception e) {
                    code[0] = 500;
                    result[0] = "{\"error\":\"" + e.getMessage() + "\"}";
                }
                sendJson(ex, code[0], result[0]);
            });

            bridgeServer.createContext("/api/restore", ex -> {
                cors(ex);
                if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 204, ""); return; }
                final String[] result = { "" };
                final int[] code = { 200 };
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        onRestore();
                        result[0] = buildStatusJson();
                    });
                } catch (Exception e) {
                    code[0] = 500;
                    result[0] = "{\"error\":\"" + e.getMessage() + "\"}";
                }
                sendJson(ex, code[0], result[0]);
            });

            bridgeServer.createContext("/api/macro/record/start", ex -> {
                cors(ex);
                if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 204, ""); return; }
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean autoDelay = body.contains("\"autoDelay\":true");
                if (bridgeRecorder.isRecording()) {
                    sendJson(ex, 200, "{\"status\":\"already_recording\"}");
                    return;
                }
                bridgeRecorder.startRecording(autoDelay, null);
                sendJson(ex, 200, "{\"status\":\"recording\"}");
            });

            bridgeServer.createContext("/api/macro/record/stop", ex -> {
                cors(ex);
                if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 204, ""); return; }
                if (!bridgeRecorder.isRecording()) {
                    sendJson(ex, 200, "{\"status\":\"not_recording\",\"actions\":[]}");
                    return;
                }
                List<MacroAction> recorded = bridgeRecorder.stopRecording();
                StringBuilder sb = new StringBuilder("{\"status\":\"stopped\",\"actions\":[");
                for (int i = 0; i < recorded.size(); i++) {
                    MacroAction a = recorded.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{\"type\":\"").append(a.getType().name())
                      .append("\",\"keyCode\":").append(a.getKeyCode())
                      .append(",\"delayMs\":").append(a.getDelayMs()).append("}");
                }
                sb.append("]}");
                sendJson(ex, 200, sb.toString());
            });

            bridgeServer.createContext("/api/macro/play", ex -> {
                cors(ex);
                if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 204, ""); return; }
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                // Parse actions array and settings from body
                Macro macro = parseMacroFromJson(body);
                if (macro == null || macro.getActions().isEmpty()) {
                    sendJson(ex, 400, "{\"status\":\"error\",\"message\":\"No actions to play\"}");
                    return;
                }
                if (bridgePlayer.isPlaying()) {
                    sendJson(ex, 200, "{\"status\":\"already_playing\"}");
                    return;
                }
                bridgePlayer.play(macro, null);
                sendJson(ex, 200, "{\"status\":\"playing\"}");
            });

            bridgeServer.createContext("/api/macro/play/stop", ex -> {
                cors(ex);
                if ("OPTIONS".equals(ex.getRequestMethod())) { sendJson(ex, 204, ""); return; }
                bridgePlayer.stop();
                sendJson(ex, 200, "{\"status\":\"stopped\"}");
            });

            bridgeServer.start();
            System.out.println("Bridge server listening on http://127.0.0.1:" + BRIDGE_PORT);
        } catch (Exception e) {
            System.err.println("Failed to start bridge server: " + e.getMessage());
            if (bridgeServer != null) {
                bridgeServer.stop(0);
                bridgeServer = null;
            }
        }
    }

    private void cors(HttpExchange ex) {
        // Restrict CORS to localhost only — prevents arbitrary websites from
        // sending commands to remap the keyboard via the bridge server.
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && (origin.startsWith("http://localhost:") ||
                               origin.startsWith("http://127.0.0.1:"))) {
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
        } else {
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "http://localhost:" + 3000);
        }
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        if (code == 204) {
            ex.sendResponseHeaders(204, -1);
        } else {
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
        ex.close();
    }

    private final Gson gson = new Gson();

    private String buildStatusJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("hookActive", hookActive);
        obj.addProperty("mappingCount", pendingMappings.size());
        JsonObject mappingsObj = new JsonObject();
        for (Map.Entry<Integer, Integer> e : pendingMappings.entrySet()) {
            mappingsObj.addProperty(String.valueOf(e.getKey()), e.getValue());
        }
        obj.add("mappings", mappingsObj);
        return gson.toJson(obj);
    }

    private void applyMappingsFromJson(String body) {
        pendingMappings.clear();
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("mappings")) {
                JsonObject mappings = root.getAsJsonObject("mappings");
                for (Map.Entry<String, JsonElement> e : mappings.entrySet()) {
                    try {
                        int from = Integer.parseInt(e.getKey());
                        int to = e.getValue().getAsInt();
                        pendingMappings.put(from, to);
                    } catch (NumberFormatException ignored) { }
                }
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            System.err.println("Invalid JSON in applyMappings: " + e.getMessage());
        }
        onApply();
    }

    private Macro parseMacroFromJson(String body) {
        Macro macro = new Macro("bridge-play");
        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (com.google.gson.JsonSyntaxException e) {
            System.err.println("Invalid JSON in parseMacro: " + e.getMessage());
            return macro;
        }

        if (root.has("cycleMode")) {
            String mode = root.get("cycleMode").getAsString();
            macro.setCycleMode("UNTIL_RELEASED".equals(mode)
                    ? Macro.CycleMode.UNTIL_RELEASED
                    : Macro.CycleMode.SPECIFIED_TIMES);
        } else {
            macro.setCycleMode(Macro.CycleMode.SPECIFIED_TIMES);
        }

        if (root.has("cycleCount")) {
            macro.setCycleCount(root.get("cycleCount").getAsInt());
        }

        if (!root.has("actions")) return macro;

        JsonArray actionsArr = root.getAsJsonArray("actions");
        List<MacroAction> actions = new ArrayList<>();
        for (JsonElement el : actionsArr) {
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("type")) continue;
            String typeStr = obj.get("type").getAsString();
            MacroAction.Type type;
            try { type = MacroAction.Type.valueOf(typeStr); }
            catch (IllegalArgumentException e) { continue; }
            int kc = obj.has("keyCode") ? obj.get("keyCode").getAsInt() : 0;
            long dm = obj.has("delayMs") ? obj.get("delayMs").getAsLong() : 0;
            actions.add(new MacroAction(type, kc, dm));
        }
        macro.setActions(actions);
        return macro;
    }

    private void shutdown() {
        if (hookActive) hook.stop();
        if (bridgeServer != null) bridgeServer.stop(0);
        profileManager.saveSync();
        dispose();
        System.exit(0);
    }
}
