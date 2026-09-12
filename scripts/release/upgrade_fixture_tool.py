#!/usr/bin/env python3
"""Validate and select the signed production upgrade fixture corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


FIXTURE_ID = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
SOURCE_FIELDS = {
    "tag", "versionName", "versionCode", "databaseVersion", "commitSha", "packageName",
    "apkAsset", "metadataAsset", "apkSha256", "metadataSha256", "signerSha256",
}
LANE_FIELDS = {"apiLevel", "target", "arch", "channel", "risk"}
METADATA_FIELDS = {
    "schemaVersion", "versionCode", "versionName", "packageName", "apkAsset",
    "apkSizeBytes", "sha256", "signerSha256", "commitSha",
}


class UpgradeFixtureError(ValueError):
    pass


def read_json(path: Path):
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as target:
        json.dump(value, target, ensure_ascii=False, indent=2)
        target.write("\n")


def _plain_asset(value, field: str) -> str:
    if not isinstance(value, str) or not value or Path(value).name != value:
        raise UpgradeFixtureError(f"{field} must be a plain asset name")
    return value


def _positive_integer(value, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise UpgradeFixtureError(f"{field} must be a positive integer")
    return value


def validate_fixture(fixture: dict, expected_id: str | None = None) -> dict:
    return _validate_fixture(fixture, expected_id, current_smoke=False)


def validate_current_smoke(fixture: dict) -> dict:
    return _validate_fixture(fixture, "current-production", current_smoke=True)


def _validate_fixture(fixture: dict, expected_id: str | None, *, current_smoke: bool) -> dict:
    required = {
        "contractVersion", "id", "risk", "source", "targetDatabaseVersion", "apiLanes",
        "seed", "expectedTarget",
    }
    if current_smoke:
        required.add("kind")
    if not isinstance(fixture, dict) or set(fixture) != required:
        raise UpgradeFixtureError("Fixture fields do not match contract version 1")
    version = 2 if current_smoke else 1
    if fixture.get("contractVersion") != version:
        raise UpgradeFixtureError(f"Fixture contractVersion must be {version}")
    if current_smoke and fixture.get("kind") != "current-smoke":
        raise UpgradeFixtureError("Current fixture requires explicit current-smoke kind")
    fixture_id = fixture.get("id")
    if not isinstance(fixture_id, str) or not FIXTURE_ID.fullmatch(fixture_id):
        raise UpgradeFixtureError("Fixture id is invalid")
    if expected_id is not None and fixture_id != expected_id:
        raise UpgradeFixtureError(f"Fixture id {fixture_id} does not match {expected_id}")
    if not isinstance(fixture.get("risk"), str) or not fixture["risk"].strip():
        raise UpgradeFixtureError(f"Fixture {fixture_id} has no risk description")

    source = fixture.get("source")
    if not isinstance(source, dict) or set(source) != SOURCE_FIELDS:
        raise UpgradeFixtureError(f"Fixture {fixture_id} source fields are incomplete")
    version_code = _positive_integer(source["versionCode"], "source.versionCode")
    _positive_integer(source["databaseVersion"], "source.databaseVersion")
    _positive_integer(fixture["targetDatabaseVersion"], "targetDatabaseVersion")
    if (fixture["targetDatabaseVersion"] < source["databaseVersion"]
            or (not current_smoke and fixture["targetDatabaseVersion"] == source["databaseVersion"])):
        raise UpgradeFixtureError(f"Fixture {fixture_id} target schema is not newer")
    if source["tag"] != f"forest-android-{version_code}":
        raise UpgradeFixtureError(f"Fixture {fixture_id} tag and versionCode differ")
    if not isinstance(source["versionName"], str) or not source["versionName"]:
        raise UpgradeFixtureError(f"Fixture {fixture_id} source versionName is missing")
    if not isinstance(source["packageName"], str) or not source["packageName"]:
        raise UpgradeFixtureError(f"Fixture {fixture_id} source packageName is missing")
    _plain_asset(source["apkAsset"], "source.apkAsset")
    _plain_asset(source["metadataAsset"], "source.metadataAsset")
    if not COMMIT.fullmatch(source["commitSha"]):
        raise UpgradeFixtureError(f"Fixture {fixture_id} source commit is invalid")
    for field in ("apkSha256", "metadataSha256", "signerSha256"):
        if not SHA256.fullmatch(source[field]):
            raise UpgradeFixtureError(f"Fixture {fixture_id} source {field} is invalid")

    lanes = fixture.get("apiLanes")
    if not isinstance(lanes, list) or not lanes:
        raise UpgradeFixtureError(f"Fixture {fixture_id} has no API lanes")
    lane_keys: set[tuple[str, str, str, str]] = set()
    for lane in lanes:
        if not isinstance(lane, dict) or set(lane) != LANE_FIELDS:
            raise UpgradeFixtureError(f"Fixture {fixture_id} has an invalid API lane")
        api_level = str(lane["apiLevel"])
        key = (api_level, lane["target"], lane["arch"], lane["channel"])
        if key in lane_keys:
            raise UpgradeFixtureError(f"Fixture {fixture_id} has a duplicate API lane {key}")
        lane_keys.add(key)
        if not all(isinstance(lane[field], str) and lane[field].strip()
                   for field in ("target", "arch", "channel", "risk")):
            raise UpgradeFixtureError(f"Fixture {fixture_id} API lane fields are empty")

    seed = fixture.get("seed")
    if not isinstance(seed, list) or not seed:
        raise UpgradeFixtureError(f"Fixture {fixture_id} has no seed tables")
    seed_tables: set[str] = set()
    for entry in seed:
        if not isinstance(entry, dict) or set(entry) != {"table", "conflict", "rows"}:
            raise UpgradeFixtureError(f"Fixture {fixture_id} has an invalid seed entry")
        table = entry["table"]
        if not isinstance(table, str) or not table or table in seed_tables:
            raise UpgradeFixtureError(f"Fixture {fixture_id} has a duplicate or invalid seed table")
        seed_tables.add(table)
        if entry["conflict"] not in ("ABORT", "REPLACE"):
            raise UpgradeFixtureError(f"Fixture {fixture_id} has an invalid conflict mode")
        if not isinstance(entry["rows"], list) or not entry["rows"]:
            raise UpgradeFixtureError(f"Fixture {fixture_id} seed table {table} has no rows")
        if not all(isinstance(row, dict) and row for row in entry["rows"]):
            raise UpgradeFixtureError(f"Fixture {fixture_id} seed table {table} has invalid rows")

    expected = fixture.get("expectedTarget")
    if not isinstance(expected, list) or not expected:
        raise UpgradeFixtureError(f"Fixture {fixture_id} has no target expectations")
    for entry in expected:
        if not isinstance(entry, dict) or set(entry) != {"table", "where", "values"}:
            raise UpgradeFixtureError(f"Fixture {fixture_id} has an invalid target expectation")
        if not isinstance(entry["table"], str) or not entry["table"]:
            raise UpgradeFixtureError(f"Fixture {fixture_id} target table is invalid")
        if not isinstance(entry["where"], dict) or not entry["where"]:
            raise UpgradeFixtureError(f"Fixture {fixture_id} target selector is empty")
        if not isinstance(entry["values"], dict) or not entry["values"]:
            raise UpgradeFixtureError(f"Fixture {fixture_id} target values are empty")
    return fixture


def load_corpus(root: Path) -> dict[str, dict]:
    manifest_path = root / "corpus.json"
    manifest = read_json(manifest_path)
    if (not isinstance(manifest, dict) or set(manifest) != {"contractVersion", "fixtures"}
            or manifest.get("contractVersion") != 1
            or not isinstance(manifest.get("fixtures"), list) or not manifest["fixtures"]):
        raise UpgradeFixtureError("Upgrade fixture manifest is invalid")
    fixtures: dict[str, dict] = {}
    listed_files: set[str] = set()
    for entry in manifest["fixtures"]:
        if not isinstance(entry, dict) or set(entry) != {"id", "file"}:
            raise UpgradeFixtureError("Upgrade fixture manifest entry is invalid")
        fixture_id = entry["id"]
        file_name = _plain_asset(entry["file"], "fixture file")
        if fixture_id in fixtures:
            raise UpgradeFixtureError(f"Duplicate fixture id {fixture_id}")
        if file_name in listed_files or file_name == manifest_path.name:
            raise UpgradeFixtureError(f"Duplicate fixture file {file_name}")
        listed_files.add(file_name)
        fixtures[fixture_id] = validate_fixture(read_json(root / file_name), fixture_id)
    actual_files = {path.name for path in root.glob("*.json") if path.name != manifest_path.name}
    if actual_files != listed_files:
        raise UpgradeFixtureError(
            f"Fixture files differ from manifest; missing={sorted(listed_files - actual_files)}, "
            f"unknown={sorted(actual_files - listed_files)}"
        )
    return fixtures


def fixture_by_id(root: Path, fixture_id: str) -> dict:
    fixtures = load_corpus(root)
    try:
        return fixtures[fixture_id]
    except KeyError as error:
        raise UpgradeFixtureError(f"Unknown fixture id {fixture_id}") from error


def matrix(root: Path) -> dict:
    include = []
    for fixture in load_corpus(root).values():
        for lane in fixture["apiLanes"]:
            include.append({
                "fixture_id": fixture["id"],
                "source_tag": fixture["source"]["tag"],
                "api-level": lane["apiLevel"],
                "target": lane["target"],
                "arch": lane["arch"],
                "channel": lane["channel"],
            })
    return {"include": include}


def verify_source(fixture: dict, release: dict, tag_ref: dict,
                  metadata_path: Path, apk_path: Path, *, current_smoke: bool = False) -> None:
    fixture = validate_current_smoke(fixture) if current_smoke else validate_fixture(fixture)
    source = fixture["source"]
    if (release.get("tag_name") != source["tag"] or release.get("draft") is not False
            or release.get("prerelease") is not False
            or release.get("target_commitish", "").lower() != source["commitSha"]):
        raise UpgradeFixtureError("Source release identity differs from fixture")
    if tag_ref.get("ref") != f'refs/tags/{source["tag"]}':
        raise UpgradeFixtureError("Source tag name differs from fixture")
    target = tag_ref.get("object", {})
    if target.get("type") != "commit" or target.get("sha", "").lower() != source["commitSha"]:
        raise UpgradeFixtureError("Source tag does not point to fixture commit")
    if hashlib.sha256(metadata_path.read_bytes()).hexdigest() != source["metadataSha256"]:
        raise UpgradeFixtureError("Source metadata bytes differ from fixture")
    metadata = read_json(metadata_path)
    if set(metadata) != METADATA_FIELDS or metadata.get("schemaVersion") != 1:
        raise UpgradeFixtureError("Source release metadata contract is invalid")
    expected_metadata = {
        "versionCode": source["versionCode"],
        "versionName": source["versionName"],
        "packageName": source["packageName"],
        "apkAsset": source["apkAsset"],
        "sha256": source["apkSha256"],
        "signerSha256": source["signerSha256"],
        "commitSha": source["commitSha"],
    }
    for field, expected in expected_metadata.items():
        if metadata.get(field) != expected:
            raise UpgradeFixtureError(f"Source metadata {field} differs from fixture")
    apk_bytes = apk_path.read_bytes()
    if len(apk_bytes) != metadata["apkSizeBytes"]:
        raise UpgradeFixtureError("Source APK size differs from metadata")
    if hashlib.sha256(apk_bytes).hexdigest() != source["apkSha256"]:
        raise UpgradeFixtureError("Source APK bytes differ from fixture")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    commands = root.add_subparsers(dest="command", required=True)

    matrix_command = commands.add_parser("matrix")
    matrix_command.add_argument("--fixtures", type=Path, required=True)

    fixture_command = commands.add_parser("fixture")
    fixture_command.add_argument("--fixtures", type=Path, required=True)
    fixture_command.add_argument("--fixture-id", required=True)
    fixture_command.add_argument("--output", type=Path, required=True)

    for command in ("verify-source", "verify-current-source"):
        verify = commands.add_parser(command)
        verify.add_argument("--fixture", type=Path, required=True)
        verify.add_argument("--release-json", type=Path, required=True)
        verify.add_argument("--tag-ref-json", type=Path, required=True)
        verify.add_argument("--metadata", type=Path, required=True)
        verify.add_argument("--apk", type=Path, required=True)
    return root


def main() -> int:
    try:
        arguments = parser().parse_args()
        if arguments.command == "matrix":
            print(json.dumps(matrix(arguments.fixtures), separators=(",", ":")))
        elif arguments.command == "fixture":
            write_json(arguments.output, fixture_by_id(arguments.fixtures, arguments.fixture_id))
        else:
            verify_source(read_json(arguments.fixture), read_json(arguments.release_json),
                          read_json(arguments.tag_ref_json), arguments.metadata, arguments.apk,
                          current_smoke=arguments.command == "verify-current-source")
        return 0
    except (OSError, KeyError, TypeError, json.JSONDecodeError, UpgradeFixtureError) as error:
        print(f"upgrade_fixture_tool: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
