import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description="Validate the Android build environment.")
    parser.add_argument("command", choices=["smoke", "app", "release", "wrapper", "check"])
    arguments = parser.parse_args()
    configuration = json.loads((ROOT / ".tools" / "environment.json").read_text(encoding="utf-8"))
    environment = os.environ.copy()
    environment.update({
        "JAVA_HOME": configuration["java_home"],
        "ANDROID_HOME": configuration["android_sdk"],
        "ANDROID_SDK_ROOT": configuration["android_sdk"],
        "GRADLE_USER_HOME": str(ROOT / ".tools" / "gradle-home"),
        "ANDROID_USER_HOME": str(ROOT / ".tools" / "android-user"),
    })
    project = ROOT / "tools" / "smoke" if arguments.command == "smoke" else ROOT
    variant = "release" if arguments.command == "release" else "debug"
    sdk_path = configuration["android_sdk"].replace("\\", "/").replace(":", "\\:")
    (project / "local.properties").write_text(f"sdk.dir={sdk_path}\n", encoding="utf-8", newline="\n")
    task = "wrapper" if arguments.command == "wrapper" else "assembleRelease" if variant == "release" else "assembleDebug"
    command = [str(Path(configuration["gradle"]["path"]) / "bin" / "gradle.bat"),
               "--no-daemon", "--console=plain", "--stacktrace", task]
    if arguments.command == "check":
        command[-1:] = ["exportDebugTestClasspath", "lintAnalyzeRelease", "--rerun", "lintRelease"]
    if arguments.command == "wrapper":
        command.append("--no-validate-url")
    subprocess.run(command, cwd=project, env=environment, check=True)
    if arguments.command == "check":
        classpath = (ROOT / "app/build/theme-test-classpath.txt").read_text(encoding="utf-8")
        subprocess.run([str(Path(configuration["java_home"]) / "bin/java.exe"), "-cp", classpath,
                        "org.junit.runner.JUnitCore", "dev.hydrogen.tieba.ui.theme.ThemeContrastTest",
                        "dev.hydrogen.tieba.data.IdentityParserTest", "dev.hydrogen.tieba.data.ThreadStorageTest",
                        "dev.hydrogen.tieba.data.NativeProtocolTest", "dev.hydrogen.tieba.ui.NavigationPhysicsTest",
                        "dev.hydrogen.tieba.ui.BrowsingHeaderTest",
                        "dev.hydrogen.tieba.ui.VideoPlaybackControlsTest",
                        "dev.hydrogen.tieba.data.PostParserTest", "dev.hydrogen.tieba.data.ImageDownloadTest",
                        "dev.hydrogen.tieba.data.DownloadStorageTest",
                        "dev.hydrogen.tieba.data.CommunityParserTest", "dev.hydrogen.tieba.data.ThemePackTest",
                        "dev.hydrogen.tieba.data.MediaParserTest", "dev.hydrogen.tieba.data.HotTopicsParserTest",
                        "dev.hydrogen.tieba.data.MessageRepairTest",
                        "dev.hydrogen.tieba.data.ChatProtocolTest",
                        "dev.hydrogen.tieba.data.GroupChatTest",
                        "dev.hydrogen.tieba.data.ProfileProtocolTest",
                        "dev.hydrogen.tieba.data.LoginCompletionTest",
                        "dev.hydrogen.tieba.ui.GroupRefreshTest",
                        "dev.hydrogen.tieba.data.GroupModerationTest",
                        "dev.hydrogen.tieba.ui.ChatReadStateTest",
                        "dev.hydrogen.tieba.ui.GroupModerationControllerTest",
                        "dev.hydrogen.tieba.ui.PublishingForumControllerTest",
                        "dev.hydrogen.tieba.ui.SettingsNavigationTest",
                        "dev.hydrogen.tieba.ui.PageNavigationTest",
                        "dev.hydrogen.tieba.data.ContentFiltersTest",
                        "dev.hydrogen.tieba.data.AppUpdateTest",
                        "dev.hydrogen.tieba.data.PublicReleaseUpdatesTest",
                        "dev.hydrogen.tieba.ui.UpdateControllerTest",
                        "dev.hydrogen.tieba.data.PrivateChatConnectionTest",
                        "dev.hydrogen.tieba.data.ForumSignInTest"],
                       cwd=ROOT, env=environment, check=True)
    if arguments.command in ("wrapper", "check"):
        return
    apk = project / "app" / "build" / "outputs" / "apk" / variant / f"app-{variant}.apk"
    if not apk.is_file():
        raise RuntimeError("The build completed without producing an APK.")
    sdk = Path(configuration["android_sdk"])
    signer = sdk / "build-tools" / configuration["build_tools"] / "apksigner.bat"
    subprocess.run([str(signer), "verify", "--verbose", str(apk)], env=environment, check=True)
    print(f"Verified APK: {apk}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Build verification failed: {error}", file=sys.stderr)
        sys.exit(1)
