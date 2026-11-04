"""Private Audiveris PDF -> MusicXML service; no data is retained."""
import hmac
import re
import os
from pathlib import Path
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_UPLOAD = 12 * 1024 * 1024
MAX_OUTPUT = 12 * 1024 * 1024
SLOT = threading.BoundedSemaphore(1)


class RecognitionError(Exception):
    pass


def convert(pdf: bytes, *, binary=None):
    if len(pdf) > MAX_UPLOAD or not pdf[:1024].lstrip().startswith(b"%PDF-"):
        raise ValueError("PDF invalide ou trop volumineux (12 Mo maximum).")
    binary = binary or os.environ.get("AUDIVERIS_BIN", "/opt/audiveris/bin/Audiveris")
    with tempfile.TemporaryDirectory(prefix="piano-omr-") as folder:
        root = Path(folder)
        source = root / "score.pdf"
        target = root / "out"
        target.mkdir()
        source.write_bytes(pdf)
        try:
            completed = subprocess.run(
                [binary, "-batch", "-export", "-output", str(target), "--", str(source)],
                cwd=root, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT, timeout=180, check=False,
            )
        except subprocess.TimeoutExpired as err:
            raise RecognitionError("Délai OMR dépassé (3 minutes).") from err
        except OSError as err:
            raise RecognitionError("Moteur Audiveris indisponible.") from err
        if completed.returncode != 0:
            # Do not print PDF contents, title or the full verbose OMR transcript.
            # Only extract recognizable CLI/Java failure signatures for diagnostics.
            output = completed.stdout.decode("utf-8", errors="replace")
            patterns = (
                r"(?i)(?:java\.lang\.[A-Za-z0-9.$]+(?:Exception|Error))",
                r"(?i)(?:NoClassDefFoundError|UnsatisfiedLinkError|OutOfMemoryError)",
                r"(?i)(?:Permission denied|No such file or directory|not found|Unable to access jarfile)",
                r"(?i)(?:error opening display|Xvfb failed|xvfb-run: error|Could not find or load main class)",
            )
            found = []
            for pattern in patterns:
                for match in re.finditer(pattern, output):
                    found.append(match.group(0))
            details = ", ".join(dict.fromkeys(found))[:400] or "aucune signature d'erreur reconnue"
            print(f"Audiveris rc={completed.returncode}, diagnostics: {details}", flush=True)
            raise RecognitionError("Audiveris n'a pas réussi à lire la partition.")
        files = [*target.rglob("*.mxl"), *target.rglob("*.musicxml"), *target.rglob("*.xml")]
        if not files:
            raise RecognitionError("Aucun résultat MusicXML produit.")
        files.sort(key=lambda f: (f.suffix.lower() != ".mxl", -f.stat().st_size))
        chosen = files[0]
        if chosen.stat().st_size > MAX_OUTPUT:
            raise RecognitionError("MusicXML trop volumineux.")
        data = chosen.read_bytes()
        if chosen.suffix.lower() == ".mxl":
            if not data.startswith(b"PK"):
                raise RecognitionError("Archive MXL invalide.")
            return data, "application/vnd.recordare.musicxml"
        if b"<score-partwise" not in data[:20000]:
            raise RecognitionError("MusicXML non pris en charge.")
        return data, "application/vnd.recordare.musicxml+xml"


class Handler(BaseHTTPRequestHandler):
    server_version = "PianoOMR/1"
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass  # Do not log scores, credentials or client URLs.

    def reply(self, code, content, mime="text/plain; charset=utf-8"):
        data = content.encode("utf-8") if isinstance(content, str) else content
        self.send_response(code)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.reply(200, "ok") if self.path == "/health" else self.reply(404, "Introuvable")

    def do_POST(self):
        if self.path != "/v1/recognize":
            return self.reply(404, "Introuvable")
        token = os.environ.get("OMR_API_TOKEN", "")
        if not token or not hmac.compare_digest(
            self.headers.get("Authorization", ""), "Bearer " + token
        ):
            return self.reply(401, "Jeton non valide.")
        if self.headers.get("Content-Type", "").split(";")[0].strip().lower() != "application/pdf":
            return self.reply(415, "application/pdf attendu.")
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.reply(400, "Taille invalide.")
        if length < 8 or length > MAX_UPLOAD:
            return self.reply(413, "PDF trop volumineux ou vide.")
        if not SLOT.acquire(blocking=False):
            return self.reply(429, "Serveur occupé.")
        try:
            self.connection.settimeout(45)
            pdf = self.rfile.read(length)
            if len(pdf) != length:
                return self.reply(400, "Fichier incomplet.")
            try:
                result, mime = convert(pdf)
            except ValueError as err:
                return self.reply(400, str(err))
            except RecognitionError as err:
                return self.reply(422, str(err))
            self.reply(200, result, mime)
        finally:
            SLOT.release()


if __name__ == "__main__":
    token = os.environ.get("OMR_API_TOKEN", "")
    if len(token) < 24:
        raise SystemExit("Configurer OMR_API_TOKEN avec au moins 24 caractères aléatoires.")
    bind = os.environ.get("OMR_LISTEN", "127.0.0.1")
    port = int(os.environ.get("PORT") or os.environ.get("OMR_PORT", "8080"))
    if os.environ.get("OMR_DIAGNOSE_STARTUP") == "1":
        try:
            diag = subprocess.run(
                [os.environ.get("AUDIVERIS_BIN", "/opt/audiveris/bin/Audiveris"), "-help"],
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60,
                stdin=subprocess.DEVNULL, check=False,
            )
            print(f"Audiveris startup CLI exit={diag.returncode}", flush=True)
            if diag.returncode != 0:
                lines = diag.stdout.decode("utf-8", errors="replace").splitlines()
                # This is the built-in -help command, with no private PDF data.
                print("Audiveris startup CLI output: " + " | ".join(lines[-20:])[:1500], flush=True)
        except (OSError, subprocess.TimeoutExpired) as err:
            print(f"Audiveris startup CLI problem: {type(err).__name__}", flush=True)
    print("Service OMR privé en attente (protéger par HTTPS/TLS en amont).", flush=True)
    ThreadingHTTPServer((bind, port), Handler).serve_forever()
