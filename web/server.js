const express = require('express');
const fs = require('fs');
const path = require('path');
const http = require('http');
const { URL } = require('url');

const app = express();
const PORT = 3000;

const OLLAMA_HOST = process.env.OLLAMA_HOST || 'http://localhost:11434';
const OLLAMA_MODEL = process.env.OLLAMA_MODEL || 'gemma3';
const JAVA_BRIDGE = process.env.JAVA_BRIDGE || 'http://127.0.0.1:8230';
const PROFILES_FILE = process.env.PROFILES_FILE ||
  path.join(require('os').homedir(), '.keyremapper', 'profiles.json');

async function proxyToJava(javaPath, options = {}) {
  const { method = 'GET', body } = options;
  let url;
  try {
    url = new URL(javaPath, JAVA_BRIDGE.endsWith('/') ? JAVA_BRIDGE : JAVA_BRIDGE + '/');
  } catch {
    return { status: 502, body: Buffer.from(JSON.stringify({ error: 'Invalid JAVA_BRIDGE' })) };
  }
  try {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: method === 'POST' ? (body !== undefined ? body : '{}') : undefined
    });
    const buf = Buffer.from(await res.arrayBuffer());
    return { status: res.status, body: buf };
  } catch {
    return { status: 200, body: Buffer.from(JSON.stringify({ error: 'Java app not running' })) };
  }
}

app.use(express.json({ limit: '2mb' }));
app.use(express.static(path.join(__dirname, 'public')));

async function loadProfiles() {
  try {
    const data = await fs.promises.readFile(PROFILES_FILE, 'utf8');
    return JSON.parse(data);
  } catch (_) {}
  const defaults = {
    profiles: [{ id: crypto.randomUUID(), name: 'Profile 1', mappings: [], macros: [] }],
    activeIndex: 0
  };
  await saveProfiles(defaults);
  return defaults;
}

async function saveProfiles(data) {
  await fs.promises.mkdir(path.dirname(PROFILES_FILE), { recursive: true });
  const tmp = PROFILES_FILE + '.tmp';
  await fs.promises.writeFile(tmp, JSON.stringify(data, null, 2));
  await fs.promises.rename(tmp, PROFILES_FILE);
}

app.get('/api/profiles', async (_req, res) => {
  res.json(await loadProfiles());
});

app.post('/api/profiles', async (req, res) => {
  await saveProfiles(req.body);
  res.json({ ok: true });
});

app.get('/api/ollama/status', async (_req, res) => {
  try {
    const url = new URL('/api/tags', OLLAMA_HOST);
    const data = await fetchJSON(url);
    res.json({ online: true, model: OLLAMA_MODEL, models: data.models || [] });
  } catch {
    res.json({ online: false, model: OLLAMA_MODEL });
  }
});

app.get('/api/hook/status', async (_req, res) => {
  const r = await proxyToJava('/api/status');
  res.status(r.status).type('json').send(r.body);
});

app.post('/api/hook/apply', async (req, res) => {
  const raw = JSON.stringify(req.body ?? {});
  const r = await proxyToJava('/api/apply', { method: 'POST', body: raw });
  res.status(r.status).type('json').send(r.body);
});

app.post('/api/hook/disable', async (req, res) => {
  const r = await proxyToJava('/api/disable', { method: 'POST', body: '{}' });
  res.status(r.status).type('json').send(r.body);
});

app.post('/api/hook/restore', async (req, res) => {
  const r = await proxyToJava('/api/restore', { method: 'POST', body: '{}' });
  res.status(r.status).type('json').send(r.body);
});

app.post('/api/macro/record/start', async (req, res) => {
  const raw = JSON.stringify(req.body ?? {});
  const r = await proxyToJava('/api/macro/record/start', { method: 'POST', body: raw });
  res.status(r.status).type('json').send(r.body);
});

app.post('/api/macro/record/stop', async (req, res) => {
  const r = await proxyToJava('/api/macro/record/stop', { method: 'POST', body: '{}' });
  res.status(r.status).type('json').send(r.body);
});

app.post('/api/macro/play', async (req, res) => {
  const raw = JSON.stringify(req.body ?? {});
  const r = await proxyToJava('/api/macro/play', { method: 'POST', body: raw });
  res.status(r.status).type('json').send(r.body);
});

app.post('/api/macro/play/stop', async (req, res) => {
  const r = await proxyToJava('/api/macro/play/stop', { method: 'POST', body: '{}' });
  res.status(r.status).type('json').send(r.body);
});

app.post('/api/ollama/chat', (req, res) => {
  const payload = JSON.stringify({
    model: req.body.model || OLLAMA_MODEL,
    messages: req.body.messages || [],
    stream: true
  });

  const url = new URL('/api/chat', OLLAMA_HOST);
  const options = {
    hostname: url.hostname,
    port: url.port,
    path: url.pathname,
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) },
    timeout: 180000
  };

  const proxyReq = http.request(options, (proxyRes) => {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive'
    });
    proxyRes.on('data', (chunk) => res.write(chunk));
    proxyRes.on('end', () => res.end());
  });

  proxyReq.on('error', () => {
    if (!res.headersSent) res.status(502).json({ error: 'Ollama unreachable' });
  });
  proxyReq.on('timeout', () => { proxyReq.destroy(); });
  proxyReq.write(payload);
  proxyReq.end();
});

function fetchJSON(url) {
  return new Promise((resolve, reject) => {
    http.get(url, { timeout: 5000 }, (res) => {
      let body = '';
      res.on('data', (c) => body += c);
      res.on('end', () => { try { resolve(JSON.parse(body)); } catch { reject(); } });
    }).on('error', reject).on('timeout', function () { this.destroy(); reject(new Error('timeout')); });
  });
}

app.listen(PORT, () => {
  console.log(`Key Remapper Web running at http://localhost:${PORT}`);
  console.log(`Ollama endpoint: ${OLLAMA_HOST}  model: ${OLLAMA_MODEL}`);
  console.log(`Java bridge proxy: ${JAVA_BRIDGE}`);
});
