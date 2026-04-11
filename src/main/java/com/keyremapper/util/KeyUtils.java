package com.keyremapper.util;

import java.awt.event.KeyEvent;

public final class KeyUtils {

    private KeyUtils() {}

    /** Short label suitable for keyboard key caps and picker buttons (1-8 chars). */
    public static String getKeyLabel(int vk) {
        if (vk >= 0x41 && vk <= 0x5A) return String.valueOf((char) vk);
        if (vk >= 0x30 && vk <= 0x39) return String.valueOf((char) vk);
        if (vk >= 0x70 && vk <= 0x87) return "F" + (vk - 0x6F);
        if (vk >= 0x60 && vk <= 0x69) return "Num " + (vk - 0x60);
        switch (vk) {
            case 0x08: return "Bksp";
            case 0x09: return "Tab";
            case 0x0D: return "Enter";
            case 0x10: return "Shift";
            case 0x11: return "Ctrl";
            case 0x12: return "Alt";
            case 0x13: return "Pause";
            case 0x14: return "Caps";
            case 0x1B: return "Esc";
            case 0x20: return "Space";
            case 0x21: return "PgUp";
            case 0x22: return "PgDn";
            case 0x23: return "End";
            case 0x24: return "Home";
            case 0x25: return "\u2190";
            case 0x26: return "\u2191";
            case 0x27: return "\u2192";
            case 0x28: return "\u2193";
            case 0x2C: return "PrtSc";
            case 0x2D: return "Ins";
            case 0x2E: return "Del";
            case 0x5B: return "LWin";
            case 0x5C: return "RWin";
            case 0x5D: return "App";
            case 0x6A: return "Num *";
            case 0x6B: return "Num +";
            case 0x6D: return "Num -";
            case 0x6E: return "Num .";
            case 0x6F: return "Num /";
            case 0x90: return "NumLk";
            case 0x91: return "ScrLk";
            case 0xA0: return "LShift";
            case 0xA1: return "RShift";
            case 0xA2: return "LCtrl";
            case 0xA3: return "RCtrl";
            case 0xA4: return "LAlt";
            case 0xA5: return "RAlt";
            case 0xBA: return ";";
            case 0xBB: return "=";
            case 0xBC: return ",";
            case 0xBD: return "-";
            case 0xBE: return ".";
            case 0xBF: return "/";
            case 0xC0: return "`";
            case 0xDB: return "[";
            case 0xDC: return "\\";
            case 0xDD: return "]";
            case 0xDE: return "'";
            default:   return String.format("0x%02X", vk);
        }
    }

    /** Longer label for the key-picker panel. */
    public static String getPickerLabel(int vk) {
        if (vk >= 0x60 && vk <= 0x69) return "Num " + (vk - 0x60);
        switch (vk) {
            case 0x08: return "Backspace";
            case 0x14: return "CapsLock";
            case 0x1B: return "Esc";
            case 0x21: return "PageUp";
            case 0x22: return "PageDown";
            case 0x2C: return "PrintScreen";
            case 0x2D: return "Insert";
            case 0x2E: return "Delete";
            case 0x5D: return "App";
            case 0x91: return "Scroll Lock";
            case 0x90: return "NumLock";
            case 0xA0: return "LShift";
            case 0xA1: return "RShift";
            case 0xA2: return "LCtrl";
            case 0xA3: return "RCtrl";
            case 0xA4: return "LAlt";
            case 0xA5: return "RAlt";
            case 0x5B: return "LWin";
            case 0x5C: return "RWin";
            case 0x25: return "Left";
            case 0x26: return "Up";
            case 0x27: return "Right";
            case 0x28: return "Down";
            default:   return getKeyLabel(vk);
        }
    }

    public static int javaKeyToWindowsVK(int javaKeyCode) {
        switch (javaKeyCode) {
            case KeyEvent.VK_ENTER:         return 0x0D;
            case KeyEvent.VK_DELETE:         return 0x2E;
            case KeyEvent.VK_INSERT:         return 0x2D;
            case KeyEvent.VK_BACK_QUOTE:     return 0xC0;
            case KeyEvent.VK_OPEN_BRACKET:   return 0xDB;
            case KeyEvent.VK_CLOSE_BRACKET:  return 0xDD;
            case KeyEvent.VK_BACK_SLASH:     return 0xDC;
            case KeyEvent.VK_SEMICOLON:      return 0xBA;
            case KeyEvent.VK_QUOTE:          return 0xDE;
            case KeyEvent.VK_COMMA:          return 0xBC;
            case KeyEvent.VK_PERIOD:         return 0xBE;
            case KeyEvent.VK_SLASH:          return 0xBF;
            case KeyEvent.VK_MINUS:          return 0xBD;
            case KeyEvent.VK_EQUALS:         return 0xBB;
            case KeyEvent.VK_NUM_LOCK:       return 0x90;
            case KeyEvent.VK_SCROLL_LOCK:    return 0x91;
            case KeyEvent.VK_PRINTSCREEN:    return 0x2C;
            case KeyEvent.VK_CONTEXT_MENU:   return 0x5D;
            default:                         return javaKeyCode;
        }
    }
}
