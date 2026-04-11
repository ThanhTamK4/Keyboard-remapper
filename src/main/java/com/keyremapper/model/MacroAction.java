package com.keyremapper.model;

public class MacroAction {

    public enum Type { KEY_DOWN, KEY_UP, DELAY }

    private Type type;
    private int keyCode;
    private long delayMs;

    public MacroAction() {}

    public MacroAction(Type type, int keyCode, long delayMs) {
        this.type = type;
        this.keyCode = keyCode;
        this.delayMs = delayMs;
    }

    public static MacroAction keyDown(int vk, long delayMs) {
        return new MacroAction(Type.KEY_DOWN, vk, delayMs);
    }

    public static MacroAction keyUp(int vk, long delayMs) {
        return new MacroAction(Type.KEY_UP, vk, delayMs);
    }

    public static MacroAction delay(long ms) {
        return new MacroAction(Type.DELAY, 0, ms);
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public int getKeyCode() { return keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }
    public long getDelayMs() { return delayMs; }
    public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
}
