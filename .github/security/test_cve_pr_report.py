"""Verify base comparisons, partial coverage and safe rendering of artifact data."""

import json
from pathlib import Path
import tempfile
import unittest

from cve_coverage import REQUIRED
import cve_pr_report as report


class PrReportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.head, self.base = self.root / "head", self.root / "base"
        self.target = {"id": "webapp", "build": "filesystem", "expected_type": "npm", "enabled": True}
        self.vulnerability = {"VulnerabilityID": "CVE-2026-1234", "PkgName": "fixture", "Severity": "CRITICAL",
                              "InstalledVersion": "1", "FixedVersion": "3"}
        self.write_context(self.head, "head", [self.target])
        self.write_context(self.base, "base", [self.target])
        self.write_report(self.head, [self.vulnerability])
        self.write_report(self.base, [self.vulnerability])

    def write_context(self, root, sha, targets, version="v0.75.0"):
        root.mkdir(exist_ok=True)
        (root / "cve-context.json").write_text(json.dumps({"schema_version": 1, "head_sha": sha, "base_sha": "base",
            "targets": targets, "trivy_version": version, "scan_policy": {"ignore_unfixed": False}}), encoding="utf-8")

    def write_report(self, root, vulnerabilities, target="webapp", state="completed", eosl=False):
        folder = root / target
        folder.mkdir(exist_ok=True)
        for name in REQUIRED:
            (folder / name).write_text("report", encoding="utf-8")
        (folder / "results.json").write_text(json.dumps({"Results": [{"Type": "npm", "Packages": [{"Name": "fixture"}],
            "Vulnerabilities": vulnerabilities}], "Metadata": {"OS": {"EOSL": eosl}}}), encoding="utf-8")
        (folder / "status.json").write_text(json.dumps({"status": state}), encoding="utf-8")
        (folder / "trivy-version.txt").write_text("Version: 0.75.0\nUpdatedAt: 2026-10-08\n", encoding="utf-8")

    def render(self, baseline="100"):
        return report.render(self.head, self.base, "owner/repo", 383, "head", "base", 101, baseline)

    def test_missing_baseline_is_unknown_not_zero(self):
        text = self.render(baseline="")
        self.assertIn("comparison unavailable", text)
        self.assertNotIn("Additional findings: 0", text)
        self.assertIn("1 completed, 0 skipped, 0 failed", text)

    def test_existing_vulnerability_version_change_is_not_added(self):
        self.write_report(self.head, [{**self.vulnerability, "InstalledVersion": "2"}])
        text = self.render()
        self.assertIn("Additional findings: 0", text)
        self.assertIn("Resolved findings: 0", text)

    def test_added_and_resolved_vulnerabilities_are_compared_as_sets(self):
        new = {**self.vulnerability, "VulnerabilityID": "CVE-2026-5678"}
        self.write_report(self.head, [new, new])
        text = self.render()
        self.assertIn("Additional findings: 1", text)
        self.assertIn("Resolved findings: 1", text)
        self.assertIn("| webapp | completed | 2 |", text)
        self.assertIn("CVE-2026-5678", text)

    def test_wrong_commit_and_scanner_version_cannot_be_compared(self):
        for sha, version in (("wrong", "v0.75.0"), ("base", "v0.76.0")):
            with self.subTest(sha=sha, version=version):
                self.write_context(self.base, sha, [self.target], version)
                text = self.render()
                self.assertIn("comparison unavailable", text)
                self.assertNotIn("Additional findings: 0", text)

    def test_failed_head_scan_is_not_a_resolution(self):
        self.write_report(self.head, [], state="failed")
        text = self.render()
        self.assertIn("comparison unavailable", text)
        self.assertIn("0 completed, 0 skipped, 1 failed", text)
        self.assertNotIn("Resolved findings: 1", text)

    def test_partial_baseline_does_not_claim_complete_no_new_findings(self):
        second = {**self.target, "id": "second"}
        self.write_context(self.head, "head", [self.target, second])
        self.write_report(self.head, [self.vulnerability], target="second")
        text = self.render()
        self.assertIn("1/2 comparable targets", text)
        self.assertIn("Comparison is incomplete", text)
        self.assertIn("| second | completed | 1 | 0 | 0 | 0 | 0 | — | — |", text)

    def test_missing_or_invalid_reports_are_coverage_failures(self):
        for value in (None, "not json"):
            with self.subTest(value=value):
                path = self.head / "webapp" / "results.json"
                if value is None:
                    path.unlink()
                else:
                    path.write_text(value)
                self.assertIn("1 failed", self.render())

    def test_database_changes_are_not_attributed_to_the_pr(self):
        (self.base / "webapp" / "trivy-version.txt").write_text("Version: 0.75.0\nUpdatedAt: 2026-10-07\n")
        self.assertIn("cannot all be attributed to the PR", self.render())

    def test_changed_scan_policy_cannot_be_compared(self):
        path = self.base / "cve-context.json"
        data = json.loads(path.read_text())
        data["scan_policy"]["ignore_unfixed"] = True
        path.write_text(json.dumps(data))
        self.assertIn("different scan policies", self.render())

    def test_changed_pr_base_cannot_be_compared(self):
        path = self.head / "cve-context.json"
        data = json.loads(path.read_text())
        data["base_sha"] = "old-base"
        path.write_text(json.dumps(data))
        self.assertIn("PR base changed", self.render())

    def test_eol_and_available_fixes_are_reported(self):
        self.write_report(self.head, [self.vulnerability], eosl=True)
        text = self.render()
        self.assertIn("completed (EOL OS)", text)
        self.assertIn("1/1 critical", text)
        self.assertIn("EOL operating systems", text)

    def test_artifact_content_cannot_inject_html_markdown_or_mentions(self):
        malicious = {**self.vulnerability, "PkgName": "<script>@maintainer|[click](url)`\n"}
        self.write_report(self.head, [malicious])
        text = self.render()
        self.assertNotIn("<script>", text)
        self.assertNotIn("@maintainer", text)
        self.assertNotIn("[click]", text)
        self.assertIn("&lt;script&gt;", text)

    def test_manifest_paths_cannot_escape_the_artifact_directory(self):
        for name in ("../outside", "/absolute", "duplicate"):
            targets = [{**self.target, "id": name}]
            if name == "duplicate":
                targets *= 2
            with self.subTest(name=name):
                self.write_context(self.head, "head", targets)
                with self.assertRaises(ValueError):
                    report.context(self.head, "head")

    def test_credential_skip_is_visible_and_not_resolved(self):
        target = {**self.target, "requires_auth": True, "enabled": False}
        self.write_context(self.head, "head", [target])
        self.write_report(self.head, [], state="skipped")
        self.assertIn("0 completed, 1 skipped, 0 failed", self.render())


if __name__ == "__main__":
    unittest.main()
