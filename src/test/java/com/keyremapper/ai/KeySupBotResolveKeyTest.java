package com.keyremapper.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
class KeySupBotResolveKeyTest {

    // ---- Single character: letters ----

    @Test
    void resolveKey_singleLetters() {
        assertEquals(0x41, KeySupBot.resolveKey("a"));
        assertEquals(0x41, KeySupBot.resolveKey("A"));
        assertEquals(0x5A, KeySupBot.resolveKey("z"));
        assertEquals(0x5A, KeySupBot.resolveKey("Z"));
    }

    @Test
    void resolveKey_allLetters() {
        for (char c = 'A'; c <= 'Z'; c++) {
            assertEquals((int) c, KeySupBot.resolveKey(String.valueOf(c)));
            assertEquals((int) c, KeySupBot.resolveKey(String.valueOf(Character.toLowerCase(c))));
        }
    }

    // ---- Single character: digits ----

    @Test
    void resolveKey_digits() {
        for (char c = '0'; c <= '9'; c++) {
            assertEquals((int) c, KeySupBot.resolveKey(String.valueOf(c)));
        }
    }

    // ---- Single character: punctuation ----

    @Test
    void resolveKey_punctuation() {
        assertEquals(0xBD, KeySupBot.resolveKey("-"));
        assertEquals(0xBB, KeySupBot.resolveKey("="));
        assertEquals(0xDB, KeySupBot.resolveKey("["));
        assertEquals(0xDD, KeySupBot.resolveKey("]"));
        assertEquals(0xDC, KeySupBot.resolveKey("\\"));
        assertEquals(0xBA, KeySupBot.resolveKey(";"));
        assertEquals(0xDE, KeySupBot.resolveKey("'"));
        assertEquals(0xC0, KeySupBot.resolveKey("`"));
        assertEquals(0xBF, KeySupBot.resolveKey("/"));
        assertEquals(0xBC, KeySupBot.resolveKey(","));
        assertEquals(0xBE, KeySupBot.resolveKey("."));
    }

    // ---- Named keys: common ----

    @ParameterizedTest
    @CsvSource({
        "Escape, 0x1B",
        "Esc, 0x1B",
        "Enter, 0x0D",
        "Return, 0x0D",
        "Tab, 0x09",
        "Space, 0x20",
        "Spacebar, 0x20",
        "Backspace, 0x08",
        "Bksp, 0x08",
        "Delete, 0x2E",
        "Del, 0x2E",
        "Insert, 0x2D",
        "Ins, 0x2D",
        "Home, 0x24",
        "End, 0x23",
        "PageUp, 0x21",
        "PgUp, 0x21",
        "PageDown, 0x22",
        "PgDn, 0x22",
        "CapsLock, 0x14",
        "Caps, 0x14",
        "NumLock, 0x90",
        "ScrollLock, 0x91",
        "PrintScreen, 0x2C",
        "PrtSc, 0x2C",
        "Pause, 0x13",
        "Break, 0x13"
    })
    void resolveKey_namedKeys(String input, String expectedHex) {
        int expected = Integer.decode(expectedHex);
        assertEquals(expected, KeySupBot.resolveKey(input));
    }

    // ---- Named keys: modifiers ----

    @ParameterizedTest
    @CsvSource({
        "Shift, 0xA0",
        "LShift, 0xA0",
        "LeftShift, 0xA0",
        "RShift, 0xA1",
        "RightShift, 0xA1",
        "Ctrl, 0xA2",
        "Control, 0xA2",
        "LCtrl, 0xA2",
        "RCtrl, 0xA3",
        "RightCtrl, 0xA3",
        "Alt, 0xA4",
        "LAlt, 0xA4",
        "RAlt, 0xA5",
        "RightAlt, 0xA5",
        "Win, 0x5B",
        "Windows, 0x5B",
        "Super, 0x5B",
        "RWin, 0x5C",
        "Menu, 0x5D",
        "App, 0x5D"
    })
    void resolveKey_modifiers(String input, String expectedHex) {
        int expected = Integer.decode(expectedHex);
        assertEquals(expected, KeySupBot.resolveKey(input));
    }

