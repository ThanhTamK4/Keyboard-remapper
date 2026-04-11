package com.keyremapper.model;

public class KeyMapping {

    private int fromKeyCode;
    private int toKeyCode;
    private String fromKeyName;
    private String toKeyName;

    public KeyMapping() {}

    public KeyMapping(int fromKeyCode, int toKeyCode, String fromKeyName, String toKeyName) {
        this.fromKeyCode = fromKeyCode;
        this.toKeyCode = toKeyCode;
        this.fromKeyName = fromKeyName;
        this.toKeyName = toKeyName;
    }

    public int getFromKeyCode() { return fromKeyCode; }
    public void setFromKeyCode(int fromKeyCode) { this.fromKeyCode = fromKeyCode; }

    public int getToKeyCode() { return toKeyCode; }
    public void setToKeyCode(int toKeyCode) { this.toKeyCode = toKeyCode; }

    public String getFromKeyName() { return fromKeyName; }
    public void setFromKeyName(String fromKeyName) { this.fromKeyName = fromKeyName; }

    public String getToKeyName() { return toKeyName; }
    public void setToKeyName(String toKeyName) { this.toKeyName = toKeyName; }
}
