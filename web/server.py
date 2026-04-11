"""Key Remapper Web — Python server (stdlib only, no pip needed)."""

import http.server
import json
import os
import socketserver
import urllib.request
import urllib.error
import uuid
import threading

PORT = 3000
OLLAMA_HOST = os.environ.get('OLLAMA_HOST', 'http://localhost:11434')
OLLAMA_MODEL = os.environ.get('OLLAMA_MODEL', 'gemma3')
JAVA_BRIDGE = os.environ.get('JAVA_BRIDGE', 'http://127.0.0.1:8230')
_DEFAULT_PROFILES_DIR = os.path.join(os.path.expanduser('~'), '.keyremapper')
PROFILES_FILE = os.environ.get(
    'PROFILES_FILE',
    os.path.join(_DEFAULT_PROFILES_DIR, 'profiles.json')
)
PUBLIC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'public')


_profile_lock = threading.RLock()


def load_profiles():
    with _profile_lock:
        try:
            with open(PROFILES_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            defaults = {
                'profiles': [{'id': str(uuid.uuid4()), 'name': 'Profile 1', 'mappings': [], 'macros': []}],
                'activeIndex': 0
            }
            save_profiles(defaults)
            return defaults


def save_profiles(data):
    with _profile_lock:
        os.makedirs(os.path.dirname(PROFILES_FILE), exist_ok=True)
        with open(PROFILES_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)


MIME_TYPES = {
    '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript',
    '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
}


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        # Only log errors (4xx/5xx), silence successful requests
        if args and str(args[1]).startswith(('4', '5')):
            super().log_message(fmt, *args)

    # --- Static files ---
    def serve_static(self, path):
        if path == '/':
            path = '/index.html'
        filepath = os.path.join(PUBLIC_DIR, path.lstrip('/'))
        filepath = os.path.realpath(filepath)
        if not filepath.startswith(os.path.realpath(PUBLIC_DIR)):
            self.send_error(403)
            return
        if not os.path.isfile(filepath):
            self.send_error(404)
            return
        ext = os.path.splitext(filepath)[1].lower()
        mime = MIME_TYPES.get(ext, 'application/octet-stream')
        with open(filepath, 'rb') as f:
            data = f.read()
        self.send_response(200)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', len(data))
        self.end_headers()
        self.wfile.write(data)

    # --- Bridge proxy helper ---
    def _proxy_to_java(self, java_path, method='GET', body=None):
        """Forward a request to the Java bridge server and return the response."""
        url = JAVA_BRIDGE + java_path
        try:
            req = urllib.request.Request(url, method=method,
                                         headers={'Content-Type': 'application/json'})
            if method == 'POST':
                req.data = body if body else b'{}'
            elif body:
                req.data = body
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.read()
        except urllib.error.HTTPError as e:
            return json.dumps({'error': f'Bridge returned HTTP {e.code}'}).encode()
        except Exception:
            return json.dumps({'error': 'Java app not running'}).encode()

    def _send_json(self, code, body):
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', len(body))
        self.end_headers()
        self.wfile.write(body)

    # --- GET ---
    def do_GET(self):
        if self.path == '/api/profiles':
            self._send_json(200, json.dumps(load_profiles()).encode())
        elif self.path == '/api/ollama/status':
            try:
                req = urllib.request.Request(OLLAMA_HOST + '/api/tags', method='GET')
                with urllib.request.urlopen(req, timeout=5) as resp:
                    data = json.loads(resp.read())
                body = json.dumps({'online': True, 'model': OLLAMA_MODEL, 'models': data.get('models', [])}).encode()
            except Exception:
                body = json.dumps({'online': False, 'model': OLLAMA_MODEL}).encode()
            self._send_json(200, body)
        elif self.path == '/api/hook/status':
            self._send_json(200, self._proxy_to_java('/api/status'))
        else:
            self.serve_static(self.path.split('?')[0])

    # --- POST ---
    def _check_origin(self):
        origin = self.headers.get('Origin', '')
        if origin and not (origin.startswith('http://localhost:') or
                           origin.startswith('http://127.0.0.1:')):
            self.send_error(403, 'Forbidden origin')
            return False
        return True

    def do_POST(self):
        if not self._check_origin():
            return
        content_len = int(self.headers.get('Content-Length', 0))
        raw = self.rfile.read(content_len)

        if self.path == '/api/profiles':
            try:
                data = json.loads(raw)
                save_profiles(data)
                body = b'{"ok":true}'
            except Exception as e:
                body = json.dumps({'error': str(e)}).encode()
            self._send_json(200, body)

        elif self.path == '/api/ollama/chat':
            self._proxy_ollama_chat(raw)
        elif self.path == '/api/hook/apply':
            self._send_json(200, self._proxy_to_java('/api/apply', 'POST', raw))
        elif self.path == '/api/hook/disable':
            self._send_json(200, self._proxy_to_java('/api/disable', 'POST', raw))
        elif self.path == '/api/hook/restore':
            self._send_json(200, self._proxy_to_java('/api/restore', 'POST', raw))
        elif self.path == '/api/macro/record/start':
            self._send_json(200, self._proxy_to_java('/api/macro/record/start', 'POST', raw))
        elif self.path == '/api/macro/record/stop':
            self._send_json(200, self._proxy_to_java('/api/macro/record/stop', 'POST', raw or b'{}'))
        elif self.path == '/api/macro/play':
            self._send_json(200, self._proxy_to_java('/api/macro/play', 'POST', raw))
        elif self.path == '/api/macro/play/stop':
            self._send_json(200, self._proxy_to_java('/api/macro/play/stop', 'POST', raw or b'{}'))
        else:
            self.send_error(404)

    def _proxy_ollama_chat(self, raw_body):
        try:
            incoming = json.loads(raw_body)
        except json.JSONDecodeError:
            self.send_error(400)
            return

        payload = json.dumps({
            'model': incoming.get('model', OLLAMA_MODEL),
            'messages': incoming.get('messages', []),
            'stream': True,
            'options': {'temperature': 0.3}
        }).encode()

        req = urllib.request.Request(
            OLLAMA_HOST + '/api/chat',
            data=payload,
            headers={'Content-Type': 'application/json'},
            method='POST'
        )

        try:
            upstream = urllib.request.urlopen(req, timeout=180)
        except Exception:
            self.send_error(502, 'Ollama unreachable')
            return

        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.send_header('Cache-Control', 'no-cache')
        self.send_header('Connection', 'close')
        self.end_headers()
        self.close_connection = True

        try:
            while True:
                chunk = upstream.read(4096)
                if not chunk:
                    break
                self.wfile.write(chunk)
                self.wfile.flush()
        except Exception:
            pass
        finally:
            upstream.close()


class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def main():
    server = ThreadedHTTPServer(('127.0.0.1', PORT), Handler)
    print(f'Key Remapper Web running at http://localhost:{PORT}')
    print(f'Ollama endpoint: {OLLAMA_HOST}  model: {OLLAMA_MODEL}')
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\nShutting down.')
        server.server_close()


if __name__ == '__main__':
    main()
