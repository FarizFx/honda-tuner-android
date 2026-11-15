"""Compile and exercise actual Android protocol sources without Android SDK/USB."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent))
from toolchain import ROOT, find_java_home, java_bin


def run(args, **kwargs):
    result = subprocess.run(args, text=True, encoding="utf-8", errors="replace", capture_output=True, **kwargs)
    if result.returncode:
        raise RuntimeError("Command failed:\n" + result.stdout + result.stderr)
    return result.stdout + result.stderr


def argfile(paths):
    return "\n".join('"%s"' % str(path).replace("\\", "/") for path in paths) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home")
    options = parser.parse_args()
    home = find_java_home(options.java_home)
    build = ROOT / "build/core-tests"
    classes = build / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    sources = sorted((ROOT / "app/src/main/java/id/hondatuner/core").glob("*.java"))
    sources += sorted((ROOT / "tests/java").rglob("*.java"))
    source_list = build / "sources.txt"
    source_list.write_text(argfile(sources), encoding="utf-8", newline="\n")
    began = time.time()
    compile_output = run([java_bin(home, "javac"), "--release", "8", "-encoding", "UTF-8", "-Xlint:all", "-d", str(classes), "@" + str(source_list)], timeout=60)
    suite = run([java_bin(home, "java"), "-ea", "-cp", str(classes), "id.hondatuner.core.CoreTest"], timeout=30)
    vectors = json.loads((ROOT / "tests/reference_vectors.json").read_text(encoding="utf-8"))
    requests = "".join("%d\t%s\n" % (vector["table"], vector["body"]) for vector in vectors)
    raw = run([java_bin(home, "java"), "-cp", str(classes), "id.hondatuner.core.DecodeCli"], input=requests, timeout=30)
    decoded = [json.loads(line) for line in raw.splitlines()]
    if len(decoded) != len(vectors):
        raise AssertionError("Decoder fixture count mismatch")
    compared = 0
    for vector, actual in zip(vectors, decoded):
        expected = vector["values"]
        if list(actual) != list(expected):
            raise AssertionError("Field ordering mismatch for table %02X" % vector["table"])
        for name, value in expected.items():
            found = actual[name]
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                okay = isinstance(found, (int, float)) and not isinstance(found, bool) and math.isclose(value, found, rel_tol=1e-12, abs_tol=1e-12)
            else:
                okay = type(value) is type(found) and value == found
            if not okay:
                raise AssertionError("Table %02X %s: expected %r, got %r" % (vector["table"], name, value, found))
            compared += 1
    evidence = ROOT / "evidence"
    evidence.mkdir(exist_ok=True)
    (evidence / "core_tests.txt").write_text(compile_output + suite + "\nPASS: %d reference decoder vectors, %d field values\n" % (len(vectors), compared), encoding="utf-8", newline="\n")
    report = {
        "result": "passed", "java_home": str(home), "java_version": run([java_bin(home, "java"), "-version"], timeout=10).strip(),
        "java_release": 8, "core_regression_tests": int(suite.split("PASS: ")[1].split()[0]),
        "reference_decoder_vectors": len(vectors), "reference_field_values": compared,
        "elapsed_seconds": round(time.time() - began, 3),
        "source_sha256": {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources},
        "android_sdk_compilation": False, "physical_android_usb_or_ecu_test": False,
        "fixtures_are_vehicle_recordings": False,
    }
    (evidence / "core_verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(suite, end="")
    print("PASS: %d reference decoder vectors; %d field values" % (len(vectors), compared))


if __name__ == "__main__":
    main()
