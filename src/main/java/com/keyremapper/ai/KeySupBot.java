package com.keyremapper.ai;

import com.keyremapper.util.KeyUtils;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyboard assistant that uses Ollama for natural-language understanding
 * and falls back to rule-based regex parsing when Ollama is unavailable.
 */
public class KeySupBot {

    /* ================================================================== */
    /*  Actions interface — implemented by MainFrame                      */
    /* ================================================================== */

    public interface Actions {
        // Profile commands
        String createProfile(String name);
        String deleteProfile(String identifier);
        String renameProfile(String identifier, String newName);
        String listProfiles();
        String switchProfile(String identifier);
        // Key mapping commands
        String mapKey(int fromVk, int toVk);
        String swapKeys(int vk1, int vk2);
        String removeMapping(int fromVk);
        String clearMappings();
        String showMappings();
        String apply();
        String restore();
        // Macro commands
        String createMacro(String name);
        String deleteMacro(String name);
        String listMacros();
        String addMacroKeyPress(String macroName, int vk);
        String clearMacro(String macroName);
    }

    private static final int MAX_HISTORY = 20;
    private static final Pattern CMD_PATTERN =
            Pattern.compile("`?\\[CMD:(\\w+)\\(([^)]*)\\)]`?");

    private final Actions actions;
    private final OllamaClient ollama;
    private final List<OllamaClient.Message> history = Collections.synchronizedList(new ArrayList<>());
    private final Random rng = new Random();
    private volatile boolean ollamaAvailable;

    public KeySupBot(Actions actions) {
        this.actions = actions;
        this.ollama = new OllamaClient();
        seedFewShotExamples();
    }

    /** Pre-loads a few-shot exchange so the model sees the command format in action. */
    private void seedFewShotExamples() {
        history.add(new OllamaClient.Message("user", "Create a profile called Gaming"));
        history.add(new OllamaClient.Message("assistant",
                "Sure! I'll create a Gaming profile for you.\n[CMD:create_profile(Gaming)]\nDone!"));
        history.add(new OllamaClient.Message("user", "Map CapsLock to Escape"));
        history.add(new OllamaClient.Message("assistant",
                "I'll remap CapsLock to Escape for you.\n[CMD:map_key(CapsLock, Escape)]\nAll set! Click Apply or type \"apply\" to activate it."));
        history.add(new OllamaClient.Message("user", "Thanks, now clear those and delete that profile"));
        history.add(new OllamaClient.Message("assistant",
                "No problem! I'll clear the mappings and delete the Gaming profile.\n[CMD:clear_mappings()]\n[CMD:delete_profile(Gaming)]\nDone! Everything is cleaned up."));
    }

    /* ================================================================== */
    /*  Availability                                                      */
    /* ================================================================== */

    /** Pings Ollama in the background; calls back on completion. */
    public void checkAvailability(Consumer<Boolean> callback) {
        new Thread(() -> {
            ollamaAvailable = ollama.isAvailable();
            if (callback != null) callback.accept(ollamaAvailable);
        }, "OllamaCheck").start();
    }

    public boolean isOllamaAvailable() { return ollamaAvailable; }
    public String getModelName() { return ollama.getModel(); }

    /* ================================================================== */
    /*  Greeting                                                          */
    /* ================================================================== */

    public String getGreeting() {
        return "Hi! I'm KeySup, your keyboard assistant.\n\n" +
                "I can help you with:\n" +
                "  \u2022 Create / delete / rename profiles\n" +
                "  \u2022 Remap keys (e.g. \"map CapsLock to Escape\")\n" +
                "  \u2022 Answer general questions\n\n" +
                "Just type naturally or say \"help\" for commands.";
    }

    /* ================================================================== */
    /*  Async Ollama processing (primary path)                            */
    /* ================================================================== */

