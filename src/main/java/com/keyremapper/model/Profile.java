package com.keyremapper.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Profile {

    private String id;
    private String name;
    private List<KeyMapping> mappings;
    private List<Macro> macros;

    public Profile() {
        this.id = UUID.randomUUID().toString();
        this.name = "Profile 1";
        this.mappings = new ArrayList<>();
        this.macros = new ArrayList<>();
    }

    public Profile(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.mappings = new ArrayList<>();
        this.macros = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<KeyMapping> getMappings() { return mappings; }
    public void setMappings(List<KeyMapping> mappings) { this.mappings = mappings; }

    public List<Macro> getMacros() { return macros != null ? macros : (macros = new ArrayList<>()); }
    public void setMacros(List<Macro> macros) { this.macros = macros; }

    @Override
    public String toString() { return name; }
}
