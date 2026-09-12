from copy import deepcopy
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

from current_upgrade_smoke import DATABASE_CONTRACT, database_version, fixture, select_source
from upgrade_fixture_tool import UpgradeFixtureError, validate_fixture, validate_current_smoke, verify_source

ROOT = Path(__file__).resolve().parents[2]


class CurrentUpgradeSmokeTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.plan = dict(repository="owner/repo", tagPrefix="forest-android-", versionCode=1017301,
                         packageName="de.thonktank.autosecretary", apkAsset="AutoSecretary.apk",
                         metadataAsset="release-metadata.json", signerSha256="a" * 64)
        self.release = dict(tag_name="forest-android-1017201", target_commitish="b" * 40,
                            draft=False, prerelease=False,
                            assets=[dict(name=self.plan[k]) for k in ("apkAsset", "metadataAsset")])
        self.tag = dict(ref="refs/tags/forest-android-1017201", object=dict(type="commit", sha="b" * 40))
        self.apk = self.root / "AutoSecretary.apk"
        self.apk.write_bytes(b"synthetic apk bytes; cryptographic certificate checked by Android tooling")
        self.metadata = self.root / "release-metadata.json"
        self.metadata.write_text(json.dumps(dict(
            schemaVersion=1, versionCode=1017201, versionName="0.2.172",
            packageName=self.plan["packageName"], apkAsset=self.apk.name,
            apkSizeBytes=self.apk.stat().st_size, sha256=hashlib.sha256(self.apk.read_bytes()).hexdigest(),
            signerSha256=self.plan["signerSha256"], commitSha="b" * 40)))
        self.template = json.loads((ROOT / "release/current-smoke/schema-27.json").read_text())
        self.contract = (ROOT / DATABASE_CONTRACT).read_text()

    def build(self, **overrides):
        arguments = dict(plan=self.plan, release=self.release, tag_ref=self.tag,
                         metadata_path=self.metadata, apk_path=self.apk,
                         source_contract=self.contract, target_contract=self.contract, template=self.template)
        arguments.update(overrides)
        return fixture(**arguments)

    def test_same_schema_is_only_allowed_for_explicit_current_smoke(self):
        result = self.build()
        self.assertEqual(27, result["source"]["databaseVersion"])
        self.assertEqual(27, result["targetDatabaseVersion"])
        validate_current_smoke(result)
        with self.assertRaises(UpgradeFixtureError):
            validate_fixture(result)
        historical = deepcopy(result)
        historical.pop("kind")
        historical["contractVersion"] = 1
        with self.assertRaises(UpgradeFixtureError):
            validate_fixture(historical)
        result["kind"] = "historical"
        with self.assertRaises(UpgradeFixtureError):
            validate_current_smoke(result)

    def test_current_source_is_highest_published_and_never_silently_falls_back(self):
        old = deepcopy(self.release)
        old["tag_name"] = "forest-android-1017101"
        self.assertEqual(self.release, select_source(self.plan, [old, self.release]))
        for defect in ("assets", "mutable", "duplicate", "missing", "future"):
            bad = deepcopy(self.release)
            if defect == "assets": bad["assets"] = []
            if defect == "mutable": bad["target_commitish"] = "main"
            if defect == "future": bad["tag_name"] = "forest-android-1017401"
            releases = [old, bad]
            if defect == "duplicate": releases.append(deepcopy(bad))
            if defect == "missing": releases = []
            with self.subTest(defect=defect), self.assertRaises(UpgradeFixtureError):
                select_source(self.plan, releases)

    def test_source_metadata_format_is_not_used_as_database_version(self):
        self.assertEqual(27, database_version(self.contract))
        self.assertEqual(1, json.loads(self.metadata.read_text())["schemaVersion"])
        with self.assertRaises(UpgradeFixtureError):
            self.build(source_contract=self.contract.replace("VERSION = 27;", "VERSION = 28;"))
        with self.assertRaises(UpgradeFixtureError):
            self.build(target_contract=self.contract.replace("VERSION = 27;", "VERSION = 26;"))
        with self.assertRaises(UpgradeFixtureError):
            database_version("// no database version")

    def test_release_identity_tag_and_metadata_must_agree(self):
        for field, value in (("packageName", "other"), ("signerSha256", "0" * 64),
                             ("commitSha", "0" * 40), ("versionCode", 1017301),
                             ("sha256", "0" * 64)):
            original = self.metadata.read_text()
            data = json.loads(original)
            data[field] = value
            self.metadata.write_text(json.dumps(data))
            with self.subTest(field=field), self.assertRaises(UpgradeFixtureError):
                self.build()
            self.metadata.write_text(original)
        tag = deepcopy(self.tag)
        tag["object"]["sha"] = "c" * 40
        with self.assertRaises(UpgradeFixtureError):
            self.build(tag_ref=tag)

    def test_after_pinning_every_source_byte_is_bound(self):
        result = self.build()
        for path in (self.apk, self.metadata):
            original = path.read_bytes()
            path.write_bytes(original + b" ")
            with self.assertRaises(UpgradeFixtureError):
                verify_source(result, self.release, self.tag, self.metadata, self.apk, current_smoke=True)
            path.write_bytes(original)

    def test_current_fixture_is_valid_in_real_sqlite_and_preserves_all_seed_values(self):
        result = self.build()
        schema = json.loads(next((ROOT / "app/schemas").rglob("27.json")).read_text())["database"]
        database = sqlite3.connect(":memory:")
        self.addCleanup(database.close)
        database.execute("PRAGMA foreign_keys=ON")
        columns = {}
        for entity in schema["entities"]:
            table = entity["tableName"]
            database.execute(entity["createSql"].replace("${TABLE_NAME}", table))
            columns[table] = {field["columnName"] for field in entity["fields"]}
        for entry in result["seed"]:
            for row in entry["rows"]:
                self.assertEqual(columns[entry["table"]], set(row))
                names = ",".join('"' + key + '"' for key in row)
                marks = ",".join("?" for _ in row)
                database.execute(f'INSERT INTO "{entry["table"]}" ({names}) VALUES ({marks})', list(row.values()))
        self.assertEqual([], database.execute("PRAGMA foreign_key_check").fetchall())
        for expected in result["expectedTarget"]:
            names = ",".join('"' + key + '"' for key in expected["values"])
            where = " AND ".join('"' + key + '" = ?' for key in expected["where"])
            actual = database.execute(f'SELECT {names} FROM "{expected["table"]}" WHERE {where}',
                                      list(expected["where"].values())).fetchall()
            self.assertEqual([tuple(expected["values"].values())], actual)
        self.assertEqual(1, database.execute("SELECT count(*) FROM occurrences WHERE state='COMPLETED'").fetchone()[0])
        self.assertEqual(1, database.execute("SELECT count(*) FROM today_placements").fetchone()[0])
