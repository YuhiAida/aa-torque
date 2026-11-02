#!/usr/bin/env python3
"""Tests for tools/release_notes.py."""
import os
import subprocess
import tempfile
import unittest


SCRIPT = os.path.join(os.path.dirname(__file__), "release_notes.py")


def git(cwd, *args):
    subprocess.run(["git", *args], cwd=cwd, check=True,
                   capture_output=True, text=True)


def make_repo(commits):
    """A repo with tag 1.0.0 and one commit per message."""
    d = tempfile.mkdtemp()
    git(d, "init", "-q")
    git(d, "config", "user.email", "t@t.com")
    git(d, "config", "user.name", "t")
    path = os.path.join(d, "f.txt")
    with open(path, "w") as f:
        f.write("x\n")
    git(d, "add", "f.txt")
    git(d, "commit", "-q", "-m", "chore: initial")
    git(d, "tag", "1.0.0")
    for message in commits:
        with open(path, "a") as f:
            f.write(message + "\n")
        git(d, "add", "f.txt")
        git(d, "commit", "-q", "-m", message)
    return d


def add_pull_request(repo, number, message):
    """Commit on a branch, then merge it the way GitHub does."""
    git(repo, "checkout", "-q", "-b", f"pr-{number}")
    path = os.path.join(repo, "f.txt")
    with open(path, "a") as f:
        f.write(message + "\n")
    git(repo, "add", "f.txt")
    git(repo, "commit", "-q", "-m", message)
    git(repo, "checkout", "-q", "master")
    git(repo, "merge", "-q", "--no-ff", f"pr-{number}",
        "-m", f"Merge pull request #{number} from t/pr-{number}")


def run(repo, *args):
    return subprocess.run(
        ["python3", SCRIPT, "--cwd", repo, *args],
        check=True, capture_output=True, text=True).stdout


class ReleaseNotesTest(unittest.TestCase):
    def test_head_fields(self):
        repo = make_repo(["feat: add a thing", "fix: solve it"])
        body = run(repo, "--tag", "1.0.0", "--version", "1.1.0",
                   "--date", "November 2, 2026")
        self.assertIn("# AA Torque v1.1.0", body)
        self.assertIn("**Release Date:** November 2, 2026", body)
        self.assertIn("**Full Changelog**: [1.0.0...1.1.0]", body)

    def test_sections_and_cleaned_subjects(self):
        repo = make_repo(["feat(settings): add a thing", "fix: solve it"])
        body = run(repo, "--tag", "1.0.0", "--version", "1.1.0")
        self.assertIn("## ✨ Features", body)
        self.assertIn("- Add a thing", body)
        self.assertIn("## 🐛 Bug Fixes", body)
        self.assertIn("- Solve it", body)

    def test_merge_commit_becomes_a_pull_request_line(self):
        repo = make_repo(["fix: solve it"])
        add_pull_request(repo, 12, "feat: add a card")
        body = run(repo, "--tag", "1.0.0", "--version", "1.1.0")
        self.assertIn("- Add a card ([#12](https://github.com/YuhiAida/aa-torque/pull/12))",
                      body)
        self.assertIn("1 merged pull request", body)

    def test_summary_counts(self):
        repo = make_repo(["feat: add a thing", "fix: solve it", "ci: tune it"])
        body = run(repo, "--tag", "1.0.0", "--version", "1.1.0")
        self.assertIn("> Minor release: 1 feature, 1 fix and 1 build and CI change since 1.0.0.", body)

    def test_dev_variant(self):
        repo = make_repo(["fix: solve it"])
        body = run(repo, "--tag", "1.0.0", "--version", "1.0.0-dev.3", "--dev")
        self.assertIn("aa-torque-dev-1.0.0-dev.3.apk", body)
        self.assertIn("com.aatorque.stats.debug", body)
        self.assertNotIn("aa-torque-1.0.0-dev.3.apk` over", body)

    def test_out_writes_the_file(self):
        repo = make_repo(["fix: solve it"])
        out = os.path.join(repo, "notes.md")
        run(repo, "--tag", "1.0.0", "--version", "1.0.1", "--out", out)
        with open(out) as handle:
            self.assertIn("# AA Torque v1.0.1", handle.read())


if __name__ == "__main__":
    unittest.main()
