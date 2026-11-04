import sys
from pathlib import Path
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from server import convert, RecognitionError, MAX_UPLOAD


class ConversionTests(unittest.TestCase):
    def test_rejects_bad_pdfs_and_oversized_inputs(self):
        with self.assertRaises(ValueError):
            convert(b"not a PDF")
        with self.assertRaises(ValueError):
            convert(b"%PDF-1.7\n" + b"x" * MAX_UPLOAD)

    def test_recognizes_generated_mxl(self):
        with tempfile.TemporaryDirectory() as folder:
            command = Path(folder) / "fake-audiveris"
            command.write_text(
                "#!/usr/bin/env python3\n"
                "import pathlib, sys\n"
                "root = pathlib.Path(sys.argv[sys.argv.index('-output') + 1]); root.mkdir(exist_ok=True)\n"
                "(root / 'result.mxl').write_bytes(b'PK\\x03\\x04-example')\n"
            )
            command.chmod(0o755)
            data, mime = convert(b"%PDF-1.7\nmini", binary=str(command))
            self.assertEqual(b"PK\x03\x04-example", data)
            self.assertEqual("application/vnd.recordare.musicxml", mime)

    def test_reports_failure_when_omr_exits_nonzero(self):
        with tempfile.TemporaryDirectory() as folder:
            command = Path(folder) / "fail"
            command.write_text("#!/bin/sh\nexit 2\n")
            command.chmod(0o755)
            with self.assertRaises(RecognitionError):
                convert(b"%PDF-1.7\nmini", binary=str(command))


if __name__ == "__main__":
    unittest.main()