    /**
     * Processes the user's message asynchronously via Ollama.
     * Tokens stream through {@code onToken}; the final processed text
     * (with commands executed) is delivered through {@code onDone}.
     * Falls back to regex if Ollama fails.
     */
    public void processAsync(String input,
                             Consumer<String> onToken,
                             Consumer<String> onDone) {
        history.add(new OllamaClient.Message("user", input));
        trimHistory();

        new Thread(() -> {
            try {
                List<OllamaClient.Message> messages = new ArrayList<>();
                messages.add(new OllamaClient.Message("system", buildSystemPrompt()));
                synchronized (history) {
                    messages.addAll(history);
                }

                String rawResponse = ollama.streamChat(messages, onToken);

                history.add(new OllamaClient.Message("assistant", rawResponse));
                trimHistory();

                // Execute commands on the EDT (they modify Swing state)
                final String[] finalText = { rawResponse };
                if (CMD_PATTERN.matcher(rawResponse).find()) {
                    try {
                        SwingUtilities.invokeAndWait(
                                () -> finalText[0] = executeCommands(rawResponse));
                    } catch (Exception e) {
                        finalText[0] = rawResponse;
                    }
                }

                if (onDone != null) onDone.accept(finalText[0]);

            } catch (Exception e) {
                // Ollama failed — fall back to regex processing
                ollamaAvailable = false;
                history.remove(history.size() - 1); // remove the user message we added

                String fallback = process(input);
                if (onDone != null)
                    onDone.accept(fallback + "\n\n(Ollama unavailable \u2014 using offline mode)");
            }
        }, "KeySupAsync").start();
    }

    /* ================================================================== */
    /*  System prompt                                                     */
    /* ================================================================== */

    private String buildSystemPrompt() {
        String state;
        try {
            state = actions.listProfiles() + "\n" + actions.showMappings() +
                    "\n" + actions.listMacros();
        } catch (Exception e) {
            state = "(unable to read state)";
        }

        return "You are KeySup, a friendly AI keyboard assistant built into the Key Remapper app.\n" +
                "You can DIRECTLY CONTROL the app by including command tags in your responses.\n" +
                "You also answer general questions as a helpful chatbot.\n\n" +

                "## HOW TO EXECUTE COMMANDS\n" +
                "When the user asks you to do something in the app, you MUST include the command on its own line:\n" +
                "[CMD:function_name(arguments)]\n\n" +
                "The app will execute it automatically and show the result to the user.\n\n" +

                "## AVAILABLE COMMANDS\n" +
                "[CMD:create_profile(name)] - Create a new profile\n" +
                "[CMD:delete_profile(name)] - Delete a profile\n" +
                "[CMD:rename_profile(old_name, new_name)] - Rename a profile\n" +
                "[CMD:list_profiles()] - Show all profiles\n" +
                "[CMD:switch_profile(name)] - Switch active profile\n" +
                "[CMD:map_key(from_key, to_key)] - Remap a key\n" +
                "[CMD:swap_keys(key1, key2)] - Swap two keys\n" +
                "[CMD:remove_mapping(key)] - Remove a remapping\n" +
                "[CMD:clear_mappings()] - Clear all remappings\n" +
                "[CMD:show_mappings()] - Show current remappings\n" +
                "[CMD:apply()] - Apply remappings system-wide\n" +
                "[CMD:restore()] - Stop remapping and restore defaults\n" +
                "[CMD:create_macro(name)] - Create a new macro\n" +
                "[CMD:delete_macro(name)] - Delete a macro\n" +
                "[CMD:list_macros()] - Show all macros\n" +
                "[CMD:add_macro_key(macro_name, key)] - Add a key press (down+up) to a macro\n" +
                "[CMD:clear_macro(name)] - Clear all actions from a macro\n\n" +

                "## KEY NAMES TO USE\n" +
                "Letters: A-Z | Numbers: 0-9 | Function: F1-F12\n" +
                "Escape, CapsLock, Space, Enter, Tab, Backspace, Delete, Insert\n" +
                "Home, End, PageUp, PageDown, Left, Right, Up, Down\n" +
                "Shift, Ctrl, Alt, LShift, RShift, LCtrl, RCtrl, LAlt, RAlt\n" +
                "Win, PrintScreen, ScrollLock, Pause, NumLock, Num0-Num9, App\n\n" +

                "## EXAMPLES\n" +
                "User: \"remap caps lock to escape\"\n" +
                "You: Sure! I'll remap that for you.\n[CMD:map_key(CapsLock, Escape)]\nDone! Type \"apply\" to activate.\n\n" +
                "User: \"swap A and B keys and apply\"\n" +
                "You: Swapping A and B and applying!\n[CMD:swap_keys(A, B)]\n[CMD:apply()]\nAll set!\n\n" +
                "User: \"make a new profile for work\"\n" +
                "You: Creating a Work profile now.\n[CMD:create_profile(Work)]\nYour new profile is ready!\n\n" +
                "User: \"create a macro called Greet that types hi\"\n" +
                "You: I'll create a Greet macro and add the keys for you.\n[CMD:create_macro(Greet)]\n[CMD:add_macro_key(Greet, H)]\n[CMD:add_macro_key(Greet, I)]\nYour Greet macro is ready!\n\n" +
                "User: \"what's the weather like?\"\n" +
                "You: (just answer normally, no command needed)\n\n" +

                "## RULES\n" +
                "1. ALWAYS include [CMD:...] when the user asks to perform an app action. Never just describe what you would do.\n" +
                "2. You can include multiple commands in one response.\n" +
                "3. Keep responses concise and friendly.\n" +
                "4. For non-keyboard questions, just chat normally without commands.\n\n" +

                "## CURRENT APP STATE\n" + state;
    }

