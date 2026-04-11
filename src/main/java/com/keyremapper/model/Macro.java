package com.keyremapper.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Macro {

    public enum CycleMode { UNTIL_RELEASED, SPECIFIED_TIMES }

    private String id;
    private String name;
    private List<MacroAction> actions;
    private boolean autoInsertDelay;
    private CycleMode cycleMode;
    private int cycleCount;

    public Macro() {
        this.id = UUID.randomUUID().toString();
        this.actions = new ArrayList<>();
        this.autoInsertDelay = true;
        this.cycleMode = CycleMode.SPECIFIED_TIMES;
        this.cycleCount = 1;
    }

    public Macro(String name) {
        this();
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<MacroAction> getActions() { return actions; }
    public void setActions(List<MacroAction> actions) { this.actions = actions; }
    public boolean isAutoInsertDelay() { return autoInsertDelay; }
    public void setAutoInsertDelay(boolean v) { this.autoInsertDelay = v; }
    public CycleMode getCycleMode() { return cycleMode; }
    public void setCycleMode(CycleMode m) { this.cycleMode = m; }
    public int getCycleCount() { return cycleCount; }
    public void setCycleCount(int c) { this.cycleCount = c; }

    @Override
    public String toString() { return name; }
}
