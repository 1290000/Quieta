"""Regression coverage for documentation storage policy."""

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from check_repo_hygiene import DOC_ASSETS, violation


class RepoHygieneTest(unittest.TestCase):
    def test_documents_and_approved_assets(self):
        for path in ["docs/README.md", "docs/design/topic.md", *DOC_ASSETS]:
            with self.subTest(path=path):
                self.assertIsNone(violation(path))

    def test_temporary_and_unapproved_docs(self):
        for path in [
            "docs/home.xml", "docs/home.PNG", "docs/demo.mp4",
            "docs/video-frames/frame.jpg", "docs/icon-concepts/draft.png",
            "docs/installerx-ref/Animation.kt", "docs/installerx-ref/tree.json",
            "docs/unused.py", "DOCS/screenshot.png",
        ]:
            with self.subTest(path=path):
                self.assertIsNotNone(violation(path))

    def test_local_artifacts_even_when_forced_into_index(self):
        for path in ["artifacts/frame.png", "artifacts/notes.md", "Artifacts/ui.xml"]:
            with self.subTest(path=path):
                self.assertIsNotNone(violation(path))

    def test_application_resources_are_not_docs(self):
        for path in ["app/src/main/res/layout/main.xml", "ui/src/main/Test.kt"]:
            with self.subTest(path=path):
                self.assertIsNone(violation(path))

    def test_real_index_rejects_forced_artifacts(self):
        root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            env = dict(os.environ, GIT_INDEX_FILE=str(Path(temporary) / "index"))

            def git(*args, **kwargs):
                return subprocess.run(
                    ["git", *args], cwd=root, env=env, check=True,
                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs,
                )

            git("read-tree", "--empty")
            blob = git("rev-parse", "HEAD:README.md").stdout.decode().strip()
            git("update-index", "--add", "--cacheinfo", "100644", blob, "docs/guide.md")
            command = [sys.executable, str(root / "scripts/check_repo_hygiene.py")]
            allowed = subprocess.run(command, env=env, capture_output=True)
            self.assertEqual(allowed.returncode, 0, allowed.stderr)
            for path in ["docs/forced.xml", "artifacts/forced.md"]:
                git("update-index", "--add", "--cacheinfo", "100644", blob, path)
            rejected = subprocess.run(command, env=env, capture_output=True)
            self.assertEqual(rejected.returncode, 1)
            self.assertIn(b"docs/forced.xml", rejected.stderr)
            self.assertIn(b"artifacts/forced.md", rejected.stderr)


if __name__ == "__main__":
    unittest.main()
