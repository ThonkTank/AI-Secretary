#!/usr/bin/env python3
"""Pin the current published upgrade source once, before building the candidate."""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

from upgrade_fixture_tool import (COMMIT, UpgradeFixtureError, read_json, write_json,
                                  validate_current_smoke, verify_source)

DATABASE_CONTRACT = "app/src/main/java/de/thonktank/autosecretary/DatabaseContract.java"


def database_version(source: str) -> int:
    matches = re.findall(r"\bint\s+VERSION\s*=\s*(\d+)\s*;", source)
    if len(matches) != 1 or int(matches[0]) <= 0:
        raise UpgradeFixtureError("Immutable source has no unique database version")
    return int(matches[0])


def select_source(plan: dict, releases: list[dict]) -> dict:
    prefix = plan["tagPrefix"]
    candidates = []
    for release in releases:
        if release.get("draft") is not False or release.get("prerelease") is not False:
            continue
        tag = release.get("tag_name", "")
        if not isinstance(tag, str) or not re.fullmatch(re.escape(prefix) + r"[0-9]+", tag):
            continue
        candidates.append((int(tag[len(prefix):]), release))
    if not candidates:
        raise UpgradeFixtureError("Current published production source is missing")
    highest = max(version for version, _ in candidates)
    latest = [release for version, release in candidates if version == highest]
    if len(latest) != 1:
        raise UpgradeFixtureError("Current published source is ambiguous")
    if highest >= plan["versionCode"]:
        raise UpgradeFixtureError("Candidate must be newer than current published source")
    release = latest[0]
    if not COMMIT.fullmatch(release.get("target_commitish", "")):
        raise UpgradeFixtureError("Current release does not pin an immutable commit")
    assets = [asset.get("name") for asset in release.get("assets", [])]
    for asset in (plan["apkAsset"], plan["metadataAsset"]):
        if assets.count(asset) != 1:
            raise UpgradeFixtureError(f"Current release is missing a unique {asset}")
    return release


def fixture(plan: dict, release: dict, tag_ref: dict, metadata_path: Path, apk_path: Path,
            source_contract: str, target_contract: str, template: dict) -> dict:
    source_version = database_version(source_contract)
    target_version = database_version(target_contract)
    if template.get("databaseVersion") != source_version:
        raise UpgradeFixtureError(f"No current smoke template for source schema {source_version}")
    metadata = read_json(metadata_path)
    for field in ("packageName", "apkAsset", "signerSha256"):
        if metadata.get(field) != plan[field]:
            raise UpgradeFixtureError(f"Current source {field} is incompatible")
    if metadata["versionCode"] >= plan["versionCode"]:
        raise UpgradeFixtureError("Current smoke requires a higher application version")
    day = datetime.now(timezone.utc).date().isoformat()
    rendered = bind_calendar_day(template, day)
    seed = rendered["seed"]
    expected = rendered["expectedTarget"]
    result = {
        "contractVersion": 2, "kind": "current-smoke", "id": "current-production",
        "risk": "Signed in-place update retains tasks, Today ordering and completed history",
        "source": {
            "tag": release["tag_name"], "versionName": metadata["versionName"],
            "versionCode": metadata["versionCode"], "databaseVersion": source_version,
            "commitSha": release["target_commitish"], "packageName": metadata["packageName"],
            "apkAsset": plan["apkAsset"], "metadataAsset": plan["metadataAsset"],
            "apkSha256": hashlib.sha256(apk_path.read_bytes()).hexdigest(),
            "metadataSha256": hashlib.sha256(metadata_path.read_bytes()).hexdigest(),
            "signerSha256": plan["signerSha256"],
        },
        "targetDatabaseVersion": target_version,
        "apiLanes": [{"apiLevel": 35, "target": "google_apis", "arch": "x86_64",
                      "channel": "stable", "risk": "Current signed production update"}],
        "seed": seed, "expectedTarget": expected,
    }
    validate_current_smoke(result)
    verify_source(result, release, tag_ref, metadata_path, apk_path, current_smoke=True)
    return result


def bind_calendar_day(value: object, day: str) -> object:
    """Resolve the template's exact day marker without changing historical dates."""
    if isinstance(value, dict):
        return {key: bind_calendar_day(item, day) for key, item in value.items()}
    if isinstance(value, list):
        return [bind_calendar_day(item, day) for item in value]
    return day if value == "${TODAY}" else value


def gh_json(endpoint: str) -> dict:
    return json.loads(subprocess.check_output(["gh", "api", endpoint], text=True))


def prepare(plan: dict, releases: list[dict], target_contract: Path,
            templates: Path, output: Path) -> None:
    release = select_source(plan, releases)
    output.mkdir(parents=True, exist_ok=False)
    repository = plan["repository"]
    source = output / "source"
    source.mkdir()
    tag = release["tag_name"]
    commit = release["target_commitish"]
    tag_ref = gh_json(f"repos/{repository}/git/ref/tags/{tag}")
    encoded = gh_json(f"repos/{repository}/contents/{DATABASE_CONTRACT}?ref={commit}")
    source_contract = base64.b64decode(encoded["content"], validate=False).decode("utf-8")
    version = database_version(source_contract)
    template = read_json(templates / f"schema-{version}.json")
    subprocess.run(["gh", "release", "download", tag, "--repo", repository, "--dir", str(source),
                    "--pattern", plan["apkAsset"], "--pattern", plan["metadataAsset"]], check=True)
    result = fixture(plan, release, tag_ref, source / plan["metadataAsset"],
                     source / plan["apkAsset"], source_contract, target_contract.read_text(), template)
    write_json(source / "release.json", release)
    write_json(source / "tag-ref.json", tag_ref)
    (source / "DatabaseContract.java").write_text(source_contract)
    write_json(output / "assets" / "current-production.json", result)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--releases", type=Path, required=True)
    parser.add_argument("--target-contract", type=Path, default=Path(DATABASE_CONTRACT))
    parser.add_argument("--templates", type=Path, default=Path("release/current-smoke"))
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        prepare(read_json(args.plan), read_json(args.releases), args.target_contract,
                args.templates, args.output_dir)
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(f"Current upgrade source rejected: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
