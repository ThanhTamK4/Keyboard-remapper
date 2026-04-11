package com.keyremapper.hook;

import com.keyremapper.model.Macro;
import com.keyremapper.model.MacroAction;
import com.sun.jna.platform.win32.BaseTSD;

/**
 * Plays back a recorded macro by injecting key events with timing.
 * Supports cycle-until-released and specified-cycle-count modes.
 */
public class MacroPlayer {

    private static final int KEYEVENTF_KEYUP = 0x0002;

    private final KeyboardHook.NativeUser32 user32 = KeyboardHook.NativeUser32.INSTANCE;
    private final BaseTSD.ULONG_PTR ZERO_EXTRA = new BaseTSD.ULONG_PTR(0);

    private volatile boolean playing;
    private Thread playThread;
    private Runnable onFinished;

    public void play(Macro macro, Runnable onFinished) {
        if (playing) return;
        playing = true;
        this.onFinished = onFinished;

        playThread = new Thread(() -> {
            try {
                int times = macro.getCycleMode() == Macro.CycleMode.SPECIFIED_TIMES
                        ? macro.getCycleCount() : Integer.MAX_VALUE;

                for (int i = 0; i < times && playing; i++) {
                    for (MacroAction action : macro.getActions()) {
                        if (!playing) break;

                        switch (action.getType()) {
                            case KEY_DOWN:
                                if (action.getDelayMs() > 0) Thread.sleep(action.getDelayMs());
                                user32.keybd_event((byte) action.getKeyCode(), (byte) 0, 0, ZERO_EXTRA);
                                break;
                            case KEY_UP:
                                if (action.getDelayMs() > 0) Thread.sleep(action.getDelayMs());
                                user32.keybd_event((byte) action.getKeyCode(), (byte) 0, KEYEVENTF_KEYUP, ZERO_EXTRA);
                                break;
                            case DELAY:
                                Thread.sleep(action.getDelayMs());
                                break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                playing = false;
                if (this.onFinished != null) {
                    javax.swing.SwingUtilities.invokeLater(this.onFinished);
                }
            }
        }, "MacroPlayer");

        playThread.setDaemon(true);
        playThread.start();
    }

    public void stop() {
        playing = false;
        if (playThread != null) {
            playThread.interrupt();
            try { playThread.join(1000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    public boolean isPlaying() { return playing; }
}
