package com.keyremapper.hook;

import com.keyremapper.model.MacroAction;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.MSG;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Records keyboard events (key down / key up) with timing via a temporary
 * low-level keyboard hook. Events pass through to the OS normally.
 */
public class MacroRecorder {

    private static final int WH_KEYBOARD_LL = 13;
    private static final int WM_KEYDOWN     = 0x0100;
    private static final int WM_KEYUP       = 0x0101;
    private static final int WM_SYSKEYDOWN  = 0x0104;
    private static final int WM_SYSKEYUP    = 0x0105;
    private static final int WM_QUIT        = 0x0012;
    private static final int LLKHF_INJECTED = 0x00000010;

    private final KeyboardHook.NativeUser32 user32 = KeyboardHook.NativeUser32.INSTANCE;

    private volatile boolean recording;
    private long lastEventNano;
    private final List<MacroAction> actions = new ArrayList<>();
    private Consumer<MacroAction> onAction;
    private boolean autoInsertDelay;

    private volatile HHOOK hookHandle;
    private volatile int hookThreadId;
    private Thread hookThread;

    @SuppressWarnings("unused")
    private KeyboardHook.NativeUser32.LowLevelKeyboardProc hookProc;

    public void startRecording(boolean autoDelay, Consumer<MacroAction> onAction) {
        if (recording) return;
        this.onAction = onAction;
        this.autoInsertDelay = autoDelay;
        actions.clear();
        recording = true;
        lastEventNano = System.nanoTime();

        CountDownLatch ready = new CountDownLatch(1);

        hookThread = new Thread(() -> {
            hookThreadId = Kernel32.INSTANCE.GetCurrentThreadId();

            hookProc = (nCode, wParam, lParam) -> {
                if (nCode >= 0 && recording) {
                    KeyboardHook.KBDLLHOOKSTRUCT info =
                            new KeyboardHook.KBDLLHOOKSTRUCT(new Pointer(lParam.longValue()));

                    boolean injected = (info.flags & LLKHF_INJECTED) != 0;
                    if (!injected) {
                        int msg = wParam.intValue();
                        boolean isDown = (msg == WM_KEYDOWN || msg == WM_SYSKEYDOWN);
                        boolean isUp   = (msg == WM_KEYUP   || msg == WM_SYSKEYUP);

                        if (isDown || isUp) {
                            long now = System.nanoTime();
                            long delayMs = (now - lastEventNano) / 1_000_000;
                            lastEventNano = now;

                            if (autoInsertDelay && delayMs > 0 && !actions.isEmpty()) {
                                MacroAction d = MacroAction.delay(delayMs);
                                actions.add(d);
                                if (onAction != null) onAction.accept(d);
                            }

                            MacroAction a = isDown
                                    ? MacroAction.keyDown(info.vkCode, autoInsertDelay ? 0 : delayMs)
                                    : MacroAction.keyUp(info.vkCode, autoInsertDelay ? 0 : delayMs);
                            actions.add(a);
                            if (onAction != null) onAction.accept(a);
                        }
                    }
                }
                return user32.CallNextHookEx(hookHandle, nCode, wParam, lParam);
            };

            HINSTANCE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
            hookHandle = user32.SetWindowsHookExW(WH_KEYBOARD_LL, hookProc, hMod, 0);

            if (hookHandle == null) {
                recording = false;
                ready.countDown();
                return;
            }

            ready.countDown();

            MSG msg = new MSG();
            while (user32.GetMessageW(msg, null, 0, 0) > 0) { }

            user32.UnhookWindowsHookEx(hookHandle);
            hookHandle = null;
            recording = false;
        }, "MacroRecorder");

        hookThread.setDaemon(true);
        hookThread.start();

        try { ready.await(3, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public List<MacroAction> stopRecording() {
        if (!recording) return new ArrayList<>(actions);
        recording = false;
        user32.PostThreadMessageW(hookThreadId, WM_QUIT, new WPARAM(0), new LPARAM(0));
        try {
            hookThread.join(3000);
            if (hookThread.isAlive()) System.err.println("Warning: macro recorder thread did not terminate within 3s");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return new ArrayList<>(actions);
    }

    public boolean isRecording() { return recording; }
}
