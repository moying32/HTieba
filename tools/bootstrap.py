import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import venv
import zipfile


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".tools"
GRADLE_VERSION = "8.13"
JADX_VERSION = "1.5.6"
MITMPROXY_VERSION = "12.3.1"


def fetch(url, destination=None):
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "HydrogenTieba-Setup/1.0"})
            with urllib.request.urlopen(request, timeout=60) as response:
                if destination is None:
                    return response.read().decode("utf-8").strip()
                temporary = destination.with_suffix(destination.suffix + ".part")
                with temporary.open("wb") as output:
                    shutil.copyfileobj(response, output)
                expected_size = response.headers.get("Content-Length")
                if expected_size and temporary.stat().st_size != int(expected_size):
                    raise OSError(f"Incomplete download: {url}")
                temporary.replace(destination)
                return destination
        except (OSError, urllib.error.URLError):
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    resolved = destination.resolve()
    with zipfile.ZipFile(archive) as package:
        for entry in package.infolist():
            if not (destination / entry.filename).resolve().is_relative_to(resolved):
                raise RuntimeError(f"Unsafe archive entry: {entry.filename}")
        package.extractall(destination)


def install_archive(name, version, url, executable, checksum_url=None):
    destination = TOOLS / f"{name}-{version}"
    archive = TOOLS / "downloads" / f"{name}-{version}.zip"
    archive.parent.mkdir(parents=True, exist_ok=True)
    expected = fetch(checksum_url).split()[0] if checksum_url else None
    if not archive.is_file() or not zipfile.is_zipfile(archive) or (expected and digest(archive) != expected):
        print(f"Downloading {name} {version}...", flush=True)
        fetch(url, archive)
    actual = digest(archive)
    if expected and actual != expected:
        raise RuntimeError(f"SHA-256 verification failed for {name}")
    if not (destination / executable).is_file():
        extract(archive, TOOLS if name == "gradle" else destination)
    if not (destination / executable).is_file():
        raise RuntimeError(f"Archive does not contain {executable}")
    print(f"Ready: {name} {version}", flush=True)
    return {"version": version, "path": str(destination), "source": url, "sha256": actual,
            "publisher_checksum_verified": expected is not None}


def install_proxy():
    environment = TOOLS / "python"
    interpreter = environment / "Scripts" / "python.exe"
    if not interpreter.is_file():
        venv.EnvBuilder(with_pip=True).create(environment)
    subprocess.run([str(interpreter), "-m", "pip", "install", "--disable-pip-version-check",
                    "--only-binary=:all:", f"mitmproxy=={MITMPROXY_VERSION}"], check=True)
    locked = subprocess.check_output([str(interpreter), "-m", "pip", "freeze"], text=True)
    (TOOLS / "requirements.lock.txt").write_text(locked, encoding="utf-8")
    print(f"Ready: mitmproxy {MITMPROXY_VERSION}", flush=True)
    return {"version": MITMPROXY_VERSION, "path": str(environment), "source": "https://pypi.org/simple"}


def discover():
    java_home = os.environ.get("JAVA_HOME")
    if not java_home and shutil.which("java"):
        java_home = str(Path(shutil.which("java")).resolve().parents[1])
    candidates = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                  str(Path.home() / "AppData" / "Local" / "Android" / "Sdk")]
    sdk = next((Path(candidate) for candidate in candidates
                if candidate and (Path(candidate) / "platforms" / "android-36" / "android.jar").is_file()), None)
    if not java_home or not (Path(java_home) / "bin" / "javac.exe").is_file():
        raise RuntimeError("A JDK is required. Set JAVA_HOME to JDK 21.")
    if sdk is None or not (sdk / "build-tools" / "35.0.0" / "aapt2.exe").is_file():
        raise RuntimeError("Android SDK platform 36 and Build Tools 35.0.0 are required.")
    return {"java_home": java_home, "android_sdk": str(sdk), "compile_sdk": 36,
            "build_tools": "35.0.0", "google_maven_mirror": "https://maven.aliyun.com/repository/google"}


def main():
    parser = argparse.ArgumentParser(description="Install local Android and APK analysis tools.")
    parser.add_argument("--check", action="store_true", help="Check the installed environment without downloads.")
    arguments = parser.parse_args()
    environment = discover()
    if arguments.check:
        configuration = json.loads((TOOLS / "environment.json").read_text(encoding="utf-8"))
        checks = {
            "gradle": Path(configuration["gradle"]["path"]) / "bin" / "gradle.bat",
            "jadx": Path(configuration["jadx"]["path"]) / "bin" / "jadx.bat",
            "mitmweb": Path(configuration["mitmproxy"]["path"]) / "Scripts" / "mitmweb.exe",
            "adb": Path(environment["android_sdk"]) / "platform-tools" / "adb.exe",
        }
        for name, path in checks.items():
            if not path.is_file():
                raise RuntimeError(f"Missing {name}: {path}")
            print(f"OK {name}: {path}")
        return
    TOOLS.mkdir(exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        jobs = {
            "gradle": executor.submit(install_archive, "gradle", GRADLE_VERSION,
                f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip",
                "bin/gradle.bat",
                f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip.sha256"),
            "jadx": executor.submit(install_archive, "jadx", JADX_VERSION,
                f"https://github.com/skylot/jadx/releases/download/v{JADX_VERSION}/jadx-{JADX_VERSION}.zip",
                "bin/jadx.bat"),
            "mitmproxy": executor.submit(install_proxy),
        }
        results = {}
        failures = []
        for name, job in jobs.items():
            try:
                results[name] = job.result()
            except Exception as error:
                failures.append(f"{name}: {error}")
        if failures:
            raise RuntimeError("\n".join(failures))
        environment.update(results)
    (TOOLS / "environment.json").write_text(json.dumps(environment, indent=2), encoding="utf-8")
    print("Environment installed. Run: python tools/build.py smoke", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Setup failed: {error}", file=sys.stderr)
        sys.exit(1)
