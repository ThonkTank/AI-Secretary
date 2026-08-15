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
            "androidBuildTools=35.0.0\n",
            encoding="utf-8",
        )
        self.contract = read_properties(self.properties)

    def tearDown(self):
        self.temporary.cleanup()

    def test_versions_are_monotone_and_names_match_attempts(self):
        first = allocate_version(self.contract, 20, 1)
        retry = allocate_version(self.contract, 20, 2)
        later = allocate_version(self.contract, 21, 1)
        self.assertEqual((1_002_001, "0.2.20"), first)
        self.assertEqual((1_002_002, "0.2.20-r2"), retry)
        self.assertGreater(retry[0], first[0])
        self.assertGreater(later[0], retry[0])

    def test_new_release_plan_contains_the_public_tag_contract(self):
        plan = release_plan(self.contract, 20, 1, COMMIT, [])
        self.assertEqual("create_draft", plan["action"])
        self.assertEqual("forest-android-1002001", plan["tag"])
        self.assertEqual("0.2.20", plan["versionName"])
        self.assertEqual("ThonkTank/AI-Secretary", plan["repository"])

    def test_new_release_must_be_newer_than_every_existing_channel_tag(self):
        releases = [{
            "tag_name": "forest-android-1002501",
            "target_commitish": "c" * 40,
            "draft": False,
            "assets": [],
        }]
        with self.assertRaises(ReleaseContractError):
            release_plan(self.contract, 20, 1, COMMIT, releases)

    def test_draft_with_existing_assets_is_resumed(self):
        releases = [{
            "id": 42,
            "tag_name": "forest-android-1002001",
            "target_commitish": COMMIT,
            "draft": True,
            "assets": [{"name": "AutoSecretary.apk"}],
        }]
        plan = release_plan(self.contract, 20, 2, COMMIT, releases)
        self.assertEqual("resume_draft", plan["action"])
        self.assertEqual(42, plan["releaseId"])
        self.assertEqual(["AutoSecretary.apk"], plan["existingAssets"])
        self.assertEqual(1_002_001, plan["versionCode"])
        self.assertEqual("0.2.20", plan["versionName"])

    def test_published_commit_stops_duplicate_release(self):
        releases = [{
            "id": 43,
            "tag_name": "forest-android-1002001",
            "target_commitish": COMMIT,
            "draft": False,
            "assets": [],
        }]
        self.assertEqual("already_published",
                         release_plan(self.contract, 20, 2, COMMIT, releases)["action"])

    def test_existing_tag_must_point_to_the_planned_commit(self):
        plan = release_plan(self.contract, 20, 1, COMMIT, [])
        validate_tag_ref(plan, {
            "ref": "refs/tags/forest-android-1002001",
            "object": {"type": "commit", "sha": COMMIT},
        })
        with self.assertRaises(ReleaseContractError):
            validate_tag_ref(plan, {
                "ref": "refs/tags/forest-android-1002001",
                "object": {"type": "commit", "sha": "c" * 40},
            })

    def test_metadata_schema_hash_and_size_are_derived_from_apk(self):
        apk = self.root / "AutoSecretary.apk"
        apk.write_bytes(b"signed apk bytes")
        plan = release_plan(self.contract, 20, 1, COMMIT, [])
        metadata = build_metadata(plan, apk)
        self.assertEqual(len(b"signed apk bytes"), metadata["apkSizeBytes"])
        self.assertEqual(hashlib.sha256(b"signed apk bytes").hexdigest(), metadata["sha256"])
        validate_metadata(metadata, apk, plan)
        apk.write_bytes(b"tampered")
        with self.assertRaises(ReleaseContractError):
            validate_metadata(metadata, apk, plan)

    def test_previous_release_ignores_drafts_prereleases_and_invalid_tags(self):
        plan = release_plan(self.contract, 20, 1, COMMIT, [])
        releases = [
            {"tag_name": "forest-android-1001901", "draft": False, "prerelease": False},
            {"tag_name": "forest-android-1001902", "draft": True, "prerelease": False},
            {"tag_name": "forest-android-invalid", "draft": False, "prerelease": False},
            {"tag_name": "forest-android-1001903", "draft": False, "prerelease": True},
        ]
        self.assertEqual("forest-android-1001901", previous_release(plan, releases))

    def test_unknown_release_property_is_rejected(self):
        with self.properties.open("a", encoding="utf-8") as target:
            target.write("unexpected=true\n")
        with self.assertRaises(ReleaseContractError):
            read_properties(self.properties)


if __name__ == "__main__":
    unittest.main()
