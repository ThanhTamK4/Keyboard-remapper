/**
 * Tests for Key Remapper web UI pure functions.
 * Run with: node test_app.js
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

// ================================================================
//  Extract pure functions from app.js (no DOM dependencies)
// ================================================================

// Load VK_LABELS from shared JSON (single source of truth)
const VK_LABELS = {};
const rawLabels = JSON.parse(fs.readFileSync(path.join(__dirname, 'public', 'vk_labels.json'), 'utf8'));
for (const [k, v] of Object.entries(rawLabels)) VK_LABELS[parseInt(k)] = v;

function vkLabel(vk) { return VK_LABELS[vk] || ('0x'+vk.toString(16).toUpperCase()); }

const KEY_ALIASES = {
  'a':0x41,'b':0x42,'c':0x43,'d':0x44,'e':0x45,'f':0x46,'g':0x47,'h':0x48,
  'i':0x49,'j':0x4A,'k':0x4B,'l':0x4C,'m':0x4D,'n':0x4E,'o':0x4F,'p':0x50,
  'q':0x51,'r':0x52,'s':0x53,'t':0x54,'u':0x55,'v':0x56,'w':0x57,'x':0x58,'y':0x59,'z':0x5A,
  '0':0x30,'1':0x31,'2':0x32,'3':0x33,'4':0x34,'5':0x35,'6':0x36,'7':0x37,'8':0x38,'9':0x39,
  'escape':0x1B,'esc':0x1B,'tab':0x09,'capslock':0x14,'caps':0x14,
  'space':0x20,'spacebar':0x20,'enter':0x0D,'return':0x0D,
  'backspace':0x08,'bksp':0x08,'back':0x08,
  'delete':0x2E,'del':0x2E,'insert':0x2D,'ins':0x2D,
  'home':0x24,'end':0x23,
  'pageup':0x21,'pgup':0x21,'pagedown':0x22,'pgdn':0x22,
  'printscreen':0x2C,'prtsc':0x2C,'scrolllock':0x91,'scrlk':0x91,
  'pause':0x13,'break':0x13,'numlock':0x90,'numlk':0x90,
  'up':0x26,'uparrow':0x26,'arrowup':0x26,
  'down':0x28,'downarrow':0x28,'arrowdown':0x28,
  'left':0x25,'leftarrow':0x25,'arrowleft':0x25,
  'right':0x27,'rightarrow':0x27,'arrowright':0x27,
  'shift':0xA0,'lshift':0xA0,'leftshift':0xA0,'rshift':0xA1,'rightshift':0xA1,
  'ctrl':0xA2,'control':0xA2,'lctrl':0xA2,'leftctrl':0xA2,'rctrl':0xA3,'rightctrl':0xA3,
  'alt':0xA4,'lalt':0xA4,'leftalt':0xA4,'ralt':0xA5,'rightalt':0xA5,
  'win':0x5B,'windows':0x5B,'lwin':0x5B,'super':0x5B,'rwin':0x5C,'rightwin':0x5C,
  'menu':0x5D,'app':0x5D,'contextmenu':0x5D,
  'f1':0x70,'f2':0x71,'f3':0x72,'f4':0x73,'f5':0x74,'f6':0x75,
  'f7':0x76,'f8':0x77,'f9':0x78,'f10':0x79,'f11':0x7A,'f12':0x7B,
  'num0':0x60,'numpad0':0x60,'num1':0x61,'numpad1':0x61,
  'num2':0x62,'numpad2':0x62,'num3':0x63,'numpad3':0x63,
  'num4':0x64,'numpad4':0x64,'num5':0x65,'numpad5':0x65,
  'num6':0x66,'numpad6':0x66,'num7':0x67,'numpad7':0x67,
  'num8':0x68,'numpad8':0x68,'num9':0x69,'numpad9':0x69,
  'numpadmultiply':0x6A,'numpadadd':0x6B,'numpadsubtract':0x6D,
  'numpaddecimal':0x6E,'numpaddivide':0x6F,
  '-':0xBD,'=':0xBB,'[':0xDB,']':0xDD,'\\':0xDC,';':0xBA,"'":0xDE,
  '`':0xC0,',':0xBC,'.':0xBE,'/':0xBF,
  'semicolon':0xBA,'equals':0xBB,'comma':0xBC,'minus':0xBD,'period':0xBE,
  'slash':0xBF,'backquote':0xC0,'bracketleft':0xDB,'backslash':0xDC,
  'bracketright':0xDD,'quote':0xDE
};

function resolveKey(name) {
  const raw = name.trim().toLowerCase();
  if (KEY_ALIASES[raw] !== undefined) return KEY_ALIASES[raw];
  const normalized = raw.replace(/[\s_\-]/g, '');
  return KEY_ALIASES[normalized] ?? null;
}

// ================================================================
//  Minimal state + stubs for executeCommand testing
// ================================================================

let state = {
  profiles: [],
  activeIndex: 0,
  pendingMappings: {},
  selectedVk: -1,
  hookActive: false,
  javaConnected: false,
  selectedMacroIdx: -1,
  selectedActionIdx: -1,
};

function resetState() {
  state.profiles = [{ id: 'test-1', name: 'Profile 1', mappings: [], macros: [] }];
  state.activeIndex = 0;
  state.pendingMappings = {};
  state.selectedVk = -1;
  state.hookActive = false;
  state.javaConnected = false;
  state.selectedMacroIdx = -1;
}

// Stubs for DOM functions
function drawKeyboard() {}
function renderProfileList() {}
function renderMacroList() {}
function renderMacroActions() {}
function saveProfiles() {}
function updateStatus() {}
function updatePickerHighlight() {}
function activateProfile() {
  const p = state.profiles[state.activeIndex];
  state.pendingMappings = {};
  for (const m of (p.mappings || [])) {
    state.pendingMappings[m.fromKeyCode] = m.toKeyCode;
  }
}
function resizeCanvas() {}
function handleBridgeResponse() {}

function findProfile(id) {
  const trimmed = (id || '').trim();
  if (!trimmed) return -1;
  const num = parseInt(trimmed, 10);
  if (!isNaN(num) && num >= 1 && num <= state.profiles.length) return num - 1;
  return state.profiles.findIndex(p => p.name.toLowerCase() === trimmed.toLowerCase());
}

function findMacro(id) {
  const macros = state.profiles[state.activeIndex]?.macros || [];
  const trimmed = (id || '').trim();
  if (!trimmed) return -1;
  const num = parseInt(trimmed, 10);
  if (!isNaN(num) && num >= 1 && num <= macros.length) return num - 1;
  return macros.findIndex(m => m.name.toLowerCase() === trimmed.toLowerCase());
}

function executeCommand(cmd, args) {
  const a = args.split(',').map(s => s.trim());
  switch (cmd) {
    case 'map_key': {
      const from = resolveKey(a[0]), to = resolveKey(a[1]);
      if (from == null || to == null) return 'Unknown key name.';
      state.pendingMappings[from] = to;
      drawKeyboard();
      return `Mapped ${vkLabel(from)} \u2192 ${vkLabel(to)}. Click Apply to activate.`;
    }
    case 'swap_keys': {
      const k1 = resolveKey(a[0]), k2 = resolveKey(a[1]);
      if (k1 == null || k2 == null) return 'Unknown key name.';
      state.pendingMappings[k1] = k2;
      state.pendingMappings[k2] = k1;
      drawKeyboard();
      return `Swapped ${vkLabel(k1)} \u2194 ${vkLabel(k2)}.`;
    }
    case 'remove_mapping': {
      const k = resolveKey(a[0]);
      if (k == null) return 'Unknown key.';
      if (!(k in state.pendingMappings)) return `${vkLabel(k)} has no mapping.`;
      delete state.pendingMappings[k];
      drawKeyboard();
      return `Removed mapping for ${vkLabel(k)}.`;
    }
    case 'clear_mappings':
      state.pendingMappings = {};
      drawKeyboard();
      return 'Cleared all mappings.';
    case 'show_mappings': {
      const entries = Object.entries(state.pendingMappings);
      if (!entries.length) return 'No mappings configured.';
      return 'Mappings:\n' + entries.map(([f,t]) => `  ${vkLabel(+f)} \u2192 ${vkLabel(t)}`).join('\n');
    }
    case 'create_profile': {
      const name = a[0];
      state.profiles.push({ id: 'gen-' + Date.now(), name, mappings: [], macros: [] });
      renderProfileList();
      saveProfiles();
      return `Created profile "${name}".`;
    }
    case 'delete_profile': {
      const idx = findProfile(a[0]);
      if (idx < 0) return 'Profile not found.';
      if (state.profiles.length <= 1) return "Can't delete the last profile.";
      const n = state.profiles[idx].name;
      state.profiles.splice(idx, 1);
      state.activeIndex = Math.min(state.activeIndex, state.profiles.length - 1);
      activateProfile();
      saveProfiles();
      return `Deleted "${n}".`;
    }
    case 'rename_profile': {
      const idx = findProfile(a[0]);
      if (idx < 0) return 'Profile not found.';
      const old = state.profiles[idx].name;
      state.profiles[idx].name = a[1];
      renderProfileList();
      saveProfiles();
      return `Renamed "${old}" to "${a[1]}".`;
    }
    case 'list_profiles':
      return 'Profiles:\n' + state.profiles.map((p,i) =>
        `  ${i+1}. ${p.name}${i === state.activeIndex ? '  (active)' : ''}`
      ).join('\n');
    case 'switch_profile': {
      const idx = findProfile(a[0]);
      if (idx < 0) return 'Profile not found.';
      state.activeIndex = idx;
      activateProfile();
      saveProfiles();
      return `Switched to "${state.profiles[idx].name}".`;
    }
    case 'create_macro': {
      const p = state.profiles[state.activeIndex];
      if (!p.macros) p.macros = [];
      p.macros.push({ id: 'mac-' + Date.now(), name: a[0], actions: [], autoInsertDelay: false, cycleMode: 'UNTIL_RELEASED', cycleCount: 1 });
      renderMacroList();
      saveProfiles();
      return `Created macro "${a[0]}".`;
    }
    case 'delete_macro': {
      const p = state.profiles[state.activeIndex];
      const i = (p.macros||[]).findIndex(m => m.name.toLowerCase() === a[0].toLowerCase());
      if (i < 0) return 'Macro not found.';
      const n = p.macros[i].name;
      p.macros.splice(i, 1);
      renderMacroList();
      saveProfiles();
      return `Deleted macro "${n}".`;
    }
    case 'add_macro_key': {
      const p = state.profiles[state.activeIndex];
      const macro = (p.macros||[]).find(m => m.name.toLowerCase() === a[0].toLowerCase());
      if (!macro) return 'Macro not found.';
      const vk = resolveKey(a[1]);
      if (vk == null) return 'Unknown key: ' + a[1];
      macro.actions.push({ type: 'KEY_DOWN', keyCode: vk, delayMs: 0 });
      macro.actions.push({ type: 'KEY_UP', keyCode: vk, delayMs: 10 });
      renderMacroActions();
      saveProfiles();
      return `Added ${vkLabel(vk)} press to "${macro.name}".`;
    }
    case 'clear_macro': {
      const p = state.profiles[state.activeIndex];
      const m = (p.macros||[]).find(m => m.name.toLowerCase() === a[0].toLowerCase());
      if (!m) return 'Macro not found.';
      const cnt = m.actions.length;
      m.actions = [];
      renderMacroActions();
      saveProfiles();
      return `Cleared ${cnt} action(s) from "${m.name}".`;
    }
    case 'list_macros': {
      const macros = state.profiles[state.activeIndex].macros || [];
      if (!macros.length) return 'No macros.';
      return macros.map((m,i) => `  ${i+1}. ${m.name} (${m.actions.length} actions)`).join('\n');
    }
    default:
      return `Unknown command: ${cmd}`;
  }
}

// ================================================================
//  Test runner
// ================================================================

let passed = 0, failed = 0, errors = [];

function test(name, fn) {
  try {
    fn();
    passed++;
  } catch (e) {
    failed++;
    errors.push({ name, error: e.message });
    console.error(`  FAIL: ${name} — ${e.message}`);
  }
}

// ================================================================
//  resolveKey tests
// ================================================================

console.log('--- resolveKey tests ---');

test('resolveKey: single letters', () => {
  assert.strictEqual(resolveKey('a'), 0x41);
  assert.strictEqual(resolveKey('A'), 0x41);
  assert.strictEqual(resolveKey('z'), 0x5A);
  assert.strictEqual(resolveKey('Z'), 0x5A);
});

test('resolveKey: all letters a-z', () => {
  for (let i = 0; i < 26; i++) {
    const c = String.fromCharCode(97 + i); // a-z
    assert.strictEqual(resolveKey(c), 0x41 + i);
  }
});

test('resolveKey: digits', () => {
  for (let i = 0; i <= 9; i++) {
    assert.strictEqual(resolveKey(String(i)), 0x30 + i);
  }
});

test('resolveKey: named keys', () => {
  assert.strictEqual(resolveKey('Escape'), 0x1B);
  assert.strictEqual(resolveKey('Esc'), 0x1B);
  assert.strictEqual(resolveKey('CapsLock'), 0x14);
  assert.strictEqual(resolveKey('Caps'), 0x14);
  assert.strictEqual(resolveKey('Enter'), 0x0D);
  assert.strictEqual(resolveKey('Space'), 0x20);
  assert.strictEqual(resolveKey('Backspace'), 0x08);
  assert.strictEqual(resolveKey('Tab'), 0x09);
  assert.strictEqual(resolveKey('Delete'), 0x2E);
  assert.strictEqual(resolveKey('Insert'), 0x2D);
});

test('resolveKey: modifiers', () => {
  assert.strictEqual(resolveKey('Shift'), 0xA0);
  assert.strictEqual(resolveKey('LShift'), 0xA0);
  assert.strictEqual(resolveKey('RShift'), 0xA1);
  assert.strictEqual(resolveKey('Ctrl'), 0xA2);
  assert.strictEqual(resolveKey('Alt'), 0xA4);
  assert.strictEqual(resolveKey('Win'), 0x5B);
});

test('resolveKey: arrows', () => {
  assert.strictEqual(resolveKey('Left'), 0x25);
  assert.strictEqual(resolveKey('LeftArrow'), 0x25);
  assert.strictEqual(resolveKey('ArrowLeft'), 0x25);
  assert.strictEqual(resolveKey('Up'), 0x26);
  assert.strictEqual(resolveKey('Down'), 0x28);
  assert.strictEqual(resolveKey('Right'), 0x27);
});

test('resolveKey: F-keys', () => {
  assert.strictEqual(resolveKey('F1'), 0x70);
  assert.strictEqual(resolveKey('F12'), 0x7B);
  assert.strictEqual(resolveKey('f1'), 0x70);
});

test('resolveKey: numpad', () => {
  assert.strictEqual(resolveKey('Num0'), 0x60);
  assert.strictEqual(resolveKey('Numpad9'), 0x69);
});

test('resolveKey: punctuation', () => {
  assert.strictEqual(resolveKey(';'), 0xBA);
  assert.strictEqual(resolveKey('='), 0xBB);
  assert.strictEqual(resolveKey('-'), 0xBD);
  assert.strictEqual(resolveKey(','), 0xBC);
  assert.strictEqual(resolveKey('.'), 0xBE);
  assert.strictEqual(resolveKey('/'), 0xBF);
  assert.strictEqual(resolveKey('`'), 0xC0);
  assert.strictEqual(resolveKey('['), 0xDB);
  assert.strictEqual(resolveKey(']'), 0xDD);
  assert.strictEqual(resolveKey('\\'), 0xDC);
  assert.strictEqual(resolveKey("'"), 0xDE);
});

test('resolveKey: normalization with spaces', () => {
  assert.strictEqual(resolveKey('Caps Lock'), 0x14);
  assert.strictEqual(resolveKey('caps lock'), 0x14);
  assert.strictEqual(resolveKey('Page Up'), 0x21);
});

test('resolveKey: normalization with underscores/hyphens', () => {
  assert.strictEqual(resolveKey('caps_lock'), 0x14);
  assert.strictEqual(resolveKey('caps-lock'), 0x14);
  assert.strictEqual(resolveKey('left_arrow'), 0x25);
});

test('resolveKey: unknown returns null', () => {
  assert.strictEqual(resolveKey('garbage'), null);
  assert.strictEqual(resolveKey('FooBar'), null);
  assert.strictEqual(resolveKey('F99'), null);
});

test('resolveKey: JS-only aliases', () => {
  assert.strictEqual(resolveKey('semicolon'), 0xBA);
  assert.strictEqual(resolveKey('equals'), 0xBB);
  assert.strictEqual(resolveKey('comma'), 0xBC);
  assert.strictEqual(resolveKey('minus'), 0xBD);
  assert.strictEqual(resolveKey('period'), 0xBE);
  assert.strictEqual(resolveKey('slash'), 0xBF);
  assert.strictEqual(resolveKey('backquote'), 0xC0);
  assert.strictEqual(resolveKey('bracketleft'), 0xDB);
  assert.strictEqual(resolveKey('backslash'), 0xDC);
  assert.strictEqual(resolveKey('bracketright'), 0xDD);
  assert.strictEqual(resolveKey('quote'), 0xDE);
});

test('resolveKey: break alias', () => {
  assert.strictEqual(resolveKey('break'), 0x13);
});

// ================================================================
//  Parity test with shared data
// ================================================================

console.log('\n--- resolveKey parity test ---');

const parityFile = path.join(__dirname, '..', 'tests', 'resolve_key_test_data.json');
if (fs.existsSync(parityFile)) {
  const parityData = JSON.parse(fs.readFileSync(parityFile, 'utf8'));
  test('resolveKey: parity with Java (shared test data)', () => {
    const failures = [];
    for (const { input, expected, jsOnly } of parityData) {
      const result = resolveKey(input);
      const expectedVal = expected === -1 ? null : expected;
      if (result !== expectedVal && !jsOnly) {
        failures.push(`  "${input}": expected ${expectedVal}, got ${result}`);
      }
    }
    if (failures.length > 0) {
      assert.fail('Parity failures:\n' + failures.join('\n'));
    }
  });
} else {
  console.log('  SKIP: parity test data not found (run from project root)');
}

// ================================================================
//  vkLabel tests
// ================================================================

console.log('\n--- vkLabel tests ---');

test('vkLabel: known keys', () => {
  assert.strictEqual(vkLabel(0x41), 'A');
  assert.strictEqual(vkLabel(0x1B), 'Esc');
  assert.strictEqual(vkLabel(0x70), 'F1');
  assert.strictEqual(vkLabel(0x20), 'Space');
});

test('vkLabel: unknown returns hex', () => {
  assert.strictEqual(vkLabel(0xFF), '0xFF');
  assert.strictEqual(vkLabel(0x00), '0x0');
});

// ================================================================
//  executeCommand tests
// ================================================================

console.log('\n--- executeCommand tests ---');

test('executeCommand: map_key', () => {
  resetState();
  const result = executeCommand('map_key', 'CapsLock, Escape');
  assert.ok(result.includes('Mapped'));
  assert.strictEqual(state.pendingMappings[0x14], 0x1B);
});

test('executeCommand: swap_keys', () => {
  resetState();
  executeCommand('swap_keys', 'A, B');
  assert.strictEqual(state.pendingMappings[0x41], 0x42);
  assert.strictEqual(state.pendingMappings[0x42], 0x41);
});

test('executeCommand: remove_mapping existing', () => {
  resetState();
  state.pendingMappings[0x41] = 0x42;
  const result = executeCommand('remove_mapping', 'A');
  assert.ok(result.includes('Removed'));
  assert.strictEqual(state.pendingMappings[0x41], undefined);
});

test('executeCommand: remove_mapping nonexistent', () => {
  resetState();
  const result = executeCommand('remove_mapping', 'A');
  assert.ok(result.includes('no mapping'));
});

test('executeCommand: clear_mappings', () => {
  resetState();
  state.pendingMappings[0x41] = 0x42;
  executeCommand('clear_mappings', '');
  assert.deepStrictEqual(state.pendingMappings, {});
});

test('executeCommand: show_mappings empty', () => {
  resetState();
  const result = executeCommand('show_mappings', '');
  assert.ok(result.includes('No mappings'));
});

test('executeCommand: show_mappings with data', () => {
  resetState();
  state.pendingMappings[0x41] = 0x42;
  const result = executeCommand('show_mappings', '');
  assert.ok(result.includes('Mappings:'));
  assert.ok(result.includes('A'));
});

test('executeCommand: create_profile', () => {
  resetState();
  const result = executeCommand('create_profile', 'Gaming');
  assert.ok(result.includes('Created'));
  assert.strictEqual(state.profiles.length, 2);
  assert.strictEqual(state.profiles[1].name, 'Gaming');
});

test('executeCommand: delete_profile last', () => {
  resetState();
  const result = executeCommand('delete_profile', 'Profile 1');
  assert.ok(result.includes("Can't delete"));
});

test('executeCommand: delete_profile with multiple', () => {
  resetState();
  state.profiles.push({ id: 'p2', name: 'Second', mappings: [], macros: [] });
  const result = executeCommand('delete_profile', 'Second');
  assert.ok(result.includes('Deleted'));
  assert.strictEqual(state.profiles.length, 1);
});

test('executeCommand: rename_profile', () => {
  resetState();
  executeCommand('rename_profile', 'Profile 1, Renamed');
  assert.strictEqual(state.profiles[0].name, 'Renamed');
});

test('executeCommand: list_profiles', () => {
  resetState();
  const result = executeCommand('list_profiles', '');
  assert.ok(result.includes('Profile 1'));
  assert.ok(result.includes('active'));
});

test('executeCommand: switch_profile', () => {
  resetState();
  state.profiles.push({ id: 'p2', name: 'Second', mappings: [], macros: [] });
  executeCommand('switch_profile', 'Second');
  assert.strictEqual(state.activeIndex, 1);
});

test('executeCommand: create_macro', () => {
  resetState();
  executeCommand('create_macro', 'TestMacro');
  assert.strictEqual(state.profiles[0].macros.length, 1);
  assert.strictEqual(state.profiles[0].macros[0].name, 'TestMacro');
});

test('executeCommand: add_macro_key', () => {
  resetState();
  executeCommand('create_macro', 'TestMacro');
  executeCommand('add_macro_key', 'TestMacro, A');
  const macro = state.profiles[0].macros[0];
  assert.strictEqual(macro.actions.length, 2);
  assert.strictEqual(macro.actions[0].type, 'KEY_DOWN');
  assert.strictEqual(macro.actions[0].keyCode, 0x41);
  assert.strictEqual(macro.actions[1].type, 'KEY_UP');
});

test('executeCommand: delete_macro', () => {
  resetState();
  executeCommand('create_macro', 'TestMacro');
  executeCommand('delete_macro', 'TestMacro');
  assert.strictEqual(state.profiles[0].macros.length, 0);
});

test('executeCommand: clear_macro', () => {
  resetState();
  executeCommand('create_macro', 'TestMacro');
  executeCommand('add_macro_key', 'TestMacro, A');
  executeCommand('clear_macro', 'TestMacro');
  assert.strictEqual(state.profiles[0].macros[0].actions.length, 0);
});

test('executeCommand: unknown key error', () => {
  resetState();
  const result = executeCommand('map_key', 'FooBar, Escape');
  assert.ok(result.includes('Unknown'));
});

test('executeCommand: unknown command', () => {
  resetState();
  const result = executeCommand('nonexistent', '');
  assert.ok(result.includes('Unknown command'));
});

// ================================================================
//  Per-mapping enable/disable toggle
// ================================================================

// Mirror of activateProfile()'s mapping-load logic in app.js
function loadDisabledFromProfile(profile) {
  const disabled = new Set();
  for (const m of (profile.mappings || [])) {
    if (m.enabled === false) disabled.add(m.fromKeyCode);
  }
  return disabled;
}

// Mirror of saveProfiles()'s mapping-write logic in app.js
function buildSavedMappings(pendingMappings, pendingDisabled) {
  return Object.entries(pendingMappings).map(([from, to]) => {
    const fromCode = parseInt(from);
    return {
      fromKeyCode: fromCode, toKeyCode: to,
      fromKeyName: vkLabel(fromCode), toKeyName: vkLabel(to),
      enabled: !pendingDisabled.has(fromCode)
    };
  });
}

test('legacy profile (no enabled field) loads with empty disabled set', () => {
  const profile = { mappings: [
    { fromKeyCode: 0x14, toKeyCode: 0x1B, fromKeyName: 'Caps', toKeyName: 'Esc' }
  ]};
  const disabled = loadDisabledFromProfile(profile);
  assert.strictEqual(disabled.size, 0);
});

test('profile with enabled:false populates disabled set', () => {
  const profile = { mappings: [
    { fromKeyCode: 0x41, toKeyCode: 0x42, enabled: true },
    { fromKeyCode: 0x14, toKeyCode: 0x1B, enabled: false }
  ]};
  const disabled = loadDisabledFromProfile(profile);
  assert.strictEqual(disabled.size, 1);
  assert.ok(disabled.has(0x14));
  assert.ok(!disabled.has(0x41));
});

test('saving round-trips disabled flag', () => {
  const pendingMappings = { 0x41: 0x42, 0x14: 0x1B };
  const pendingDisabled = new Set([0x14]);
  const saved = buildSavedMappings(pendingMappings, pendingDisabled);
  const a = saved.find(m => m.fromKeyCode === 0x41);
  const caps = saved.find(m => m.fromKeyCode === 0x14);
  assert.strictEqual(a.enabled, true);
  assert.strictEqual(caps.enabled, false);
});

test('save then load round-trip preserves disabled set', () => {
  const initial = new Set([0x14]);
  const saved = buildSavedMappings({ 0x41: 0x42, 0x14: 0x1B }, initial);
  const reloaded = loadDisabledFromProfile({ mappings: saved });
  assert.strictEqual(reloaded.size, 1);
  assert.ok(reloaded.has(0x14));
});

// ================================================================
//  Results
// ================================================================

console.log(`\n${'='.repeat(40)}`);
console.log(`Results: ${passed} passed, ${failed} failed`);
if (errors.length) {
  console.log('\nFailures:');
  errors.forEach(e => console.log(`  ${e.name}: ${e.error}`));
}
process.exit(failed > 0 ? 1 : 0);
