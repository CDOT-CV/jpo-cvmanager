"""Check coverage gaps and report-only findings, not upstream tool behavior."""

import json
from pathlib import Path
import tempfile
import unittest

import cve_coverage as coverage


class CoverageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.target = {"id": "fixture", "build": "filesystem", "expected_type": "npm"}
        self.folder = self.root / "fixture"
        self.folder.mkdir()

    def write(self, name, data):
        (self.folder / name).write_text(json.dumps(data), encoding="utf-8")

    def reports(self, packages=True):
        for name in coverage.REQUIRED:
            (self.folder / name).write_text("report", encoding="utf-8")
        self.write("results.json", {"Results": [{"Type": "npm", "Packages": [{"Name": "fixture"}] if packages else [],
                                                "Vulnerabilities": [{"Severity": "CRITICAL", "FixedVersion": ""}]}]})
        self.write("status.json", {"status": "completed"})

    def test_inventory_rejects_new_and_untracked_dockerfiles(self):
        items = [{"id": "image", "dockerfile": "Dockerfile"}]
        for tracked in ([], ["Dockerfile", "services/Dockerfile.new"]):
            with self.subTest(tracked=tracked), self.assertRaisesRegex(ValueError, "coverage mismatch"):
                coverage.matrix(items, tracked, False)

    def test_inventory_rejects_duplicate_ids(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            coverage.matrix([self.target, self.target], [], False)

    def test_authentication_changes_only_authenticated_target_eligibility(self):
        items = [{"id": "public"}, {"id": "private", "requires_auth": True}]
        self.assertEqual([t["enabled"] for t in coverage.matrix(items, [], False)["include"]], [True, False])
        self.assertTrue(all(t["enabled"] for t in coverage.matrix(items, [], True)["include"]))

    def test_unfixed_critical_findings_do_not_fail_coverage(self):
        self.reports()
        text, failed = coverage.coverage([self.target], self.root, False)
        self.assertFalse(failed)
        self.assertIn("CRITICAL: 1", text)

    def test_missing_and_empty_reports_fail(self):
        self.reports()
        for content in (None, ""):
            with self.subTest(content=content):
                path = self.folder / "results.sarif"
                if content is None:
                    path.unlink()
                else:
                    path.write_text(content)
                self.assertTrue(coverage.coverage([self.target], self.root, False)[1])

    def test_missing_status_or_failed_execution_cannot_pass(self):
        self.reports()
        (self.folder / "status.json").unlink()
        self.assertTrue(coverage.coverage([self.target], self.root, False)[1])
        self.write("status.json", {"status": "failed"})
        self.assertTrue(coverage.coverage([self.target], self.root, False)[1])

    def test_empty_or_wrong_package_type_fails(self):
        self.reports(packages=False)
        self.assertTrue(coverage.coverage([self.target], self.root, False)[1])
        self.write("results.json", {"Results": [{"Type": "debian", "Packages": [{"Name": "libc6"}]}]})
        self.assertTrue(coverage.coverage([self.target], self.root, False)[1])

    def test_only_untrusted_authenticated_target_may_skip(self):
        self.write("status.json", {"status": "skipped"})
        self.assertTrue(coverage.coverage([self.target], self.root, False)[1])
        self.target["requires_auth"] = True
        self.assertFalse(coverage.coverage([self.target], self.root, False)[1])
        self.assertTrue(coverage.coverage([self.target], self.root, True)[1])

    def test_wrong_image_architecture_fails(self):
        self.reports()
        self.target["build"] = "docker"
        self.write("image.json", {"Os": "linux", "Architecture": "arm64"})
        self.assertTrue(coverage.coverage([self.target], self.root, False)[1])
        self.write("image.json", {"Os": "linux", "Architecture": "amd64"})
        self.assertFalse(coverage.coverage([self.target], self.root, False)[1])

    def test_eol_warning_is_visible_without_failing_findings(self):
        self.reports()
        report = json.loads((self.folder / "results.json").read_text())
        report["Metadata"] = {"OS": {"EOSL": True}}
        self.write("results.json", report)
        summary, failed = coverage.coverage([self.target], self.root, False)
        self.assertFalse(failed)
        self.assertIn("EOL operating system", summary)


if __name__ == "__main__":
    unittest.main()
