#!/usr/bin/env python3
"""Build a release body from conventional commits since the last tag.

The body follows the shape of the Hermes Agent releases: a title, a release date, a
one-line summary, measured stats, one section per commit type, an updating note and a
full-changelog link.

Usage:
    python3 tools/release_notes.py --tag 2.4.0 --head HEAD --version 2.4.1 --print
    python3 tools/release_notes.py --tag 2.4.0 --head HEAD --version 2.4.1 --out notes.md
    python3 tools/release_notes.py --tag 2.4.0 --version 2.4.0-dev.7 --dev --print
"""
import argparse
import datetime
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import next_release  # noqa: E402

REPO_URL = "https://github.com/YuhiAida/aa-torque"
MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August",
          "September", "October", "November", "December"]

SECTIONS = [
    ("breaking", "⚠️ Breaking Changes"),
    ("features", "✨ Features"),
    ("improvements", "🔧 Improvements"),
    ("fixes", "🐛 Bug Fixes"),
    ("docs", "📚 Documentation"),
    ("tests", "🧪 Tests"),
    ("build_ci", "🏗️ Build and CI"),
    ("chores", "🧹 Chores"),
    ("other", "📦 Other Changes"),
]

LABELS = {
    "breaking": ("breaking change", "breaking changes"),
    "features": ("feature", "features"),
    "improvements": ("improvement", "improvements"),
    "fixes": ("fix", "fixes"),
    "docs": ("documentation change", "documentation changes"),
    "tests": ("test change", "test changes"),
    "build_ci": ("build and CI change", "build and CI changes"),
    "chores": ("chore", "chores"),
    "other": ("other change", "other changes"),
}

PREFIX = re.compile(
    r"^(feat|fix|docs?|chore|refactor|tests?|perf|ci|build|style|revert|deps|bump)"
    r"(\([^)]*\))?!?:\s*",
    re.IGNORECASE,
)
MERGE = re.compile(r"^Merge pull request #(\d+) from ")
CONVENTIONAL = re.compile(r"^[a-z]+(\([^)]*\))?!?:\s*", re.IGNORECASE)


def format_date(value):
    """Format a YYYY-MM-DD date as 'November 2, 2026'; keep any other text as it is."""
    if value and re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        day = datetime.date.fromisoformat(value)
        return f"{MONTHS[day.month - 1]} {day.day}, {day.year}"
    return value


def git(*args, cwd=None):
    return subprocess.check_output(
        ["git", *args], cwd=cwd, text=True, stderr=subprocess.DEVNULL)


def pull_request_title(slug, number, cwd=None):
    """Ask the GitHub API for the pull request title; None when it cannot answer."""
    if not slug:
        return None
    try:
        out = subprocess.run(
            ["gh", "api", f"repos/{slug}/pulls/{number}", "--jq", ".title"],
            cwd=cwd, text=True, capture_output=True, timeout=20)
    except (OSError, subprocess.TimeoutExpired):
        return None
    title = out.stdout.strip()
    return title if out.returncode == 0 and title else None


def clean_subject(subject):
    """Drop one conventional prefix and capitalize, as the Hermes notes do."""
    subject = PREFIX.sub("", subject, count=1).strip()
    if subject:
        subject = subject[0].upper() + subject[1:]
    return subject


def categorize(subject, body=""):
    """Map a commit subject to one section key."""
    lowered = subject.lower()
    if "breaking change" in body.lower() or re.match(r"^[a-z]+(\([^)]*\))?!:", lowered):
        return "breaking"
    if re.match(r"^feat(\([^)]*\))?!?:", lowered):
        return "features"
    if re.match(r"^fix(\([^)]*\))?!?:", lowered):
        return "fixes"
    if re.match(r"^(perf|refactor|revert)(\([^)]*\))?!?:", lowered):
        return "improvements"
    if re.match(r"^docs?(\([^)]*\))?!?:", lowered):
        return "docs"
    if re.match(r"^tests?(\([^)]*\))?!?:", lowered):
        return "tests"
    if re.match(r"^(ci|build|deps|bump)(\([^)]*\))?!?:", lowered):
        return "build_ci"
    if re.match(r"^(chore|style)(\([^)]*\))?!?:", lowered):
        return "chores"
    return "other"


def entries(tag, head, cwd=None, slug=None):
    """Yield one entry per merged PR, plus direct commits, newest first.

    A merge commit carries the PR number; its second parent carries the real subject.
    With a repository slug the pull request title is preferred, because it is written for
    the release notes while a branch commit may describe the mechanics.
    """
    raw = git("log", "--first-parent", "--format=%H%x1f%s%x1f%b%x1e",
              f"{tag}..{head}", cwd=cwd)
    for record in raw.split("\x1e"):
        if not record.strip():
            continue
        parts = record.split("\x1f", 2)
        sha = parts[0].strip()
        subject = parts[1].strip() if len(parts) > 1 else ""
        body = parts[2].strip() if len(parts) > 2 else ""
        pr = None
        match = MERGE.match(subject)
        if match:
            pr = match.group(1)
            try:
                real = git("log", "-1", "--format=%s%x1f%b", f"{sha}^2", cwd=cwd)
                real_parts = real.split("\x1f", 1)
                subject = real_parts[0].strip()
                body = real_parts[1].strip() if len(real_parts) > 1 else ""
            except subprocess.CalledProcessError:
                pass
        if not subject:
            continue
        category = categorize(subject, body)
        if pr:
            title = pull_request_title(slug, pr, cwd=cwd)
            if title:
                subject = title
                if CONVENTIONAL.match(title):
                    category = categorize(title)
        yield {
            "sha": sha,
            "short": sha[:7],
            "pr": pr,
            "subject": subject,
            "category": category,
        }


