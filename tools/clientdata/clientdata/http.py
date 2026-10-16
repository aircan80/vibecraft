"""Polite HTTP client: throttled, identifies itself, fails loudly on non-200."""
import threading
import time

import requests


class FetchError(Exception):
    def __init__(self, path, status):
        super().__init__("GET %s failed with HTTP %s" % (path, status))
        self.path = path
        self.status = status


class Client:
    def __init__(self, base_url, user_agent, rate_per_second=4.0, session=None):
        if rate_per_second <= 0:
            raise ValueError("rate_per_second must be positive")
        self.base_url = base_url.rstrip("/")
        self.user_agent = user_agent
        self._interval = 1.0 / rate_per_second
        self._next_start = 0.0
        self._lock = threading.Lock()
        self._session = session or requests.Session()
        self._session.headers["User-Agent"] = user_agent

    def _throttle(self):
        with self._lock:
            now = time.monotonic()
            wait = self._next_start - now
            if wait > 0:
                time.sleep(wait)
                now = time.monotonic()
            self._next_start = now + self._interval

    def _get(self, path):
        self._throttle()
        resp = self._session.get(self.base_url + path, timeout=(10, 120))
        if resp.status_code != 200:
            raise FetchError(path, resp.status_code)
        return resp

    def get_text(self, path):
        resp = self._get(path)
        resp.encoding = resp.encoding or "utf-8"
        return resp.text

    def get_bytes(self, path):
        return self._get(path).content
