import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.hydrogen.tieba"


def main():
    parser = argparse.ArgumentParser(description="Install and inspect Hydrogen Tieba on one connected device.")
    parser.add_argument("command", choices=["info", "install", "launch", "screenshot", "inspect", "logs"])
    parser.add_argument("--release", action="store_true")
    parser.add_argument("--name", default="app")
    arguments = parser.parse_args()
    configuration = json.loads((ROOT / ".tools/environment.json").read_text(encoding="utf-8"))
    adb = str(Path(configuration["android_sdk"]) / "platform-tools/adb.exe")
    listing = subprocess.check_output([adb, "devices"], text=True)
    devices = [line.split()[0] for line in listing.splitlines()[1:] if line.strip().endswith("\tdevice")]
    if len(devices) != 1:
        raise RuntimeError("Connect and authorize exactly one Android device before continuing.")
    command = [adb, "-s", devices[0]]

    def output(*parts):
        return subprocess.check_output(command + list(parts), text=True, encoding="utf-8", errors="replace").strip()

    if arguments.command == "info":
        for name, property_name in [("Model", "ro.product.model"), ("Android", "ro.build.version.release")]:
            print(f"{name}: {output('shell', 'getprop', property_name)}")
    elif arguments.command == "install":
        variant = "release" if arguments.release else "debug"
        apk = ROOT / f"app/build/outputs/apk/{variant}/app-{variant}.apk"
        if not apk.is_file():
            raise RuntimeError(f"Build the {variant} APK first.")
        subprocess.run(command + ["install", "-r", str(apk)], check=True)
    elif arguments.command == "launch":
        print(output("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity"))
    elif arguments.command == "logs":
        process_id = output("shell", "pidof", PACKAGE)
        if not process_id:
            raise RuntimeError("The app is not running.")
        print(output("logcat", "-d", f"--pid={process_id.split()[0]}", "-t", "250"))
    else:
        activity = output("shell", "dumpsys", "activity", "activities")
        resumed = "\n".join(line for line in activity.splitlines() if "ResumedActivity" in line)
        if PACKAGE not in resumed:
            raise RuntimeError("Open Hydrogen Tieba before inspecting its UI.")
        power = output("shell", "dumpsys", "power")
        if "mWakefulness=Awake" not in power:
            raise RuntimeError("Wake and unlock the device before inspecting its UI.")
        policy = output("shell", "dumpsys", "window", "policy")
        if re.search(r"^\s*(?:showing|mIsShowing)=true", policy, re.MULTILINE):
            raise RuntimeError("Unlock the device before inspecting its UI.")
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,60}", arguments.name):
            raise RuntimeError("Use an ASCII filename containing letters, digits, underscores or hyphens.")
        destination = ROOT / ".tools/checks/screenshots"
        destination.mkdir(parents=True, exist_ok=True)
        if arguments.command == "screenshot":
            target = destination / f"{arguments.name}.png"
            with target.open("wb") as image:
                subprocess.run(command + ["exec-out", "screencap", "-p"], stdout=image, check=True)
        else:
            result = output("shell", "uiautomator", "dump", "/data/local/tmp/hydrogen-ui.xml")
            if "UI hierchary dumped to:" not in result:
                raise RuntimeError("The UI hierarchy could not be captured; retry with the app visible.")
            target = destination / f"{arguments.name}.xml"
            subprocess.run(command + ["pull", "/data/local/tmp/hydrogen-ui.xml", str(target)], check=True)
        print(target)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Device operation failed: {error}", file=sys.stderr)
        sys.exit(1)
