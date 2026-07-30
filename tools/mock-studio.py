"""
A stand-in for a Hermes Studio server, so the app can be run and screenshotted
without one.

    python3 tools/mock-studio.py

Then sign in from a debug build with any username and password:

    emulator   http://10.0.2.2:8099
    device     http://<your machine's LAN address>:8099

Debug builds allow plain HTTP to those two hosts (see
app/src/debug/res/xml/network_security_config.xml); release builds do not.
"""
import base64, json, re, time
from http.server import BaseHTTPRequestHandler, HTTPServer

# A 1x1 grey PNG stands in for /logo.png; the app only has to fetch and cache it.
LOGO = base64.b64decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
)

PROFILES = [
    {"name": "manager", "model": "claude-opus-5", "active": True, "avatar": None},
    {"name": "barq", "model": "claude-sonnet-5", "active": False, "avatar": None},
    {"name": "deep-engineer", "model": "gpt-5", "active": False, "avatar": None},
]
SESSIONS = [
    {"id": "s1", "title": "تقرير الأسبوع", "model": "claude-opus-5", "updated_at": "2026-07-30T18:20:00", "profile": "manager"},
    {"id": "s2", "title": "Deploy the staging box", "model": "claude-sonnet-5", "updated_at": "2026-07-30T14:02:00", "profile": "barq"},
    {"id": "s3", "title": "مراجعة كود الاستديو", "model": "gpt-5", "updated_at": "2026-07-29T09:41:00", "profile": "deep-engineer"},
]
MESSAGES = [
    {"id": "m1", "role": "user", "content": "وش وضع التقرير؟", "timestamp": "2026-07-30T18:19:00"},
    {"id": "m2", "role": "assistant", "content": "خلصت الجزء الأول ورفعته على السيرفر. باقي المراجعة النهائية.", "timestamp": "2026-07-30T18:20:00"},
]
ROOMS = [{"id": "r1", "name": "غرفة التطوير", "agentCount": 3, "memberCount": 2, "updatedAt": "2026-07-30T12:00:00"}]

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a): pass

    def send(self, payload, code=200):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split('?')[0]
        if path == '/logo.png':
            self.send_response(200)
            self.send_header('Content-Type', 'image/png')
            self.send_header('Content-Length', str(len(LOGO)))
            self.end_headers()
            self.wfile.write(LOGO)
        elif path == '/api/auth/me': self.send({"username": "twuijri"})
        elif path == '/api/hermes/profiles': self.send({"profiles": PROFILES})
        elif path == '/api/hermes/sessions': self.send({"sessions": SESSIONS})
        elif re.match(r'/api/hermes/sessions/conversations/.+/messages', path): self.send({"messages": MESSAGES})
        elif path == '/api/hermes/group-chat/rooms': self.send({"rooms": ROOMS})
        elif re.match(r'/api/hermes/group-chat/rooms/.+', path):
            self.send({"room": ROOMS[0], "agents": [{"name": "barq"}], "members": [], "messages": [
                {"id": "g1", "role": "assistant", "senderName": "barq", "content": "جاهز.", "timestamp": "2026-07-30T12:00:00"}]})
        elif path == '/api/hermes/config': self.send({"model": {"default": "claude-opus-5"}})
        elif path == '/api/hermes/available-models':
            self.send({"groups": [{"provider": "anthropic", "models": ["claude-opus-5", "claude-sonnet-5"]},
                                  {"provider": "openai", "models": ["gpt-5"]}]})
        else: self.send({"error": "not found"}, 404)

    def do_POST(self):
        path = self.path.split('?')[0]
        length = int(self.headers.get('Content-Length') or 0)
        self.rfile.read(length)
        if path == '/api/auth/login': self.send({"token": "mock-token"})
        elif path == '/api/chat-run/runs':
            time.sleep(1)
            self.send({"output": "تم، سجلت الملاحظة.", "session_id": "s1"})
        elif path.endswith('/gateway/restart'): self.send({"success": True})
        else: self.send({"success": True})

    def do_PUT(self):
        length = int(self.headers.get('Content-Length') or 0)
        self.rfile.read(length)
        self.send({"success": True})

if __name__ == '__main__':
    print('mock Hermes Studio on http://0.0.0.0:8099 — any credentials work')
    HTTPServer(('0.0.0.0', 8099), Handler).serve_forever()
