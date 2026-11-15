"""Verify a real Gradle-built APK, then export it and its SHA-256 for installation."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parent))
from toolchain import ROOT, find_java_home, find_sdk, java_bin


def checked(args):
    result = subprocess.run(args, text=True, encoding="utf-8", errors="replace", capture_output=True, timeout=60)
    if result.returncode:
        raise RuntimeError("APK verification failed:\n" + result.stdout + result.stderr)
    return result.stdout + result.stderr


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk")
    parser.add_argument("--java-home")
    options = parser.parse_args()
    apk = Path(options.apk).resolve()
    if not apk.is_file():
        raise RuntimeError("Gradle has not produced the requested APK: " + str(apk))
    sdk = find_sdk(options.sdk)
    home = find_java_home(options.java_home)
    build_tools = sdk / "build-tools/35.0.0"
    signer = build_tools / "lib/apksigner.jar"
    aapt = build_tools / ("aapt2.exe" if os.name == "nt" else "aapt2")
    if not signer.is_file() or not aapt.is_file():
        raise RuntimeError("Install Android SDK build-tools 35.0.0 first")
    signature = checked([java_bin(home, "java"), "-jar", str(signer), "verify", "--verbose", "--print-certs", str(apk)])
    badging = checked([str(aapt), "dump", "badging", str(apk)])
    app_config = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    version_code = re.search(r"^\s*versionCode\s+(\d+)\s*$", app_config, re.MULTILINE).group(1)
    version_name = re.search(r"^\s*versionName\s+'([^']+)'\s*$", app_config, re.MULTILINE).group(1)
    for expected in ("name='id.hondatuner.android'", "versionCode='%s'" % version_code, "versionName='%s'" % version_name, "minSdkVersion:'26'", "targetSdkVersion:'35'", "name='id.hondatuner.android.MainActivity'", "name='android.hardware.usb.host'"):
        if expected not in badging:
            raise RuntimeError("APK metadata is not the expected app: " + expected + "\n" + badging)
    if "uses-permission:" in badging:
        raise RuntimeError("Unexpected manifest permission in the diagnostic APK")
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("APK ZIP integrity failed")
        names = archive.namelist()
        for required in ("AndroidManifest.xml", "classes.dex", "resources.arsc", "assets/ecu_catalog.tsv"):
            if required not in names:
                raise RuntimeError("Missing APK entry: " + required)
        if any(name.endswith((".pyd", ".dll", ".exe")) or "reference_vectors" in name for name in names):
            raise RuntimeError("Development/native artifacts were bundled into the Android app")
    output = ROOT / "dist"
    output.mkdir(exist_ok=True)
    exported = output / "HondaTuner-Android-debug.apk"
    shutil.copyfile(apk, exported)
    digest = hashlib.sha256(exported.read_bytes()).hexdigest()
    (output / (exported.name + ".sha256")).write_text(digest + "  " + exported.name + "\n", encoding="utf-8")
    report = {
        "result": "passed", "apk_sha256": digest, "apk_bytes": exported.stat().st_size,
        "android_sdk_compilation": True, "apk_signature_verified": True, "zip_integrity_verified": True,
        "application_id": "id.hondatuner.android", "minimum_android_api": 26, "target_android_api": 35,
        "version_code": int(version_code), "version_name": version_name,
        "variant": "debug", "signature_verification": signature, "manifest_badging": badging,
        "physical_android_usb_or_ecu_test": False,
    }
    if os.environ.get("GITHUB_ACTIONS") == "true":
        report["github_run"] = "https://github.com/%s/actions/runs/%s" % (os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_RUN_ID"])
        report["source_commit"] = os.environ["GITHUB_SHA"]
        lint_path = ROOT / "app/build/reports/lint-results-debug.xml"
        if not lint_path.is_file():
            raise RuntimeError("The required Android lint report is missing")
        lint = ET.parse(lint_path).getroot().findall("issue")
        errors = [issue for issue in lint if issue.get("severity") in ("Error", "Fatal")]
        if errors:
            raise RuntimeError("Android lint reported errors: " + ", ".join(issue.get("id", "") for issue in errors))
        report["android_lint_report_verified"] = True
        report["android_lint_errors"] = 0
        report["android_lint_warnings"] = sum(issue.get("severity") == "Warning" for issue in lint)
    evidence = ROOT / "evidence"
    evidence.mkdir(exist_ok=True)
    (evidence / "apk_verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Verified signed debug APK: " + str(exported))
    print("SHA-256: " + digest)


if __name__ == "__main__":
    main()
