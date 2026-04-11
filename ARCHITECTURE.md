# Architecture — Key Remapper

## High-Level Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      User                                   │
│            ┌──────────┐  ┌──────────┐                       │
│            │ Desktop  │  │ Browser  │                       │
│            │ (Swing)  │  │ (Web UI) │                       │
│            └────┬─────┘  └────┬─────┘                       │
│                 │              │                             │
│    ┌────────────┘              │                             │
│    │                  ┌───────┴────────┐                    │
│    │                  │  Web Server    │                    │
│    │                  │  :3000         │                    │
│    │                  │  (Python or    │                    │
│    │                  │   Node.js)     │                    │
│    │                  └───────┬────────┘                    │
│    │                          │ proxy                       │
│    │              ┌───────────┴──────────┐                  │
│    └──────────────┤  Java Bridge Server  ├─── Ollama :11434 │
│                   │  :8230               │    (local LLM)   │
│                   └───────────┬──────────┘                  │
│                               │                             │
│                   ┌───────────┴──────────┐                  │
│                   │  Windows Kernel      │                  │
│                   │  WH_KEYBOARD_LL      │                  │
│                   └──────────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

The app has **two frontends** (Swing desktop + browser SPA) that both control a single Java backend via an HTTP bridge on port 8230. An optional Ollama LLM provides AI-powered chat.

---

## Port Map

| Service          | Port  | Protocol |
|------------------|-------|----------|
| Java Bridge      | 8230  | HTTP     |
| Web Server       | 3000  | HTTP     |
| Ollama (external)| 11434 | HTTP     |

---

## Component Diagram

```
src/main/java/com/keyremapper/
├── App.java                          ← entry point (FlatLaf + MainFrame)
│
├── ui/
│   ├── MainFrame.java    (702 LOC)   ← shell, tabs, bridge server, orchestrator
│   ├── KeyboardPanel.java (334)      ← canvas TKL keyboard (87 keys, HSL gradient)
│   ├── KeyPickerPanel.java (190)     ← target key grid (4 categories)
│   ├── ProfilePanel.java  (207)      ← profile list + CRUD buttons
│   ├── MacroPanel.java    (490)      ← macro editor, record/play controls
│   └── KeySupPanel.java   (232)      ← streaming chat bubbles
│
├── hook/
│   ├── KeyboardHook.java  (173)      ← WH_KEYBOARD_LL, key interception + injection
│   ├── MacroRecorder.java (132)      ← temporary hook for recording key sequences
│   └── MacroPlayer.java   (76)       ← keybd_event playback with timing
│
├── ai/
│   ├── KeySupBot.java     (547)      ← NLU dispatcher (Ollama + regex fallback)
│   └── OllamaClient.java (129)      ← HTTP streaming client for Ollama /api/chat
│
├── model/
│   ├── Profile.java       (43)       ← { id, name, mappings[], macros[] }
│   ├── KeyMapping.java    (31)       ← { fromKeyCode, toKeyCode, labels }
│   ├── Macro.java         (47)       ← { id, name, actions[], cycleMode }
│   └── MacroAction.java   (38)       ← { KEY_DOWN | KEY_UP | DELAY, vk, ms }
│
└── util/
    ├── ProfileManager.java (188)     ← JSON persistence, async save, reload detection
    ├── KeyUtils.java       (123)     ← VK code ↔ label lookups
    └── ConfigManager.java  (52)      ← (legacy, unused)

web/
├── server.py              (233)      ← Python stdlib HTTP server (no pip needed)
├── server.js              (172)      ← Node.js/Express alternative
├── public/
│   ├── index.html                    ← HTML shell + CSP meta tag
│   ├── app.js             (1702)     ← SPA: canvas keyboard, macros, chat, profiles
│   ├── style.css                     ← dark theme styling
│   └── vk_labels.json               ← shared VK code → label map (single source of truth)
├── test_app.js                       ← frontend unit tests (36 tests)
└── test_server.py                    ← Python server tests (21+ tests)

tests/
├── test_integration.py               ← end-to-end integration tests
└── resolve_key_test_data.json        ← test fixtures

src/test/java/                        ← JUnit 5 tests (146 tests)
```

