/* ================================================================
   Key Remapper — Web UI  (app.js)
   ================================================================ */

// --------------- State ---------------
let state = {
  profiles: [],
  activeIndex: 0,
  pendingMappings: {},   // fromVk -> toVk
  selectedVk: -1,
  hookActive: false,
  javaConnected: false,
  selectedMacroIdx: -1,
  selectedActionIdx: -1,
  ollamaOnline: false,
  chatHistory: []        // {role, content}
};

// --------------- Utilities ---------------
let _saveTimer = null;
function debouncedSave(delayMs = 500) {
  clearTimeout(_saveTimer);
  _saveTimer = setTimeout(() => saveProfiles(), delayMs);
}

// --------------- Key definitions (mirrors Java KeyUtils) ---------------
// VK_LABELS loaded from shared vk_labels.json (single source of truth)
let VK_LABELS = {};
async function loadVkLabels() {
  try {
    const res = await fetch('/vk_labels.json');
    if (res.ok) {
      const data = await res.json();
      // Convert string keys to integers
      for (const [k, v] of Object.entries(data)) VK_LABELS[parseInt(k)] = v;
    }
  } catch (e) { console.error('Failed to load VK labels:', e); }
}

function vkLabel(vk) { return VK_LABELS[vk] || ('0x'+vk.toString(16).toUpperCase()); }

// --------------- TKL keyboard layout (same coords as Java) ---------------
const LAYOUT = [];
function kd(label,vk,x,y,w,h){LAYOUT.push({label,vk,x,y,w,h:h||1});}
(function buildLayout(){
  // Row 0 — function
  kd('Esc',0x1B,0,0,1);
  kd('F1',0x70,2,0,1);kd('F2',0x71,3,0,1);kd('F3',0x72,4,0,1);kd('F4',0x73,5,0,1);
  kd('F5',0x74,6.5,0,1);kd('F6',0x75,7.5,0,1);kd('F7',0x76,8.5,0,1);kd('F8',0x77,9.5,0,1);
  kd('F9',0x78,11,0,1);kd('F10',0x79,12,0,1);kd('F11',0x7A,13,0,1);kd('F12',0x7B,14,0,1);
  kd('PrtSc',0x2C,15.5,0,1);kd('ScrLk',0x91,16.5,0,1);kd('Pause',0x13,17.5,0,1);

  // Row 1 — numbers
  let x=0;
  x=ka('`',0xC0,x,1.5,1);x=ka('1',0x31,x,1.5,1);x=ka('2',0x32,x,1.5,1);x=ka('3',0x33,x,1.5,1);
  x=ka('4',0x34,x,1.5,1);x=ka('5',0x35,x,1.5,1);x=ka('6',0x36,x,1.5,1);x=ka('7',0x37,x,1.5,1);
  x=ka('8',0x38,x,1.5,1);x=ka('9',0x39,x,1.5,1);x=ka('0',0x30,x,1.5,1);x=ka('-',0xBD,x,1.5,1);
  x=ka('=',0xBB,x,1.5,1);kd('Bksp',0x08,x,1.5,2);
  kd('Ins',0x2D,15.5,1.5,1);kd('Home',0x24,16.5,1.5,1);kd('PgUp',0x21,17.5,1.5,1);

  // Row 2 — QWERTY
  x=0;
  x=ka('Tab',0x09,x,2.5,1.5);x=ka('Q',0x51,x,2.5,1);x=ka('W',0x57,x,2.5,1);
  x=ka('E',0x45,x,2.5,1);x=ka('R',0x52,x,2.5,1);x=ka('T',0x54,x,2.5,1);
  x=ka('Y',0x59,x,2.5,1);x=ka('U',0x55,x,2.5,1);x=ka('I',0x49,x,2.5,1);
  x=ka('O',0x4F,x,2.5,1);x=ka('P',0x50,x,2.5,1);x=ka('[',0xDB,x,2.5,1);
  x=ka(']',0xDD,x,2.5,1);kd('\\',0xDC,x,2.5,1.5);
  kd('Del',0x2E,15.5,2.5,1);kd('End',0x23,16.5,2.5,1);kd('PgDn',0x22,17.5,2.5,1);

  // Row 3 — home row
  x=0;
  x=ka('Caps',0x14,x,3.5,1.75);x=ka('A',0x41,x,3.5,1);x=ka('S',0x53,x,3.5,1);
  x=ka('D',0x44,x,3.5,1);x=ka('F',0x46,x,3.5,1);x=ka('G',0x47,x,3.5,1);
  x=ka('H',0x48,x,3.5,1);x=ka('J',0x4A,x,3.5,1);x=ka('K',0x4B,x,3.5,1);
  x=ka('L',0x4C,x,3.5,1);x=ka(';',0xBA,x,3.5,1);x=ka("'",0xDE,x,3.5,1);
  kd('Enter',0x0D,x,3.5,2.25);

  // Row 4 — shift
  x=0;
  x=ka('Shift',0xA0,x,4.5,2.25);x=ka('Z',0x5A,x,4.5,1);x=ka('X',0x58,x,4.5,1);
  x=ka('C',0x43,x,4.5,1);x=ka('V',0x56,x,4.5,1);x=ka('B',0x42,x,4.5,1);
  x=ka('N',0x4E,x,4.5,1);x=ka('M',0x4D,x,4.5,1);x=ka(',',0xBC,x,4.5,1);
  x=ka('.',0xBE,x,4.5,1);x=ka('/',0xBF,x,4.5,1);kd('Shift',0xA1,x,4.5,2.75);
  kd('\u2191',0x26,16.5,4.5,1);

  // Row 5 — bottom
  x=0;
  x=ka('Ctrl',0xA2,x,5.5,1.25);x=ka('Win',0x5B,x,5.5,1.25);x=ka('Alt',0xA4,x,5.5,1.25);
  x=ka('Space',0x20,x,5.5,6.25);x=ka('Alt',0xA5,x,5.5,1.25);x=ka('Fn',0,x,5.5,1.25);
  x=ka('App',0x5D,x,5.5,1.25);kd('Ctrl',0xA3,x,5.5,1.25);
  kd('\u2190',0x25,15.5,5.5,1);kd('\u2193',0x28,16.5,5.5,1);kd('\u2192',0x27,17.5,5.5,1);
})();
function ka(l,v,x,y,w){kd(l,v,x,y,w);return x+w;}

const LW = 18.5, LH = 6.5, GAP = 0.05, ARC = 6;

// --------------- Key Picker sections ---------------
const PICKER_SECTIONS = {
  Comm: [0x08,0x09,0x0D,0x1B,0x20,0x2D,0x2E,0x24,0x23,0x21,0x22,0x2C,0x91,0x13],
  Modify: [0xA0,0xA1,0xA2,0xA3,0xA4,0xA5,0x5B,0x5D,0x14,0x90],
  'F-keys': [0x70,0x71,0x72,0x73,0x74,0x75,0x76,0x77,0x78,0x79,0x7A,0x7B],
  Keypad: [0x60,0x61,0x62,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6A,0x6B,0x6D,0x6E,0x6F],
  Alpha: [
    0x41,0x42,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4A,0x4B,0x4C,0x4D,
    0x4E,0x4F,0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57,0x58,0x59,0x5A
  ],
  Digits: [0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,0x38,0x39],
  Punct: [0xBA,0xBB,0xBC,0xBD,0xBE,0xBF,0xC0,0xDB,0xDC,0xDD,0xDE]
};

// ================================================================
//  Canvas keyboard drawing
// ================================================================

const canvas = document.getElementById('keyboard-canvas');
const ctx = canvas.getContext('2d');
let dpr = 1;

function resizeCanvas() {
  const rect = canvas.getBoundingClientRect();
  dpr = window.devicePixelRatio || 1;
  canvas.width = rect.width * dpr;
  canvas.height = rect.height * dpr;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  drawKeyboard();
}

