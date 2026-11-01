#!/usr/bin/env python3
"""Tests for tools/next_release.py."""
import json
import os
import subprocess
import tempfile
import unittest


SCRIPT = os.path.join(os.path.dirname(__file__), "next_release.py")


def git(cwd, *args):
    subprocess.run(["git", *args], cwd=cwd, check=True,
                   capture_output=True, text=True)


def make_repo(messages):
    d = tempfile.mkdtemp()
    git(d, "init", "-q")
    git(d, "config", "user.email", "t@t.com")
    git(d, "config", "user.name", "t")
    path = os.path.join(d, "f.txt")
    with open(path, "w") as f:
        f.write("x")
    git(d, "add", "f.txt")
    git(d, "commit", "-q", "-m", "chore: initial")
    git(d, "tag", "1.0.0")
    for m in messages:
        with open(path, "a") as f:
            f.write(m + "\n")
        git(d, "add", "f.txt")
        git(d, "commit", "-q", "-m", m)
    return d


def make_repo_with_body(commits):
    d = tempfile.mkdtemp()
    git(d, "init", "-q")
    git(d, "config", "user.email", "t@t.com")
    git(d, "config", "user.name", "t")
    path = os.path.join(d, "f.txt")
    with open(path, "w") as f:
        f.write("x")
    git(d, "add", "f.txt")
    git(d, "commit", "-q", "-m", "chore: initial")
    git(d, "tag", "1.0.0")
    for subject, body in commits:
        with open(path, "a") as f:
            f.write(subject + "\n")
        git(d, "add", "f.txt")
        if body:
            git(d, "commit", "-q", "-m", subject, "-m", body)
        else:
            git(d, "commit", "-q", "-m", subject)
    return d


class NextReleaseTests(unittest.TestCase):
    def run_script(self, repo, *extra):
        r = subprocess.run(
            ["python3", SCRIPT, "--tag", "1.0.0", "--head", "HEAD", *extra],
            cwd=repo, capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr)
        return r.stdout.strip()

    def test_feat_bang_major(self):
        repo = make_repo(["feat!: break the api"])
        self.assertEqual(self.run_script(repo, "--print"), "2.0.0")

    def test_feat_minor(self):
        repo = make_repo(["feat: add thing"])
        self.assertEqual(self.run_script(repo, "--print"), "1.1.0")

    def test_fix_patch(self):
        repo = make_repo(["fix: bug"])
        self.assertEqual(self.run_script(repo, "--print"), "1.0.1")

    def test_json_output(self):
        repo = make_repo(["feat: add thing", "fix: bug"])
        out = self.run_script(repo, "--json")
        data = json.loads(out)
        self.assertEqual(data["version"], "1.1.0")
        self.assertEqual(data["bump"], "minor")
        self.assertIsInstance(data["changes"], list)

    def test_stray_bang_patch(self):
        repo = make_repo(["fix: oops!"])
        self.assertEqual(self.run_script(repo, "--print"), "1.0.1")

    def test_scoped_feat_minor(self):
        repo = make_repo(["feat(api): add thing"])
        self.assertEqual(self.run_script(repo, "--print"), "1.1.0")

    def test_breaking_change_body_major(self):
        repo = make_repo_with_body([("fix: something", "BREAKING CHANGE: removed X")])
        self.assertEqual(self.run_script(repo, "--print"), "2.0.0")


if __name__ == "__main__":
    unittest.main()
