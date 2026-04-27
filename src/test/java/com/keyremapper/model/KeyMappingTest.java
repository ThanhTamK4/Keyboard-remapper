package com.keyremapper.model;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyMappingTest {

    private static final Gson GSON = new Gson();

    @Test
    void defaultEnabledIsTrue() {
        KeyMapping m = new KeyMapping(0x41, 0x42, "A", "B");
        assertTrue(m.isEnabled(),
                "New mappings must default to enabled so existing flows are unchanged");
    }

    @Test
    void defaultEnabledIsTrueForNoArgsConstructor() {
        KeyMapping m = new KeyMapping();
        assertTrue(m.isEnabled());
    }

    @Test
    void setEnabledFalseRoundTripsViaGson() {
        KeyMapping m = new KeyMapping(0x14, 0x1B, "Caps", "Esc");
        m.setEnabled(false);
        String json = GSON.toJson(m);
        KeyMapping parsed = GSON.fromJson(json, KeyMapping.class);
        assertFalse(parsed.isEnabled(),
                "enabled=false must survive JSON serialisation");
        assertEquals(0x14, parsed.getFromKeyCode());
        assertEquals(0x1B, parsed.getToKeyCode());
    }

    @Test
    void legacyJsonWithoutEnabledFieldDeserialisesAsEnabled() {
        // Pre-feature profiles.json / .bkm files do not include the field.
        String legacyJson = "{\"fromKeyCode\":65,\"toKeyCode\":66,"
                + "\"fromKeyName\":\"A\",\"toKeyName\":\"B\"}";
        KeyMapping m = GSON.fromJson(legacyJson, KeyMapping.class);
        assertTrue(m.isEnabled(),
                "Legacy mappings (no enabled field) must default to enabled");
    }
}
