"""Polite HTTP client: throttled, identifies itself, retries transient 5xx, fails loudly on non-200."""
import threading
import time

import requests

# Seconds to wait before each retry of a request answered with HTTP 5xx: three retries, then FetchError.
RETRY_DELAYS = (2, 4, 8)


class FetchError(Exception):
    def __init__(self, path, status, url=None):
        super().__init__("GET %s failed with HTTP %s" % (url or path, status))
        self.path = path
        self.url = url or path
        self.status = status


class Client:
    def __init__(self, base_url, user_agent, rate_per_second=4.0, session=None, retry_delays=None, sleep=time.sleep):
        """``retry_delays`` defaults to RETRY_DELAYS (read when the client is made); ``sleep`` waits between retries."""
        if rate_per_second <= 0:
            raise ValueError("rate_per_second must be positive")
        self._retry_delays = tuple(RETRY_DELAYS if retry_delays is None else retry_delays)
        self._sleep = sleep
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
        """GET with the throttle; a 5xx answer is retried after each of the retry delays, any other non-200 raises."""
        delays = iter(self._retry_delays)
        while True:
            self._throttle()
            resp = self._session.get(self.base_url + path, timeout=(10, 120))
            if resp.status_code == 200:
                return resp
            delay = next(delays, None) if 500 <= resp.status_code < 600 else None
            if delay is None:
                raise FetchError(path, resp.status_code, self.base_url + path)
            self._sleep(delay)

    def get_text(self, path):
        resp = self._get(path)
        resp.encoding = resp.encoding or "utf-8"
        return resp.text

    def get_bytes(self, path):
        return self._get(path).content