    /* ================================================================== */
    /*  Command parsing & execution                                       */
    /* ================================================================== */

    private String executeCommands(String response) {
        Matcher m = CMD_PATTERN.matcher(response);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String func = m.group(1);
            String args = m.group(2).trim();
            String result = dispatchCommand(func, args);
            m.appendReplacement(sb, Matcher.quoteReplacement("\u2705 " + result));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String dispatchCommand(String func, String args) {
        try {
            switch (func) {
                case "create_profile":  return actions.createProfile(args);
                case "delete_profile":  return actions.deleteProfile(args);
                case "rename_profile": {
                    String[] p = args.split(",\\s*", 2);
                    if (p.length < 2) return "rename needs old and new name.";
                    return actions.renameProfile(p[0].trim(), p[1].trim());
                }
                case "list_profiles":   return actions.listProfiles();
                case "switch_profile":  return actions.switchProfile(args);
                case "map_key": {
                    String[] p = args.split(",\\s*", 2);
                    if (p.length < 2) return "map_key needs from and to keys.";
                    int from = resolveKey(p[0].trim()), to = resolveKey(p[1].trim());
                    if (from < 0) return "Unknown key: " + p[0].trim();
                    if (to   < 0) return "Unknown key: " + p[1].trim();
                    return actions.mapKey(from, to);
                }
                case "swap_keys": {
                    String[] p = args.split(",\\s*", 2);
                    if (p.length < 2) return "swap_keys needs two keys.";
                    int a = resolveKey(p[0].trim()), b = resolveKey(p[1].trim());
                    if (a < 0) return "Unknown key: " + p[0].trim();
                    if (b < 0) return "Unknown key: " + p[1].trim();
                    return actions.swapKeys(a, b);
                }
                case "remove_mapping": {
                    int vk = resolveKey(args);
                    if (vk < 0) return "Unknown key: " + args;
                    return actions.removeMapping(vk);
                }
                case "clear_mappings":  return actions.clearMappings();
                case "show_mappings":   return actions.showMappings();
                case "apply":           return actions.apply();
                case "restore":         return actions.restore();
                case "create_macro":    return actions.createMacro(args);
                case "delete_macro":    return actions.deleteMacro(args);
                case "list_macros":     return actions.listMacros();
                case "add_macro_key": {
                    String[] p = args.split(",\\s*", 2);
                    if (p.length < 2) return "add_macro_key needs macro name and key.";
                    int vk = resolveKey(p[1].trim());
                    if (vk < 0) return "Unknown key: " + p[1].trim();
                    return actions.addMacroKeyPress(p[0].trim(), vk);
                }
                case "clear_macro":     return actions.clearMacro(args);
                default:                return "Unknown command: " + func;
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private void trimHistory() {
        synchronized (history) {
            while (history.size() > MAX_HISTORY) history.remove(0);
        }
    }

    /* ================================================================== */
    /*  Synchronous regex fallback (offline mode)                         */
    /* ================================================================== */

    public String process(String raw) {
        String input = raw.trim();
        if (input.isEmpty()) return "Please type a message!";

        Matcher m;

        if (test(input, "(?i)^(help|commands?|what can you do|how to use).*"))
            return helpText();

        m = find(input, "(?i)(?:create|add|make|new)\\s+(?:a\\s+)?profile\\s+(?:named?\\s+)?[\"']?(.+?)[\"']?$");
        if (m != null) return actions.createProfile(m.group(1).trim());

        m = find(input, "(?i)(?:delete|remove)\\s+profile\\s+[\"']?(.+?)[\"']?$");
        if (m != null) return actions.deleteProfile(m.group(1).trim());

        m = find(input, "(?i)rename\\s+profile\\s+[\"']?(.+?)[\"']?\\s+to\\s+[\"']?(.+?)[\"']?$");
        if (m != null) return actions.renameProfile(m.group(1).trim(), m.group(2).trim());

        if (test(input, "(?i)(?:list|show|display)\\s+(?:all\\s+)?profiles?"))
            return actions.listProfiles();

        m = find(input, "(?i)(?:switch|change|use|select|load|activate)\\s+(?:to\\s+)?profile\\s+[\"']?(.+?)[\"']?$");
        if (m != null) return actions.switchProfile(m.group(1).trim());

        if (test(input, "(?i).*(?:current|active|which)\\s+profile.*"))
            return actions.listProfiles();

        m = find(input, "(?i)(?:swap|switch|exchange)\\s+(?:keys?\\s+)?[\"']?(.+?)[\"']?\\s+(?:and|with|&)\\s+[\"']?(.+?)[\"']?$");
        if (m != null) {
            int a = resolveKey(m.group(1)), b = resolveKey(m.group(2));
            if (a < 0) return unknown(m.group(1));
            if (b < 0) return unknown(m.group(2));
            return actions.swapKeys(a, b);
        }

        if (test(input, "(?i)(?:clear|reset|remove\\s+all)\\s+(?:all\\s+)?mappings?"))
            return actions.clearMappings();

        m = find(input, "(?i)(?:map|remap|change|set|bind|assign)\\s+(?:key\\s+)?[\"']?(.+?)[\"']?\\s+(?:to|->|\u2192|=>)\\s+(?:key\\s+)?[\"']?(.+?)[\"']?$");
        if (m != null) {
            int from = resolveKey(m.group(1)), to = resolveKey(m.group(2));
            if (from < 0) return unknown(m.group(1));
            if (to < 0) return unknown(m.group(2));
            return actions.mapKey(from, to);
        }

        m = find(input, "(?i)(?:remove|delete|unmap|unbind)\\s+(?:mapping\\s+(?:for|of)\\s+)?(?:key\\s+)?[\"']?(.+?)[\"']?$");
        if (m != null) {
            String g = m.group(1);
            if (!g.matches("(?i)(?:all\\s+)?mappings?|profile.*")) {
                int vk = resolveKey(g);
                if (vk < 0) return unknown(g);
                return actions.removeMapping(vk);
            }
        }

        if (test(input, "(?i)(?:show|list|display|what are)\\s+(?:the\\s+)?(?:current\\s+)?mappings?"))
            return actions.showMappings();

        if (test(input, "(?i)(?:apply|activate|enable|start|turn\\s+on)(?:\\s+(?:the\\s+)?mappings?)?$"))
            return actions.apply();

        if (test(input, "(?i)(?:restore|deactivate|disable|stop|turn\\s+off)(?:\\s+(?:defaults?|mappings?))?$"))
            return actions.restore();

        // General conversation fallback
        if (test(input, "(?i)^(hi|hello|hey|howdy|yo|sup|greetings)\\b.*"))
            return pick("Hey there! How can I help you with your keyboard?",
                    "Hello! Need help with your keyboard setup?",
                    "Hi! What would you like to do?");

        if (test(input, "(?i).*(thank|thanks|thx|ty).*"))
            return pick("You're welcome!", "Happy to help!", "Anytime!");

        if (test(input, "(?i)^(bye|goodbye|see\\s*you|cya|later).*"))
            return pick("Goodbye! Happy typing!", "See you later!", "Bye!");

        if (test(input, "(?i).*(how\\s+are\\s+you|how.s\\s+it\\s+going).*"))
            return "I'm doing great, ready to help with your keyboard!";

        if (test(input, "(?i).*(who|what)\\s+are\\s+you.*"))
            return "I'm KeySup, a keyboard support assistant built into this app. " +
                    "I help you manage profiles and remap keys. Type \"help\" for commands!";

        if (test(input, "(?i).*what.?s?\\s+(?:the\\s+)?time.*"))
            return "It's " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")) + ".";

        if (test(input, "(?i).*(?:what.?s?\\s+(?:the\\s+)?date|today).*"))
            return "Today is " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) + ".";

        if (test(input, "(?i).*(?:joke|funny).*"))
            return pick(
                    "Why did the keyboard break up with the mouse? It wasn't her type.",
                    "What's a keyboard's favourite snack? Space bars.",
                    "I'd tell you a UDP joke, but you might not get it.");

        if (test(input, "(?i).*(?:meaning of life|42).*"))
            return "42, obviously.";

        return pick(
                "Hmm, I'm not sure about that. Type \"help\" to see what I can do!",
                "I didn't quite get that. Try \"help\" for available commands.",
                "That's beyond my keyboard expertise! Type \"help\" for a list of commands.",
                "Sorry, I couldn't parse that. Try something like \"map A to B\" or \"help\".");
    }

    /* ================================================================== */
    /*  Regex helpers                                                     */
    /* ================================================================== */

    private static final java.util.Map<String, Pattern> PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean test(String input, String regex) {
        return PATTERN_CACHE.computeIfAbsent(regex, Pattern::compile).matcher(input).matches();
    }

    private static Matcher find(String input, String regex) {
        Matcher m = PATTERN_CACHE.computeIfAbsent(regex, Pattern::compile).matcher(input);
        return m.find() ? m : null;
    }

    private String pick(String... opts) {
        return opts[rng.nextInt(opts.length)];
    }

    private static String unknown(String name) {
        return "I don't recognise the key \"" + name + "\". " +
                "Try names like A, CapsLock, Escape, F1, Space, etc.";
    }

    private String helpText() {
        return "Here's what I can do:\n\n" +
                "Profile Management:\n" +
                "  create profile [name]\n" +
                "  delete profile [name]\n" +
                "  rename profile [old] to [new]\n" +
                "  list profiles\n" +
                "  switch to profile [name]\n\n" +
                "Key Remapping:\n" +
                "  map [key] to [key]\n" +
                "  swap [key] and [key]\n" +
                "  remove mapping [key]\n" +
                "  clear mappings\n" +
                "  show mappings\n" +
                "  apply  /  restore\n\n" +
                "Key names: A\u2013Z, 0\u20139, F1\u2013F12, Escape, CapsLock,\n" +
                "Space, Enter, Tab, Backspace, Shift, Ctrl, Alt,\n" +
                "Delete, Insert, Home, End, PageUp, PageDown,\n" +
                "Left, Right, Up, Down, and more.";
    }

    /* ================================================================== */
    /*  Key-name resolver  (user-friendly string -> Windows VK code)      */
    /* ================================================================== */

    public static int resolveKey(String name) {
        name = name.trim();
        if (name.isEmpty()) return -1;

        if (name.length() == 1) {
            char c = Character.toUpperCase(name.charAt(0));
            if (c >= 'A' && c <= 'Z') return c;
            if (c >= '0' && c <= '9') return c;
            switch (c) {
                case '-': return 0xBD; case '=': return 0xBB;
                case '[': return 0xDB; case ']': return 0xDD;
                case '\\': return 0xDC; case ';': return 0xBA;
                case '\'': return 0xDE; case '`': return 0xC0;
                case ',': return 0xBC; case '.': return 0xBE;
                case '/': return 0xBF;
            }
        }

        String lo = name.trim().toLowerCase();
        if (lo.matches("num(?:pad)?\\s*\\+"))  return 0x6B;
        if (lo.matches("num(?:pad)?\\s*-"))    return 0x6D;
        if (lo.matches("num(?:pad)?\\s*\\*"))  return 0x6A;
        if (lo.matches("num(?:pad)?\\s*/"))    return 0x6F;
        if (lo.matches("num(?:pad)?\\s*\\."))  return 0x6E;

        String k = lo.replace(" ", "").replace("_", "").replace("-", "");

        switch (k) {
            case "escape": case "esc":               return 0x1B;
            case "enter": case "return":             return 0x0D;
            case "tab":                              return 0x09;
            case "space": case "spacebar":           return 0x20;
            case "backspace": case "bksp": case "back": return 0x08;
            case "delete": case "del":               return 0x2E;
            case "insert": case "ins":               return 0x2D;
            case "home":                             return 0x24;
            case "end":                              return 0x23;
            case "pageup": case "pgup":              return 0x21;
            case "pagedown": case "pgdn":            return 0x22;
            case "capslock": case "caps":            return 0x14;
            case "numlock": case "numlk":            return 0x90;
            case "scrolllock": case "scrlk":         return 0x91;
            case "printscreen": case "prtsc":        return 0x2C;
            case "pause": case "break":              return 0x13;

            case "shift": case "lshift": case "leftshift":   return 0xA0;
            case "rshift": case "rightshift":                 return 0xA1;
            case "ctrl": case "control": case "lctrl": case "leftctrl": return 0xA2;
            case "rctrl": case "rightctrl":                   return 0xA3;
            case "alt": case "lalt": case "leftalt":          return 0xA4;
            case "ralt": case "rightalt":                     return 0xA5;
            case "win": case "windows": case "lwin": case "super": return 0x5B;
            case "rwin": case "rightwin":                     return 0x5C;
            case "menu": case "app": case "contextmenu":      return 0x5D;

            case "left": case "leftarrow": case "arrowleft":   return 0x25;
            case "up": case "uparrow": case "arrowup":         return 0x26;
            case "right": case "rightarrow": case "arrowright": return 0x27;
            case "down": case "downarrow": case "arrowdown":   return 0x28;

            case "f1":  return 0x70; case "f2":  return 0x71;
            case "f3":  return 0x72; case "f4":  return 0x73;
            case "f5":  return 0x74; case "f6":  return 0x75;
            case "f7":  return 0x76; case "f8":  return 0x77;
            case "f9":  return 0x78; case "f10": return 0x79;
            case "f11": return 0x7A; case "f12": return 0x7B;

            case "num0": case "numpad0": return 0x60;
            case "num1": case "numpad1": return 0x61;
            case "num2": case "numpad2": return 0x62;
            case "num3": case "numpad3": return 0x63;
            case "num4": case "numpad4": return 0x64;
            case "num5": case "numpad5": return 0x65;
            case "num6": case "numpad6": return 0x66;
            case "num7": case "numpad7": return 0x67;
            case "num8": case "numpad8": return 0x68;
            case "num9": case "numpad9": return 0x69;
        }
        return -1;
    }
}
