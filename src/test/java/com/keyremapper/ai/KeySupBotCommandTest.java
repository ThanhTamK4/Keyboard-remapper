package com.keyremapper.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeySupBotCommandTest {

    private MockActions actions;
    private KeySupBot bot;

    @BeforeEach
    void setUp() {
        actions = new MockActions();
        bot = new KeySupBot(actions);
    }

    // ================================================================
    //  Regex fallback tests (offline mode)
    // ================================================================

    @Test
    void regex_mapKey() {
        String result = bot.process("map CapsLock to Escape");
        assertTrue(actions.calls.stream().anyMatch(c -> c.startsWith("mapKey(")));
    }

    @Test
    void regex_swapKeys() {
        bot.process("swap A and B");
        assertTrue(actions.calls.stream().anyMatch(c -> c.startsWith("swapKeys(")));
    }

    @Test
    void regex_createProfile() {
        bot.process("create profile Gaming");
        assertTrue(actions.calls.contains("createProfile(Gaming)"));
    }

    @Test
    void regex_deleteProfile() {
        bot.process("delete profile Gaming");
        assertTrue(actions.calls.contains("deleteProfile(Gaming)"));
    }

    @Test
    void regex_renameProfile() {
        bot.process("rename profile Old to New");
        assertTrue(actions.calls.contains("renameProfile(Old, New)"));
    }

    @Test
    void regex_listProfiles() {
        bot.process("list profiles");
        assertTrue(actions.calls.contains("listProfiles()"));
    }

    @Test
    void regex_switchProfile() {
        bot.process("switch to profile Gaming");
        assertTrue(actions.calls.contains("switchProfile(Gaming)"));
    }

    @Test
    void regex_apply() {
        bot.process("apply");
        assertTrue(actions.calls.contains("apply()"));
    }

    @Test
    void regex_restore() {
        bot.process("restore");
        assertTrue(actions.calls.contains("restore()"));
    }

    @Test
    void regex_clearMappings() {
        bot.process("clear mappings");
        assertTrue(actions.calls.contains("clearMappings()"));
    }

    @Test
    void regex_showMappings() {
        bot.process("show mappings");
        assertTrue(actions.calls.contains("showMappings()"));
    }

    @Test
    void regex_removeMappingForKey() {
        bot.process("remove mapping for CapsLock");
        assertTrue(actions.calls.stream().anyMatch(c -> c.startsWith("removeMapping(")));
    }

    // ---- Unknown key ----

    @Test
    void regex_unknownKey() {
        String result = bot.process("map FooBar to Escape");
        assertTrue(result.contains("don't recognise") || result.contains("Unknown") || result.contains("not sure"),
                "Should report unknown key, got: " + result);
    }

    // ---- Conversational ----

    @Test
    void regex_help() {
        String result = bot.process("help");
        assertTrue(result.contains("Profile Management") || result.contains("profile"),
                "Help should mention profiles");
    }

    @Test
    void regex_greeting() {
        String result = bot.process("hello");
        assertTrue(result.toLowerCase().contains("help") || result.toLowerCase().contains("hey") ||
                   result.toLowerCase().contains("hello") || result.toLowerCase().contains("hi"),
                "Should respond to greeting");
    }

    @Test
    void regex_thanks() {
        String result = bot.process("thanks");
        assertTrue(result.toLowerCase().contains("welcome") || result.toLowerCase().contains("happy") ||
                   result.toLowerCase().contains("anytime"));
    }

    @Test
    void regex_emptyInput() {
        String result = bot.process("");
        assertTrue(result.contains("type a message") || result.contains("Please"));
    }

    @Test
    void regex_unknownInput() {
        String result = bot.process("xyzzy foobar baz");
        assertTrue(result.contains("help") || result.contains("not sure") || result.contains("couldn't parse"),
                "Should suggest help for unknown input");
    }

    // ================================================================
    //  Mock Actions
    // ================================================================

    static class MockActions implements KeySupBot.Actions {
        final List<String> calls = new ArrayList<>();

        @Override public String createProfile(String name) {
            calls.add("createProfile(" + name + ")");
            return "Created " + name;
        }
        @Override public String deleteProfile(String id) {
            calls.add("deleteProfile(" + id + ")");
            return "Deleted " + id;
        }
        @Override public String renameProfile(String id, String newName) {
            calls.add("renameProfile(" + id + ", " + newName + ")");
            return "Renamed";
        }
        @Override public String listProfiles() {
            calls.add("listProfiles()");
            return "1. Profile 1 (active)";
        }
        @Override public String switchProfile(String id) {
            calls.add("switchProfile(" + id + ")");
            return "Switched to " + id;
        }
        @Override public String mapKey(int from, int to) {
            calls.add("mapKey(" + from + ", " + to + ")");
            return "Mapped";
        }
        @Override public String swapKeys(int a, int b) {
            calls.add("swapKeys(" + a + ", " + b + ")");
            return "Swapped";
        }
        @Override public String removeMapping(int from) {
            calls.add("removeMapping(" + from + ")");
            return "Removed";
        }
        @Override public String clearMappings() {
            calls.add("clearMappings()");
            return "Cleared";
        }
        @Override public String showMappings() {
            calls.add("showMappings()");
            return "No mappings";
        }
        @Override public String apply() {
            calls.add("apply()");
            return "Applied";
        }
        @Override public String restore() {
            calls.add("restore()");
            return "Restored";
        }
        @Override public String createMacro(String name) {
            calls.add("createMacro(" + name + ")");
            return "Created macro " + name;
        }
        @Override public String deleteMacro(String name) {
            calls.add("deleteMacro(" + name + ")");
            return "Deleted macro";
        }
        @Override public String listMacros() {
            calls.add("listMacros()");
            return "No macros";
        }
        @Override public String addMacroKeyPress(String macroName, int vk) {
            calls.add("addMacroKeyPress(" + macroName + ", " + vk + ")");
            return "Added key";
        }
        @Override public String clearMacro(String macroName) {
            calls.add("clearMacro(" + macroName + ")");
            return "Cleared macro";
        }
    }
}
