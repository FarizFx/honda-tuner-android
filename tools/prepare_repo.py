"""Export only current verified Android source as a GitHub-root-ready archive."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from toolchain import ROOT

OUTPUT_NAME = "honda-tuner-android-repo-20261115.zip"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_files():
    files = []
    for name in (".gitattributes", ".gitignore", "README.md", "NOTICE.md", "SESSION_HANDOFF.md", "build.gradle", "settings.gradle", "gradle.properties", "app/build.gradle"):
        files.append(ROOT / name)
    for folder in (".github", "app/src", "tools", "tests"):
        files.extend(path for path in (ROOT / folder).rglob("*") if path.is_file() and "__pycache__" not in path.parts and path.suffix != ".pyc")
    for name in ("reference_provenance.json", "core_verification.json", "core_tests.txt", "project_verification.json", "build_status.json"):
        files.append(ROOT / "evidence" / name)
    return sorted(set(files))


def validate_reports():
    for name in ("core_verification.json", "project_verification.json"):
        report = json.loads((ROOT / "evidence" / name).read_text(encoding="utf-8"))
        if report.get("result") != "passed":
            raise RuntimeError("Verification did not pass: " + name)
        for relative, expected in report["source_sha256"].items():
            path = (ROOT / relative).resolve()
            if not path.is_relative_to(ROOT) or not path.is_file() or digest(path) != expected:
                raise RuntimeError("Source changed since verification: " + relative)
    references = json.loads((ROOT / "evidence/reference_provenance.json").read_text(encoding="utf-8"))
    for relative, expected in references["generated_sha256"].items():
        if digest(ROOT / relative) != expected:
            raise RuntimeError("Generated reference changed: " + relative)


def main():
    validate_reports()
    files = source_files()
    manifest = {path.relative_to(ROOT).as_posix(): {"sha256": digest(path), "bytes": path.stat().st_size} for path in files}
    output = (ROOT / "dist").resolve()
    if not output.is_relative_to(ROOT):
        raise RuntimeError("Output path escapes the Android project")
    output.mkdir(exist_ok=True)
    archive_path = output / OUTPUT_NAME
    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            archive.write(path, path.relative_to(ROOT).as_posix())
        archive.writestr("MANIFEST.json", json.dumps({"files": manifest, "android_sdk_compilation": False, "apk_included": False}, indent=2) + "\n")
    with zipfile.ZipFile(archive_path) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("Source ZIP integrity failed")
        for name, expected in manifest.items():
            if hashlib.sha256(archive.read(name)).hexdigest() != expected["sha256"]:
                raise RuntimeError("Source archive hash mismatch: " + name)
        if ".github/workflows/build-android.yml" not in archive.namelist():
            raise RuntimeError("GitHub workflow is not at repository root")
    sha = digest(archive_path)
    (output / (OUTPUT_NAME + ".sha256")).write_text(sha + "  " + OUTPUT_NAME + "\n", encoding="utf-8", newline="\n")
    report = {"result": "passed", "archive": str(archive_path), "sha256": sha, "bytes": archive_path.stat().st_size,
              "source_files": len(files), "exact_verified_source_copy": True, "zip_integrity": True,
              "github_workflow_at_repo_root": True, "apk_included": False, "github_repository_created": False,
              "android_sdk_compilation": False, "physical_android_usb_or_ecu_test": False}
    (ROOT / "evidence/repo_package_verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("Verified repo-ready source ZIP: " + str(archive_path))
    print("%d files; %d bytes; SHA-256 %s" % (len(files), archive_path.stat().st_size, sha))


if __name__ == "__main__":
    main()
