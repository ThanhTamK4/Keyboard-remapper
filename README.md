# Key Remapper

A full-featured keyboard remapper for Windows with a visual keyboard interface, macro recording, AI assistant, and a web UI for remote control.

Intercepts key presses at the OS level using a Windows low-level keyboard hook (`WH_KEYBOARD_LL`) and replaces them with your configured target keys — system-wide, across all applications.

![Java](https://img.shields.io/badge/Java-11+-orange)
![Platform](https://img.shields.io/badge/Platform-Windows-blue)
![License](https://img.shields.io/badge/License-MIT-green)

---

## Features

### Desktop App (Java Swing)
- **Visual keyboard layout** — 87% TKL keyboard with rainbow gradient; click any key to select it
- **Key remapping** — categorised key picker (Common / Modifier / Advanced / Keypad)
- **System-wide hooks** — blocks the original key and injects the replacement via `keybd_event`
- **Macro recording & playback** — record key sequences with timing, play them back with repeat/loop modes
- **Profile system** — create, rename, delete, export (`.bkm`), and import profiles
- **AI assistant (KeySup)** — chat-based keyboard support powered by Ollama, with offline regex fallback
- **Modern dark UI** — powered by FlatLaf
- **Persistent config** — profiles saved to `~/.keyremapper/profiles.json`

### Web UI
- **Browser-based interface** — full keyboard visualization rendered on HTML5 Canvas
- **Remote profile management** — create, edit, switch, and delete profiles from any browser
- **Macro editor** — build and edit macros with a visual action table
- **AI chat** — streaming chat with Ollama integration
- **Two server options** — Python (stdlib, no pip) or Node.js (Express)
- **Bridge proxy** — web server communicates with the Java app's HTTP bridge on port 8230

### Security & Robustness
- XSS protection (no `innerHTML` with user data)
- CSRF protection via Origin header validation
- Content Security Policy
- Atomic file writes to prevent data corruption
- Thread-safe profile management with background saves
- Input validation and circular mapping detection

---

## Prerequisites

| Tool      | Version | Required |
|-----------|---------|----------|
| Java      | 11+     | Yes      |
| Python    | 3.8+    | For web server (option A) |
| Node.js   | 16+     | For web server (option B) |
| Ollama    | Latest  | Optional — enables AI assistant |

No Maven or Gradle required for the desktop app — the build script downloads dependencies automatically.

---

## Quick Start

### 1. Build

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

This downloads JNA, FlatLaf, and Gson from Maven Central, compiles the source, and packages a fat JAR.

### 2. Run the Desktop App

```bash
run.bat
```

Or manually:

```bash
java --enable-native-access=ALL-UNNAMED -jar build\key-remapper.jar
```

### 3. Run the Web UI (optional)

**Option A — Python (no dependencies):**
```bash
cd web
python server.py
```

**Option B — Node.js:**
```bash
cd web
npm install
npm start
```

Then open [http://localhost:3000](http://localhost:3000) in your browser.

> The web UI connects to the Java app's bridge server on port 8230. Make sure the desktop app is running for full functionality (apply/restore mappings, macro playback).

### 4. Enable AI Assistant (optional)

Install [Ollama](https://ollama.ai), then pull a model:

```bash
ollama pull gemma3
```

The assistant will automatically detect Ollama and switch from offline regex mode to AI-powered responses.

---

## Usage

### Key Remapping

1. **Select a key** on the visual keyboard (click it — it highlights with a white border)
2. **Pick a target** from the Key Setting panel (Common, Modifier, Advanced, or Keypad)
3. The keyboard updates to show the mapping (yellow dot indicator)
4. Click **Apply** to activate all mappings system-wide
5. Click **Restore** to reset and deactivate the hook

### Macro Recording

1. Go to the **Macro** tab
2. Click **Record** — press keys normally, all events are captured with timing
3. Click **Stop** to finish recording
4. Configure playback: repeat count, loop mode, or until-released
5. Click **Play** to execute the macro

### Profiles

- **+** to add a new profile
- **-** to delete the selected profile
- **...** for Rename, Export (`.bkm`), and Import (`.bkm`)
- Switching profiles loads that profile's mappings and macros

### AI Assistant (KeySup)

Type natural language commands in the KeySup chat tab:

- *"Remap Caps Lock to Escape"*
- *"What key is VK 0x41?"*
- *"Show my current mappings"*
- *"Apply my mappings"*

---

## Project Structure

```
keyboard/
├── src/main/java/com/keyremapper/
│   ├── App.java                    # Entry point
│   ├── ai/
│   │   ├── KeySupBot.java          # AI assistant (Ollama + regex fallback)
│   │   └── OllamaClient.java       # Ollama HTTP client
│   ├── hook/
│   │   ├── KeyboardHook.java        # Low-level keyboard hook (JNA)
│   │   ├── MacroRecorder.java       # Record key sequences
│   │   └── MacroPlayer.java         # Play back macros
│   ├── model/
│   │   ├── KeyMapping.java          # Key mapping data
│   │   ├── Macro.java               # Macro container
│   │   ├── MacroAction.java         # Individual macro action
│   │   └── Profile.java             # Profile with mappings + macros
│   ├── ui/
│   │   ├── MainFrame.java           # Main window + HTTP bridge server
│   │   ├── KeyboardPanel.java       # Visual keyboard renderer
│   │   ├── KeyPickerPanel.java      # Key selection categories
│   │   ├── KeySupPanel.java         # Chat UI for AI assistant
│   │   ├── MacroPanel.java          # Macro management UI
│   │   └── ProfilePanel.java        # Profile management UI
│   └── util/
│       ├── ConfigManager.java       # App configuration
│       ├── KeyUtils.java            # VK code utilities
│       └── ProfileManager.java      # Profile persistence
├── src/test/java/                   # Java unit tests (JUnit 5)
├── tests/                           # Python integration tests
├── web/
│   ├── public/
│   │   ├── index.html               # Web UI
│   │   ├── app.js                   # Client-side logic
│   │   ├── style.css                # Styles
│   │   └── vk_labels.json           # Shared VK code labels
│   ├── server.py                    # Python web server (stdlib)
│   ├── server.js                    # Node.js web server (Express)
│   ├── test_app.js                  # Frontend tests
│   └── test_server.py               # Python server tests
├── build.ps1                        # PowerShell build script
├── run.bat                          # Launcher script
└── pom.xml                          # Maven config (dependency versions)
```

---

## Testing

### Java unit tests (requires Maven)
```bash
mvn test
```

### Python server tests
```bash
cd web
python -m pytest test_server.py
```

### Frontend tests (Node.js)
```bash
cd web
node test_app.js
```

### Integration tests
```bash
python tests/test_integration.py
```

---

## Configuration

### Environment Variables

| Variable       | Default                      | Description                      |
|----------------|------------------------------|----------------------------------|
| `OLLAMA_MODEL` | `gemma3`                     | Ollama model for AI assistant    |
| `OLLAMA_HOST`  | `http://localhost:11434`     | Ollama server URL                |
| `JAVA_BRIDGE`  | `http://127.0.0.1:8230`     | Java bridge server URL           |
| `PROFILES_FILE`| `~/.keyremapper/profiles.json` | Profile storage path           |

### Ports

| Service        | Port  |
|----------------|-------|
| Web UI         | 3000  |
| Java Bridge    | 8230  |
| Ollama         | 11434 |

---

## Tech Stack

- **Java 11** — Swing UI, JNA for Windows API
- **JNA 5.14.0** — Native Windows keyboard hooks
- **FlatLaf 3.6** — Modern Swing look-and-feel
- **Gson 2.11.0** — JSON serialization
- **Python 3** — Web server (stdlib `http.server`)
- **Node.js + Express** — Alternative web server
- **Ollama** — Local LLM inference
- **HTML5 Canvas** — Web keyboard visualization

---

## Notes

- **Windows only** — relies on Windows APIs via JNA
- **Run as administrator** if certain keys in elevated windows are not being remapped
- Mappings persist between sessions automatically
- The web UI requires the desktop app to be running for apply/restore/macro features
