package com.keyremapper.hook;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.win32.StdCallLibrary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * System-wide keyboard hook using Windows low-level keyboard hook (WH_KEYBOARD_LL).
 * Intercepts key presses and injects replacement keys via keybd_event.
 */
public class KeyboardHook {

    private static final int WH_KEYBOARD_LL = 13;
    private static final int WM_KEYDOWN     = 0x0100;
    private static final int WM_KEYUP       = 0x0101;
    private static final int WM_SYSKEYDOWN  = 0x0104;
    private static final int WM_SYSKEYUP    = 0x0105;
    private static final int WM_QUIT        = 0x0012;
    private static final int LLKHF_INJECTED = 0x00000010;
    private static final int KEYEVENTF_KEYUP = 0x0002;

    public interface NativeUser32 extends StdCallLibrary {
        NativeUser32 INSTANCE = Native.load("user32", NativeUser32.class);

        interface LowLevelKeyboardProc extends Callback {
            LRESULT callback(int nCode, WPARAM wParam, LPARAM lParam);
        }

        HHOOK SetWindowsHookExW(int idHook, LowLevelKeyboardProc lpfn, HINSTANCE hMod, int dwThreadId);
        LRESULT CallNextHookEx(HHOOK hhk, int nCode, WPARAM wParam, LPARAM lParam);
        boolean UnhookWindowsHookEx(HHOOK hhk);
        int GetMessageW(MSG lpMsg, HWND hWnd, int wMsgFilterMin, int wMsgFilterMax);
        boolean PostThreadMessageW(int idThread, int Msg, WPARAM wParam, LPARAM lParam);
        void keybd_event(byte bVk, byte bScan, int dwFlags, BaseTSD.ULONG_PTR dwExtraInfo);
    }

    @Structure.FieldOrder({"vkCode", "scanCode", "flags", "time", "dwExtraInfo"})
    public static class KBDLLHOOKSTRUCT extends Structure {
        public int vkCode;
        public int scanCode;
        public int flags;
        public int time;
        public Pointer dwExtraInfo;

        public KBDLLHOOKSTRUCT() { super(); }
        public KBDLLHOOKSTRUCT(Pointer p) { super(p); read(); }
    }

    private final NativeUser32 user32 = NativeUser32.INSTANCE;
    // Volatile reference for atomic swap — the hook callback reads this without locking.
    private volatile Map<Integer, Integer> mappings = new ConcurrentHashMap<>();
    private final BaseTSD.ULONG_PTR ZERO_EXTRA = new BaseTSD.ULONG_PTR(0);

    private volatile boolean enabled;
    private volatile boolean running;
    private volatile HHOOK hookHandle;
    private volatile int hookThreadId;
    private Thread hookThread;

    @SuppressWarnings("unused")
    private NativeUser32.LowLevelKeyboardProc hookProc;

    public void start() {
        if (running) return;
        running = true;
        enabled = true;

        CountDownLatch ready = new CountDownLatch(1);

        hookThread = new Thread(() -> {
            hookThreadId = Kernel32.INSTANCE.GetCurrentThreadId();

            hookProc = (nCode, wParam, lParam) -> {
                if (nCode >= 0 && enabled) {
                    KBDLLHOOKSTRUCT info = new KBDLLHOOKSTRUCT(new Pointer(lParam.longValue()));

                    boolean isInjected = (info.flags & LLKHF_INJECTED) != 0;
                    if (!isInjected) {
                        Integer targetVk = mappings.get(info.vkCode);
                        if (targetVk != null) {
                            int msg = wParam.intValue();
                            boolean isKeyUp = (msg == WM_KEYUP || msg == WM_SYSKEYUP);
                            injectKey(targetVk, isKeyUp);
                            return new LRESULT(1);
                        }
                    }
                }
                return user32.CallNextHookEx(hookHandle, nCode, wParam, lParam);
            };

            HINSTANCE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
            hookHandle = user32.SetWindowsHookExW(WH_KEYBOARD_LL, hookProc, hMod, 0);

            if (hookHandle == null) {
                int err = Kernel32.INSTANCE.GetLastError();
                System.err.println("SetWindowsHookEx failed, error code: " + err);
                running = false;
                ready.countDown();
                return;
            }

            ready.countDown();

            MSG msg = new MSG();
            while (user32.GetMessageW(msg, null, 0, 0) > 0) {
                // pump messages so the hook callback is dispatched
            }

            user32.UnhookWindowsHookEx(hookHandle);
            hookHandle = null;
            running = false;
        }, "KeyboardHook");

        hookThread.setDaemon(true);
        hookThread.start();

        try {
            ready.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void injectKey(int vkCode, boolean keyUp) {
        int flags = keyUp ? KEYEVENTF_KEYUP : 0;
        user32.keybd_event((byte) vkCode, (byte) 0, flags, ZERO_EXTRA);
    }

    public void stop() {
        if (!running) return;
        enabled = false;
        user32.PostThreadMessageW(hookThreadId, WM_QUIT, new WPARAM(0), new LPARAM(0));
        try {
            hookThread.join(3000);
            if (hookThread.isAlive()) {
                System.err.println("Warning: keyboard hook thread did not terminate within 3s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning() { return running; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void updateMappings(Map<Integer, Integer> newMappings) {
        // Atomic swap: build the new map first, then replace the reference in one write.
        // This ensures the hook callback never sees a partially-filled or empty map.
        mappings = new ConcurrentHashMap<>(newMappings);
    }

    public void clearMappings() {
        mappings = new ConcurrentHashMap<>();
    }
}
