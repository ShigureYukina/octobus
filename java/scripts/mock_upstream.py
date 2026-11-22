from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import time


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

    def _send_chunk(self, data: str):
        self.wfile.write(f"{len(data):x}\r\n{data}\r\n".encode("utf-8"))

    def do_GET(self):
        if self.path == "/openai/v1/models":
            self._send_json(200, OPENAI_MODELS)
            return
        if self.path == "/anthropic/v1/models":
            self._send_json(200, ANTHROPIC_MODELS)
            return
        self._send_json(404, {"error": "not found", "path": self.path})

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8")
        data = json.loads(body) if body else {}
        model = data.get("model", "unknown")

        if self.path == "/v1/chat/completions":
            # Simulate delay
            time.sleep(0.05)
            self._send_json(200, {
                "id": "chatcmpl-mock",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": model,
                "choices": [{"index": 0, "message": {"role": "assistant", "content": "Mock response"}, "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
            })
            return

        if self.path == "/v1/messages":
            time.sleep(0.05)
            self._send_json(200, {
                "id": "msg_mock",
                "type": "message",
                "role": "assistant",
                "content": [{"type": "text", "text": "Mock response"}],
                "model": model,
                "stop_reason": "end_turn",
                "usage": {"input_tokens": 10, "output_tokens": 5}
            })
            return

        if self.path == "/openai/v1/chat/completions":
            time.sleep(0.05)
            self._send_json(200, {
                "id": "chatcmpl-mock",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": model,
                "choices": [{"index": 0, "message": {"role": "assistant", "content": "Mock response"}, "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
            })
            return

        self._send_json(404, {"error": "not found", "path": self.path})

    def log_message(self, format, *args):
        print(f"[mock] {args[0]} {args[1]} {args[2]}")


if __name__ == "__main__":
    import sys
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18081
    server = HTTPServer(("127.0.0.1", port), Handler)
    print(f"mock upstream listening on http://127.0.0.1:{port}")
    server.serve_forever()
