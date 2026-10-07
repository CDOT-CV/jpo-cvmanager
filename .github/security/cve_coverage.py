"""Validate repository-specific CVE coverage; actions own builds and scans."""

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import sys


INVENTORY = Path(__file__).with_name("cve-targets.json")
REQUIRED = ("results.json", "results.sarif", "results.txt", "sbom.cdx.json", "trivy-version.txt")


def matrix(items, tracked, trusted):
    declared = {t["dockerfile"] for t in items if "dockerfile" in t}
    dockerfiles = {p for p in tracked if Path(p).name.startswith("Dockerfile")}
    if declared != dockerfiles:
        raise ValueError(f"Dockerfile coverage mismatch: missing={sorted(dockerfiles - declared)}, "
                         f"untracked={sorted(declared - dockerfiles)}")
    if len({t["id"] for t in items}) != len(items):
        raise ValueError("Duplicate scan target IDs")
    return {"include": [{**t, "build": t.get("build", "docker"), "stage": t.get("stage", ""),
                         "requires_auth": t.get("requires_auth", False),
                         "enabled": trusted or not t.get("requires_auth"),
                         "build_args": "\n".join(t.get("args", []))} for t in items]}


def validate_report(target, folder):
    required = REQUIRED + (() if target.get("build") == "filesystem" else ("image.json",))
    missing = [name for name in required if not (folder / name).is_file() or not (folder / name).stat().st_size]
    if missing:
        raise ValueError(f"Missing or empty reports: {', '.join(missing)}")
    report = json.loads((folder / "results.json").read_text(encoding="utf-8"))
    results = report.get("Results", [])
    expected = target.get("expected_type")
    if not any(r.get("Packages") and (not expected or r.get("Type") == expected) for r in results):
        raise ValueError(f"Missing {expected or 'any'} package inventory")
    if target.get("build") != "filesystem":
        image = json.loads((folder / "image.json").read_text(encoding="utf-8"))
        if (image.get("Os"), image.get("Architecture")) != ("linux", "amd64"):
            raise ValueError("Expected a Linux AMD64 image")
    return report


def coverage(items, root, trusted):
    lines = ["## CVE scan coverage", "", "Findings are report-only; execution and coverage errors fail CI.",
             "", "| Target | Status | Findings / reason |", "| --- | --- | --- |"]
    failed = False
    for target in items:
        folder = root / target["id"]
        try:
            state = json.loads((folder / "status.json").read_text(encoding="utf-8"))["status"]
            if state == "skipped" and target.get("requires_auth") and not trusted:
                detail = "GitHub Packages build requires a trusted event"
            elif state == "completed":
                report = validate_report(target, folder)
                counts = Counter(v.get("Severity", "UNKNOWN") for r in report.get("Results", [])
                                 for v in r.get("Vulnerabilities", []))
                detail = ", ".join(f"{s}: {n}" for s, n in sorted(counts.items())) or "No known vulnerabilities found"
                if report.get("Metadata", {}).get("OS", {}).get("EOSL"):
                    detail += "; EOL operating system: vulnerability coverage may be incomplete"
            else:
                raise ValueError("Build, scan or report step failed or unexpected skip; see job logs")
        except (OSError, ValueError, KeyError, TypeError) as error:
            failed, state, detail = True, "failed", str(error)
        detail = detail.replace("|", "\\|").replace("\n", " ").replace("\r", " ")
        lines.append(f"| {target['id']} | {state} | {detail} |")
    lines += ["", "See artifacts and Actions logs for scanner metadata and warnings. "
              "CVE matching depends on identifiable packages and advisory coverage; "
              "webapp findings do not establish browser-bundle reachability."]
    return "\n".join(lines) + "\n", failed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("matrix", "reports"))
    parser.add_argument("--target")
    parser.add_argument("--reports", type=Path, default=Path("cve-reports"))
    parser.add_argument("--trusted", choices=("true", "false"), default="false")
    args = parser.parse_args()
    items = json.loads(INVENTORY.read_text(encoding="utf-8"))
    if args.operation == "matrix":
        print(json.dumps(matrix(items, sys.stdin.read().splitlines(), args.trusted == "true"), separators=(",", ":")))
    elif args.target:
        target = next(t for t in items if t["id"] == args.target)
        validate_report(target, args.reports / args.target)
    else:
        summary, failed = coverage(items, args.reports, args.trusted == "true")
        print(summary)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
                output.write(summary)
        if failed:
            sys.exit(1)


if __name__ == "__main__":
    main()
