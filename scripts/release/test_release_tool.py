import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from release_tool import (
    ReleaseContractError,
    allocate_version,
    build_metadata,
    previous_release,
    read_properties,
    release_plan,
    validate_tag_ref,
    validate_metadata,
)


COMMIT = "a" * 40
SIGNER = "b" * 64


class ReleaseToolTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.properties = self.root / "release.properties"
        self.properties.write_text(
            "repositoryOwner=ThonkTank\n"
            "repositoryName=AI-Secretary\n"
            "packageName=de.thonktank.autosecretary\n"
            "apkAsset=AutoSecretary.apk\n"
            "metadataAsset=release-metadata.json\n"
            "tagPrefix=forest-android-\n"
            "versionSeries=0.2\n"
            "versionCodeFloor=1000000\n"
            f"expectedSignerSha256={SIGNER}\n"
            "androidBuildTools=35.0.0\n"
            "supportedUpgradeTag=forest-android-1008001\n",
            encoding="utf-8",
        )
        self.contract = read_properties(self.properties)

    def tearDown(self):
        self.temporary.cleanup()

    def test_versions_advance_from_the_last_published_product_not_ci_runs(self):
        releases = [{"tag_name": "forest-android-1008901", "draft": False}]
        self.assertEqual((1_009_001, "0.2.90"), allocate_version(self.contract, releases))

        releases.append({"tag_name": "forest-android-1009001", "draft": True})
        self.assertEqual((1_009_002, "0.2.90-r2"), allocate_version(self.contract, releases))

    def test_new_release_plan_contains_the_public_tag_contract(self):
        plan = release_plan(self.contract, COMMIT, [])
        self.assertEqual("create_draft", plan["action"])
        self.assertEqual("forest-android-1000101", plan["tag"])
        self.assertEqual("0.2.1", plan["versionName"])
        self.assertEqual("ThonkTank/AI-Secretary", plan["repository"])
        self.assertEqual("forest-android-1008001", plan["upgradeFromTag"])

    def test_abandoned_draft_does_not_skip_a_visible_product_version(self):
        releases = [{
            "tag_name": "forest-android-1000101",
            "target_commitish": "c" * 40,
            "draft": True,
            "assets": [],
        }]
        plan = release_plan(self.contract, COMMIT, releases)
        self.assertEqual("forest-android-1000102", plan["tag"])
        self.assertEqual("0.2.1-r2", plan["versionName"])

    def test_draft_with_existing_assets_is_resumed(self):
        releases = [{
            "id": 42,
            "tag_name": "forest-android-1000101",
            "target_commitish": COMMIT,
            "draft": True,
            "assets": [{"name": "AutoSecretary.apk"}],
        }]
        plan = release_plan(self.contract, COMMIT, releases)
        self.assertEqual("resume_draft", plan["action"])
        self.assertEqual(42, plan["releaseId"])
        self.assertEqual(["AutoSecretary.apk"], plan["existingAssets"])
        self.assertEqual(1_000_101, plan["versionCode"])
        self.assertEqual("0.2.1", plan["versionName"])

    def test_published_commit_stops_duplicate_release(self):
        releases = [{
            "id": 43,
            "tag_name": "forest-android-1000101",
            "target_commitish": COMMIT,
            "draft": False,
            "assets": [],
        }]
        self.assertEqual("already_published",
                         release_plan(self.contract, COMMIT, releases)["action"])

    def test_existing_tag_must_point_to_the_planned_commit(self):
        plan = release_plan(self.contract, COMMIT, [])
        validate_tag_ref(plan, {
            "ref": "refs/tags/forest-android-1000101",
            "object": {"type": "commit", "sha": COMMIT},
        })
        with self.assertRaises(ReleaseContractError):
            validate_tag_ref(plan, {
                "ref": "refs/tags/forest-android-1000101",
                "object": {"type": "commit", "sha": "c" * 40},
            })

    def test_metadata_schema_hash_and_size_are_derived_from_apk(self):
        apk = self.root / "AutoSecretary.apk"
        apk.write_bytes(b"signed apk bytes")
        plan = release_plan(self.contract, COMMIT, [])
        metadata = build_metadata(plan, apk)
        self.assertEqual(len(b"signed apk bytes"), metadata["apkSizeBytes"])
        self.assertEqual(hashlib.sha256(b"signed apk bytes").hexdigest(), metadata["sha256"])
        validate_metadata(metadata, apk, plan)
        apk.write_bytes(b"tampered")
        with self.assertRaises(ReleaseContractError):
            validate_metadata(metadata, apk, plan)

    def test_upgrade_release_is_the_explicit_supported_production_version(self):
        releases = [
            {"tag_name": "forest-android-1008001", "draft": False, "prerelease": False},
            {"tag_name": "forest-android-1008901", "draft": False, "prerelease": False},
        ]
        plan = release_plan(self.contract, COMMIT, releases)
        self.assertEqual("forest-android-1008001", previous_release(plan, releases))

    def test_missing_supported_upgrade_release_is_rejected(self):
        releases = [{
            "tag_name": "forest-android-1008901", "draft": False, "prerelease": False,
        }]
        plan = release_plan(self.contract, COMMIT, releases)
        with self.assertRaises(ReleaseContractError):
            previous_release(plan, releases)

    def test_unknown_release_property_is_rejected(self):
        with self.properties.open("a", encoding="utf-8") as target:
            target.write("unexpected=true\n")
        with self.assertRaises(ReleaseContractError):
            read_properties(self.properties)


if __name__ == "__main__":
    unittest.main()
