"""Render downloaded Trivy reports as data; never build or execute PR content."""

import argparse
from collections import Counter
import html
import json
from pathlib import Path
import re

from cve_coverage import INVENTORY, validate_report


SEVERITIES = ("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN")


def cell(value):
    """Prevent report data from introducing Markdown, HTML or user mentions."""
    value = html.escape(str(value)[:180]).replace("\n", " ").replace("\r", " ")
    for character in ("|", "`", "*", "_", "[", "]", "@", "\\"):
        value = value.replace(character, f"&#{ord(character)};")
    return value


def context(root, expected_sha):
    data = json.loads((root / "cve-context.json").read_text(encoding="utf-8"))
    if data.get("schema_version") != 1 or data.get("head_sha") != expected_sha:
        raise ValueError("Scan context does not match the expected commit")
    targets = data["targets"]
    if not isinstance(targets, list) or not 1 <= len(targets) <= 100:
        raise ValueError("Invalid scan target inventory")
    ids = set()
    for target in targets:
        name = target.get("id", "")
        if not isinstance(name, str) or not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,79}", name) or name in ids:
            raise ValueError("Invalid or duplicate scan target ID")
        ids.add(name)
    return data


def snapshot(target, root):
    folder = root / target["id"]
    try:
        state = json.loads((folder / "status.json").read_text(encoding="utf-8"))["status"]
        if state == "skipped" and target.get("requires_auth") and not target.get("enabled", True):
            return {"status": "skipped", "reason": "Package credentials unavailable"}
        if state != "completed":
            raise ValueError("Build, scan or report generation did not complete")
        report = validate_report(target, folder)
        findings, counts, fixed = {}, Counter(), Counter()
        for result in report.get("Results", []):
            for vulnerability in result.get("Vulnerabilities", []):
                severity = vulnerability.get("Severity", "UNKNOWN")
                severity = severity if severity in SEVERITIES else "UNKNOWN"
                advisory, package = vulnerability.get("VulnerabilityID"), vulnerability.get("PkgName")
                if not isinstance(advisory, str) or not isinstance(package, str) or not advisory or not package:
                    raise ValueError("Missing advisory ID or package name")
                counts[severity] += 1
                if vulnerability.get("FixedVersion"):
                    fixed[severity] += 1
                # Version changes alone do not make an existing package/advisory finding new.
                key = (result.get("Type", "unknown"), package, advisory)
                findings[key] = {**vulnerability, "Severity": severity}
        version = (folder / "trivy-version.txt").read_text(encoding="utf-8")
        database_dates = tuple(line.strip() for line in version.splitlines() if "UpdatedAt:" in line)
        operating_system = report.get("Metadata", {}).get("OS") or {}
        if not isinstance(operating_system, dict):
            raise ValueError("Invalid OS metadata")
        return {"status": "completed", "counts": counts, "findings": findings, "fixed": fixed,
                "os": operating_system, "database_dates": database_dates}
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        return {"status": "failed", "reason": str(error)}


