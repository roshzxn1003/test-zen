#!/usr/bin/env python3
"""
Zenith - Local Wi-Fi APK Distribution Server
Serves the latest Android Release & Debug APKs over the local network with instant QR code scanning.
"""

import os
import sys
import socket
import io
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse

try:
    import qrcode
    import qrcode.image.svg
except ImportError:
    qrcode = None

DEFAULT_PORT = 8888
PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RELEASE_APK_PATH = os.path.join(PROJECT_DIR, "Zenith-release.apk")
DEBUG_APK_PATH = os.path.join(PROJECT_DIR, "Zenith-debug.apk")

if not os.path.exists(RELEASE_APK_PATH):
    fallback_release = os.path.join(PROJECT_DIR, "app", "build", "outputs", "apk", "release", "app-release.apk")
    if os.path.exists(fallback_release):
        RELEASE_APK_PATH = fallback_release

if not os.path.exists(DEBUG_APK_PATH):
    fallback_debug = os.path.join(PROJECT_DIR, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
    if os.path.exists(fallback_debug):
        DEBUG_APK_PATH = fallback_debug


def get_local_ip() -> str:
    """Determine the host's primary local Wi-Fi / LAN IP address."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip


def generate_qr_svg(url: str) -> str:
    """Generate inline SVG string for the given URL."""
    if not qrcode:
        return ""
    factory = qrcode.image.svg.SvgPathImage
    img = qrcode.make(url, image_factory=factory, box_size=10, border=2)
    stream = io.BytesIO()
    img.save(stream)
    return stream.getvalue().decode("utf-8")


def generate_and_save_qr_png(url: str):
    """Save QR code as PNG in project root and server_dist."""
    if not qrcode:
        return
    qr = qrcode.QRCode(version=1, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=10, border=4)
    qr.add_data(url)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    root_qr = os.path.join(PROJECT_DIR, "qr.png")
    dist_qr = os.path.join(PROJECT_DIR, "server_dist", "qr.png")
    img.save(root_qr)
    os.makedirs(os.path.dirname(dist_qr), exist_ok=True)
    img.save(dist_qr)


def print_ascii_qr(url: str):
    """Print ASCII QR code to terminal."""
    if not qrcode:
        return
    qr = qrcode.QRCode(border=1)
    qr.add_data(url)
    qr.make(fit=True)
    print("\n" + "=" * 54)
    print("  SCAN WITH YOUR PHONE CAMERA TO DOWNLOAD ZENITH APK")
    print("=" * 54 + "\n")
    qr.print_ascii(invert=True)
    print("\n" + "=" * 54)
    print(f"  Mobile Portal URL: {url}")
    print("=" * 54 + "\n")


class ApkDistributionHandler(BaseHTTPRequestHandler):
    local_ip = "127.0.0.1"
    port = DEFAULT_PORT

    def log_message(self, format, *args):
        sys.stderr.write(f"[{self.log_date_time_string()}] {self.client_address[0]} - {format % args}\n")

    def do_HEAD(self):
        parsed = urlparse(self.path)
        if parsed.path in ["/app-release.apk", "/zenith.apk", "/download"]:
            self.serve_apk_head(RELEASE_APK_PATH, "Zenith-release.apk")
        elif parsed.path == "/app-debug.apk":
            self.serve_apk_head(DEBUG_APK_PATH, "Zenith-debug.apk")
        else:
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()

    def serve_apk_head(self, file_path, download_name):
        if not os.path.exists(file_path):
            self.send_error(404, "APK not found.")
            return
        file_size = os.path.getsize(file_path)
        self.send_response(200)
        self.send_header("Content-Type", "application/vnd.android.package-archive")
        self.send_header("Content-Disposition", f'attachment; filename="{download_name}"')
        self.send_header("Content-Length", str(file_size))
        self.send_header("Accept-Ranges", "bytes")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path in ["/app-release.apk", "/zenith.apk", "/download"]:
            self.stream_file(RELEASE_APK_PATH, "Zenith-release.apk")
        elif parsed.path == "/app-debug.apk":
            self.stream_file(DEBUG_APK_PATH, "Zenith-debug.apk")
        elif parsed.path == "/qr.png":
            qr_file = os.path.join(PROJECT_DIR, "qr.png")
            if os.path.exists(qr_file):
                self.stream_file(qr_file, "qr.png", content_type="image/png")
            else:
                self.send_error(404, "QR not found")
        elif parsed.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status": "ok", "app": "Zenith", "port": 8888}\n')
        elif parsed.path in ["/", "/index.html"]:
            self.serve_portal_html()
        else:
            self.send_error(404, "Page Not Found")

    def stream_file(self, file_path, download_name, content_type="application/vnd.android.package-archive"):
        if not os.path.exists(file_path):
            self.send_error(404, f"{download_name} not found on server.")
            return

        file_size = os.path.getsize(file_path)
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        if content_type == "application/vnd.android.package-archive":
            self.send_header("Content-Disposition", f'attachment; filename="{download_name}"')
        self.send_header("Content-Length", str(file_size))
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        self.end_headers()

        with open(file_path, "rb") as f:
            while chunk := f.read(65536):
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    break

    def serve_portal_html(self):
        rel_exists = os.path.exists(RELEASE_APK_PATH)
        rel_size = f"{os.path.getsize(RELEASE_APK_PATH) / (1024 * 1024):.1f} MB" if rel_exists else "Not built"

        dbg_exists = os.path.exists(DEBUG_APK_PATH)
        dbg_size = f"{os.path.getsize(DEBUG_APK_PATH) / (1024 * 1024):.1f} MB" if dbg_exists else "Not built"

        portal_url = f"http://{self.local_ip}:{self.port}/"
        qr_svg = generate_qr_svg(portal_url)

        html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Zenith - Mobile Installation</title>
    <style>
        :root {{
            --bg-base: #090D16;
            --surface-card: #0F172A;
            --surface-elevated: #1E293B;
            --primary: #6366F1;
            --primary-gradient: linear-gradient(135deg, #6366F1, #4F46E5);
            --emerald: #10B981;
            --emerald-gradient: linear-gradient(135deg, #10B981, #059669);
            --cyan: #06B6D4;
            --text-primary: #F8FAFC;
            --text-secondary: #94A3B8;
            --border: rgba(255, 255, 255, 0.12);
        }}
        * {{
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }}
        body {{
            background: var(--bg-base);
            color: var(--text-primary);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 24px 16px;
        }}
        .container {{
            max-width: 480px;
            width: 100%;
            background: var(--surface-card);
            border: 1px solid var(--border);
            border-radius: 28px;
            padding: 32px 24px;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6), 0 0 80px rgba(99, 102, 241, 0.12);
            text-align: center;
        }}
        .badge {{
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: rgba(99, 102, 241, 0.18);
            border: 1px solid rgba(99, 102, 241, 0.35);
            color: #A5B4FC;
            padding: 6px 14px;
            border-radius: 9999px;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.5px;
            margin-bottom: 20px;
        }}
        .dot {{
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--emerald);
            box-shadow: 0 0 8px var(--emerald);
        }}
        h1 {{
            font-size: 28px;
            font-weight: 800;
            color: var(--text-primary);
            margin-bottom: 6px;
            letter-spacing: -0.5px;
        }}
        .subtitle {{
            color: var(--text-secondary);
            font-size: 14px;
            margin-bottom: 24px;
        }}
        .qr-card {{
            background: #FFFFFF;
            padding: 16px;
            border-radius: 20px;
            display: inline-block;
            margin-bottom: 24px;
            box-shadow: 0 8px 24px rgba(0,0,0,0.4);
        }}
        .qr-card svg {{
            width: 180px;
            height: 180px;
            display: block;
        }}
        .buttons-group {{
            display: flex;
            flex-direction: column;
            gap: 12px;
        }}
        .btn {{
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
            width: 100%;
            padding: 16px 20px;
            border-radius: 16px;
            font-size: 15px;
            font-weight: 700;
            text-decoration: none;
            transition: transform 0.15s ease, opacity 0.15s ease;
        }}
        .btn:active {{
            transform: scale(0.98);
        }}
        .btn-primary {{
            background: var(--emerald-gradient);
            color: #FFFFFF;
            box-shadow: 0 8px 20px rgba(16, 185, 129, 0.35);
        }}
        .btn-secondary {{
            background: var(--surface-elevated);
            color: var(--text-primary);
            border: 1px solid var(--border);
        }}
        .meta-info {{
            display: flex;
            justify-content: space-around;
            margin-top: 20px;
            padding-top: 20px;
            border-top: 1px solid var(--border);
            color: var(--text-secondary);
            font-size: 12px;
        }}
        .meta-item strong {{
            color: var(--text-primary);
            display: block;
            font-size: 13px;
            margin-top: 2px;
        }}
        .instructions {{
            margin-top: 24px;
            background: var(--surface-elevated);
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 18px;
            text-align: left;
        }}
        .instructions h3 {{
            font-size: 13px;
            color: #A5B4FC;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.8px;
            margin-bottom: 10px;
        }}
        .instructions ol {{
            padding-left: 20px;
            font-size: 13px;
            color: var(--text-secondary);
            line-height: 1.6;
        }}
        .instructions li strong {{
            color: var(--text-primary);
        }}
    </style>
</head>
<body>
    <div class="container">
        <div class="badge">
            <div class="dot"></div>
            WI-FI READY • PORT 8888
        </div>
        <h1>Zenith</h1>
        <p class="subtitle">Smart Personal & Family Finance Assistant</p>

        <div class="qr-card">
            {qr_svg if qr_svg else '<p style="color:#000;font-size:12px;padding:20px;">QR Not Available</p>'}
        </div>

        <div class="buttons-group">
            <a href="/zenith.apk" class="btn btn-primary" download="Zenith-release.apk">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                    <polyline points="7 10 12 15 17 10"></polyline>
                    <line x1="12" y1="15" x2="12" y2="3"></line>
                </svg>
                Download Release APK ({rel_size})
            </a>

            <a href="/app-debug.apk" class="btn btn-secondary" download="Zenith-debug.apk">
                Download Debug APK ({dbg_size})
            </a>
        </div>

        <div class="meta-info">
            <div class="meta-item">
                <span>Release</span>
                <strong>v1.0 (Ready)</strong>
            </div>
            <div class="meta-item">
                <span>Release Size</span>
                <strong>{rel_size}</strong>
            </div>
            <div class="meta-item">
                <span>Local IP</span>
                <strong>{self.local_ip}</strong>
            </div>
        </div>

        <div class="instructions">
            <h3>How to Install on Android</h3>
            <ol>
                <li>Tap <strong>Download Release APK</strong> above.</li>
                <li>When the download finishes, tap the notification or file in Downloads.</li>
                <li>If prompted: <em>"Allow installation from this source"</em>, tap <strong>Settings</strong> and toggle <strong>Allow</strong>.</li>
                <li>Tap <strong>Install</strong> to start Zenith!</li>
            </ol>
        </div>
    </div>
</body>
</html>"""
        encoded = html.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def run_server(port=DEFAULT_PORT):
    local_ip = get_local_ip()
    download_url = f"http://{local_ip}:{port}/"
    generate_and_save_qr_png(download_url)

    ApkDistributionHandler.local_ip = local_ip
    ApkDistributionHandler.port = port

    server_address = ("0.0.0.0", port)
    httpd = HTTPServer(server_address, ApkDistributionHandler)

    print_ascii_qr(download_url)
    print(f"Zenith Wi-Fi Distribution Server active!")
    print(f"Local IP: {local_ip}")
    print(f"Mobile Portal: {download_url}")
    print(f"Release APK: http://{local_ip}:{port}/zenith.apk")
    print(f"Debug APK:   http://{local_ip}:{port}/app-debug.apk\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping Wi-Fi distribution server...")
        httpd.server_close()


if __name__ == "__main__":
    p = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    run_server(p)
