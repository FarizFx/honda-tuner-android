"""Compile and exercise actual Android protocol sources without Android SDK/USB."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
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
    parser.add_argument("--output-dir", type=Path, help="Retain local reports separately from CI evidence")
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
    suite_results = {}
    for name in ("CoreTest", "TransportTest", "ControllerHealthTest", "UartReplyTest"):
        suite_results[name] = run([java_bin(home, "java"), "-ea", "-cp", str(classes), "id.hondatuner.core." + name], timeout=45)
    suite = "".join(suite_results.values())
    suite_counts = {name: int(re.search(r"^PASS: (\d+) .*regression tests$", result, re.M).group(1))
                    for name, result in suite_results.items()}
    lite_path = ROOT / "tests/lite_transport_fixtures.json"
    lite_cases = json.loads(lite_path.read_text(encoding="utf-8"))["cases"]
    fixture_input = "".join("\t".join((case["name"], case["echo_mode"], str(case["target"]), case["payload_hex"],
                                     str(case["timeout_ms"]), str(case["tx_completion_ms"]),
                                     ";".join(str(piece["at_ms"]) + ":" + piece["hex"] for piece in case["rx_schedule"]))) + "\n"
                            for case in lite_cases)
    fixture_raw = run([java_bin(home, "java"), "-ea", "-cp", str(classes), "id.hondatuner.core.TransportTest", "--fixtures"], input=fixture_input, timeout=30)
    fixture_results = [json.loads(line) for line in fixture_raw.splitlines()]
    if len(fixture_results) != len(lite_cases):
        raise AssertionError("Lite transport fixture count mismatch")
    for case, result in zip(lite_cases, fixture_results):
        expected = case["expect"]
        if (result["name"] != case["name"] or result["success"] != expected["success"] or
                result["writes"] != expected["writes"] or result["tx_hex"] != case["tx_hex"] or
                result["elapsed_ms"] < expected["min_elapsed_ms"] or
                (expected["success"] and result["payload_hex"] != expected["payload_hex"])):
            raise AssertionError("Lite transport fixture %s: expected %r, got %r" % (case["name"], expected, result))
    fixture_text = "".join("OK Lite fixture %s (%s, %d TX, %d ms)\n" %
                           (result["name"], "valid" if result["success"] else "rejected", result["writes"], result["elapsed_ms"])
                           for result in fixture_results)
    phone_path = ROOT / "tests/phone_1_1_3_fixtures.json"
    phone_recording = json.loads(phone_path.read_text(encoding="utf-8"))
    phone_cases = phone_recording["cases"]
    phone_input = "".join("\t".join((case["name"], case["echo_mode"], str(case["target"]), case["payload_hex"],
                                    str(case["timeout_ms"]), str(case["tx_completion_ms"]),
                                    ";".join(str(piece["at_ms"]) + ":" + piece["raw_hex"] for piece in case["rx_schedule"]))) + "\n"
                           for case in phone_cases)
    phone_raw = run([java_bin(home, "java"), "-ea", "-cp", str(classes), "id.hondatuner.core.UartReplyTest", "--fixtures"], input=phone_input, timeout=30)
    phone_results = [json.loads(line) for line in phone_raw.splitlines()]
    if len(phone_results) != len(phone_cases):
        raise AssertionError("Phone packet replay count mismatch")
    for case, result in zip(phone_cases, phone_results):
        expected = case["expect"]
        deadline = case["timeout_ms"] + case["tx_completion_ms"]
        if (result["name"] != case["name"] or result["success"] != expected["success"] or
                result["writes"] != expected["writes"] or result["tx_hex"] != case["tx_hex"] or
                result["payload_hex"] != expected["payload_hex"] or
                result["decoded_field_count"] != expected["decoded_field_count"] or
                (expected["success"] and result["elapsed_ms"] > deadline) or
                (not expected["success"] and result["elapsed_ms"] < 2 * deadline)):
            raise AssertionError("Phone packet replay %s: expected %r, got %r" % (case["name"], expected, result))
    phone_text = "".join("OK Phone replay %s (%s, %d TX, %d fields, %d simulated ms)\n" %
                         (result["name"], "valid" if result["success"] else "rejected", result["writes"], result["decoded_field_count"], result["elapsed_ms"])
                         for result in phone_results)
    total_tests = sum(suite_counts.values()) + len(lite_cases) + len(phone_cases)
    vectors = json.loads((ROOT / "tests/reference_vectors.json").read_text(encoding="utf-8"))
    phone_vectors = [{"table": int(case["expect"]["response_table"], 16), "body": case["expect"]["body_hex"], "values": case["expect"]["decoded"]}
                     for case in phone_cases if case["expect"]["success"]]
    all_vectors = vectors + phone_vectors
    requests = "".join("%d\t%s\n" % (vector["table"], vector["body"]) for vector in all_vectors)
    raw = run([java_bin(home, "java"), "-cp", str(classes), "id.hondatuner.core.DecodeCli"], input=requests, timeout=30)
    decoded = [json.loads(line) for line in raw.splitlines()]
    if len(decoded) != len(all_vectors):
        raise AssertionError("Decoder fixture count mismatch")
    compared = phone_compared = 0
    for index, (vector, actual) in enumerate(zip(all_vectors, decoded)):
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
            if index < len(vectors): compared += 1
            else: phone_compared += 1
    evidence = options.output_dir if options.output_dir is not None else ROOT / "evidence"
    evidence.mkdir(parents=True, exist_ok=True)
    (evidence / "core_tests.txt").write_text(compile_output + suite + fixture_text + phone_text + "\nPASS: %d total regression tests\nPASS: %d reference decoder vectors, %d field values\nPASS: %d recorded decoder vectors, %d field values\n" % (total_tests, len(vectors), compared, len(phone_vectors), phone_compared), encoding="utf-8", newline="\n")
    report = {
        "result": "passed", "java_home": str(home), "java_version": run([java_bin(home, "java"), "-version"], timeout=10).strip(),
        "java_release": 8, "core_regression_tests": total_tests, "regression_suites": suite_counts,
        "lite_transport_fixtures": len(lite_cases), "lite_transport_fixture_results": fixture_results,
        "lite_transport_fixture_sha256": hashlib.sha256(lite_path.read_bytes()).hexdigest(),
        "phone_packet_replay_fixtures": len(phone_cases), "phone_packet_replay_results": phone_results,
        "phone_packet_fixture_sha256": hashlib.sha256(phone_path.read_bytes()).hexdigest(),
        "phone_recording_trace_sha256": phone_recording["trace_sha256"],
        "phone_decoder_vectors": len(phone_vectors), "phone_decoder_field_values": phone_compared,
        "reference_decoder_vectors": len(vectors), "reference_field_values": compared,
        "elapsed_seconds": round(time.time() - began, 3),
        "source_sha256": {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources},
        "android_sdk_compilation": False, "physical_android_usb_or_ecu_test": False,
        "all_fixtures_are_vehicle_recordings": False,
        "phone_fixtures_are_recorded_usb_packets": True,
        "replay_clock_is_simulated": True,
    }
    (evidence / "core_verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(suite, end="")
    print(fixture_text, end="")
    print(phone_text, end="")
    print("PASS: %d total regression tests" % total_tests)
    print("PASS: %d reference decoder vectors; %d field values" % (len(vectors), compared))
    print("PASS: %d recorded decoder vectors; %d field values" % (len(phone_vectors), phone_compared))


if __name__ == "__main__":
    main()
