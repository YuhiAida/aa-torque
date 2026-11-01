#!/usr/bin/env python3
"""Compute the next release version from conventional commits since a tag.

Usage:
    python3 tools/next_release.py --tag <last> --head HEAD --print
    python3 tools/next_release.py --tag <last> --head HEAD --json
"""
import argparse
import json
import re
import subprocess
import sys


def git(*args, cwd=None):
    return subprocess.check_output(
        ["git", *args], cwd=cwd, text=True, stderr=subprocess.DEVNULL)


def commits(tag, head, cwd=None):
    raw = git("log", "--format=%s%x1f%B%x1e", f"{tag}..{head}", cwd=cwd)
    out = []
    for rec in raw.split("\x1e"):
        if not rec.strip():
            continue
        parts = rec.split("\x1f", 1)
        subject = parts[0].strip()
        body = parts[1].strip() if len(parts) > 1 else ""
        out.append((subject, body))
    return out


def merge_subjects(tag, head, cwd=None):
    raw = git("log", "--merges", "--format=%s", f"{tag}..{head}", cwd=cwd)
    return [line for line in raw.splitlines() if line.strip()]


def classify(tag, head, cwd=None):
    cs = commits(tag, head, cwd=cwd)
    major = any(("!" in s) or ("BREAKING CHANGE" in b) for s, b in cs)
    minor = (not major) and any(s.startswith("feat:") for s, b in cs)
    if major:
        return "major"
    if minor:
        return "minor"
    return "patch"


def parse_version(tag):
    t = tag.strip()
    if t[:1].lower() == "v":
        t = t[1:]
    m = re.match(r"(\d+)\.(\d+)\.(\d+)", t)
    if not m:
        raise ValueError(f"cannot parse version from tag: {tag}")
    return [int(x) for x in m.groups()]


def bump_version(tag, bump):
    nums = parse_version(tag)
    if bump == "major":
        nums[0] += 1
        nums[1] = 0
        nums[2] = 0
    elif bump == "minor":
        nums[1] += 1
        nums[2] = 0
    else:
        nums[2] += 1
    return ".".join(str(n) for n in nums)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--head", default="HEAD")
    ap.add_argument("--print", dest="print_", action="store_true")
    ap.add_argument("--json", dest="json_", action="store_true")
    ap.add_argument("--cwd", default=None)
    args = ap.parse_args()

    cwd = args.cwd
    b = classify(args.tag, args.head, cwd=cwd)
    version = bump_version(args.tag, b)
    changes = merge_subjects(args.tag, args.head, cwd=cwd)

    if args.json_:
        print(json.dumps({"version": version, "bump": b, "changes": changes}))
    else:
        print(version)
    return 0


if __name__ == "__main__":
    sys.exit(main())
