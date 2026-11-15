"""Offline structural checks. Android API compilation is performed by Gradle/CI."""
import ast
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def main():
    source = ROOT / "app/src/main"
    manifest = ET.parse(source / "AndroidManifest.xml").getroot()
    features = manifest.findall("uses-feature")
    assert any(feature.get(ANDROID + "name") == "android.hardware.usb.host" and feature.get(ANDROID + "required") == "true" for feature in features), "USB host feature missing"
    assert not manifest.findall("uses-permission"), "This diagnostic app requires no manifest permissions"
    activities = manifest.findall("application/activity")
    assert len(activities) == 1 and activities[0].get(ANDROID + "name") == "id.hondatuner.android.MainActivity"
    assert activities[0].get(ANDROID + "exported") == "true"
    assert (source / "java/id/hondatuner/android/MainActivity.java").is_file()
    metadata = activities[0].findall("meta-data")
    assert any(item.get(ANDROID + "resource") == "@xml/device_filter" for item in metadata)
    device_filter = ET.parse(source / "res/xml/device_filter.xml").getroot().findall("usb-device")
    assert len(device_filter) == 1 and device_filter[0].attrib == {"vendor-id": "1027", "product-id": "24577"}
    xml_paths = sorted((source / "res").rglob("*.xml"))
    for path in xml_paths:
        ET.parse(path)
    strings = {node.get("name") for node in ET.parse(source / "res/values/strings.xml").getroot().findall("string")}
    java_paths = sorted((source / "java").rglob("*.java"))
    references = set()
    for path in java_paths:
        text = path.read_text(encoding="utf-8")
        assert "\x00" not in text
        references.update(re.findall(r"\b(?<!android\.)R\.string\.(\w+)", text))
    tool_paths = sorted((ROOT / "tools").glob("*.py"))
    for path in tool_paths:
        ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    assert references <= strings, "Unresolved string resources: " + str(sorted(references - strings))
    app_build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    for required in ("namespace 'id.hondatuner.android'", "compileSdk 35", "minSdk 26", "targetSdk 35", "buildToolsVersion '35.0.0'"):
        assert required in app_build, "Gradle configuration missing: " + required
    assert "version '8.7.3'" in (ROOT / "build.gradle").read_text(encoding="utf-8")
    workflow_path = ROOT / ".github/workflows/build-android.yml"
    workflow_text = workflow_path.read_text(encoding="utf-8")
    assert "\t" not in workflow_text
    for required in ("workflow_dispatch:", "java-version: '17'", "gradle-version: '8.9'", ':app:assembleDebug', "verify_project.py", "test_core.py", "package_apk.py", "HondaTuner-Android-debug", "contents: read"):
        assert required in workflow_text, "Workflow step missing: " + required
    yaml_parsed = False
    try:
        import yaml
    except ImportError as error:
        raise RuntimeError("Workflow validation requires PyYAML. Install it with: python -m pip install PyYAML==6.0.2") from error
    else:
        workflow = yaml.safe_load(workflow_text)
        assert "workflow_dispatch" in workflow["on"]
        android_job = workflow["jobs"]["android"]
        runner = android_job.get("runs-on")
        assert isinstance(runner, (str, list, dict)) and runner, "Android runner selection is missing or invalid"
        assert isinstance(android_job.get("steps"), list) and android_job["steps"], "Android build steps are missing"
        yaml_parsed = True
    catalog_lines = (source / "assets/ecu_catalog.tsv").read_text(encoding="utf-8").splitlines()
    for line in catalog_lines:
        fields = line.split("\t")
        assert len(fields) == 3 and re.fullmatch("[0-9A-F]{10}", fields[0]) and fields[0] != "FFFFFFFFFF"
    checked = java_paths + xml_paths + tool_paths + [source / "AndroidManifest.xml", source / "assets/ecu_catalog.tsv", workflow_path, ROOT / "app/build.gradle", ROOT / "build.gradle", ROOT / "settings.gradle"]
    report = {
        "result": "passed", "java_source_files": len(java_paths), "resource_xml_files": len(xml_paths),
        "string_resources_resolved": len(references), "exact_id_catalog_rows": len(catalog_lines),
        "python_tool_syntax_checks": len(tool_paths),
        "yaml_parser_validation": yaml_parsed, "android_sdk_compilation": False,
        "physical_android_usb_or_ecu_test": False,
        "source_sha256": {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in checked},
    }
    evidence = ROOT / "evidence"
    evidence.mkdir(exist_ok=True)
    (evidence / "project_verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("PASS: Android manifest, USB filter, %d Java sources, %d XML resources, %d string references and %d catalog rows." %
          (len(java_paths), len(xml_paths), len(references), len(catalog_lines)))
    print("Android SDK compilation and device operation require a build host / actual phone.")


if __name__ == "__main__":
    main()
