#!/usr/bin/env python3
"""Fresh-process diagnostic contracts on the isolated emulator package, never production data."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
NAMESPACE = "de.thonktank.autosecretary"
PACKAGE = NAMESPACE + ".test"
HELPER = PACKAGE + ".test"


def validate_probe(output: str, marker: str) -> None:
    if marker not in output or any(error in output for error in (
        "FAIL:", "INSTRUMENTATION_FAILED", "Process crashed", "Process killed")):
        raise AssertionError(f"Native probe did not pass {marker}:\n{output}")


def ready_pid(output: str) -> int | None:
    match = re.search(r"diagnosticReady=protocol=1; pid=([1-9][0-9]*)", output)
    return int(match.group(1)) if match else None


class Device:
    def __init__(self, serial: str):
        self.prefix = ["adb", "-s", serial]

    def run(self, *args: str, timeout: int = 30) -> str:
        result = subprocess.run([*self.prefix, *args], text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=timeout, check=True)
        return result.stdout

    def shell(self, *args: str, timeout: int = 30) -> str:
        return self.run("shell", shlex.join(args), timeout=timeout)

    def assert_emulator(self) -> None:
        if self.shell("getprop", "ro.kernel.qemu").strip() != "1":
            raise AssertionError("Diagnostic fixture writer refuses a physical device")
        if self.shell("getprop", "sys.boot_completed").strip() != "1":
            raise AssertionError("Emulator has not completed boot")

    def command(self, runner: str, **arguments: str) -> list[str]:
        command = ["am", "instrument", "-w", "-r"]
        for key, value in arguments.items():
            command.extend(["-e", key, str(value)])
        command.append(f"{HELPER}/{NAMESPACE}.{runner}")
        return command

    def probe(self, directory: Path, name: str, runner: str, marker: str, **arguments: str) -> None:
        output = self.shell(*self.command(runner, **arguments), timeout=100)
        (directory / f"{name}.txt").write_text(output)
        validate_probe(output, marker)

    def proof(self, name: str) -> dict:
        return json.loads(self.shell("cat", f"/data/data/{PACKAGE}/files/{name}"))

    def broadcast(self, action: str, receiver: str | None = None, *extras: str) -> None:
        command = ["am", "broadcast", "--receiver-foreground", "-a", action]
        command += ["-n", f"{PACKAGE}/{NAMESPACE}.{receiver}"] if receiver else ["-p", PACKAGE]
        output = self.shell(*command, *extras)
        if "Broadcast completed" not in output or "Exception" in output or "Error:" in output:
            raise AssertionError(f"Android did not deliver {action}: {output}")


def exercise_events(device: Device, directory: Path, pid: int) -> None:
    def alive() -> None:
        if str(pid) not in device.shell("pidof", PACKAGE).split():
            raise AssertionError("Original diagnostic process ended during event delivery")

    def broadcast(action: str, receiver: str | None = None, *extras: str) -> None:
        alive()
        device.broadcast(action, receiver, *extras)
        alive()

    provider = device.proof("diagnostic-provider-proof.json")
    if not (provider["pid"] == pid and provider["earlyMode"] and provider["containerMissing"]
            and provider["workManagerInitialized"]):
        raise AssertionError(f"Provider preceded the protected state: {provider}")
    (directory / "provider.json").write_text(json.dumps(provider, indent=2))
    broadcast(PACKAGE + ".DIAGNOSTIC_EVENTS")
    for action in ("android.intent.action.BOOT_COMPLETED", "android.intent.action.MY_PACKAGE_REPLACED"):
        broadcast(action, "timer.TimerBootReceiver")
    broadcast(NAMESPACE + ".TIMER_FINISH", "timer.TimerAlarmReceiver", "--es", "timer_id", "diagnostic-timer")
    for action in ("COMPLETE", "LATER", "TOGGLE_STEP"):
        broadcast(NAMESPACE + "." + action, "TaskActionReceiver", "--es", "occurrence_id",
                         "current-smoke-open", "--es", "step_id", "diagnostic-open-step")
    for action in ("APPWIDGET_ENABLED", "APPWIDGET_UPDATE", "APPWIDGET_DELETED", "APPWIDGET_DISABLED"):
        broadcast("android.appwidget.action." + action, "TaskWidgetProvider", "--eia", "appWidgetIds", "901",
                  "--ei", "appWidgetId", "901")
    broadcast("android.appwidget.action.APPWIDGET_UPDATE_OPTIONS", "TaskWidgetProvider",
              "--ei", "appWidgetId", "901", "--esn", "appWidgetOptions")
    for activity in ("MainActivity", "FlowRunsActivity", "FlowSetupActivity"):
        alive()
        output = device.shell("am", "start", "-W", "-n", f"{PACKAGE}/{NAMESPACE}.{activity}")
        alive()
        if "Status: ok" not in output:
            raise AssertionError(f"Activity was not delivered during diagnosis: {output}")
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        alive()
        try:
            service = device.proof("diagnostic-service-proof.json")
            worker = device.proof("diagnostic-worker-proof.json")
            if "error" in service or "error" in worker:
                raise AssertionError(f"Native event witness failed: {service}, {worker}")
            if service.get("pid") == pid and worker.get("pid") == pid:
                (directory / "service.json").write_text(json.dumps(service, indent=2))
                (directory / "worker.json").write_text(json.dumps(worker, indent=2))
                break
        except (subprocess.CalledProcessError, json.JSONDecodeError):
            pass  # The actual asynchronous work is still producing its bounded result.
        time.sleep(0.1)
    else:
        raise AssertionError("Real service/worker evidence was not delivered")
    if str(pid) not in device.shell("pidof", PACKAGE).split():
        raise AssertionError("Diagnostic process died during native events")
    logcat = device.shell("logcat", "-d", "--pid", str(pid), "-v", "brief", "*:E")
    (directory / "event-errors.log").write_text(logcat)
    if re.search(r"E/(AndroidRuntime|TaskActionReceiver|TaskWidgetProvider|TaskTimers|FlowWakeWorker)\s*\(", logcat):
        raise AssertionError("Product entry logged an error during diagnosis")


def held_diagnosis(device: Device, directory: Path, abort: bool) -> int:
    logfile = directory / "diagnosis.txt"
    command = device.command("DiagnosticProbeInstrumentation", diagnosticHoldMillis="30000")
    with logfile.open("w") as output:
        process = subprocess.Popen([*device.prefix, "shell", shlex.join(command)], stdout=output,
                                   stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 25
            pid = None
            while time.monotonic() < deadline:
                pid = ready_pid(logfile.read_text())
                if pid is not None:
                    break
                if process.poll() is not None:
                    raise AssertionError(f"Diagnostic bootstrap failed:\n{logfile.read_text()}")
                time.sleep(0.1)
            if pid is None:
                raise AssertionError("Diagnostic runner did not report its early entry")
            exercise_events(device, directory, pid)
            if abort:
                device.shell("kill", "-9", str(pid))
            process.wait(timeout=40)
            result = logfile.read_text()
            if abort:
                if "OK (1 diagnostic probe)" in result:
                    raise AssertionError("Requested abort did not interrupt diagnosis")
            else:
                validate_probe(result, "OK (1 diagnostic probe)")
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                try:
                    if not device.shell("pidof", PACKAGE).strip():
                        return pid
                except subprocess.CalledProcessError as stopped:
                    if stopped.returncode == 1:
                        return pid
                    raise
                time.sleep(0.1)
            raise AssertionError("Diagnostic process did not exit; no-restart is unsupported")
        finally:
            if process.poll() is None:
                device.shell("am", "force-stop", PACKAGE)
                process.wait(timeout=10)


def run(serial: str, reports: Path) -> None:
    started = time.monotonic()
    device = Device(serial)
    device.assert_emulator()  # Before root, installation or any fixture writer.
    subprocess.run([str(ROOT / "scripts/ci/verify-instrumentation-identity.sh")], cwd=ROOT, check=True)
    device.run("root")
    device.run("wait-for-device")
    device.assert_emulator()
    if device.shell("id", "-u").strip() != "0":
        raise AssertionError("Isolated event delivery requires the emulator's root adbd")
    for apk in ("app/build/outputs/apk/instrumentation/app-instrumentation.apk",
                "app/build/outputs/apk/androidTest/instrumentation/app-instrumentation-androidTest.apk"):
        if "Success" not in device.run("install", "-r", str(ROOT / apk), timeout=90):
            raise AssertionError("Isolated test APK installation failed")
    reports.mkdir(parents=True, exist_ok=True)
    try:
        control = reports / "normal-control"
        control.mkdir()
        device.probe(control, "seed", "DiagnosticFixtureInstrumentation", "OK (1 diagnostic fixture)",
                     fixturePhase="seed", fixtureSchema="27")
        device.probe(control, "normal", "NormalDiagnosticRecoveryInstrumentation", "OK (1 normal diagnostic recovery)")
        for version in (24, 27):
            for abort in (False, True):
                case = reports / f"schema-{version}-{'abort' if abort else 'finish'}"
                case.mkdir()
                device.probe(case, "seed", "DiagnosticFixtureInstrumentation", "OK (1 diagnostic fixture)",
                             fixturePhase="seed", fixtureSchema=str(version))
                pid = held_diagnosis(device, case, abort)
                device.probe(case, "unchanged", "DiagnosticFixtureInstrumentation", "OK (1 diagnostic fixture)",
                             fixturePhase="assert", fixturePid=str(pid))
                device.probe(case, "normal", "NormalDiagnosticRecoveryInstrumentation", "OK (1 normal diagnostic recovery)")
                print(f"PASS {case.name}: native events, unchanged schema/data, normal timer/worker recovery", flush=True)
        result = {"status": "passed", "serial": serial,
            "apiLevel": device.shell("getprop", "ro.build.version.sdk").strip(),
            "schemas": [24, 27], "endings": ["finish", "abort"], "normalControl": True,
            "elapsedSeconds": round(time.monotonic() - started, 2)}
        (reports / "result.json").write_text(json.dumps(result, indent=2))
        print(json.dumps(result), flush=True)
    except BaseException:
        try:
            (reports / "failure-logcat.txt").write_text(device.run("logcat", "-d", "-v", "threadtime"))
        finally:
            (reports / "result.json").write_text(json.dumps({"status": "failed", "serial": serial}, indent=2))
        raise


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--report-dir", type=Path, required=True)
    args = parser.parse_args()
    serial = args.serial
    if not serial:
        output = subprocess.check_output(["adb", "devices"], text=True)
        devices = re.findall(r"^(\S+)\s+device$", output, flags=re.MULTILINE)
        if len(devices) != 1:
            parser.error("Select exactly one emulator through --serial or ANDROID_SERIAL")
        serial = devices[0]
    run(serial, args.report_dir)


if __name__ == "__main__":
    main()
