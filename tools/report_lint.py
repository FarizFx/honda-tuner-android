"""Expose all fatal Android lint findings directly in the Actions job log."""
import argparse
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def escape(value, *, property_value=False):
    value = str(value).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    if property_value:
        value = value.replace(":", "%3A").replace(",", "%2C")
    return value


def report(path):
    if not path.is_file():
        print("Android lint XML was not generated; inspect the compile step above.")
        return 0
    issues = ET.parse(path).getroot().findall("issue")
    errors = [issue for issue in issues if issue.get("severity") in ("Error", "Fatal")]
    warnings = [issue for issue in issues if issue.get("severity") == "Warning"]
    print("Android lint: %d errors, %d warnings." % (len(errors), len(warnings)))
    for issue in errors:
        location = issue.find("location")
        source = Path(location.get("file", "")) if location is not None else path
        try:
            source = source.relative_to(ROOT)
        except ValueError:
            pass
        line = location.get("line", "1") if location is not None else "1"
        message = "%s: %s" % (issue.get("id", "Lint"), issue.get("message", "No message"))
        print("::error file=%s,line=%s::%s" %
              (escape(source.as_posix(), property_value=True), escape(line, property_value=True), escape(message)))
        for name in ("errorLine1", "errorLine2"):
            snippet = issue.get(name)
            if snippet:
                print("    " + snippet.replace("\r", "").replace("\n", "\n    "))
    if warnings:
        counts = Counter(issue.get("id", "Lint") for issue in warnings)
        print("Warning counts: " + ", ".join("%s=%d" % pair for pair in sorted(counts.items())))
    return 1 if errors else 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, default=ROOT / "app/build/reports/lint-results-debug.xml")
    options = parser.parse_args()
    return report(options.report)


if __name__ == "__main__":
    raise SystemExit(main())
