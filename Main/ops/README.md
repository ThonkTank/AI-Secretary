# ops/

Operational scripts and AI review automation for the AutoSecretary project.

## Files at a glance

| File / Directory | What it does |
|---|---|
| `apply_skill.sh` | Long-running autonomous review loop. Iterates over every source directory, runs AI agents (Claude) with focused review skills, and checkpoints progress to git. Designed to run unattended for hours or days. |
| `test_schedule.sh` | Full integration test for the task scheduler. Builds the APK, installs it on a connected Android device, triggers slot generation, and checks the resulting 7-day schedule against expected constraints. Requires a connected device with USB debugging enabled. |
| `check_only.sh` | Like `test_schedule.sh` but skips the build and install steps. Reads logcat from an already-running app and checks the scheduling output. Useful for quick re-checks without rebuilding. |
| `lib/common.sh` | Shared shell utilities sourced by both test scripts (e.g. `count_days_with_task`). |
| `skills/` | AI agent role prompts. Each `.md` file defines the focus and output format for one review type (e.g. `review-security.md`, `review-performance.md`). See `skills/README.md` for details. |
| `release/` | Release artifacts: `AutoSecretary.apk` and `version.txt`. Managed by `./gradlew copyToRelease` — do not edit manually. |

## Prerequisites

### For test_schedule.sh / check_only.sh
- Android device with **USB debugging enabled**, connected via USB
- `adb` from the Android SDK platform-tools — install Android Studio or the standalone SDK
- Both scripts hardcode an absolute ADB path (`/home/aaron/Android/Sdk/platform-tools/adb`). **Adjust this path** to match your local SDK installation before running. Find yours with: `which adb` or `locate adb`.

### For apply_skill.sh
- `claude` CLI (Claude Code) installed and authenticated
- `git` and `gh` (GitHub CLI) for checkpoint commits and sync
- `systemd-inhibit` (optional, Linux-only) — prevents system sleep during long runs

## How to run

### Run the full schedule test (with build + install)
```sh
# From the project root:
./gradlew assembleDebug          # build first
ops/test_schedule.sh             # then test
ops/test_schedule.sh --verbose   # include full logcat output
ops/test_schedule.sh --pull-db   # also dump the SQLite DB from the device
```

### Run the check without rebuilding (device must already be running the app)
```sh
ops/check_only.sh
```

### Start the autonomous review loop
```sh
ops/apply_skill.sh               # start (runs indefinitely, cycling through all source dirs)
ops/apply_skill.sh --autonomous  # same (flag is accepted but currently a no-op)
ops/apply_skill.sh close         # signal the loop to stop after the current agent finishes
ops/apply_skill.sh refresh       # stop after current agent, then restart the loop
```

The review loop saves state in `.git/apply_skill_state` so it survives interruptions and resumes where it left off.

## What the review loop does

1. Scans all source directories (sorted by size, smallest first).
2. For each directory, selects which review skills to run based on directory size and content (UI, LOC thresholds).
3. Launches a Claude agent with the skill's role prompt and the directory as scope.
4. Every 5 directories, runs a checkpoint: commits all changes to git.
5. After all directories are processed, starts a new cycle from the beginning.

See the header comment in `apply_skill.sh` for the full skill-selection logic.

## Troubleshooting

**"No SlotGen logs found"** — The app may not have started, the "Generieren" button may not have been found, or generation took longer than the 10-second wait. Run with `--verbose` to see the full logcat.

**"Kein Geraet verbunden"** — No Android device detected. Check USB cable, enable USB debugging in developer options, and confirm `adb devices` lists your device.

**apply_skill.sh crashes repeatedly** — The self-watchdog supervisor will attempt a Claude diagnostic and restart. Check `/tmp/apply_skill_diag_*.log` for the diagnosis. If the issue requires human intervention, the watchdog outputs `WATCHDOG_ACTION: HUMAN_NEEDED` and stops.
