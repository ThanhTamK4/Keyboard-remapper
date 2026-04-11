"""Tests for the Key Remapper Python web server."""

import json
import os
import sys
import tempfile
import threading
import time
import unittest
import urllib.request
import urllib.error

# Add parent dir so we can import server module
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import server


class TestProfileOperations(unittest.TestCase):
    """Unit tests for profile load/save functions."""

    def setUp(self):
        self._orig_file = server.PROFILES_FILE
        self._tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        self._tmp.close()
        server.PROFILES_FILE = self._tmp.name
        # Reset lock to fresh state
        server._profile_lock = threading.RLock()

    def tearDown(self):
        server.PROFILES_FILE = self._orig_file
        try:
            os.unlink(self._tmp.name)
        except OSError:
            pass

    def test_load_missing_file_returns_defaults(self):
        """When profiles.json doesn't exist, should return defaults (not deadlock)."""
        os.unlink(self._tmp.name)
        server.PROFILES_FILE = self._tmp.name + '.nonexistent'
        data = server.load_profiles()
        self.assertIn('profiles', data)
        self.assertEqual(len(data['profiles']), 1)
        self.assertEqual(data['profiles'][0]['name'], 'Profile 1')
        self.assertEqual(data['activeIndex'], 0)
        # Clean up created file
        try:
            os.unlink(server.PROFILES_FILE)
        except OSError:
            pass

    def test_load_corrupt_json_returns_defaults(self):
        """Corrupt JSON should fall back to defaults."""
        with open(self._tmp.name, 'w') as f:
            f.write('{invalid json!!!}')
        data = server.load_profiles()
        self.assertIn('profiles', data)
        self.assertEqual(len(data['profiles']), 1)

    def test_save_load_roundtrip(self):
        """Save then load should preserve data."""
        original = {
            'profiles': [
                {'id': 'test-1', 'name': 'Gaming', 'mappings': [
                    {'fromKeyCode': 20, 'toKeyCode': 27}
                ], 'macros': []}
            ],
            'activeIndex': 0
        }
        server.save_profiles(original)
        loaded = server.load_profiles()
        self.assertEqual(loaded['profiles'][0]['name'], 'Gaming')
        self.assertEqual(loaded['profiles'][0]['mappings'][0]['fromKeyCode'], 20)

    def test_concurrent_saves(self):
        """Multiple threads saving concurrently should not corrupt the file."""
        errors = []

        def save_profile(i):
            try:
                data = {
                    'profiles': [{'id': f'id-{i}', 'name': f'Profile {i}',
                                  'mappings': [], 'macros': []}],
                    'activeIndex': 0
                }
                server.save_profiles(data)
            except Exception as e:
                errors.append(e)

        threads = [threading.Thread(target=save_profile, args=(i,)) for i in range(10)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        self.assertEqual(len(errors), 0, f"Errors during concurrent saves: {errors}")
        # File should be valid JSON
        with open(self._tmp.name, 'r') as f:
            data = json.load(f)
        self.assertIn('profiles', data)

    def test_deadlock_regression(self):
        """load_profiles calling save_profiles while holding lock must not deadlock.

        This is a regression test for the bug where threading.Lock (non-reentrant)
        was used. The fix is to use threading.RLock.
        """
        os.unlink(self._tmp.name)
        server.PROFILES_FILE = self._tmp.name

        result = [None]
        def load_in_thread():
            result[0] = server.load_profiles()

        t = threading.Thread(target=load_in_thread)
        t.start()
        t.join(timeout=5)  # 5 second timeout — deadlock would hang forever
        self.assertFalse(t.is_alive(), "load_profiles deadlocked!")
        self.assertIsNotNone(result[0])


class TestStaticServing(unittest.TestCase):
    """Tests for static file serving and path traversal protection."""

    @classmethod
    def setUpClass(cls):
        cls.port = 18230
        cls.server = server.ThreadedHTTPServer(('127.0.0.1', cls.port), server.Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        time.sleep(0.3)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def _get(self, path):
        url = f'http://127.0.0.1:{self.port}{path}'
        try:
            req = urllib.request.Request(url)
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status, resp.read(), resp.headers.get('Content-Type', '')
        except urllib.error.HTTPError as e:
            return e.code, e.read(), ''

    def test_root_serves_index(self):
        status, body, ctype = self._get('/')
        self.assertEqual(200, status)
        self.assertIn('text/html', ctype)
        self.assertIn(b'Key Remapper', body)

    def test_missing_file_returns_404(self):
        status, _, _ = self._get('/nonexistent_xyz_file.html')
        self.assertEqual(404, status)

    def test_path_traversal_blocked(self):
        status, _, _ = self._get('/../../etc/passwd')
        self.assertIn(status, [403, 404])

    def test_path_traversal_dot_dot(self):
        status, _, _ = self._get('/../server.py')
        self.assertIn(status, [403, 404])

    def test_mime_html(self):
        status, _, ctype = self._get('/index.html')
        self.assertEqual(200, status)
        self.assertIn('text/html', ctype)

    def test_mime_js(self):
        status, _, ctype = self._get('/app.js')
        self.assertEqual(200, status)
        self.assertIn('javascript', ctype)

    def test_mime_css(self):
        status, _, ctype = self._get('/style.css')
        self.assertEqual(200, status)
        self.assertIn('text/css', ctype)

    def test_query_string_stripped(self):
        status, body, _ = self._get('/index.html?v=123&cache=bust')
        self.assertEqual(200, status)
        self.assertIn(b'Key Remapper', body)


class TestAPIEndpoints(unittest.TestCase):
    """Integration tests for API endpoints."""

    @classmethod
    def setUpClass(cls):
        cls.port = 18231
        # Use temp profiles file for test isolation
        cls._orig_file = server.PROFILES_FILE
        cls._tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        cls._tmp.close()
        server.PROFILES_FILE = cls._tmp.name

        cls.server = server.ThreadedHTTPServer(('127.0.0.1', cls.port), server.Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        time.sleep(0.3)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        server.PROFILES_FILE = cls._orig_file
        try:
            os.unlink(cls._tmp.name)
        except OSError:
            pass

    def _request(self, method, path, body=None):
        url = f'http://127.0.0.1:{self.port}{path}'
        data = json.dumps(body).encode() if body else None
        req = urllib.request.Request(url, data=data, method=method,
                                     headers={'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status, json.loads(resp.read())
        except urllib.error.HTTPError as e:
            try:
                return e.code, json.loads(e.read())
            except Exception:
                return e.code, {}

    def test_get_profiles(self):
        status, data = self._request('GET', '/api/profiles')
        self.assertEqual(200, status)
        self.assertIn('profiles', data)
        self.assertIn('activeIndex', data)

    def test_post_profiles(self):
        profiles = {
            'profiles': [{'id': 'test-1', 'name': 'Test', 'mappings': [], 'macros': []}],
            'activeIndex': 0
        }
        status, data = self._request('POST', '/api/profiles', profiles)
        self.assertEqual(200, status)
        self.assertTrue(data.get('ok'))

        # Verify persistence
        status, data = self._request('GET', '/api/profiles')
        self.assertEqual('Test', data['profiles'][0]['name'])

    def test_post_profiles_invalid_json(self):
        url = f'http://127.0.0.1:{self.port}/api/profiles'
        req = urllib.request.Request(url, data=b'{invalid!!!}', method='POST',
                                     headers={'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                data = json.loads(resp.read())
                self.assertIn('error', data)
        except urllib.error.HTTPError:
            pass  # 400 is also acceptable

    def test_ollama_status_offline(self):
        status, data = self._request('GET', '/api/ollama/status')
        self.assertEqual(200, status)
        self.assertFalse(data.get('online'))
        self.assertIn('model', data)

    def test_hook_status_no_java(self):
        status, data = self._request('GET', '/api/hook/status')
        self.assertEqual(200, status)
        self.assertIn('error', data)

    def test_hook_apply_no_java(self):
        status, data = self._request('POST', '/api/hook/apply',
                                      {'mappings': {'20': 27}})
        self.assertEqual(200, status)
        self.assertIn('error', data)

    def test_hook_disable_no_java(self):
        status, data = self._request('POST', '/api/hook/disable')
        self.assertEqual(200, status)
        self.assertIn('error', data)

    def test_hook_restore_no_java(self):
        status, data = self._request('POST', '/api/hook/restore')
        self.assertEqual(200, status)
        self.assertIn('error', data)

    def test_macro_endpoints_no_java(self):
        for path in ['/api/macro/record/start', '/api/macro/record/stop',
                     '/api/macro/play', '/api/macro/play/stop']:
            status, data = self._request('POST', path)
            self.assertEqual(200, status)
            self.assertIn('error', data, f"Missing error for {path}")


if __name__ == '__main__':
    unittest.main()