---

## Threading Model

```
┌─ EDT (Event Dispatch Thread) ──────────────────────────────┐
│  All Swing UI updates, button callbacks, profile loads     │
│  MainFrame.onApply(), onRestore(), syncUI()                │
└────────────────────────────────────────────────────────────┘
       ▲ invokeAndWait()
       │
┌─ Bridge HTTP Pool (4 threads) ─────────────────────────────┐
│  Handles /api/* requests from web server                   │
│  Delegates to EDT for state changes                        │
└────────────────────────────────────────────────────────────┘

┌─ Hook Thread (daemon) ─────────────────────────────────────┐
│  Windows message pump + WH_KEYBOARD_LL callback            │
│  Reads mappings via volatile reference (lock-free)         │
│  Calls keybd_event() to inject replacement keys            │
└────────────────────────────────────────────────────────────┘

┌─ Recorder Thread (daemon, temporary) ──────────────────────┐
│  Separate WH_KEYBOARD_LL hook for macro recording          │
│  Fires onAction callbacks → EDT via invokeLater()          │
└────────────────────────────────────────────────────────────┘

┌─ Player Thread (daemon) ───────────────────────────────────┐
│  Iterates MacroAction list, calls keybd_event + sleep      │
│  Fires onFinished → EDT via invokeLater()                  │
└────────────────────────────────────────────────────────────┘

┌─ ProfileSaver (single-thread executor, daemon) ────────────┐
│  Async JSON writes to ~/.keyremapper/profiles.json         │
│  Prevents file I/O from blocking EDT                       │
└────────────────────────────────────────────────────────────┘

┌─ KeySupAsync Thread (daemon) ──────────────────────────────┐
│  Ollama streaming HTTP call                                │
│  Parses [CMD:...] commands → EDT via invokeAndWait()       │
└────────────────────────────────────────────────────────────┘
```

---

## Data Flow

### Key Remapping (Apply)

```
User clicks key on KeyboardPanel
  → pendingMappings[fromVk] = toVk
  → User clicks "Apply"
  → Profile.mappings updated, ProfileManager.save() [async]
  → KeyboardHook.updateMappings() [atomic volatile swap]
  → KeyboardHook.start() if not running

Hook Thread:
  WH_KEYBOARD_LL callback fires
  → check LLKHF_INJECTED (skip own injections)
  → lookup vkCode in mappings (volatile read, no lock)
  → if found: keybd_event(targetVk), return 1 (consumed)
  → if not: CallNextHookEx (pass through)
```

### Web → Java Bridge

```
Browser (app.js)
  → fetch('POST /api/hook/apply', { mappings })
  → Web Server :3000 (Python or Node.js)
  → proxy to http://127.0.0.1:8230/api/apply
  → MainFrame bridge handler
  → SwingUtilities.invokeAndWait { applyMappingsFromJson() }
  → response JSON → web server → browser
```

### AI Chat (Ollama)

```
User types message in KeySupPanel
  → KeySupBot.processAsync() on background thread
  → Build system prompt (includes current app state)
  → OllamaClient.streamChat() → POST Ollama :11434/api/chat
  → Tokens streamed back line-by-line
  → Each token → onToken callback → update chat bubble (EDT)
  → Parse [CMD:mapKey(A, B)] patterns from response
  → Execute commands on EDT via invokeAndWait()
  → Final text displayed in chat
```

---

## Bridge API Endpoints (port 8230)

| Method | Path                    | Description                        |
|--------|-------------------------|------------------------------------|
| POST   | /api/status             | Hook state, mapping count, details |
| POST   | /api/apply              | Apply key mappings from JSON       |
| POST   | /api/disable            | Pause the keyboard hook            |
| POST   | /api/restore            | Clear mappings, stop hook          |
| POST   | /api/macro/record/start | Begin macro recording              |
| POST   | /api/macro/record/stop  | Stop recording, return actions     |
| POST   | /api/macro/play         | Play a macro from JSON             |
| POST   | /api/macro/play/stop    | Stop macro playback                |

