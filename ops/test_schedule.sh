#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
source "${SCRIPT_DIR}/lib/common.sh"
source "${SCRIPT_DIR}/lib/build_and_install.sh"
source "${SCRIPT_DIR}/lib/ui_automation.sh"
source "${SCRIPT_DIR}/lib/log_parsing.sh"
source "${SCRIPT_DIR}/lib/checks.sh"
source "${SCRIPT_DIR}/lib/db.sh"

PACKAGE="com.autosecretary"
ACTIVITY=".app.MainActivity"
APK="build/outputs/apk/debug/AutoSecretary.apk"
APK_PATH="$PROJECT_ROOT/$APK"
LOG_TAG="SlotGen"
UI_DUMP="/sdcard/ui_dump.xml"
LOCAL_DUMP="/tmp/autosecretary_ui.xml"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

info() { echo -e "${BLUE}[INFO]${NC} $1"; }
ok() { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }

echo ""
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  AutoSecretary Schedule Test${NC}"
echo -e "${CYAN}  (7-Tage Multi-Day Scheduling)${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

build_debug_apk
echo ""

check_device
echo ""

wake_device
echo ""

install_debug_apk
echo ""

clear_device_logcat
echo ""

start_schedule_app
echo ""

info "Tippe 'Generieren' Button..."
tap_button "GENERIEREN"
echo ""

info "Warte 10s auf 7-Tage Slot-Generierung..."
sleep 10
echo ""

LOG_OUTPUT="$(collect_schedule_logs)"
ALL_SUMMARIES="$(extract_all_summaries "$LOG_OUTPUT")"
TODAY_SUMMARY="$(extract_today_summary "$LOG_OUTPUT")"

print_week_plan "$ALL_SUMMARIES"

if [[ "${1:-}" == "--verbose" || "${2:-}" == "--verbose" ]]; then
    echo -e "${BLUE}--- Vollstaendiges SlotGen Log ---${NC}"
    echo "$LOG_OUTPUT"
    echo ""
fi

TODAY_DOW="$(date +%u)"
run_schedule_checks "$TODAY_SUMMARY" "$LOG_OUTPUT" "$TODAY_DOW"
print_check_summary

if [[ "${1:-}" == "--pull-db" || "${2:-}" == "--pull-db" ]]; then
    if ! pull_schedule_db; then
        warn "DB-Export wurde uebersprungen"
    fi
fi

echo ""
info "Fertig!"
