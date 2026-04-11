package com.keyremapper.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

class KeyUtilsTest {

    // ---- getKeyLabel: Letters ----

    @Test
    void getKeyLabel_letters() {
        for (int vk = 0x41; vk <= 0x5A; vk++) {
            assertEquals(String.valueOf((char) vk), KeyUtils.getKeyLabel(vk));
        }
    }

    // ---- getKeyLabel: Digits ----

    @Test
    void getKeyLabel_digits() {
        for (int vk = 0x30; vk <= 0x39; vk++) {
            assertEquals(String.valueOf((char) vk), KeyUtils.getKeyLabel(vk));
        }
    }

    // ---- getKeyLabel: F-keys ----

    @Test
    void getKeyLabel_fKeys() {
        assertEquals("F1", KeyUtils.getKeyLabel(0x70));
        assertEquals("F12", KeyUtils.getKeyLabel(0x7B));
        assertEquals("F24", KeyUtils.getKeyLabel(0x87));
    }

    // ---- getKeyLabel: Numpad ----

    @Test
    void getKeyLabel_numpad() {
        assertEquals("Num 0", KeyUtils.getKeyLabel(0x60));
        assertEquals("Num 9", KeyUtils.getKeyLabel(0x69));
    }

    // ---- getKeyLabel: Special keys ----

    @ParameterizedTest
    @CsvSource({
        "0x08, Bksp",
        "0x09, Tab",
        "0x0D, Enter",
        "0x13, Pause",
        "0x14, Caps",
        "0x1B, Esc",
        "0x20, Space",
        "0x21, PgUp",
        "0x22, PgDn",
        "0x23, End",
        "0x24, Home",
        "0x2C, PrtSc",
        "0x2D, Ins",
        "0x2E, Del",
        "0x5B, LWin",
        "0x5D, App",
        "0x90, NumLk",
        "0x91, ScrLk",
        "0xA0, LShift",
        "0xA1, RShift",
        "0xA2, LCtrl",
        "0xA3, RCtrl",
        "0xA4, LAlt",
        "0xA5, RAlt"
    })
    void getKeyLabel_specialKeys(String vkHex, String expected) {
        int vk = Integer.decode(vkHex);
        assertEquals(expected, KeyUtils.getKeyLabel(vk));
    }

    // ---- getKeyLabel: Numpad operators ----

    @Test
    void getKeyLabel_numpadOperators() {
        assertEquals("Num *", KeyUtils.getKeyLabel(0x6A));
        assertEquals("Num +", KeyUtils.getKeyLabel(0x6B));
        assertEquals("Num -", KeyUtils.getKeyLabel(0x6D));
        assertEquals("Num .", KeyUtils.getKeyLabel(0x6E));
        assertEquals("Num /", KeyUtils.getKeyLabel(0x6F));
    }

    // ---- getKeyLabel: Punctuation ----

    @Test
    void getKeyLabel_punctuation() {
        assertEquals(";", KeyUtils.getKeyLabel(0xBA));
        assertEquals("=", KeyUtils.getKeyLabel(0xBB));
        assertEquals(",", KeyUtils.getKeyLabel(0xBC));
        assertEquals("-", KeyUtils.getKeyLabel(0xBD));
        assertEquals(".", KeyUtils.getKeyLabel(0xBE));
        assertEquals("/", KeyUtils.getKeyLabel(0xBF));
        assertEquals("`", KeyUtils.getKeyLabel(0xC0));
        assertEquals("[", KeyUtils.getKeyLabel(0xDB));
        assertEquals("\\", KeyUtils.getKeyLabel(0xDC));
        assertEquals("]", KeyUtils.getKeyLabel(0xDD));
        assertEquals("'", KeyUtils.getKeyLabel(0xDE));
    }

    // ---- getKeyLabel: Arrow keys (Unicode) ----

    @Test
    void getKeyLabel_arrows() {
        assertEquals("\u2190", KeyUtils.getKeyLabel(0x25));
        assertEquals("\u2191", KeyUtils.getKeyLabel(0x26));
        assertEquals("\u2192", KeyUtils.getKeyLabel(0x27));
        assertEquals("\u2193", KeyUtils.getKeyLabel(0x28));
    }

    // ---- getKeyLabel: Unknown VK -> hex ----

    @Test
    void getKeyLabel_unknownReturnsHex() {
        assertEquals("0xFF", KeyUtils.getKeyLabel(0xFF));
        assertEquals("0x00", KeyUtils.getKeyLabel(0x00));
    }

    // ---- getPickerLabel: Overrides ----

    @Test
    void getPickerLabel_overrides() {
        assertEquals("Backspace", KeyUtils.getPickerLabel(0x08));
        assertEquals("CapsLock", KeyUtils.getPickerLabel(0x14));
        assertEquals("PageUp", KeyUtils.getPickerLabel(0x21));
        assertEquals("PageDown", KeyUtils.getPickerLabel(0x22));
        assertEquals("PrintScreen", KeyUtils.getPickerLabel(0x2C));
        assertEquals("Insert", KeyUtils.getPickerLabel(0x2D));
        assertEquals("Delete", KeyUtils.getPickerLabel(0x2E));
        assertEquals("Scroll Lock", KeyUtils.getPickerLabel(0x91));
        assertEquals("NumLock", KeyUtils.getPickerLabel(0x90));
    }

    // ---- getPickerLabel: Arrow overrides ----

    @Test
    void getPickerLabel_arrows() {
        assertEquals("Left", KeyUtils.getPickerLabel(0x25));
        assertEquals("Up", KeyUtils.getPickerLabel(0x26));
        assertEquals("Right", KeyUtils.getPickerLabel(0x27));
        assertEquals("Down", KeyUtils.getPickerLabel(0x28));
    }

    // ---- getPickerLabel: Falls through to getKeyLabel ----

    @Test
    void getPickerLabel_fallsThrough() {
        assertEquals("A", KeyUtils.getPickerLabel(0x41));
        assertEquals("F1", KeyUtils.getPickerLabel(0x70));
        assertEquals("Tab", KeyUtils.getPickerLabel(0x09));
    }

    // ---- javaKeyToWindowsVK: Known translations ----

    @Test
    void javaKeyToWindowsVK_knownTranslations() {
        assertEquals(0x0D, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_ENTER));
        assertEquals(0x2E, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_DELETE));
        assertEquals(0x2D, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_INSERT));
        assertEquals(0xC0, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_BACK_QUOTE));
        assertEquals(0xDB, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_OPEN_BRACKET));
        assertEquals(0xDD, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_CLOSE_BRACKET));
        assertEquals(0xDC, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_BACK_SLASH));
        assertEquals(0xBA, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_SEMICOLON));
        assertEquals(0xDE, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_QUOTE));
        assertEquals(0xBC, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_COMMA));
        assertEquals(0xBE, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_PERIOD));
        assertEquals(0xBF, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_SLASH));
        assertEquals(0xBD, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_MINUS));
        assertEquals(0xBB, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_EQUALS));
        assertEquals(0x90, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_NUM_LOCK));
        assertEquals(0x91, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_SCROLL_LOCK));
        assertEquals(0x2C, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_PRINTSCREEN));
        assertEquals(0x5D, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_CONTEXT_MENU));
    }

    // ---- javaKeyToWindowsVK: Passthrough ----

    @Test
    void javaKeyToWindowsVK_passthrough() {
        assertEquals(KeyEvent.VK_A, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_A));
        assertEquals(KeyEvent.VK_F1, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_F1));
        assertEquals(KeyEvent.VK_SPACE, KeyUtils.javaKeyToWindowsVK(KeyEvent.VK_SPACE));
    }
}
