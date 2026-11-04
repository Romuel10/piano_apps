import io
import sys
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from server import convert, RecognitionError, MAX_UPLOAD


class ConversionTests(unittest.TestCase):
    def fake_engine(self, directory, raster_only=False):
        command = Path(directory) / "fake-audiveris"
        command.write_text(
            "#!/usr/bin/env python3\n"
            "import pathlib, sys, zipfile\n"
            "source = pathlib.Path(sys.argv[-1])\n"
            "if " + repr(bool(raster_only)) + " and source.suffix != '.tif':\n"
            "    sys.exit(2)\n"
            "root = pathlib.Path(sys.argv[sys.argv.index('-output') + 1]); root.mkdir(exist_ok=True)\n"
            "with zipfile.ZipFile(root / 'result.mxl', 'w') as z:\n"
            "    z.writestr('score.musicxml', '<score-partwise version=\"4.0\"/>')\n"
        )
        command.chmod(0o755)
        return str(command)

    def test_rejects_bad_pdfs_and_oversized_inputs(self):
        with self.assertRaises(ValueError):
            convert(b"not a PDF")
        with self.assertRaises(ValueError):
            convert(b"%PDF-1.7\n" + b"x" * MAX_UPLOAD)

    def test_recognizes_valid_musicxml_mxl(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = self.fake_engine(folder)
            data, mime = convert(b"%PDF-1.7\nmini", binary=binary)
            self.assertTrue(data.startswith(b"PK"))
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                self.assertIn("score-partwise", z.read("score.musicxml").decode())
            self.assertEqual("application/vnd.recordare.musicxml", mime)

    def test_falls_back_to_multipage_raster_if_direct_pdf_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = self.fake_engine(folder, raster_only=True)

            def make_raster(source, dest):
                self.assertEqual(".pdf", source.suffix)
                dest.write_bytes(b"II" + b"\0" * 20)

            with patch("server._render_pdf_as_tiff", side_effect=make_raster) as renderer:
                data, mime = convert(b"%PDF-1.7\nmini", binary=binary)
            self.assertTrue(data.startswith(b"PK"))
            self.assertEqual(1, renderer.call_count)

    def test_rejects_corrupted_or_non_musicxml_mxl(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / "invalid"
            binary.write_text(
                "#!/usr/bin/env python3\n"
                "import pathlib,sys\n"
                "out=pathlib.Path(sys.argv[sys.argv.index('-output')+1]);out.mkdir(exist_ok=True)\n"
                "(out/'result.mxl').write_bytes(b'PK-not-real')\n"
            )
            binary.chmod(0o755)
            with patch("server._render_pdf_as_tiff", side_effect=RecognitionError("bad PDF")):
                with self.assertRaises(RecognitionError):
                    convert(b"%PDF-1.7\nmini", binary=str(binary))

    def test_reports_failure_when_omr_exits_nonzero(self):
        with tempfile.TemporaryDirectory() as folder:
            command = Path(folder) / "fail"
            command.write_text("#!/bin/sh\nexit 2\n")
            command.chmod(0o755)
            with patch("server._render_pdf_as_tiff", side_effect=RecognitionError("bad PDF")):
                with self.assertRaises(RecognitionError):
                    convert(b"%PDF-1.7\nmini", binary=str(command))


if __name__ == "__main__":
    unittest.main()