All endpoints return JSON. Errors return HTTP 400/500 with `{"error":"..."}`.

---

## Web Server API (port 3000)

| Method | Path                | Proxied to    | Description                    |
|--------|---------------------|---------------|--------------------------------|
| GET    | /api/profiles       | local file    | Load profiles from disk        |
| POST   | /api/profiles       | local file    | Save profiles to disk          |
| GET    | /api/ollama/status  | Ollama :11434 | Check LLM availability         |
| POST   | /api/ollama/chat    | Ollama :11434 | Stream chat (SSE)              |
| GET    | /api/hook/status    | Java :8230    | Hook status                    |
| POST   | /api/hook/apply     | Java :8230    | Apply mappings                 |
| POST   | /api/hook/disable   | Java :8230    | Disable hook                   |
| POST   | /api/hook/restore   | Java :8230    | Restore defaults               |
| POST   | /api/macro/*        | Java :8230    | Macro record/play operations   |
| GET    | /*                  | static files  | index.html, app.js, style.css  |

---

## Persistence

```
~/.keyremapper/profiles.json
{
  "profiles": [
    {
      "id": "uuid",
      "name": "Profile 1",
      "mappings": [
        { "fromKeyCode": 65, "toKeyCode": 66, "fromKeyName": "A", "toKeyName": "B" }
      ],
      "macros": [
        {
          "id": "uuid",
          "name": "My Macro",
          "actions": [
            { "type": "KEY_DOWN", "keyCode": 65, "delayMs": 0 },
            { "type": "DELAY",    "keyCode": 0,  "delayMs": 100 },
            { "type": "KEY_UP",   "keyCode": 65, "delayMs": 0 }
          ],
          "cycleMode": "SPECIFIED_TIMES",
          "cycleCount": 1,
          "autoInsertDelay": true
        }
      ]
    }
  ],
  "activeIndex": 0
}
```

- Both desktop and web UIs read/write the same file
- Desktop saves async (background thread), sync on shutdown
- Web server uses atomic writes (tmp + rename)
- Desktop detects external changes via `reloadIfChanged()` (file mtime)

---

## Key Design Decisions

**Lock-free hook reads** — The keyboard hook runs on a high-priority thread with a Windows message pump. Mappings are stored in a `volatile ConcurrentHashMap` reference. Updates swap the entire reference atomically. The hook thread never blocks on a lock.

**Dual frontend, single backend** — Both Swing and web UIs share the same `profiles.json` and control the same hook via HTTP. This lets users access the remapper from a browser on the same machine without running two instances.

**Ollama with regex fallback** — The AI assistant degrades gracefully. When Ollama is running, it streams responses with `[CMD:function(args)]` inline commands. When offline, ~40 hardcoded regex patterns handle common requests (map, swap, apply, list, etc.).

**Separate recorder hook** — Macro recording uses its own `WH_KEYBOARD_LL` hook, independent from the remapping hook. This allows recording while remapping is active, and avoids any interference between the two.

**EDT synchronization** — All bridge HTTP handlers that modify Swing state use `SwingUtilities.invokeAndWait()`. This guarantees thread safety without manual locking on UI components.

---

## Dependencies

| Library          | Version | Purpose                              |
|------------------|---------|--------------------------------------|
| JNA              | 5.14.0  | Windows API binding (hooks, keybd_event) |
| JNA Platform     | 5.14.0  | Windows-specific JNA types           |
| FlatLaf          | 3.6     | Modern dark Swing theme              |
| Gson             | 2.11.0  | JSON serialization                   |
| JUnit 5          | 5.10.2  | Unit testing                         |
| Express          | 4.21.0  | Node.js web server (optional)        |
| Ollama           | —       | Local LLM (external, optional)       |

---

## Test Coverage

| Suite                    | Count | Runner          |
|--------------------------|-------|-----------------|
| Java unit tests          | 146   | JUnit 5 / Maven |
| Python server tests      | 21+   | unittest        |
| Frontend JS tests        | 36    | Node.js         |
| Python integration tests | 8     | unittest        |
| **Total**                | **211+** |              |