def render(head, base, repository, pr, head_sha, base_sha, run_id, baseline_run_id="", conclusion="success"):
    run_url = f"https://github.com/{repository}/actions/runs/{run_id}"
    lines = ["## CVE scan report", "", f"Commit `{head_sha[:12]}` · [Workflow and full reports]({run_url})",
             "", f"CI outcome: **{cell(conclusion)}**. CVE findings are report-only."]
    unavailable = "No retained successful scan of the exact base commit is available."
    try:
        head_context = context(head, head_sha)
        targets = head_context["targets"]
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        head_context = None
        targets = json.loads(INVENTORY.read_text(encoding="utf-8"))
        unavailable = "PR scan context is missing or invalid; comparison is unavailable."
    base_context = None
    if head_context and baseline_run_id:
        try:
            candidate = context(base, base_sha)
            if head_context.get("base_sha") != base_sha:
                raise ValueError("PR base changed after the scan")
            if not head_context.get("trivy_version") or candidate.get("trivy_version") != head_context["trivy_version"]:
                raise ValueError("Base and PR scans used different Trivy versions")
            if not head_context.get("scan_policy") or candidate.get("scan_policy") != head_context["scan_policy"]:
                raise ValueError("Base and PR scans used different scan policies")
            base_context = candidate
        except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
            unavailable = f"Exact-base reports are unavailable or incompatible: {cell(error)}."
    base_targets = {target["id"]: target for target in base_context["targets"]} if base_context else {}
    totals, statuses, fixed = Counter(), Counter(), Counter()
    rows, additions, removals, eosl, gaps = [], [], [], [], []
    compared, database_changed = 0, False
    for target in targets:
        name = target["id"]
        current = snapshot(target, head)
        statuses[current["status"]] += 1
        new_count = resolved_count = "—"
        if current["status"] == "completed":
            totals.update(current["counts"])
            fixed.update(current["fixed"])
            if current["os"].get("EOSL"):
                eosl.append(name)
            previous = snapshot(base_targets[name], base) if name in base_targets else None
            if previous and previous["status"] == "completed":
                compared += 1
                new = current["findings"].keys() - previous["findings"].keys()
                resolved = previous["findings"].keys() - current["findings"].keys()
                additions.extend((name, current["findings"][key]) for key in new)
                removals.extend((name, previous["findings"][key]) for key in resolved)
                new_count, resolved_count = len(new), len(resolved)
                if (not current["database_dates"] or not previous["database_dates"] or
                        current["database_dates"] != previous["database_dates"]):
                    database_changed = True
            elif base_context:
                gaps.append(name)
            values = [current["counts"][severity] for severity in SEVERITIES]
            status = "completed (EOL OS)" if name in eosl else "completed"
        else:
            values, status = ["—"] * len(SEVERITIES), current["status"]
            gaps.append(name)
        rows.append("| " + " | ".join(map(cell, [name, status, *values, new_count, resolved_count])) + " |")
    if base_context and compared:
        lines += ["", f"**Additional findings: {len(additions)} · Resolved findings: {len(removals)}** "
                  f"across {compared}/{len(targets)} comparable targets, versus base `{base_sha[:12]}`.",
                  f"[Exact-base scan](https://github.com/{repository}/actions/runs/{baseline_run_id})."]
        if compared != len(targets):
            lines.append("**Comparison is incomplete**; unscanned or unmatched targets are not counted as zero.")
        if database_changed:
            lines.append("Advisory database metadata differs or is unavailable. Newly observed findings may reflect "
                         "database updates, so they cannot all be attributed to the PR.")
        lines.append("The comparison also reflects refreshed base images and resolved dependencies, "
                     "not only source changes. ‘Resolved’ means absent from the PR scan, not proven unreachable.")
    else:
        lines += ["", f"**Additional CVEs: comparison unavailable.** {unavailable if not base_context else 'No targets have comparable completed reports.'}"]
    lines += ["", f"Coverage: **{statuses['completed']} completed, {statuses['skipped']} skipped, "
              f"{statuses['failed']} failed**.", "",
              "| Service / scan target | Status | Critical | High | Medium | Low | Unknown | Added | Resolved |",
              "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |", *rows,
              "| **Total finding instances** | | " + " | ".join(str(totals[s]) for s in SEVERITIES) + " | | |", "",
              "Severity counts are finding instances and can repeat across packages/images. Added/resolved counts "
              "deduplicate each (ecosystem, package, advisory) within a target; version changes alone are not new findings. "
              "Advisories can have CVE or other identifiers.", "",
              f"Fixed versions are reported for **{fixed['CRITICAL']}/{totals['CRITICAL']} critical** and "
              f"**{fixed['HIGH']}/{totals['HIGH']} high** instances; upgrade compatibility still needs review."]
    if eosl:
        lines += ["", "**EOL operating systems:** " + ", ".join(map(cell, eosl)) +
                  ". Advisory coverage may be incomplete; prioritize supported base images."]
    if gaps:
        lines += ["", "**Coverage / comparison gaps:** " + ", ".join(map(cell, sorted(set(gaps)))) +
                  ". Inspect the linked job logs and artifacts."]
    if additions:
        additions.sort(key=lambda item: (SEVERITIES.index(item[1]["Severity"]), item[0],
                                         item[1]["VulnerabilityID"], item[1]["PkgName"]))
        lines += ["", "<details>", f"<summary>Additional findings: showing {min(25, len(additions))} of {len(additions)}</summary>",
                  "", "| Severity | Target | Advisory | Package | Fixed version |", "| --- | --- | --- | --- | --- |"]
        for name, vulnerability in additions[:25]:
            values = [vulnerability["Severity"], name, vulnerability["VulnerabilityID"],
                      vulnerability["PkgName"], vulnerability.get("FixedVersion") or "Not reported"]
            lines.append("| " + " | ".join(map(cell, values)) + " |")
        lines += ["", "</details>"]
    lines += ["", f"[GitHub Code scanning](https://github.com/{repository}/security/code-scanning?query=pr%3A{pr}+tool%3ATrivy)",
              "", "Package presence does not establish exploitability; webapp dependency findings do not establish "
              "browser-bundle reachability. Full JSON, text, SARIF and SBOM reports are retained for 30 days."]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--head", type=Path, required=True)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--pr", type=int, required=True)
    parser.add_argument("--head-sha", required=True)
    parser.add_argument("--base-sha", required=True)
    parser.add_argument("--run-id", type=int, required=True)
    parser.add_argument("--baseline-run-id", default="")
    parser.add_argument("--conclusion", default="success")
    parser.add_argument("--output", type=Path, required=True)
    args = vars(parser.parse_args())
    output = args.pop("output")
    output.write_text(render(**args), encoding="utf-8")


if __name__ == "__main__":
    main()
