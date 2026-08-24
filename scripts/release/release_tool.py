#!/usr/bin/env python3
"""Build and validate Auto Secretary's deterministic release contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


REQUIRED_PROPERTIES = {
    "repositoryOwner",
    "repositoryName",
    "packageName",
    "apkAsset",
    "metadataAsset",
    "tagPrefix",
    "versionSeries",
    "versionCodeFloor",
    "expectedSignerSha256",
    "androidBuildTools",
    "supportedUpgradeTag",
}
SHA256 = re.compile(r"^[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
MAX_VERSION_CODE = 2_100_000_000
MAX_APK_BYTES = 8 * 1024 * 1024


class ReleaseContractError(ValueError):
    pass


def load_json(path: Path):
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as target:
        json.dump(value, target, ensure_ascii=False, indent=2)
        target.write("\n")


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ReleaseContractError(f"Invalid property at {path}:{number}")
        key, value = line.split("=", 1)
        if not key or not value or key in values:
            raise ReleaseContractError(f"Invalid property at {path}:{number}")
        values[key] = value
    missing = REQUIRED_PROPERTIES - values.keys()
    unknown = values.keys() - REQUIRED_PROPERTIES
    if missing or unknown:
        raise ReleaseContractError(
            f"Release properties differ from the schema; missing={sorted(missing)}, "
            f"unknown={sorted(unknown)}"
        )
    if not SHA256.fullmatch(values["expectedSignerSha256"]):
        raise ReleaseContractError("expectedSignerSha256 must be lowercase SHA-256")
    floor = integer(values["versionCodeFloor"], "versionCodeFloor")
    if floor <= 0 or floor >= MAX_VERSION_CODE:
        raise ReleaseContractError("versionCodeFloor is outside the Android range")
    for key in ("apkAsset", "metadataAsset"):
        if Path(values[key]).name != values[key]:
            raise ReleaseContractError(f"{key} must be a plain asset name")
    upgrade_code = version_from_tag(values["supportedUpgradeTag"], values["tagPrefix"])
    if upgrade_code is None:
        raise ReleaseContractError("supportedUpgradeTag must follow the production tag contract")
    return values


def integer(value, field: str) -> int:
    if isinstance(value, bool):
        raise ReleaseContractError(f"{field} must be an integer")
    try:
        result = int(value)
    except (TypeError, ValueError) as error:
        raise ReleaseContractError(f"{field} must be an integer") from error
    return result


def version_from_tag(tag: str, prefix: str) -> int | None:
    if not isinstance(tag, str) or not tag.startswith(prefix):
        return None
    suffix = tag[len(prefix):]
    if not suffix.isdigit() or suffix.startswith("0"):
        return None
    value = int(suffix)
    return value if 0 < value <= MAX_VERSION_CODE else None


def version_name_for_code(series: str, floor: int, version_code: int) -> str:
    delta = version_code - floor
    run_number, run_attempt = divmod(delta, 100)
    if run_number <= 0 or not 1 <= run_attempt < 100:
        raise ReleaseContractError("Version code does not follow the configured allocation")
    name = f"{series}.{run_number}"
    return name if run_attempt == 1 else f"{name}-r{run_attempt}"


def allocate_version(contract: dict[str, str], releases: list[dict]) -> tuple[int, str]:
    """Allocate the next product version, independent of CI run numbers.

    A failed workflow may leave a draft in the next product-version window. In that case the
    next free retry code is used without advancing the user-visible product version again.
    """
    floor = integer(contract["versionCodeFloor"], "versionCodeFloor")
    stable_codes = [
        code for release in releases
        if not release.get("draft") and not release.get("prerelease")
        and (code := version_from_tag(release.get("tag_name"), contract["tagPrefix"])) is not None
    ]
    last_product = max(((code - floor) // 100 for code in stable_codes), default=0)
    next_product = last_product + 1
    window_start = floor + next_product * 100
    occupied = {
        code for release in releases
        if (code := version_from_tag(release.get("tag_name"), contract["tagPrefix"])) is not None
    }
    version_code = next((window_start + retry for retry in range(1, 100)
                         if window_start + retry not in occupied), None)
    if version_code is None or version_code > MAX_VERSION_CODE:
        raise ReleaseContractError("Allocated version code exceeds the Android limit")
    return version_code, version_name_for_code(contract["versionSeries"], floor, version_code)


def release_plan(contract: dict[str, str], commit: str, releases: list[dict]) -> dict:
    if not COMMIT.fullmatch(commit):
        raise ReleaseContractError("commit must be a full lowercase Git SHA")
    prefix = contract["tagPrefix"]
    floor = integer(contract["versionCodeFloor"], "versionCodeFloor")

    candidates = []
    for release in releases:
        code = version_from_tag(release.get("tag_name"), prefix)
        if code is None or release.get("target_commitish", "").lower() != commit:
            continue
        candidates.append((code, release))

    published = [(code, release) for code, release in candidates if not release.get("draft")]
    if published:
        code, release = max(published, key=lambda item: item[0])
        action = "already_published"
    else:
        drafts = [(code, release) for code, release in candidates if release.get("draft")]
        if drafts:
            code, release = max(drafts, key=lambda item: item[0])
            version_name_for_code(contract["versionSeries"], floor, code)
            action = "resume_draft"
        else:
            code, name = allocate_version(contract, releases)
            release = {"tag_name": f"{prefix}{code}", "id": None, "assets": []}
            action = "create_draft"

    name = version_name_for_code(contract["versionSeries"], floor, code)
    return {
        "schemaVersion": 1,
        "action": action,
        "releaseId": release.get("id"),
        "existingAssets": sorted(asset.get("name", "") for asset in release.get("assets", [])),
        "repository": f'{contract["repositoryOwner"]}/{contract["repositoryName"]}',
        "packageName": contract["packageName"],
        "apkAsset": contract["apkAsset"],
        "metadataAsset": contract["metadataAsset"],
        "tagPrefix": prefix,
        "tag": release["tag_name"],
        "versionCode": code,
        "versionName": name,
        "signerSha256": contract["expectedSignerSha256"],
        "androidBuildTools": contract["androidBuildTools"],
        "upgradeFromTag": contract["supportedUpgradeTag"],
        "commitSha": commit,
    }


def build_metadata(plan: dict, apk: Path) -> dict:
    size = apk.stat().st_size
    if size <= 0 or size > MAX_APK_BYTES:
        raise ReleaseContractError("APK size is outside the updater limit")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    metadata = {
        "schemaVersion": 1,
        "versionCode": integer(plan["versionCode"], "versionCode"),
        "versionName": plan["versionName"],
        "packageName": plan["packageName"],
        "apkAsset": plan["apkAsset"],
        "apkSizeBytes": size,
        "sha256": digest,
        "signerSha256": plan["signerSha256"],
        "commitSha": plan["commitSha"],
    }
    validate_metadata(metadata, apk, plan)
    return metadata


def validate_metadata(metadata: dict, apk: Path, plan: dict | None = None) -> None:
    expected_fields = {
        "schemaVersion", "versionCode", "versionName", "packageName", "apkAsset",
        "apkSizeBytes", "sha256", "signerSha256", "commitSha",
    }
    if set(metadata) != expected_fields or metadata.get("schemaVersion") != 1:
        raise ReleaseContractError("Release metadata does not match schema version 1")
    version_code = integer(metadata["versionCode"], "versionCode")
    size = integer(metadata["apkSizeBytes"], "apkSizeBytes")
    if version_code <= 0 or size <= 0 or size > MAX_APK_BYTES:
        raise ReleaseContractError("Release metadata contains invalid numeric values")
    if not isinstance(metadata["versionName"], str) or not metadata["versionName"]:
        raise ReleaseContractError("versionName is missing")
    if not SHA256.fullmatch(metadata.get("sha256", "")):
        raise ReleaseContractError("APK SHA-256 is invalid")
    if not SHA256.fullmatch(metadata.get("signerSha256", "")):
        raise ReleaseContractError("Signer SHA-256 is invalid")
    if not COMMIT.fullmatch(metadata.get("commitSha", "")):
        raise ReleaseContractError("Commit SHA is invalid")
    if apk.stat().st_size != size or hashlib.sha256(apk.read_bytes()).hexdigest() != metadata["sha256"]:
        raise ReleaseContractError("APK bytes do not match release metadata")
    if plan is not None:
        mapping = {
            "versionCode": "versionCode", "versionName": "versionName",
            "packageName": "packageName", "apkAsset": "apkAsset",
            "signerSha256": "signerSha256", "commitSha": "commitSha",
        }
        for metadata_key, plan_key in mapping.items():
            if metadata[metadata_key] != plan[plan_key]:
                raise ReleaseContractError(f"{metadata_key} differs from the release plan")


def previous_release(plan: dict, releases: list[dict]) -> str:
    expected = plan["upgradeFromTag"]
    prefix = plan["tagPrefix"]
    candidate = integer(plan["versionCode"], "versionCode")
    for release in releases:
        code = version_from_tag(release.get("tag_name"), prefix)
        if (release.get("tag_name") == expected and code is not None and code < candidate
                and not release.get("draft") and not release.get("prerelease")):
            return expected
    raise ReleaseContractError(f"Supported upgrade release {expected} is unavailable")


def validate_tag_ref(plan: dict, tag_ref: dict) -> None:
    expected_ref = f'refs/tags/{plan["tag"]}'
    if tag_ref.get("ref") != expected_ref:
        raise ReleaseContractError("Existing Git tag has an unexpected name")
    target = tag_ref.get("object", {})
    if target.get("type") != "commit" or target.get("sha", "").lower() != plan["commitSha"]:
        raise ReleaseContractError("Existing Git tag does not point to the release commit")


def command_plan(args) -> None:
    contract = read_properties(args.properties)
    releases = load_json(args.releases) if args.releases else []
    plan = release_plan(contract, args.commit, releases)
    write_json(args.output, plan)


def command_metadata(args) -> None:
    write_json(args.output, build_metadata(load_json(args.plan), args.apk))


def command_validate(args) -> None:
    validate_metadata(load_json(args.metadata), args.apk,
                      load_json(args.plan) if args.plan else None)


def command_previous(args) -> None:
    print(previous_release(load_json(args.plan), load_json(args.releases)))


def command_tag(args) -> None:
    validate_tag_ref(load_json(args.plan), load_json(args.ref_json))


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    commands = root.add_subparsers(dest="command", required=True)

    plan = commands.add_parser("plan")
    plan.add_argument("--properties", type=Path, required=True)
    plan.add_argument("--commit", required=True)
    plan.add_argument("--releases", type=Path)
    plan.add_argument("--output", type=Path, required=True)
    plan.set_defaults(handler=command_plan)

    metadata = commands.add_parser("metadata")
    metadata.add_argument("--plan", type=Path, required=True)
    metadata.add_argument("--apk", type=Path, required=True)
    metadata.add_argument("--output", type=Path, required=True)
    metadata.set_defaults(handler=command_metadata)

    validate = commands.add_parser("validate")
    validate.add_argument("--metadata", type=Path, required=True)
    validate.add_argument("--apk", type=Path, required=True)
    validate.add_argument("--plan", type=Path)
    validate.set_defaults(handler=command_validate)

    previous = commands.add_parser("previous")
    previous.add_argument("--plan", type=Path, required=True)
    previous.add_argument("--releases", type=Path, required=True)
    previous.set_defaults(handler=command_previous)

    tag = commands.add_parser("tag")
    tag.add_argument("--plan", type=Path, required=True)
    tag.add_argument("--ref-json", type=Path, required=True)
    tag.set_defaults(handler=command_tag)
    return root


def main() -> int:
    try:
        args = parser().parse_args()
        args.handler(args)
        return 0
    except (OSError, KeyError, json.JSONDecodeError, ReleaseContractError) as error:
        print(f"release_tool: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
