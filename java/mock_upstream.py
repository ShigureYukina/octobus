from http.server import BaseHTTPRequestHandler, HTTPServer
import json


OPENAI_MODELS = {
    "object": "list",
    "data": [
        {"id": "gpt-4o"},
        {"id": "gpt-4o-mini"},
        {"id": "gpt-4.1"},
        {"id": "gpt-4.1-mini"},
        {"id": "gpt-3.5-turbo"},
        {"id": "o1-mini"},
        {"id": "o3-mini"},
    ],
}

ANTHROPIC_MODELS = {
    "data": [
        {"id": "claude-3-7-sonnet-20250219"},
        {"id": "claude-3-5-sonnet-20241022"},
        {"id": "claude-3-5-haiku-20241022"},
        {"id": "claude-3-opus-20240229"},
        {"id": "claude-3-haiku-20240307"},
    ],
    "has_more": False,
}


class Handler(BaseHTTPRequestHandler):
    def _send_json(self, code: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/openai/v1/models"):
            self._send_json(200, OPENAI_MODELS)
            return
        if self.path.startswith("/anthropic/v1/models"):
            self._send_json(200, ANTHROPIC_MODELS)
            return
        self._send_json(404, {"error": "not found", "path": self.path})

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 18081), Handler)
    print("mock upstream listening on http://127.0.0.1:18081")
    server.serve_forever()
