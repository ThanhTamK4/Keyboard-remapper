"""Integration smoke test for the Key Remapper web server.

Starts the Python server, runs basic API tests, then shuts down.
Run with: python tests/test_integration.py
"""

import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
import urllib.request
import urllib.error


SERVER_SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'web', 'server.py')
TEST_PORT = 18232


class TestIntegration(unittest.TestCase):
    """Smoke tests that start the real server as a subprocess."""

    server_proc = None

    @classmethod
    def setUpClass(cls):
        # Create temp profiles file
        cls._tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        cls._tmp.close()

        # Start server with test port
        env = os.environ.copy()
        env['PROFILES_FILE'] = cls._tmp.name

        # Modify the server to use test port by overriding PORT
        cls.server_proc = subprocess.Popen(
            [sys.executable, '-c',
             f'import sys; sys.path.insert(0, r"{os.path.dirname(SERVER_SCRIPT)}"); '
             f'import server; server.PORT = {TEST_PORT}; '
             f'server.PROFILES_FILE = r"{cls._tmp.name}"; '
             f'server.main()'],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            env=env
        )

        # Wait for server to start
        for _ in range(30):
            try:
                urllib.request.urlopen(f'http://127.0.0.1:{TEST_PORT}/', timeout=1)
                break
            except Exception:
                time.sleep(0.2)
        else:
            cls.server_proc.kill()
            raise RuntimeError('Server failed to start')

    @classmethod
    def tearDownClass(cls):
        if cls.server_proc:
            cls.server_proc.kill()
            cls.server_proc.wait()
        try:
            os.unlink(cls._tmp.name)
        except OSError:
            pass

    def _request(self, method, path, body=None):
        url = f'http://127.0.0.1:{TEST_PORT}{path}'
        data = json.dumps(body).encode() if body else None
        req = urllib.request.Request(url, data=data, method=method,
                                     headers={'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return resp.status, json.loads(resp.read())
        except urllib.error.HTTPError as e:
            try:
                return e.code, json.loads(e.read())
            except Exception:
                return e.code, {}

    # ---- Homepage ----

    def test_homepage_loads(self):
        req = urllib.request.Request(f'http://127.0.0.1:{TEST_PORT}/')
        with urllib.request.urlopen(req, timeout=5) as resp:
            self.assertEqual(200, resp.status)
            body = resp.read()
            self.assertIn(b'Key Remapper', body)

    # ---- Profile API ----

    def test_get_profiles(self):
        status, data = self._request('GET', '/api/profiles')
        self.assertEqual(200, status)
        self.assertIn('profiles', data)
        self.assertIsInstance(data['profiles'], list)
        self.assertIn('activeIndex', data)

    def test_post_then_get_profiles(self):
        profiles = {
            'profiles': [
                {'id': 'int-test-1', 'name': 'IntegrationTest', 'mappings': [
                    {'fromKeyCode': 20, 'toKeyCode': 27, 'fromKeyName': 'Caps', 'toKeyName': 'Esc'}
                ], 'macros': []}
            ],
            'activeIndex': 0
        }
        status, data = self._request('POST', '/api/profiles', profiles)
        self.assertEqual(200, status)
        self.assertTrue(data.get('ok'))

        status, data = self._request('GET', '/api/profiles')
        self.assertEqual(200, status)
        self.assertEqual('IntegrationTest', data['profiles'][0]['name'])
        self.assertEqual(20, data['profiles'][0]['mappings'][0]['fromKeyCode'])

    # ---- Ollama status ----

    def test_ollama_status(self):
        status, data = self._request('GET', '/api/ollama/status')
        self.assertEqual(200, status)
        self.assertIn('online', data)
        self.assertIn('model', data)

    # ---- Bridge proxy (Java not running) ----

    def test_hook_status_graceful_error(self):
        status, data = self._request('GET', '/api/hook/status')
        self.assertEqual(200, status)
        self.assertIn('error', data)

    def test_hook_apply_graceful_error(self):
        status, data = self._request('POST', '/api/hook/apply', {'mappings': {}})
        self.assertEqual(200, status)
        self.assertIn('error', data)

    def test_macro_record_start_graceful_error(self):
        status, data = self._request('POST', '/api/macro/record/start', {'autoDelay': False})
        self.assertEqual(200, status)
        self.assertIn('error', data)


if __name__ == '__main__':
    unittest.main()
