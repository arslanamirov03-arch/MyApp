#!/usr/bin/env python3
"""
Простой локальный сервер для игры "Уютный Дом".
Запуск:   python3 serve.py
Затем откройте в браузере:  http://localhost:8000

Simple local server for the "Cozy Home" game.
Run:      python3 serve.py
Then open in your browser:  http://localhost:8000
"""
import http.server
import socketserver
import os
import webbrowser

PORT = 8000
DIRECTORY = os.path.dirname(os.path.abspath(__file__))


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    os.chdir(DIRECTORY)
    with socketserver.TCPServer(("", PORT), Handler) as httpd:
        url = f"http://localhost:{PORT}"
        print("=" * 52)
        print("  Игра запущена / Game is running")
        print(f"  Откройте / Open:  {url}")
        print("  Остановить / Stop: Ctrl + C")
        print("=" * 52)
        try:
            webbrowser.open(url)
        except Exception:
            pass
        httpd.serve_forever()
