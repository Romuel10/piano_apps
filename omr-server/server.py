"""Private Audiveris PDF -> MusicXML service; no data is retained."""
import hmac
import io
import zipfile
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


def _diagnostic(stdout: bytes, returncode: int, stage: str):
    """Log only categories of errors, never user titles, lyrics or PDF content."""
    output = stdout.decode("utf-8", errors="replace")
    checks = (
        ("memory", r"(?i)OutOfMemoryError|Java heap space|Cannot allocate memory"),
        ("PDF engine", r"(?i)Ghostscript|gs: not found|PDFImageReader|PDF renderer"),
        ("native dependency", r"(?i)UnsatisfiedLinkError|NoClassDefFoundError"),
        ("image parsing", r"(?i)Cannot load input|ImageIO|Unsupported image"),
        ("permission", r"(?i)Permission denied|AccessDeniedException"),
        ("OMR processing", r"(?i)ProcessingCancellationException|StepException|BookException"),
    )
    kinds = [label for label, expression in checks if re.search(expression, output)]
    print(
        f"Audiveris stage={stage} exit={returncode}; "
        f"error_classes={','.join(kinds) or 'unspecified'}",
        flush=True,
    )


def _exported_file(target: Path):
    candidates = [
        *target.rglob("*.mxl"),
        *target.rglob("*.musicxml"),
        *target.rglob("*.xml"),
    ]
    # Avoid picking up any metadata XML that is not actually a score.
    candidates = [file for file in candidates if "META-INF" not in file.parts]
    candidates.sort(key=lambda file: (file.suffix.lower() != ".mxl", -file.stat().st_size))
    for candidate in candidates:
        if candidate.stat().st_size > MAX_OUTPUT:
            continue
        data = candidate.read_bytes()
        if candidate.suffix.lower() == ".mxl":
            try:
                with zipfile.ZipFile(io.BytesIO(data)) as archive:
                    music = [
                        name for name in archive.namelist()
                        if name.lower().endswith((".xml", ".musicxml"))
                        and not name.lower().startswith("meta-inf/")
                    ]
                    if any(
                        b"<score-partwise" in archive.read(name)[:40000]
                        or b"<score-timewise" in archive.read(name)[:40000]
                        for name in music[:4]
                    ):
                        return data, "application/vnd.recordare.musicxml"
            except (zipfile.BadZipFile, OSError):
                continue
        if b"<score-partwise" in data[:40000] or b"<score-timewise" in data[:40000]:
            return data, "application/vnd.recordare.musicxml+xml"
    return None


def _render_pdf_as_tiff(source: Path, target: Path):
    # A 300-DPI, multipage TIFF preserves ALL the piano systems in ONE score.
    # Audiveris reads multi-page TIFF as a book; splitting into separate PNG
    # documents would lose the flow between the pages.
    info = subprocess.run(
        ["pdfinfo", str(source)], stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL, timeout=12, check=False,
    )
    if info.returncode != 0:
        raise RecognitionError("Le PDF semble illisible ou endommagé.")
    match = re.search(r"(?m)^Pages:\s*(\d+)", info.stdout.decode("ascii", errors="replace"))
    if not match:
        raise RecognitionError("Impossible de compter les pages du PDF.")
    pages = int(match.group(1))
    if pages < 1 or pages > 20:
        raise RecognitionError("Limite du service : 20 pages par conversion.")
    rendered = subprocess.run(
        [
            "gs", "-q", "-dSAFER", "-dBATCH", "-dNOPAUSE", "-dUseCropBox",
            "-sDEVICE=tiffg4", "-r300", "-dNumRenderingThreads=1",
            "-sOutputFile=" + str(target), str(source),
        ],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL, timeout=40, check=False,
    )
    if rendered.returncode != 0 or not target.is_file() or target.stat().st_size == 0:
        raise RecognitionError("Impossible de convertir les pages PDF en images pour Audiveris.")
    if target.stat().st_size > 85 * 1024 * 1024:
        raise RecognitionError("La partition contient trop d'images à analyser.")
    print(f"PDF preprocessed as {pages} TIFF page(s) at 300 dpi", flush=True)


def convert(pdf: bytes, *, binary=None):
    if len(pdf) > MAX_UPLOAD or len(pdf) < 8 or not pdf[:1024].lstrip().startswith(b"%PDF-"):
        raise ValueError("PDF invalide ou trop volumineux (12 Mo maximum).")
    binary = binary or os.environ.get("AUDIVERIS_BIN", "/opt/audiveris/bin/Audiveris")
    with tempfile.TemporaryDirectory(prefix="piano-omr-") as folder:
        root = Path(folder)
        source = root / "score.pdf"
        source.write_bytes(pdf)

        def attempt(input_file: Path, stage: str, timeout: int):
            dest = root / stage
            dest.mkdir()
            try:
                completed = subprocess.run(
                    [binary, "-batch", "-export", "-output", str(dest), "--", str(input_file)],
                    cwd=root, stdin=subprocess.DEVNULL,
                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    timeout=timeout, check=False,
                )
            except subprocess.TimeoutExpired:
                print(f"Audiveris stage={stage} timed out", flush=True)
                return None
            except OSError as error:
                raise RecognitionError("Le moteur Audiveris est indisponible.") from error
            result = _exported_file(dest)
            if completed.returncode != 0 or result is None:
                _diagnostic(completed.stdout, completed.returncode, stage)
            return result

        # Try native PDF first. Fall back to multipage TIFF if the native
        # PDF importer fails or creates no valid MusicXML score.
        result = attempt(source, "pdf", 85)
        if result is not None:
            return result

        raster = root / "score.tif"
        _render_pdf_as_tiff(source, raster)
        result = attempt(raster, "raster", 105)
        if result is not None:
            return result

        raise RecognitionError(
            "Audiveris n'a pas pu reconnaître suffisamment de symboles "
            "pour produire du MusicXML. Vérifiez la netteté de la partition."
        )


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
