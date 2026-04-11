package com.keyremapper.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MacroActionTest {

    @Test
    void keyDown_factory() {
        MacroAction a = MacroAction.keyDown(0x41, 50);
        assertEquals(MacroAction.Type.KEY_DOWN, a.getType());
        assertEquals(0x41, a.getKeyCode());
        assertEquals(50, a.getDelayMs());
    }

    @Test
    void keyUp_factory() {
        MacroAction a = MacroAction.keyUp(0x41, 10);
        assertEquals(MacroAction.Type.KEY_UP, a.getType());
        assertEquals(0x41, a.getKeyCode());
        assertEquals(10, a.getDelayMs());
    }

    @Test
    void delay_factory() {
        MacroAction a = MacroAction.delay(100);
        assertEquals(MacroAction.Type.DELAY, a.getType());
        assertEquals(0, a.getKeyCode());
        assertEquals(100, a.getDelayMs());
    }

    @Test
    void macro_defaults() {
        Macro m = new Macro("Test");
        assertEquals("Test", m.getName());
        assertNotNull(m.getId());
        assertNotNull(m.getActions());
        assertTrue(m.getActions().isEmpty());
        assertTrue(m.isAutoInsertDelay());
        assertEquals(Macro.CycleMode.SPECIFIED_TIMES, m.getCycleMode());
        assertEquals(1, m.getCycleCount());
    }

    @Test
    void profile_defaults() {
        Profile p = new Profile("MyProfile");
        assertEquals("MyProfile", p.getName());
        assertNotNull(p.getId());
        assertNotNull(p.getMappings());
        assertTrue(p.getMappings().isEmpty());
        assertNotNull(p.getMacros());
        assertTrue(p.getMacros().isEmpty());
    }

    @Test
    void keyMapping_constructor() {
        KeyMapping km = new KeyMapping(0x14, 0x1B, "Caps", "Esc");
        assertEquals(0x14, km.getFromKeyCode());
        assertEquals(0x1B, km.getToKeyCode());
        assertEquals("Caps", km.getFromKeyName());
        assertEquals("Esc", km.getToKeyName());
    }
}