def shortstat(tag, head, cwd=None):
    """Return (files, insertions, deletions) for the range."""
    try:
        out = git("diff", "--shortstat", f"{tag}..{head}", cwd=cwd).strip()
    except subprocess.CalledProcessError:
        return 0, 0, 0
    files = re.search(r"(\d+) files? changed", out)
    adds = re.search(r"(\d+) insertions?\(\+\)", out)
    dels = re.search(r"(\d+) deletions?\(-\)", out)
    return (
        int(files.group(1)) if files else 0,
        int(adds.group(1)) if adds else 0,
        int(dels.group(1)) if dels else 0,
    )


def plural(key, count):
    one, many = LABELS[key]
    return f"{count} {one if count == 1 else many}"


def summary(bump, counts, tag):
    """One line under the date, e.g. 'Patch release: 3 fixes and 1 CI change since 2.4.0.'"""
    parts = [plural(key, counts[key]) for key, _ in SECTIONS if counts.get(key)]
    if not parts:
        return f"{bump.capitalize()} release since {tag}."
    if len(parts) == 1:
        listed = parts[0]
    else:
        listed = ", ".join(parts[:-1]) + " and " + parts[-1]
    return f"{bump.capitalize()} release: {listed} since {tag}."


def build(tag, head, version, release_tag=None, date=None, repo_url=REPO_URL,
          dev=False, cwd=None, repo_slug=None, version_code=None):
    """Return the markdown body for one release."""
    release_tag = release_tag or version
    date = format_date(date) or format_date(datetime.date.today().isoformat())
    sha = git("rev-parse", head, cwd=cwd).strip()
    items = list(entries(tag, head, cwd=cwd, slug=repo_slug))

    counts = {}
    for item in items:
        counts[item["category"]] = counts.get(item["category"], 0) + 1

    bump = next_release.classify(tag, head, cwd=cwd)
    files, adds, deletes = shortstat(tag, head, cwd=cwd)
    merges = sum(1 for item in items if item["pr"])
    commits = int(git("rev-list", "--count", f"{tag}..{head}", cwd=cwd).strip())

    header = f"**Release Date:** {date}"
    if version_code:
        header += f" \u00b7 versionCode {version_code}"
    lines = [header, ""]
    lines += ["> " + summary(bump, counts, tag), ""]

    lines += ["## About this release", ""]
    stat = (
        f"Measured at commit `{sha[:12]}`, the window since `{tag}` contains "
        f"{commits} commits, {merges} merged pull requests and {files} changed files "
        f"(+{adds} / −{deletes})."
    )
    lines += [stat, ""]

    for key, title in SECTIONS:
        group = [item for item in items if item["category"] == key]
        if not group:
            continue
        lines += [f"## {title}", ""]
        for item in group:
            if item["pr"]:
                link = f"([#{item['pr']}]({repo_url}/pull/{item['pr']}))"
            else:
                link = f"([`{item['short']}`]({repo_url}/commit/{item['sha']}))"
            lines.append(f"- {clean_subject(item['subject'])} {link}")
        lines.append("")

    lines += ["## 📥 Updating", ""]
    if dev:
        lines += [
            f"- Install `aa-torque-dev-{version}.apk`. Its package is "
            "`com.aatorque.stats.debug`, so it sits beside the stable app and has its own "
            "Android Auto install record.",
            "",
        ]
    else:
        lines += [
            f"- Install `aa-torque-{version}.apk` over the current app. Settings, "
            "dashboards and themes are kept.",
            "- The APK is signed with the same key as every release, so it installs over "
            "the previous version.",
            "",
        ]

    lines += [f"**Full Changelog**: [{tag}...{release_tag}]"
              f"({repo_url}/compare/{tag}...{release_tag})", ""]
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True, help="previous stable tag")
    ap.add_argument("--head", default="HEAD")
    ap.add_argument("--version", required=True, help="version this release ships")
    ap.add_argument("--release-tag", default=None, help="tag for the compare link")
    ap.add_argument("--date", default=None)
    ap.add_argument("--repo-url", default=REPO_URL)
    ap.add_argument("--repo-slug", default=None,
                    help="owner/name; ask the API for pull request titles")
    ap.add_argument("--version-code", default=None,
                    help="show the versionCode next to the release date")
    ap.add_argument("--dev", action="store_true")
    ap.add_argument("--out", default=None)
    ap.add_argument("--print", dest="print_", action="store_true")
    ap.add_argument("--cwd", default=None)
    args = ap.parse_args()

    body = build(args.tag, args.head, args.version, args.release_tag, args.date,
                 args.repo_url, args.dev, args.cwd, args.repo_slug,
                 args.version_code)
    if args.out:
        with open(args.out, "w") as handle:
            handle.write(body + "\n")
    if args.print_ or not args.out:
        print(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
