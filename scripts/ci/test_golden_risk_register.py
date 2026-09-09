import csv
import hashlib
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GOLDEN_ROOT = ROOT / "app" / "src" / "test" / "resources" / "golden"
REGISTER = ROOT / "app" / "src" / "test" / "resources" / "golden-risks.tsv"


class GoldenRiskRegisterTest(unittest.TestCase):
    def test_every_golden_has_one_unique_risk(self):
        with REGISTER.open(encoding="utf-8", newline="") as stream:
            rows = list(csv.DictReader(stream, delimiter="\t"))

        paths = [row["path"] for row in rows]
        risk_ids = [row["risk_id"] for row in rows]
        descriptions = [row["risk"].strip() for row in rows]
        actual_paths = sorted(
            path.relative_to(GOLDEN_ROOT).as_posix()
            for path in GOLDEN_ROOT.rglob("*")
            if path.is_file()
        )

        self.assertEqual(actual_paths, sorted(paths))
        self.assertEqual(len(paths), len(set(paths)))
        self.assertEqual(len(risk_ids), len(set(risk_ids)))
        self.assertEqual(len(descriptions), len(set(descriptions)))
        self.assertTrue(all(descriptions))

    def test_no_byte_identical_golden_is_kept_twice(self):
        by_digest = {}
        for path in sorted(GOLDEN_ROOT.rglob("*")):
            if not path.is_file():
                continue
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            by_digest.setdefault(digest, []).append(path.relative_to(GOLDEN_ROOT).as_posix())

        duplicates = [paths for paths in by_digest.values() if len(paths) > 1]
        self.assertEqual([], duplicates)


if __name__ == "__main__":
    unittest.main()