    // ---- Named keys: arrows ----

    @ParameterizedTest
    @CsvSource({
        "Left, 0x25",
        "LeftArrow, 0x25",
        "ArrowLeft, 0x25",
        "Up, 0x26",
        "UpArrow, 0x26",
        "ArrowUp, 0x26",
        "Right, 0x27",
        "RightArrow, 0x27",
        "ArrowRight, 0x27",
        "Down, 0x28",
        "DownArrow, 0x28",
        "ArrowDown, 0x28"
    })
    void resolveKey_arrows(String input, String expectedHex) {
        int expected = Integer.decode(expectedHex);
        assertEquals(expected, KeySupBot.resolveKey(input));
    }

    // ---- F-keys ----

    @Test
    void resolveKey_fKeys() {
        assertEquals(0x70, KeySupBot.resolveKey("F1"));
        assertEquals(0x71, KeySupBot.resolveKey("F2"));
        assertEquals(0x7B, KeySupBot.resolveKey("F12"));
        assertEquals(0x70, KeySupBot.resolveKey("f1"));
    }

    // ---- Numpad keys ----

    @Test
    void resolveKey_numpad() {
        assertEquals(0x60, KeySupBot.resolveKey("Num0"));
        assertEquals(0x60, KeySupBot.resolveKey("Numpad0"));
        assertEquals(0x69, KeySupBot.resolveKey("Num9"));
        assertEquals(0x69, KeySupBot.resolveKey("Numpad9"));
    }

    // ---- Numpad operators (regex path) ----

    @Test
    void resolveKey_numpadOperators() {
        assertEquals(0x6B, KeySupBot.resolveKey("Num+"));
        assertEquals(0x6B, KeySupBot.resolveKey("Numpad +"));
        assertEquals(0x6D, KeySupBot.resolveKey("Num-"));
        assertEquals(0x6D, KeySupBot.resolveKey("Numpad -"));
        assertEquals(0x6A, KeySupBot.resolveKey("Num*"));
        assertEquals(0x6A, KeySupBot.resolveKey("Numpad *"));
        assertEquals(0x6F, KeySupBot.resolveKey("Num/"));
        assertEquals(0x6E, KeySupBot.resolveKey("Num."));
    }

    // ---- Normalization: spaces, underscores, hyphens ----

    @Test
    void resolveKey_normalizesSpaces() {
        assertEquals(0x14, KeySupBot.resolveKey("Caps Lock"));
        assertEquals(0x14, KeySupBot.resolveKey("caps lock"));
    }

    @Test
    void resolveKey_normalizesUnderscores() {
        assertEquals(0x14, KeySupBot.resolveKey("caps_lock"));
        assertEquals(0x91, KeySupBot.resolveKey("scroll_lock"));
    }

    @Test
    void resolveKey_normalizesHyphens() {
        assertEquals(0x14, KeySupBot.resolveKey("caps-lock"));
    }

    // ---- Whitespace trimming ----

    @Test
    void resolveKey_trimming() {
        assertEquals(0x14, KeySupBot.resolveKey("  CapsLock  "));
        assertEquals(0x41, KeySupBot.resolveKey("  A  "));
    }

    // ---- Empty / unknown -> -1 ----

    @Test
    void resolveKey_emptyReturnsNegative() {
        assertEquals(-1, KeySupBot.resolveKey(""));
        assertEquals(-1, KeySupBot.resolveKey("   "));
    }

    @Test
    void resolveKey_unknownReturnsNegative() {
        assertEquals(-1, KeySupBot.resolveKey("nonexistent"));
        assertEquals(-1, KeySupBot.resolveKey("FooBar"));
        assertEquals(-1, KeySupBot.resolveKey("F99"));
    }
}
