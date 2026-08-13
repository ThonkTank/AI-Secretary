#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-$(command -v adb || true)}"
APKSIGNER="${APKSIGNER:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/35.0.0/apksigner}"
AAPT="${AAPT:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/35.0.0/aapt}"
DATABASE="autosecretary.db"
BUILD4_CERT="1e0e90509d79efacebaec1af024f2577d7799cf5534e841db7417184287dbfb2"
BUILD4_V27_ROOM_IDENTITY="87fa112d19ca59d751c7933a42b85cd9"
BUILD4_V30_ROOM_IDENTITY="51ffa9b42fba4bd0b74c6eb9d8809c00"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
    echo "Export: $0 export [com.autosecretary]" >&2
    echo "Stage:  $0 stage ARCHIVE.zip" >&2
    exit 2
}

[[ -n "$ADB" ]] || { echo "adb wurde nicht gefunden" >&2; exit 1; }
[[ -x "$APKSIGNER" ]] || { echo "apksigner wurde nicht gefunden; APKSIGNER setzen" >&2; exit 1; }
[[ -x "$AAPT" ]] || { echo "aapt wurde nicht gefunden; AAPT setzen" >&2; exit 1; }
command -v sqlite3 >/dev/null || { echo "sqlite3 wurde nicht gefunden" >&2; exit 1; }
[[ $# -ge 1 ]] || usage

hash_file() {
    sha256sum "$1" | awk '{print $1}'
}

verify_sidecar() {
    local archive="$1" expected actual
    [[ -f "$archive" && -f "$archive.sha256" ]] || {
        echo "Archiv oder .sha256-Datei fehlt" >&2
        exit 1
    }
    expected="$(awk 'NR == 1 { print tolower($1) }' "$archive.sha256")"
    [[ "$expected" =~ ^[0-9a-f]{64}$ ]] || {
        echo "Ungültige SHA-256-Sidecar" >&2
        exit 1
    }
    actual="$(hash_file "$archive")"
    [[ "$actual" == "$expected" ]] || {
        echo "Archiv-Prüfsumme stimmt nicht" >&2
        exit 1
    }
    echo "$archive: OK"
}

export_database() {
    local package="${1:-com.autosecretary}"
    [[ "$package" == "com.autosecretary" ]] || {
        echo "Die Build-4-Brücke akzeptiert ausschließlich com.autosecretary" >&2
        exit 1
    }
    local stamp destination archive archive_temporary apk_path certificate version_hex version room_identity
    local expected_identity app_version_code app_version_name
    stamp="$(date +%Y%m%d-%H%M%S-%N)"
    destination="$ROOT/ops/local-backups/$package-$stamp"
    archive="$ROOT/ops/local-backups/AutoSecretary-build4-$stamp.zip"
    archive_temporary="$archive.partial.zip"
    mkdir -p "$destination"

    $ADB shell am force-stop "$package"
    apk_path="$($ADB shell pm path "$package" | sed -n 's/^package://p' | head -1 | tr -d '\r')"
    [[ -n "$apk_path" ]] || { echo "Installiertes APK wurde nicht gefunden" >&2; exit 1; }
    $ADB exec-out cat "$apk_path" > "$destination/installed.apk"
    certificate="$($APKSIGNER verify --print-certs "$destination/installed.apk" \
        | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')"
    [[ "$certificate" == "$BUILD4_CERT" ]] || {
        echo "Abbruch: installiertes APK ist nicht mit dem Build-4-Zertifikat signiert" >&2
        exit 1
    }
    app_version_code="$($AAPT dump badging "$destination/installed.apk" \
        | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
    app_version_name="$($AAPT dump badging "$destination/installed.apk" \
        | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"

    if ! $ADB shell run-as "$package" test -f "databases/$DATABASE"; then
        echo "run-as kann $package/databases/$DATABASE nicht lesen; Build 4 muss debuggable sein" >&2
        exit 1
    fi
    for suffix in "" "-wal" "-shm"; do
        if $ADB shell run-as "$package" test -f "databases/$DATABASE$suffix"; then
            $ADB exec-out run-as "$package" cat "databases/$DATABASE$suffix" \
                > "$destination/$DATABASE$suffix"
        fi
    done
    version_hex="$(od -An -t x1 -j 60 -N 4 "$destination/$DATABASE" | tr -d ' \n')"
    version="$((16#$version_hex))"
    case "$version" in
        27) expected_identity="$BUILD4_V27_ROOM_IDENTITY" ;;
        30) expected_identity="$BUILD4_V30_ROOM_IDENTITY" ;;
        *)
            echo "Abbruch: unterstützt werden die verifizierten Datenbanken v27 und v30; gefunden wurde v$version" >&2
            exit 1
            ;;
    esac
    room_identity="$(sqlite3 "file:$destination/$DATABASE?immutable=1" \
        'SELECT identity_hash FROM room_master_table WHERE id = 42;')"
    [[ "$room_identity" == "$expected_identity" ]] || {
        echo "Abbruch: Datenbank hat nicht das exakte Build-4-Room-Schema" >&2
        exit 1
    }

    {
        printf 'sourcePackage=%s\n' "$package"
        printf 'sourceCertificateSha256=%s\n' "$certificate"
        printf 'sourceAppVersionCode=%s\n' "$app_version_code"
        printf 'sourceAppVersionName=%s\n' "$app_version_name"
        printf 'sourceDatabaseVersion=%s\n' "$version"
        printf 'sourceRoomIdentityHash=%s\n' "$room_identity"
        printf 'createdAt=%s\n' "$(date --iso-8601=seconds)"
        printf 'databaseSha256=%s\n' "$(hash_file "$destination/$DATABASE")"
        if [[ -f "$destination/$DATABASE-wal" ]]; then
            printf 'walSha256=%s\n' "$(hash_file "$destination/$DATABASE-wal")"
        fi
        if [[ -f "$destination/$DATABASE-shm" ]]; then
            printf 'shmSha256=%s\n' "$(hash_file "$destination/$DATABASE-shm")"
        fi
    } > "$destination/metadata.properties"

    local entries=("$destination/$DATABASE" "$destination/metadata.properties")
    [[ ! -f "$destination/$DATABASE-wal" ]] || entries+=("$destination/$DATABASE-wal")
    [[ ! -f "$destination/$DATABASE-shm" ]] || entries+=("$destination/$DATABASE-shm")
    rm -f "$archive_temporary"
    zip -q -j "$archive_temporary" "${entries[@]}"
    mv "$archive_temporary" "$archive"
    printf '%s  %s\n' "$(hash_file "$archive")" "$(basename "$archive")" \
        > "$archive.sha256"
    rm -f "$destination/installed.apk"
    echo "Verifiziertes Build-4-Archiv: $archive"
    echo "Vor jeder Paketänderung Archiv und .sha256 zusätzlich extern sichern."
}

stage_archive() {
    [[ $# -eq 1 && -f "$1" ]] || usage
    local archive="$1" target="/sdcard/Download/AutoSecretary-build4-export.zip"
    verify_sidecar "$archive"
    unzip -tq "$archive"
    $ADB push "$archive" "$target"
    echo "Archiv bereitgestellt: $target"
    echo "In der frisch installierten App 'Build-4-Archiv auswählen' öffnen und diese Datei wählen."
}

case "$1" in
    export) shift; export_database "${1:-com.autosecretary}" ;;
    stage) shift; stage_archive "$@" ;;
    *) usage ;;
esac
