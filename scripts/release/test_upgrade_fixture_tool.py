import copy
import hashlib
import json
import pathlib
import shutil
import tempfile
import unittest

import upgrade_fixture_tool


ROOT = pathlib.Path(__file__).resolve().parents[2]
CORPUS = ROOT / "release" / "upgrade-fixtures"


class UpgradeFixtureToolTest(unittest.TestCase):
    def test_repository_corpus_produces_the_exact_six_risk_lanes(self):
        result = upgrade_fixture_tool.matrix(CORPUS)

        self.assertEqual(
            [
                ("schema-8-floor", "forest-android-1008001", 26),
                ("schema-8-floor", "forest-android-1008001", 35),
                ("schema-8-floor", "forest-android-1008001", "37.0"),
                ("schema-20-organic-flow", "forest-android-1013701", 26),
                ("schema-22-clean-candidate", "forest-android-1015701", 26),
                ("schema-23-repair-boundary", "forest-android-1015801", 26),
            ],
            [
                (lane["fixture_id"], lane["source_tag"], lane["api-level"])
                for lane in result["include"]
            ],
        )

    def test_target_expectations_accept_values_or_explicit_absence(self):
        for entry in (
            {"table": "migration_recovery", "where": {"sourceId": "clean"}, "absent": True},
            {"table": "tasks", "where": {"id": "task"}, "values": {"note": None}},
        ):
            with self.subTest(entry=entry):
                fixture = copy.deepcopy(upgrade_fixture_tool.fixture_by_id(CORPUS, "schema-8-floor"))
                fixture["expectedTarget"] = [entry]
                upgrade_fixture_tool.validate_fixture(fixture)

    def test_target_expectations_reject_ambiguous_or_unbounded_checks(self):
        base = {"table": "migration_recovery", "where": {"sourceId": "clean"}, "absent": True}
        defects = [
            {**base, "absent": False}, {**base, "absent": "true"}, {**base, "absent": 1},
            {**base, "where": {}}, {**base, "values": {"sourceSchema": 24}},
            {**base, "unknown": True}, {"table": "tasks", "where": {"id": "task"}},
            {"table": "tasks", "where": {"id": "task"}, "values": {}},
            {**base, "table": "tasks WHERE 1=1"}, {**base, "where": {"id OR 1=1": "clean"}},
        ]
        for entry in defects:
            with self.subTest(entry=entry):
                fixture = copy.deepcopy(upgrade_fixture_tool.fixture_by_id(CORPUS, "schema-8-floor"))
                fixture["expectedTarget"] = [entry]
                with self.assertRaises(upgrade_fixture_tool.UpgradeFixtureError):
                    upgrade_fixture_tool.validate_fixture(fixture)

    def test_unknown_fixture_is_rejected(self):
        with self.assertRaisesRegex(upgrade_fixture_tool.UpgradeFixtureError, "Unknown"):
            upgrade_fixture_tool.fixture_by_id(CORPUS, "schema-22-missing")

    def test_manifest_rejects_duplicate_missing_and_orphan_files(self):
        for defect in ("duplicate", "missing", "orphan"):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as temporary:
                root = pathlib.Path(temporary) / "fixtures"
                shutil.copytree(CORPUS, root)
                manifest = self.read(root / "corpus.json")
                if defect == "duplicate":
                    manifest["fixtures"].append(copy.deepcopy(manifest["fixtures"][0]))
                    self.write(root / "corpus.json", manifest)
                elif defect == "missing":
                    (root / manifest["fixtures"][0]["file"]).unlink()
                else:
                    self.write(root / "orphan.json", {})

                with self.assertRaises((OSError, upgrade_fixture_tool.UpgradeFixtureError)):
                    upgrade_fixture_tool.load_corpus(root)

    def test_source_verification_accepts_exact_bytes_and_identity(self):
        fixture, release, tag_ref, metadata, apk = self.source_evidence()

        upgrade_fixture_tool.verify_source(fixture, release, tag_ref, metadata, apk)

    def test_source_verification_rejects_every_identity_boundary(self):
        mutations = {
            "release": lambda release, tag, metadata, apk: release.update(
                {"target_commitish": "0" * 40}
            ),
            "tag": lambda release, tag, metadata, apk: tag["object"].update(
                {"sha": "1" * 40}
            ),
            "metadata": lambda release, tag, metadata, apk: metadata.write_bytes(
                metadata.read_bytes() + b" "
            ),
            "apk": lambda release, tag, metadata, apk: apk.write_bytes(
                apk.read_bytes() + b"tampered"
            ),
        }
        for boundary, mutate in mutations.items():
            with self.subTest(boundary=boundary):
                fixture, release, tag_ref, metadata, apk = self.source_evidence()
                mutate(release, tag_ref, metadata, apk)
                with self.assertRaises(upgrade_fixture_tool.UpgradeFixtureError):
                    upgrade_fixture_tool.verify_source(
                        fixture, release, tag_ref, metadata, apk
                    )

    def source_evidence(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = pathlib.Path(temporary.name)
        fixture = copy.deepcopy(
            upgrade_fixture_tool.fixture_by_id(CORPUS, "schema-8-floor")
        )
        apk = root / fixture["source"]["apkAsset"]
        apk.write_bytes(b"exact signed source apk")
        fixture["source"]["apkSha256"] = hashlib.sha256(apk.read_bytes()).hexdigest()
        metadata_value = {
            "schemaVersion": 1,
            "versionCode": fixture["source"]["versionCode"],
            "versionName": fixture["source"]["versionName"],
            "packageName": fixture["source"]["packageName"],
            "apkAsset": fixture["source"]["apkAsset"],
            "apkSizeBytes": apk.stat().st_size,
            "sha256": fixture["source"]["apkSha256"],
            "signerSha256": fixture["source"]["signerSha256"],
            "commitSha": fixture["source"]["commitSha"],
        }
        metadata = root / fixture["source"]["metadataAsset"]
        self.write(metadata, metadata_value)
        fixture["source"]["metadataSha256"] = hashlib.sha256(
            metadata.read_bytes()
        ).hexdigest()
        release = {
            "tag_name": fixture["source"]["tag"],
            "draft": False,
            "prerelease": False,
            "target_commitish": fixture["source"]["commitSha"],
        }
        tag_ref = {
            "ref": f"refs/tags/{fixture['source']['tag']}",
            "object": {"type": "commit", "sha": fixture["source"]["commitSha"]},
        }
        return fixture, release, tag_ref, metadata, apk

    @staticmethod
    def read(path):
        return json.loads(path.read_text(encoding="utf-8"))

    @staticmethod
    def write(path, value):
        path.write_text(json.dumps(value) + "\n", encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
