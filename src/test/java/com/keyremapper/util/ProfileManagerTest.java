package com.keyremapper.util;

import com.keyremapper.model.KeyMapping;
import com.keyremapper.model.Profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfileManagerTest {

    // ---- toVkMap / fromVkMap (static, no filesystem needed) ----

    @Test
    void toVkMap_convertsCorrectly() {
        Profile p = new Profile("Test");
        p.getMappings().add(new KeyMapping(0x14, 0x1B, "Caps", "Esc"));
        p.getMappings().add(new KeyMapping(0x41, 0x42, "A", "B"));

        Map<Integer, Integer> map = ProfileManager.toVkMap(p);
        assertEquals(2, map.size());
        assertEquals(0x1B, (int) map.get(0x14));
        assertEquals(0x42, (int) map.get(0x41));
    }

    @Test
    void toVkMap_emptyMappings() {
        Profile p = new Profile("Test");
        Map<Integer, Integer> map = ProfileManager.toVkMap(p);
        assertTrue(map.isEmpty());
    }

    @Test
    void fromVkMap_convertsCorrectly() {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(0x14, 0x1B);

        List<KeyMapping> mappings = ProfileManager.fromVkMap(map);
        assertEquals(1, mappings.size());
        assertEquals(0x14, mappings.get(0).getFromKeyCode());
        assertEquals(0x1B, mappings.get(0).getToKeyCode());
        assertEquals("Caps", mappings.get(0).getFromKeyName());
        assertEquals("Esc", mappings.get(0).getToKeyName());
    }

    @Test
    void fromVkMap_emptyMap() {
        List<KeyMapping> mappings = ProfileManager.fromVkMap(new HashMap<>());
        assertTrue(mappings.isEmpty());
    }

    @Test
    void toVkMap_excludesDisabledMappings() {
        Profile p = new Profile("Test");
        KeyMapping enabled = new KeyMapping(0x41, 0x42, "A", "B");
        KeyMapping disabled = new KeyMapping(0x14, 0x1B, "Caps", "Esc");
        disabled.setEnabled(false);
        p.getMappings().add(enabled);
        p.getMappings().add(disabled);

        Map<Integer, Integer> map = ProfileManager.toVkMap(p);
        assertEquals(1, map.size(),
                "Disabled mappings should be excluded from the hook map");
        assertEquals(0x42, (int) map.get(0x41));
        assertFalse(map.containsKey(0x14),
                "Disabled mapping CapsLock->Esc should not appear");
    }

    @Test
    void toVkMap_fromVkMap_roundtrip() {
        Map<Integer, Integer> original = new HashMap<>();
        original.put(0x14, 0x1B);
        original.put(0x41, 0x42);

        List<KeyMapping> mappings = ProfileManager.fromVkMap(original);
        Profile p = new Profile("Test");
        p.setMappings(mappings);
        Map<Integer, Integer> roundtripped = ProfileManager.toVkMap(p);

        assertEquals(original, roundtripped);
    }

    // ---- ProfileManager lifecycle (uses real filesystem) ----

    @Test
    void defaultProfile_createdOnInit() {
        // Uses the default constructor which reads ~/.keyremapper/profiles.json
        // If the file already exists, it loads from it; otherwise creates a default
        ProfileManager pm = new ProfileManager();
        assertNotNull(pm.getProfiles());
        assertFalse(pm.getProfiles().isEmpty());
        assertNotNull(pm.getActiveProfile());
    }

    @Test
    void addProfile_increases() {
        ProfileManager pm = new ProfileManager();
        int before = pm.getProfiles().size();
        pm.addProfile("TestNew");
        assertEquals(before + 1, pm.getProfiles().size());
        assertEquals("TestNew", pm.getProfiles().get(pm.getProfiles().size() - 1).getName());
    }

    @Test
    void deleteProfile_preventsDeletingLast() {
        ProfileManager pm = new ProfileManager();
        // Ensure only one profile
        while (pm.getProfiles().size() > 1) {
            pm.deleteProfile(pm.getProfiles().size() - 1);
        }
        assertFalse(pm.deleteProfile(0));
        assertEquals(1, pm.getProfiles().size());
    }

    @Test
    void deleteProfile_adjustsActiveIndex() {
        ProfileManager pm = new ProfileManager();
        // Add two extra profiles to ensure we have at least 3
        pm.addProfile("TestA");
        pm.addProfile("TestB");
        int lastIdx = pm.getProfiles().size() - 1;
        pm.setActiveIndex(lastIdx);
        assertEquals(lastIdx, pm.getActiveIndex());
        assertTrue(pm.deleteProfile(lastIdx));
        // After deleting the last, activeIndex should be clamped
        assertTrue(pm.getActiveIndex() < pm.getProfiles().size());
        assertEquals(pm.getProfiles().size() - 1, pm.getActiveIndex());
    }

    @Test
    void renameProfile_valid() {
        ProfileManager pm = new ProfileManager();
        pm.renameProfile(0, "Renamed");
        assertEquals("Renamed", pm.getProfiles().get(0).getName());
    }

    @Test
    void renameProfile_outOfBounds() {
        ProfileManager pm = new ProfileManager();
        String originalName = pm.getProfiles().get(0).getName();
        pm.renameProfile(99, "X");
        // Should be a no-op, original profile unchanged
        assertEquals(originalName, pm.getProfiles().get(0).getName());
    }

    @Test
    void setActiveIndex_boundsChecking() {
        ProfileManager pm = new ProfileManager();
        pm.setActiveIndex(-1);
        assertEquals(0, pm.getActiveIndex());
        pm.setActiveIndex(999);
        assertEquals(0, pm.getActiveIndex());
    }

    @Test
    void exportImportProfile(@TempDir File tempDir) throws IOException {
        ProfileManager pm = new ProfileManager();
        // Create a fresh profile specifically for this test
        Profile original = pm.addProfile("ExportTest");
        original.getMappings().add(new KeyMapping(0x14, 0x1B, "Caps", "Esc"));
        int originalMappingCount = original.getMappings().size();

        File exportFile = new File(tempDir, "test.bkm");
        pm.exportProfile(original, exportFile);
        assertTrue(exportFile.exists());

        int before = pm.getProfiles().size();
        Profile imported = pm.importProfile(exportFile);
        assertEquals(before + 1, pm.getProfiles().size());
        assertNotEquals(original.getId(), imported.getId()); // new UUID
        assertEquals(original.getName(), imported.getName());
        assertEquals(originalMappingCount, imported.getMappings().size());
        // Find the CapsLock -> Esc mapping
        boolean found = imported.getMappings().stream()
                .anyMatch(m -> m.getFromKeyCode() == 0x14 && m.getToKeyCode() == 0x1B);
        assertTrue(found, "Imported profile should contain CapsLock->Esc mapping");
    }
}