function keyColor(cx, sel, mapped) {
  const hue = (0.80 - (cx / LW) * 0.80) * 360;
  const sat = sel ? 75 : 60;
  const light = sel ? 40 : (mapped ? 30 : 22);
  return `hsl(${hue}, ${sat}%, ${light}%)`;
}

function drawKeyboard() {
  const w = canvas.getBoundingClientRect().width;
  const h = canvas.getBoundingClientRect().height;
  ctx.clearRect(0, 0, w, h);

  const pad = 12;
  const unit = Math.min((w - pad * 2) / LW, (h - pad * 2) / LH);
  const ox = (w - unit * LW) / 2;
  const oy = (h - unit * LH) / 2;

  // Case
  const margin = unit * 0.18;
  ctx.fillStyle = '#26262a';
  ctx.strokeStyle = '#3a3a3e';
  ctx.lineWidth = 1.5;
  roundRect(ctx, ox - margin, oy - margin, LW * unit + margin * 2, LH * unit + margin * 2, 14, true, true);

  // Keys
  for (const k of LAYOUT) {
    const gap = GAP * unit;
    const kx = ox + k.x * unit + gap;
    const ky = oy + k.y * unit + gap;
    const kw = k.w * unit - gap * 2;
    const kh = k.h * unit - gap * 2;
    const sel = k.vk === state.selectedVk && k.vk > 0;
    const mapped = k.vk in state.pendingMappings;

    ctx.fillStyle = keyColor(k.x + k.w / 2, sel, mapped);
    roundRect(ctx, kx, ky, kw, kh, ARC, true, false);

    if (sel) {
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 2.2;
      roundRect(ctx, kx, ky, kw, kh, ARC, false, true);
      ctx.lineWidth = 1;
    }

    const label = mapped ? vkLabel(state.pendingMappings[k.vk]) : k.label;
    const fontSize = Math.max(9, unit * (k.w >= 1.4 ? 0.22 : 0.26));
    ctx.fillStyle = '#fff';
    ctx.font = `${fontSize}px "Segoe UI", system-ui, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, kx + kw / 2, ky + kh / 2);

    if (mapped && !sel) {
      ctx.fillStyle = 'rgba(255,200,50,0.7)';
      ctx.beginPath();
      ctx.arc(kx + kw - 5, ky + 6, 3, 0, Math.PI * 2);
      ctx.fill();
    }
  }
}

function roundRect(ctx, x, y, w, h, r, fill, stroke) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + w - r, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + r);
  ctx.lineTo(x + w, y + h - r);
  ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  ctx.lineTo(x + r, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.closePath();
  if (fill) ctx.fill();
  if (stroke) ctx.stroke();
}

function hitTestKeyboard(mx, my) {
  const rect = canvas.getBoundingClientRect();
  const cx = mx - rect.left;
  const cy = my - rect.top;
  const w = rect.width, h = rect.height;
  const pad = 12;
  const unit = Math.min((w - pad * 2) / LW, (h - pad * 2) / LH);
  const ox = (w - unit * LW) / 2;
  const oy = (h - unit * LH) / 2;
  for (const k of LAYOUT) {
    const gap = GAP * unit;
    const kx = ox + k.x * unit + gap;
    const ky = oy + k.y * unit + gap;
    const kw = k.w * unit - gap * 2;
    const kh = k.h * unit - gap * 2;
    if (cx >= kx && cx <= kx + kw && cy >= ky && cy <= ky + kh) return k;
  }
  return null;
}

canvas.addEventListener('click', (e) => {
  const hit = hitTestKeyboard(e.clientX, e.clientY);
  if (hit && hit.vk > 0) {
    state.selectedVk = hit.vk;
    drawKeyboard();
    updatePickerHighlight();
  }
});

window.addEventListener('resize', resizeCanvas);

// ================================================================
//  Key Picker
// ================================================================

function buildPicker() {
  const grid = document.getElementById('picker-grid');
  grid.innerHTML = '';
  for (const [section, keys] of Object.entries(PICKER_SECTIONS)) {
    const lbl = document.createElement('div');
    lbl.className = 'picker-section-label';
    lbl.textContent = section;
    grid.appendChild(lbl);
    for (const vk of keys) {
      const btn = document.createElement('button');
      btn.className = 'picker-btn';
      btn.textContent = vkLabel(vk);
      btn.dataset.vk = vk;
      btn.addEventListener('click', () => onPickerClick(vk));
      grid.appendChild(btn);
    }
  }
}

function onPickerClick(targetVk) {
  if (state.selectedVk <= 0) return;
  if (targetVk === state.selectedVk) {
    delete state.pendingMappings[state.selectedVk];
  } else {
    // Warn about circular mappings (A→B and B→A)
    if (state.pendingMappings[targetVk] === state.selectedVk) {
      if (!confirm(`Warning: ${vkLabel(targetVk)} is already mapped to ${vkLabel(state.selectedVk)}.\nThis creates a circular swap. Continue?`)) return;
    }
    state.pendingMappings[state.selectedVk] = targetVk;
  }
  drawKeyboard();
  updatePickerHighlight();
}

function updatePickerHighlight() {
  const highlighted = state.pendingMappings[state.selectedVk] ?? -1;
  document.querySelectorAll('.picker-btn').forEach(btn => {
    btn.classList.toggle('highlighted', parseInt(btn.dataset.vk) === highlighted);
  });
}

// ================================================================
//  Profile management
// ================================================================

async function loadProfiles() {
  try {
    const res = await fetch('/api/profiles');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    state.profiles = data.profiles || [];
    state.activeIndex = data.activeIndex || 0;
    if (state.profiles.length === 0) {
      state.profiles = [{ id: crypto.randomUUID(), name: 'Profile 1', mappings: [], macros: [] }];
      state.activeIndex = 0;
    }
  } catch (e) {
    console.error('Failed to load profiles:', e);
    state.profiles = [{ id: crypto.randomUUID(), name: 'Profile 1', mappings: [], macros: [] }];
    state.activeIndex = 0;
  }
  activateProfile();
}

function activateProfile() {
  const p = state.profiles[state.activeIndex];
  state.pendingMappings = {};
  for (const m of (p.mappings || [])) {
    state.pendingMappings[m.fromKeyCode] = m.toKeyCode;
  }
  state.selectedVk = -1;
  drawKeyboard();
  updatePickerHighlight();
  renderProfileList();
  renderMacroList();
}

function renderProfileList() {
  const ul = document.getElementById('profile-list');
  ul.innerHTML = '';
  state.profiles.forEach((p, i) => {
    const li = document.createElement('li');
    li.textContent = p.name;
    if (i === state.activeIndex) li.classList.add('active');
    li.addEventListener('click', async () => {
      state.activeIndex = i;
      activateProfile();
      await saveProfiles();
    });
    ul.appendChild(li);
  });
}

async function saveProfiles() {
  const p = state.profiles[state.activeIndex];
  p.mappings = Object.entries(state.pendingMappings).map(([from, to]) => ({
    fromKeyCode: parseInt(from), toKeyCode: to,
    fromKeyName: vkLabel(parseInt(from)), toKeyName: vkLabel(to)
  }));
  try {
    await fetch('/api/profiles', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ profiles: state.profiles, activeIndex: state.activeIndex })
    });
  } catch (e) {
    console.error('Failed to save profiles:', e);
  }
}

document.getElementById('profile-add').addEventListener('click', () => {
  const name = prompt('Profile name:');
  if (!name || !name.trim()) return;
  const trimmed = name.trim().slice(0, 50);
  state.profiles.push({ id: crypto.randomUUID(), name: trimmed, mappings: [], macros: [] });
  state.activeIndex = state.profiles.length - 1;
  activateProfile();
  saveProfiles();
});

document.getElementById('profile-del').addEventListener('click', () => {
  if (state.profiles.length <= 1) { alert('Cannot delete the last profile.'); return; }
  if (!confirm(`Delete "${state.profiles[state.activeIndex].name}"?`)) return;
  state.profiles.splice(state.activeIndex, 1);
  state.activeIndex = Math.min(state.activeIndex, state.profiles.length - 1);
  activateProfile();
  saveProfiles();
});

document.getElementById('profile-more').addEventListener('click', (e) => {
  e.stopPropagation();
  document.getElementById('profile-menu').classList.toggle('hidden');
});
document.addEventListener('click', () => {
  document.getElementById('profile-menu').classList.add('hidden');
});

document.querySelectorAll('#profile-menu button').forEach(btn => {
  btn.addEventListener('click', (e) => {
    const action = e.target.dataset.action;
    const p = state.profiles[state.activeIndex];
    if (action === 'rename') {
      const name = prompt('New name:', p.name);
      if (name && name.trim()) { p.name = name.trim().slice(0, 50); renderProfileList(); saveProfiles(); }
    } else if (action === 'export') {
      const exportData = { ...p, version: 1 };
      const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = p.name + '.bkm';
      a.click();
    } else if (action === 'import') {
      document.getElementById('import-file').click();
    }
  });
});

document.getElementById('import-file').addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const imported = JSON.parse(reader.result);
      imported.id = crypto.randomUUID();
      state.profiles.push(imported);
      state.activeIndex = state.profiles.length - 1;
      activateProfile();
      saveProfiles();
    } catch { alert('Invalid .bkm file.'); }
  };
  reader.readAsText(file);
  e.target.value = '';
});

// ================================================================
//  Bottom bar actions
// ================================================================

function updateStatus() {
  const label = document.getElementById('status-label');
  if (state.hookActive && state.javaConnected) {
    const n = Object.keys(state.pendingMappings).length;
    label.textContent = `\u25CF  Active \u2014 ${n} mapping(s)`;
    label.className = 'status-active';
  } else if (Object.keys(state.pendingMappings).length > 0 && !state.javaConnected) {
    label.textContent = `\u25CB  ${Object.keys(state.pendingMappings).length} mapping(s) configured`;
    label.className = 'status-inactive';
  } else {
    label.textContent = '\u25CB  Inactive';
    label.className = 'status-inactive';
  }
  updateBridgeIndicator();
}

function updateBridgeIndicator() {
  const el = document.getElementById('bridge-status');
  if (!el) return;
  if (state.javaConnected) {
    el.textContent = '\u25CF Desktop app connected';
    el.className = 'bridge-connected';
  } else {
    el.textContent = '\u25CB Desktop app not detected \u2014 start the desktop app to activate hooks';
    el.className = 'bridge-disconnected';
  }
}

async function pollBridgeStatus() {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 3000);
    const res = await fetch('/api/hook/status', { signal: controller.signal });
    clearTimeout(timer);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data.error) {
      state.javaConnected = false;
      state.hookActive = false;
    } else {
      state.javaConnected = true;
      state.hookActive = data.hookActive;
    }
  } catch {
    state.javaConnected = false;
    state.hookActive = false;
  }
  updateStatus();
}

function handleBridgeResponse(data) {
  if (data && !data.error) {
    state.javaConnected = true;
    state.hookActive = data.hookActive;
  } else {
    state.javaConnected = false;
    state.hookActive = false;
  }
  updateStatus();
}

document.getElementById('btn-apply').addEventListener('click', async () => {
  if (Object.keys(state.pendingMappings).length === 0) {
    alert('No mappings to apply. Map some keys first!');
    return;
  }
  saveProfiles();

  // Always try the bridge first, even if last poll said disconnected
  try {
    const res = await fetch('/api/hook/apply', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mappings: state.pendingMappings })
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data && !data.error) {
      state.javaConnected = true;
      state.hookActive = data.hookActive;
      updateStatus();
      return;
    }
  } catch (e) { console.error('Apply failed:', e); }

  // Bridge unreachable
  state.javaConnected = false;
  state.hookActive = false;
  updateStatus();
  alert('Mappings saved, but the desktop app is not running.\n\nStart the Key Remapper desktop app (run.bat) to activate system-wide key remapping.');
});

document.getElementById('btn-disable').addEventListener('click', async () => {
  try {
    const res = await fetch('/api/hook/disable', { method: 'POST' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data && !data.error) {
      state.javaConnected = true;
      state.hookActive = data.hookActive;
      updateStatus();
      return;
    }
  } catch (e) { console.error('Disable failed:', e); }

  state.javaConnected = false;
  state.hookActive = false;
  updateStatus();
  if (!state.javaConnected) {
    alert('Desktop app is not running. Nothing to disable.');
  }
});

document.getElementById('btn-restore').addEventListener('click', async () => {
  if (!confirm('Clear all mappings and restore defaults?')) return;
  state.pendingMappings = {};
  state.selectedVk = -1;
  const p = state.profiles[state.activeIndex];
  p.mappings = [];
  drawKeyboard();
  updatePickerHighlight();
  saveProfiles();

  try {
    const res = await fetch('/api/hook/restore', { method: 'POST' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data && !data.error) {
      state.javaConnected = true;
      state.hookActive = data.hookActive;
      updateStatus();
      return;
    }
  } catch (e) { console.error('Restore failed:', e); }

  state.hookActive = false;
  updateStatus();
});

// ================================================================
//  Tab switching
// ================================================================

document.querySelectorAll('.tab').forEach(tab => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
    tab.classList.add('active');
    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
    if (tab.dataset.tab === 'key-settings') {
      requestAnimationFrame(resizeCanvas);
    }
  });
});

// ================================================================
//  Macro panel
// ================================================================

function renderMacroList() {
  const p = state.profiles[state.activeIndex];
  const ul = document.getElementById('macro-list');
  ul.innerHTML = '';
  (p.macros || []).forEach((m, i) => {
    const li = document.createElement('li');
    li.textContent = m.name;
    if (i === state.selectedMacroIdx) li.classList.add('active');
    li.addEventListener('click', () => {
      state.selectedMacroIdx = i;
      state.selectedActionIdx = -1;
      renderMacroList();
      renderMacroActions();
      syncMacroSettings();
    });
    ul.appendChild(li);
  });
  if (state.selectedMacroIdx >= (p.macros || []).length) {
    state.selectedMacroIdx = -1;
  }
  renderMacroActions();
}

function renderMacroActions() {
  const tbody = document.getElementById('macro-table-body');
  tbody.innerHTML = '';
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0 || !p.macros || !p.macros[state.selectedMacroIdx]) return;
  const macro = p.macros[state.selectedMacroIdx];
  (macro.actions || []).forEach((a, i) => {
    const tr = document.createElement('tr');
    if (i === state.selectedActionIdx) tr.classList.add('selected');
    tr.addEventListener('click', () => {
      state.selectedActionIdx = i;
      renderMacroActions();
    });
    const eventLabel = a.type === 'KEY_DOWN' ? 'Key Down' : a.type === 'KEY_UP' ? 'Key Up' : 'Delay';
    const keyLabel = a.type === 'DELAY' ? '' : vkLabel(a.keyCode);
    for (const text of [String(i + 1), eventLabel, keyLabel, String(a.delayMs)]) {
      const td = document.createElement('td');
      td.textContent = text;
      tr.appendChild(td);
    }
    tbody.appendChild(tr);
  });
}

function syncMacroSettings() {
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0 || !p.macros) return;
  const macro = p.macros[state.selectedMacroIdx];
  document.getElementById('macro-auto-delay').checked = !!macro.autoInsertDelay;
  document.querySelectorAll('input[name="cycle-mode"]').forEach(r => {
    r.checked = r.value === (macro.cycleMode || 'UNTIL_RELEASED');
  });
  document.getElementById('macro-cycle-count').value = macro.cycleCount || 1;
}

document.getElementById('macro-add').addEventListener('click', () => {
  const name = prompt('Macro name:');
  if (!name || !name.trim()) return;
  const trimmed = name.trim().slice(0, 50);
  const p = state.profiles[state.activeIndex];
  if (!p.macros) p.macros = [];
  p.macros.push({ id: crypto.randomUUID(), name: trimmed, actions: [], autoInsertDelay: false, cycleMode: 'UNTIL_RELEASED', cycleCount: 1 });
  state.selectedMacroIdx = p.macros.length - 1;
  saveProfiles();
  renderMacroList();
});

document.getElementById('macro-rename').addEventListener('click', () => {
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0 || !p.macros) return;
  const macro = p.macros[state.selectedMacroIdx];
  const newName = prompt('New name for "' + macro.name + '":', macro.name);
  if (!newName || !newName.trim() || newName.trim() === macro.name) return;
  macro.name = newName.trim().slice(0, 50);
  saveProfiles();
  renderMacroList();
});

document.getElementById('macro-del').addEventListener('click', () => {
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0 || !p.macros) return;
  if (!confirm(`Delete "${p.macros[state.selectedMacroIdx].name}"?`)) return;
  p.macros.splice(state.selectedMacroIdx, 1);
  state.selectedMacroIdx = -1;
  saveProfiles();
  renderMacroList();
});

document.getElementById('macro-action-modify').addEventListener('click', () => {
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0 || state.selectedActionIdx < 0) return;
  const action = p.macros[state.selectedMacroIdx].actions[state.selectedActionIdx];
  const val = prompt('New delay (ms):', action.delayMs);
  if (val === null) return;
  action.delayMs = Math.max(0, parseInt(val) || 0);
  saveProfiles();
  renderMacroActions();
});

document.getElementById('macro-action-delete').addEventListener('click', () => {
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0 || state.selectedActionIdx < 0) return;
  p.macros[state.selectedMacroIdx].actions.splice(state.selectedActionIdx, 1);
  state.selectedActionIdx = -1;
  saveProfiles();
  renderMacroActions();
});

document.getElementById('macro-action-insert').addEventListener('click', () => {
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0) { alert('Select a macro first.'); return; }
  const macro = p.macros[state.selectedMacroIdx];
  const typeEl = document.getElementById('macro-insert-type');
  const valEl = document.getElementById('macro-insert-value');
  const t = typeEl.value;
  const val = valEl.value.trim();
  if (!val) { alert('Enter a key name or delay value.'); return; }

  const insertIdx = state.selectedActionIdx >= 0 ? state.selectedActionIdx + 1 : macro.actions.length;

  if (t === 'delay') {
    const ms = parseInt(val);
    if (isNaN(ms) || ms < 0) { alert('Invalid delay. Enter a number in milliseconds.'); return; }
    macro.actions.splice(insertIdx, 0, { type: 'DELAY', keyCode: 0, delayMs: ms });
  } else {
    const vk = resolveKey(val);
    if (vk == null) {
      const num = val.startsWith('0x') ? parseInt(val, 16) : parseInt(val);
      if (isNaN(num)) { alert(`Unknown key "${val}". Try names like A, CapsLock, Escape, F1, Space, etc.`); return; }
      macro.actions.splice(insertIdx, 0, { type: t === 'keydown' ? 'KEY_DOWN' : 'KEY_UP', keyCode: num, delayMs: 0 });
    } else {
      macro.actions.splice(insertIdx, 0, { type: t === 'keydown' ? 'KEY_DOWN' : 'KEY_UP', keyCode: vk, delayMs: 0 });
    }
  }
  valEl.value = '';
  saveProfiles();
  renderMacroActions();
});

document.getElementById('macro-insert-type').addEventListener('change', (e) => {
  const valEl = document.getElementById('macro-insert-value');
  valEl.placeholder = e.target.value === 'delay' ? 'Delay in ms (e.g. 100)' : 'Key name (e.g. A, Space, F1)';
  valEl.value = '';
});

document.getElementById('macro-auto-delay').addEventListener('change', (e) => {
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0) return;
  p.macros[state.selectedMacroIdx].autoInsertDelay = e.target.checked;
  saveProfiles();
});

document.querySelectorAll('input[name="cycle-mode"]').forEach(r => {
  r.addEventListener('change', () => {
    const p = state.profiles[state.activeIndex];
    if (state.selectedMacroIdx < 0) return;
    p.macros[state.selectedMacroIdx].cycleMode = document.querySelector('input[name="cycle-mode"]:checked').value;
    saveProfiles();
  });
});

document.getElementById('macro-cycle-count').addEventListener('change', (e) => {
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0) return;
  p.macros[state.selectedMacroIdx].cycleCount = Math.max(1, parseInt(e.target.value) || 1);
  saveProfiles();
});

// ================================================================
//  Macro Record / Play  (through Java bridge)
// ================================================================

const recordBtn = document.getElementById('macro-record');
const playBtn = document.getElementById('macro-play');
let isRecording = false;
let isPlaying = false;

function updateMacroBridgeNote(msg) {
  const el = document.getElementById('macro-bridge-note');
  if (el) el.textContent = msg;
}

recordBtn.addEventListener('click', async () => {
  if (!state.javaConnected) {
    alert('Recording requires the desktop app to be running.\n\nStart the Key Remapper desktop app (run.bat) first.');
    return;
  }
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0) { alert('Select or create a macro first.'); return; }

  if (isRecording) {
    try {
      const res = await fetch('/api/macro/record/stop', { method: 'POST' });
      const data = await res.json();
      if (data.error) {
        updateMacroBridgeNote('Error stopping record: ' + data.error);
      } else if (data.actions && data.actions.length > 0) {
        p.macros[state.selectedMacroIdx].actions = data.actions;
        saveProfiles();
        renderMacroActions();
        updateMacroBridgeNote(`Recorded ${data.actions.length} action(s).`);
      } else {
        updateMacroBridgeNote('No keys were recorded.');
      }
    } catch (err) {
      updateMacroBridgeNote('Failed to stop recording: ' + err.message);
    }
    isRecording = false;
    recordBtn.textContent = 'Record';
    recordBtn.classList.remove('btn-danger');
  } else {
    const autoDelay = document.getElementById('macro-auto-delay').checked;
    try {
      const res = await fetch('/api/macro/record/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ autoDelay })
      });
      const data = await res.json();
      if (data.error) {
        alert('Cannot start recording: ' + data.error + '\n\nMake sure the desktop app is up to date (rebuild with run.bat).');
        return;
      }
      if (data.status === 'recording' || data.status === 'already_recording') {
        isRecording = true;
        recordBtn.textContent = 'Stop Recording';
        recordBtn.classList.add('btn-danger');
        updateMacroBridgeNote('Recording... Press keys, then click Stop Recording.');
      }
    } catch (err) {
      alert('Failed to start recording: ' + err.message + '\n\nIs the desktop app running?');
    }
  }
});

playBtn.addEventListener('click', async () => {
  if (!state.javaConnected) {
    alert('Playback requires the desktop app to be running.\n\nStart the Key Remapper desktop app (run.bat) first.');
    return;
  }
  const p = state.profiles[state.activeIndex];
  if (state.selectedMacroIdx < 0) { alert('Select a macro first.'); return; }
  const macro = p.macros[state.selectedMacroIdx];
  if (!macro.actions || macro.actions.length === 0) { alert('This macro has no actions to play.'); return; }

  if (isPlaying) {
    try { await fetch('/api/macro/play/stop', { method: 'POST' }); } catch (e) { console.error('Stop playback failed:', e); }
    isPlaying = false;
    playBtn.textContent = 'Play';
    playBtn.classList.remove('btn-danger');
    updateMacroBridgeNote('Playback stopped.');
    return;
  }

  try {
    const res = await fetch('/api/macro/play', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        actions: macro.actions,
        cycleMode: macro.cycleMode || 'SPECIFIED_TIMES',
        cycleCount: macro.cycleCount || 1
      })
    });
    const data = await res.json();
    if (data.error) {
      alert('Cannot play macro: ' + data.error + '\n\nMake sure the desktop app is up to date (rebuild with run.bat).');
      return;
    }
    if (data.status === 'playing' || data.status === 'already_playing') {
      isPlaying = true;
      playBtn.textContent = 'Stop';
      playBtn.classList.add('btn-danger');
      updateMacroBridgeNote('Playing macro...');
      if (macro.cycleMode !== 'UNTIL_RELEASED') {
        const totalMs = macro.actions.reduce((sum, a) => sum + (a.delayMs || 0), 0) * (macro.cycleCount || 1) + 500;
        setTimeout(() => {
          if (isPlaying) {
            isPlaying = false;
            playBtn.textContent = 'Play';
            playBtn.classList.remove('btn-danger');
            updateMacroBridgeNote('Playback finished.');
          }
        }, Math.min(totalMs, 30000));
      }
    }
  } catch (err) {
    alert('Failed to start playback: ' + err.message + '\n\nIs the desktop app running?');
  }
});

// ================================================================
//  KeySup Chat
// ================================================================

const chatMessages = document.getElementById('chat-messages');
const chatInput = document.getElementById('chat-input');
const chatSend = document.getElementById('chat-send');

const CMD_PATTERN = /`?\[CMD:(\w+)\(([^)]*)\)\]`?/g;

function buildSystemPrompt() {
  let appState;
  try {
    appState = executeCommand('list_profiles','') + '\n' +
               executeCommand('show_mappings','') + '\n' +
               executeCommand('list_macros','');
  } catch { appState = '(unable to read state)'; }

  return `You are KeySup, a friendly AI keyboard assistant with FULL CONTROL over the Key Remapper web app.
You DIRECTLY CONTROL the app by including command tags in your responses. Every command you include is automatically executed.
You also answer general questions as a helpful chatbot.

## HOW TO EXECUTE COMMANDS
When the user asks you to do something, you MUST include the command tag on its own line:
[CMD:function_name(arguments)]

The app parses your response, executes every [CMD:...] tag it finds, and shows the result inline.

## AVAILABLE COMMANDS

### Profile Management
[CMD:create_profile(name)] - Create a new profile
[CMD:delete_profile(name)] - Delete a profile by name or number
[CMD:rename_profile(old_name, new_name)] - Rename a profile
[CMD:list_profiles()] - List all profiles with active indicator
[CMD:switch_profile(name)] - Switch to a profile by name or number

### Key Remapping
[CMD:map_key(from_key, to_key)] - Remap one key to another
[CMD:swap_keys(key1, key2)] - Swap two keys bidirectionally
[CMD:remove_mapping(key)] - Remove a single remapping
[CMD:clear_mappings()] - Clear ALL remappings
[CMD:show_mappings()] - Display current remappings
[CMD:apply()] - Apply remappings system-wide (needs desktop app)
[CMD:restore()] - Restore all keys to default
[CMD:disable()] - Disable the keyboard hook without clearing mappings

### Macro Management
[CMD:create_macro(name)] - Create a new empty macro
[CMD:delete_macro(name)] - Delete a macro
[CMD:rename_macro(old_name, new_name)] - Rename a macro
[CMD:select_macro(name_or_number)] - Select a macro for editing / recording
[CMD:list_macros()] - List all macros in the active profile
[CMD:add_macro_key(macro_name, key)] - Add a key press (down+up) to a macro
[CMD:clear_macro(name)] - Remove all actions from a macro
[CMD:macro_record_start(autoDelay)] - Start recording key events into the selected macro (autoDelay: true or false; requires desktop app)
[CMD:macro_record_stop()] - Stop recording and save actions into the selected macro
[CMD:macro_play(name)] - Play a macro by name, or [CMD:macro_play()] for the selected macro (requires desktop app)
[CMD:macro_play_stop()] - Stop macro playback

### UI Navigation
[CMD:switch_tab(tab_name)] - Switch to a tab: "key-settings", "macros", or "chat"
[CMD:select_key(key)] - Highlight a key on the virtual keyboard
[CMD:help()] - Show available commands to the user

Recording and playing macros require the Key Remapper desktop app (run.bat) and a server that proxies /api/macro/* to Java (the bundled Python server does this). Apply/remap system-wide also needs the desktop app.

## KEY NAMES
Letters: A-Z | Numbers: 0-9 | Function: F1-F12
Escape, CapsLock, Space, Enter, Tab, Backspace, Delete, Insert
Home, End, PageUp, PageDown, Left, Right, Up, Down
Shift, Ctrl, Alt, LShift, RShift, LCtrl, RCtrl, LAlt, RAlt
Win, PrintScreen, ScrollLock, Pause, NumLock, Num0-Num9, App, Menu

## EXAMPLES

User: "remap caps lock to escape"
You: Sure! Remapping CapsLock to Escape.
[CMD:map_key(CapsLock, Escape)]
Done! Click Apply or say "apply" to activate.

User: "swap A and B keys and apply"
You: Swapping A and B and applying!
[CMD:swap_keys(A, B)]
[CMD:apply()]
All set!

User: "make a gaming profile and map WASD to arrow keys"
You: Creating a Gaming profile and setting up WASD arrow mapping!
[CMD:create_profile(Gaming)]
[CMD:switch_profile(Gaming)]
[CMD:map_key(W, Up)]
[CMD:map_key(A, Left)]
[CMD:map_key(S, Down)]
[CMD:map_key(D, Right)]
Your Gaming profile is ready with WASD mapped to arrows!

User: "create a macro called Greet that types hi"
You: Creating the Greet macro!
[CMD:create_macro(Greet)]
[CMD:add_macro_key(Greet, H)]
[CMD:add_macro_key(Greet, I)]
Your Greet macro is ready!

User: "create a macro with only number inputs"
You: I'll create a NumberKeys macro with all number keys!
[CMD:create_macro(NumberKeys)]
[CMD:add_macro_key(NumberKeys, 0)]
[CMD:add_macro_key(NumberKeys, 1)]
[CMD:add_macro_key(NumberKeys, 2)]
[CMD:add_macro_key(NumberKeys, 3)]
[CMD:add_macro_key(NumberKeys, 4)]
[CMD:add_macro_key(NumberKeys, 5)]
[CMD:add_macro_key(NumberKeys, 6)]
[CMD:add_macro_key(NumberKeys, 7)]
[CMD:add_macro_key(NumberKeys, 8)]
[CMD:add_macro_key(NumberKeys, 9)]
Done! The NumberKeys macro has all digits 0-9.

User: "show me the macros tab"
You: Switching to the Macros tab!
[CMD:switch_tab(macros)]
Here you go!

User: "what's the weather like?"
You: I'm a keyboard assistant so I can't check the weather, but I'm great at remapping keys! Need help with anything?

## RULES
1. ALWAYS include [CMD:...] tags when the user asks to perform ANY app action. Never just describe what you would do — EXECUTE IT IMMEDIATELY.
2. Do NOT ask clarifying questions if you can reasonably infer the user's intent. Pick sensible defaults and execute immediately. For example, if the user says "create a macro with number keys", pick a name like "NumberKeys" and add all number keys right away.
3. You can and SHOULD include multiple commands in one response when the task requires it.
4. Keep responses concise — a short sentence before/after the commands is enough.
5. For non-keyboard questions, chat normally without commands, but always offer to help with keyboard tasks.
6. NEVER wrap commands in code blocks or backticks. Write them as plain text on their own line.
7. If the user asks something vague like "set up gaming keys", take initiative — create a profile, add common gaming mappings (WASD to arrows, etc.), and tell the user what you did.

## CURRENT APP STATE
` + appState;
}

function seedFewShotExamples() {
  state.chatHistory.push(
    { role: 'user', content: 'Create a profile called Gaming' },
    { role: 'assistant', content: "Sure! Creating a Gaming profile for you.\n[CMD:create_profile(Gaming)]\nDone!" },
    { role: 'user', content: 'Map CapsLock to Escape' },
    { role: 'assistant', content: "Remapping CapsLock to Escape.\n[CMD:map_key(CapsLock, Escape)]\nAll set! Type \"apply\" to activate." },
    { role: 'user', content: 'Create a macro with number keys' },
    { role: 'assistant', content: "Creating a NumberKeys macro with all digits!\n[CMD:create_macro(NumberKeys)]\n[CMD:add_macro_key(NumberKeys, 0)]\n[CMD:add_macro_key(NumberKeys, 1)]\n[CMD:add_macro_key(NumberKeys, 2)]\n[CMD:add_macro_key(NumberKeys, 3)]\n[CMD:add_macro_key(NumberKeys, 4)]\n[CMD:add_macro_key(NumberKeys, 5)]\n[CMD:add_macro_key(NumberKeys, 6)]\n[CMD:add_macro_key(NumberKeys, 7)]\n[CMD:add_macro_key(NumberKeys, 8)]\n[CMD:add_macro_key(NumberKeys, 9)]\nYour NumberKeys macro is ready with digits 0-9!" },
    { role: 'user', content: 'Clear those and delete that profile' },
    { role: 'assistant', content: "Cleaning up!\n[CMD:clear_mappings()]\n[CMD:delete_profile(Gaming)]\nDone! Everything is cleaned up." }
  );
}

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
    case 'apply':
      saveProfiles();
      fetch('/api/hook/apply', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mappings: state.pendingMappings })
      }).then(r => r.json()).then(handleBridgeResponse).catch(() => {
        state.hookActive = false; updateStatus();
      });
      return state.javaConnected
        ? `Applied ${Object.keys(state.pendingMappings).length} mapping(s) system-wide.`
        : `Mappings saved (${Object.keys(state.pendingMappings).length}). Start the desktop app to activate.`;
    case 'restore':
      state.pendingMappings = {};
      state.profiles[state.activeIndex].mappings = [];
      drawKeyboard();
      saveProfiles();
      fetch('/api/hook/restore', { method: 'POST' })
        .then(r => r.json()).then(handleBridgeResponse).catch(() => {
          state.hookActive = false; updateStatus();
        });
      return 'Restored defaults.';
    case 'create_profile': {
      const name = a[0];
      state.profiles.push({ id: crypto.randomUUID(), name, mappings: [], macros: [] });
      renderProfileList();
      debouncedSave();
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
      debouncedSave();
      return `Deleted "${n}".`;
    }
    case 'rename_profile': {
      const idx = findProfile(a[0]);
      if (idx < 0) return 'Profile not found.';
      const old = state.profiles[idx].name;
      state.profiles[idx].name = a[1];
      renderProfileList();
      debouncedSave();
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
      debouncedSave();
      return `Switched to "${state.profiles[idx].name}".`;
    }
    case 'create_macro': {
      const p = state.profiles[state.activeIndex];
      if (!p.macros) p.macros = [];
      p.macros.push({ id: crypto.randomUUID(), name: a[0], actions: [], autoInsertDelay: false, cycleMode: 'UNTIL_RELEASED', cycleCount: 1 });
      renderMacroList();
      debouncedSave();
      return `Created macro "${a[0]}".`;
    }
    case 'delete_macro': {
      const p = state.profiles[state.activeIndex];
      const i = (p.macros||[]).findIndex(m => m.name.toLowerCase() === a[0].toLowerCase());
      if (i < 0) return 'Macro not found.';
      const n = p.macros[i].name;
      p.macros.splice(i, 1);
      renderMacroList();
      debouncedSave();
      return `Deleted macro "${n}".`;
    }
    case 'list_macros': {
      const macros = state.profiles[state.activeIndex].macros || [];
      if (!macros.length) return 'No macros.';
      return macros.map((m,i) => `  ${i+1}. ${m.name} (${m.actions.length} actions)`).join('\n');
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
      debouncedSave();
      return `Added ${vkLabel(vk)} press to "${macro.name}".`;
    }
    case 'clear_macro': {
      const p = state.profiles[state.activeIndex];
      const m = (p.macros||[]).find(m => m.name.toLowerCase() === a[0].toLowerCase());
      if (!m) return 'Macro not found.';
      const cnt = m.actions.length;
      m.actions = [];
      renderMacroActions();
      debouncedSave();
      return `Cleared ${cnt} action(s) from "${m.name}".`;
    }
    case 'select_macro': {
      const idx = findMacro(a[0]);
      if (idx < 0) return 'Macro not found.';
      const macros = state.profiles[state.activeIndex].macros || [];
      state.selectedMacroIdx = idx;
      renderMacroList();
      renderMacroActions();
      return `Selected macro "${macros[idx].name}".`;
    }
    case 'rename_macro': {
      const p = state.profiles[state.activeIndex];
      const macros = p.macros || [];
      const i = macros.findIndex(m => m.name.toLowerCase() === a[0].toLowerCase());
      if (i < 0) return 'Macro not found.';
      if (!a[1]) return 'New name required.';
      const old = macros[i].name;
      macros[i].name = a[1];
      renderMacroList();
      debouncedSave();
      return `Renamed macro "${old}" to "${a[1]}".`;
    }
    case 'switch_tab': {
      const tabName = a[0].toLowerCase().replace(/[\s_\-]/g, '');
      let targetTab = null;
      if (/key|setting|remap|map/.test(tabName)) targetTab = 'key-settings';
      else if (/macro/.test(tabName)) targetTab = 'macro';
      else if (/chat|ai|bot|keysup|support/.test(tabName)) targetTab = 'keysup';
      if (!targetTab) return `Unknown tab "${a[0]}". Use "key-settings", "macro", or "keysup".`;
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
      const btn = document.querySelector(`.tab[data-tab="${targetTab}"]`);
      if (btn) btn.classList.add('active');
      document.getElementById('tab-' + targetTab)?.classList.add('active');
      if (targetTab === 'key-settings') { resizeCanvas(); drawKeyboard(); }
      return `Switched to ${targetTab} tab.`;
    }
    case 'select_key': {
      const vk = resolveKey(a[0]);
      if (vk == null) return 'Unknown key: ' + a[0];
      state.selectedVk = vk;
      drawKeyboard();
      updatePickerHighlight();
      executeCommand('switch_tab', 'key-settings');
      return `Selected ${vkLabel(vk)} on the keyboard.`;
    }
    case 'disable':
      fetch('/api/hook/disable', { method: 'POST' })
        .then(r => r.json()).then(handleBridgeResponse).catch(() => {
          state.hookActive = false; updateStatus();
        });
      return state.javaConnected ? 'Disabled keyboard hook.' : 'Hook disabled (desktop app not connected).';
    case 'help':
      return "I can help you with:\n" +
        "\nProfile Management:\n  create profile [name]\n  delete profile [name]\n  rename profile [old] to [new]\n  list profiles\n  switch to profile [name]\n" +
        "\nKey Remapping:\n  map [key] to [key]\n  swap [key] and [key]\n  remove mapping [key]\n  clear mappings\n  show mappings\n  apply / restore / disable\n" +
        "\nMacros:\n  create / delete / rename macro\n  select macro [name]\n  add [key] to macro [name]\n  clear macro [name]\n  list macros\n  start/stop macro recording (desktop app)\n  play macro [name] / stop playback\n" +
        "\nNavigation:\n  go to [tab name] (key-settings, macros, chat)\n  select [key] on keyboard\n" +
        "\nKey names: A-Z, 0-9, F1-F12, Escape, CapsLock, Space, Enter, Tab, Backspace, Shift, Ctrl, Alt, Delete, Insert, Home, End, PageUp, PageDown, Left, Right, Up, Down, and more.";
    default:
      return `Unknown command: ${cmd}`;
  }
}

const ASYNC_COMMANDS = new Set(['macro_record_start', 'macro_record_stop', 'macro_play', 'macro_play_stop']);

async function runAsyncCommand(cmd, args) {
  const a = args.split(',').map(s => s.trim());
  switch (cmd) {
    case 'macro_record_start': {
      if (!state.javaConnected) {
        return 'Recording requires the Key Remapper desktop app (run.bat) with the web server that proxies to Java.';
      }
      const p = state.profiles[state.activeIndex];
      if (state.selectedMacroIdx < 0 || !p.macros || !p.macros[state.selectedMacroIdx]) {
        return 'Select or create a macro first ([CMD:select_macro(name)] or [CMD:create_macro(name)]).';
      }
      const autoDelay = a[0] === 'true' || /^(1|yes|on)$/i.test(a[0] || '');
      let data;
      try {
        const res = await fetch('/api/macro/record/start', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ autoDelay })
        });
        data = await res.json();
      } catch (e) {
        return 'Failed to start recording: ' + (e.message || 'network error');
      }
      if (data.error) return 'Cannot start recording: ' + data.error;
      if (data.status === 'recording' || data.status === 'already_recording') {
        return 'Recording started. Press keys on the physical keyboard, then run [CMD:macro_record_stop()].';
      }
      return typeof data.status === 'string' ? data.status : JSON.stringify(data);
    }
    case 'macro_record_stop': {
      if (!state.javaConnected) return 'Desktop app not connected.';
      const p = state.profiles[state.activeIndex];
      if (state.selectedMacroIdx < 0 || !p.macros || !p.macros[state.selectedMacroIdx]) {
        return 'No macro selected.';
      }
      let data;
      try {
        const res = await fetch('/api/macro/record/stop', { method: 'POST' });
        data = await res.json();
      } catch (e) {
        return 'Failed to stop recording: ' + (e.message || 'network error');
      }
      if (data.error) return 'Error: ' + data.error;
      const macro = p.macros[state.selectedMacroIdx];
      if (data.actions && data.actions.length > 0) {
        macro.actions = data.actions;
        await saveProfiles();
        renderMacroActions();
        return `Recorded ${data.actions.length} action(s) into "${macro.name}" (saved to profile).`;
      }
      return 'No keys were recorded.';
    }
    case 'macro_play': {
      if (!state.javaConnected) {
        return 'Playback requires the Key Remapper desktop app (run.bat).';
      }
      const p = state.profiles[state.activeIndex];
      let macro = null;
      if (a[0]) {
        const idx = findMacro(a[0]);
        if (idx < 0) return 'Macro not found.';
        state.selectedMacroIdx = idx;
        macro = p.macros[idx];
        renderMacroList();
        renderMacroActions();
      } else {
        if (state.selectedMacroIdx < 0 || !p.macros || !p.macros[state.selectedMacroIdx]) {
          return 'Select a macro first ([CMD:select_macro(name)]) or pass the macro name: [CMD:macro_play(name)].';
        }
        macro = p.macros[state.selectedMacroIdx];
      }
      if (!macro.actions || macro.actions.length === 0) return 'Macro has no actions to play.';
      let data;
      try {
        const res = await fetch('/api/macro/play', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            actions: macro.actions,
            cycleMode: macro.cycleMode || 'SPECIFIED_TIMES',
            cycleCount: macro.cycleCount || 1
          })
        });
        data = await res.json();
      } catch (e) {
        return 'Failed to play: ' + (e.message || 'network error');
      }
      if (data.error) return 'Cannot play: ' + data.error;
      if (data.status === 'playing' || data.status === 'already_playing') {
        return `Playing macro "${macro.name}"...`;
      }
      return data.message || JSON.stringify(data);
    }
    case 'macro_play_stop': {
      let data;
      try {
        const res = await fetch('/api/macro/play/stop', { method: 'POST' });
        data = await res.json();
      } catch (e) {
        return 'Failed to stop: ' + (e.message || 'network error');
      }
      if (data.error) return 'Error: ' + data.error;
      return 'Playback stopped.';
    }
    default:
      return `Unknown async command: ${cmd}`;
  }
}

async function runCommand(cmd, args) {
  if (ASYNC_COMMANDS.has(cmd)) {
    return runAsyncCommand(cmd, args);
  }
  return executeCommand(cmd, args);
}

function findProfile(id) {
  const trimmed = id.trim();
  const num = parseInt(trimmed);
  if (!isNaN(num) && num >= 1 && num <= state.profiles.length) return num - 1;
  return state.profiles.findIndex(p => p.name.toLowerCase() === trimmed.toLowerCase());
}

function addChatBubble(text, cls) {
  const div = document.createElement('div');
  div.className = 'chat-bubble ' + cls;
  div.textContent = text;
  chatMessages.appendChild(div);
  chatMessages.scrollTop = chatMessages.scrollHeight;
  return div;
}

async function sendChat(text) {
  if (!text.trim()) return;
  addChatBubble(text, 'user');
  chatInput.value = '';
  chatInput.disabled = true;
  chatSend.disabled = true;

  try {
    if (!state.ollamaOnline) {
      const reply = await processOffline(text);
      addChatBubble(reply, 'bot');
      return;
    }

    state.chatHistory.push({ role: 'user', content: text });
    if (state.chatHistory.length > 100) state.chatHistory = state.chatHistory.slice(-50);
    const messages = [
      { role: 'system', content: buildSystemPrompt() },
      ...state.chatHistory.slice(-20)
    ];

    const bubble = addChatBubble('Thinking...', 'bot');
    let full = '';

    try {
      const res = await fetch('/api/ollama/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messages })
      });

      if (!res.ok) {
        bubble.textContent = 'Ollama returned an error (HTTP ' + res.status + '). Is Ollama running?';
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop();
        for (const line of lines) {
          if (!line.trim()) continue;
          try {
            const obj = JSON.parse(line);
            if (obj.message?.content) {
              full += obj.message.content;
              bubble.textContent = full.replace(/`?\[CMD:\w+\([^)]*\)\]`?/g, '').trim() || 'Processing...';
              chatMessages.scrollTop = chatMessages.scrollHeight;
            }
          } catch {}
        }
      }

      if (buffer.trim()) {
        try {
          const obj = JSON.parse(buffer);
          if (obj.message?.content) full += obj.message.content;
        } catch {}
      }

      if (!full.trim()) {
        bubble.textContent = '(Empty response from AI)';
        return;
      }

      state.chatHistory.push({ role: 'assistant', content: full });

      let displayText = full;
      const cmdRegex = /`?\[CMD:(\w+)\(([^)]*)\)\]`?/g;
      const allMatches = [...full.matchAll(cmdRegex)];

      for (const match of allMatches) {
        try {
          const result = await runCommand(match[1], match[2]);
          displayText = displayText.replace(match[0], '\u2705 ' + result);
        } catch (cmdErr) {
          displayText = displayText.replace(match[0], '\u274C Error: ' + cmdErr.message);
        }
      }

      // Fallback: if AI returned no commands, try offline parser on the original input
      if (allMatches.length === 0) {
        const fallback = await processOffline(text);
        if (!fallback.startsWith("I didn't understand")) {
          displayText = displayText + '\n\n' + fallback;
        }
      }

      bubble.textContent = displayText;
    } catch (err) {
      if (full) {
        state.chatHistory.push({ role: 'assistant', content: full });
        let displayText = full;
        for (const match of full.matchAll(/`?\[CMD:(\w+)\(([^)]*)\)\]`?/g)) {
          try {
            const result = await runCommand(match[1], match[2]);
            displayText = displayText.replace(match[0], '\u2705 ' + result);
          } catch (cmdErr) { console.error('Command failed:', cmdErr); }
        }
        bubble.textContent = displayText + '\n\n(Connection interrupted)';
      } else {
        bubble.textContent = 'Error communicating with Ollama: ' + (err.message || 'Connection failed');
      }
    }
  } finally {
    chatInput.disabled = false;
    chatSend.disabled = false;
    chatInput.focus();
  }
}

async function processOffline(text) {
  const input = text.trim();
  const t = input.toLowerCase();
  let m;

  if (/^(help|commands?|what can you do|how to use)/i.test(t))
    return executeCommand('help', '');

  // --- Profile commands ---
  m = t.match(/(?:create|add|make|new)\s+(?:a\s+)?profile\s+(?:named?\s+)?["']?(.+?)["']?$/);
  if (m) return executeCommand('create_profile', m[1].trim());

  m = t.match(/(?:delete|remove)\s+profile\s+["']?(.+?)["']?$/);
  if (m) return executeCommand('delete_profile', m[1].trim());

  m = t.match(/rename\s+profile\s+["']?(.+?)["']?\s+to\s+["']?(.+?)["']?$/);
  if (m) return executeCommand('rename_profile', m[1].trim() + ',' + m[2].trim());

  if (/(?:list|show|display)\s+(?:all\s+)?profiles?/i.test(t))
    return executeCommand('list_profiles', '');

  m = t.match(/(?:switch|change|use|select|load|activate)\s+(?:to\s+)?profile\s+["']?(.+?)["']?$/);
  if (m) return executeCommand('switch_profile', m[1].trim());

  if (/(?:current|active|which)\s+profile/i.test(t))
    return executeCommand('list_profiles', '');

  // --- Key mapping commands ---
  m = t.match(/(?:swap|switch|exchange)\s+(?:keys?\s+)?["']?(.+?)["']?\s+(?:and|with|&)\s+["']?(.+?)["']?$/);
  if (m) return executeCommand('swap_keys', m[1].trim() + ',' + m[2].trim());

  if (/(?:clear|reset|remove\s+all)\s+(?:all\s+)?mappings?/i.test(t))
    return executeCommand('clear_mappings', '');

  m = t.match(/(?:map|remap|change|set|bind|assign)\s+(?:key\s+)?["']?(.+?)["']?\s+(?:to|->|→|=>)\s+(?:key\s+)?["']?(.+?)["']?$/);
  if (m) return executeCommand('map_key', m[1].trim() + ',' + m[2].trim());

  m = t.match(/(?:remove|delete|unmap|unbind)\s+(?:mapping\s+(?:for|of)\s+)?(?:key\s+)?["']?(.+?)["']?$/);
  if (m && !/(?:all\s+)?mappings?|profile/i.test(m[1])) return executeCommand('remove_mapping', m[1].trim());

  if (/(?:show|list|display|what are)\s+(?:the\s+)?(?:current\s+)?mappings?/i.test(t))
    return executeCommand('show_mappings', '');

  if (/(?:apply|activate|enable|start|turn\s+on)(?:\s+(?:the\s+)?mappings?)?$/i.test(t))
    return executeCommand('apply', '');

  if (/(?:restore|deactivate|stop|turn\s+off)(?:\s+(?:defaults?|mappings?))?$/i.test(t))
    return executeCommand('restore', '');

  if (/(?:disable)(?:\s+(?:the\s+)?(?:hook|mappings?|keyboard))?$/i.test(t))
    return executeCommand('disable', '');

  // --- Macro commands ---
  m = t.match(/(?:create|add|make|new)\s+(?:a\s+)?macro\s+(?:named?\s+|called\s+)?["']?(.+?)["']?$/);
  if (m) return executeCommand('create_macro', m[1].trim());

  m = t.match(/(?:delete|remove)\s+macro\s+["']?(.+?)["']?$/);
  if (m) return executeCommand('delete_macro', m[1].trim());

  if (/(?:list|show|display)\s+(?:all\s+)?macros?/i.test(t))
    return executeCommand('list_macros', '');

  m = t.match(/(?:add)\s+(?:key\s+)?["']?(.+?)["']?\s+(?:to|into)\s+(?:macro\s+)?["']?(.+?)["']?$/);
  if (m) return executeCommand('add_macro_key', m[2].trim() + ',' + m[1].trim());

  m = t.match(/(?:clear|reset)\s+macro\s+["']?(.+?)["']?$/);
  if (m) return executeCommand('clear_macro', m[1].trim());

  m = t.match(/rename\s+macro\s+["']?(.+?)["']?\s+to\s+["']?(.+?)["']?$/);
  if (m) return executeCommand('rename_macro', m[1].trim() + ',' + m[2].trim());

  m = t.match(/(?:select|choose|pick)\s+macro\s+["']?(.+?)["']?$/);
  if (m) return executeCommand('select_macro', m[1].trim());

  if (/(?:stop|finish)\s+(?:macro\s+)?recording/i.test(t))
    return await runCommand('macro_record_stop', '');

  if (/(?:start|begin)\s+(?:macro\s+)?recording/i.test(t)) {
    const auto = /auto\s*delay|with\s+auto/i.test(t);
    return await runCommand('macro_record_start', auto ? 'true' : 'false');
  }

  m = t.match(/^play\s+macro\s+["']?(.+?)["']?$/i);
  if (m && m[1].trim()) return await runCommand('macro_play', m[1].trim());

  if (/^play\s+(?:the\s+)?macro$/i.test(input.trim()))
    return await runCommand('macro_play', '');

  if (/(?:stop)\s+(?:macro\s+)?playback|stop\s+playing(?:\s+macro)?/i.test(t))
    return await runCommand('macro_play_stop', '');

  // --- Tab navigation ---
  m = t.match(/(?:go\s+to|open|show|switch\s+to|navigate\s+to)\s+(?:the\s+)?(.+?)\s*(?:tab|page|section|view)?$/);
  if (m) return executeCommand('switch_tab', m[1].trim());

  m = t.match(/(?:select|highlight|pick|choose)\s+(?:the\s+)?(?:key\s+)?["']?(.+?)["']?\s+(?:on\s+(?:the\s+)?keyboard|key)/);
  if (m) return executeCommand('select_key', m[1].trim());

  // --- General conversation ---
  if (/^(hi|hello|hey|howdy|yo|sup|greetings)\b/i.test(t))
    return pick("Hey there! How can I help you with your keyboard?",
                "Hello! Need help with your keyboard setup?",
                "Hi! What would you like to do?");

  if (/(thank|thanks|thx|ty)/i.test(t))
    return pick("You're welcome!", "Happy to help!", "Anytime!");

  if (/^(bye|goodbye|see\s*you|cya|later)/i.test(t))
    return pick("Goodbye! Happy typing!", "See you later!", "Bye!");

  if (/(how\s+are\s+you|how.s\s+it\s+going)/i.test(t))
    return "I'm doing great, ready to help with your keyboard!";

  if (/(who|what)\s+are\s+you/i.test(t))
    return "I'm KeySup, a keyboard support assistant built into this app. I help you manage profiles and remap keys. Type \"help\" for commands!";

  if (/what.?s?\s+(?:the\s+)?time/i.test(t))
    return "It's " + new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) + ".";

  if (/what.?s?\s+(?:the\s+)?date|today/i.test(t))
    return "Today is " + new Date().toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }) + ".";

  if (/(?:joke|funny)/i.test(t))
    return pick(
      "Why did the keyboard break up with the mouse? It wasn't her type.",
      "What's a keyboard's favourite snack? Space bars.",
      "I'd tell you a UDP joke, but you might not get it.");

  return "I didn't understand that. Try 'help' for available commands. (Connect Ollama for full AI chat.)";
}

function pick(...opts) { return opts[Math.floor(Math.random() * opts.length)]; }

chatSend.addEventListener('click', () => sendChat(chatInput.value));
chatInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') sendChat(chatInput.value);
});

async function checkOllama() {
  try {
    const res = await fetch('/api/ollama/status');
    const data = await res.json();
    state.ollamaOnline = data.online;
    if (data.online) {
      addChatBubble(`AI connected: ${data.model}`, 'info');
    } else {
      addChatBubble('Ollama offline — using basic commands only.', 'info');
    }
  } catch {
    addChatBubble('Ollama offline — using basic commands only.', 'info');
  }
}

// ================================================================
//  Init
// ================================================================

(async function init() {
  await loadVkLabels();
  buildPicker();
  await loadProfiles();
  resizeCanvas();
  await pollBridgeStatus();
  updateStatus();
  seedFewShotExamples();
  addChatBubble("Hi! I'm KeySup, your keyboard support assistant. Ask me anything about key remapping!", 'bot');
  checkOllama();
  setInterval(pollBridgeStatus, 10000);
})();
