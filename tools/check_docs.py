#!/usr/bin/env python3
"""Check the markdown in this repository for broken references.

Two kinds of reference are verified:

1. Relative links and images: ``[text](docs/building.md)``, ``![alt](img/x.png)``.
   Absolute URLs, mail links and pure anchors are ignored.
2. Repository paths written in backticks, such as ``app/src/main/...`` or
   ``SettingsActivity.kt``. A token is treated as a path when it contains a slash or ends
   with a known source/document extension; anything with spaces, globs or placeholders is
   skipped so prose stays possible.

Exit code 0 when everything resolves, 1 otherwise. Stdlib only, no network.

Usage:
    python3 tools/check_docs.py
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MARKDOWN_GLOBS = ("*.md", "docs/**/*.md")
# Generated output is machine-written and its paths come from the sources it lists.
SKIP_PATHS = (pathlib.PurePosixPath("docs/generated"),)

LINK_PATTERN = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
INLINE_CODE_PATTERN = re.compile(r"`([^`\n]+)`")
PATH_EXTENSIONS = (
    ".md", ".kt", ".java", ".gradle", ".properties", ".yml", ".yaml",
    ".py", ".proto", ".aidl", ".xml", ".bat", ".ini", ".json",
)
EXTERNAL_PREFIXES = ("http://", "https://", "mailto:", "#", "tel:", "git@")
# Paths in the docs are often written relative to a source root, or are outputs that only
# exist after a build. A candidate is resolved against each of these before it counts as broken.
RESOLVE_ROOTS = ("", "app/src/main/java", "app/src/main/java/com/aatorque", "app/src/main", "app")
# Generated or machine-local, so they legitimately do not exist in a clone.
IGNORED_PREFIXES = ("app/build/", "build/", "dist/", "lib/speedviewlib")
IGNORED_PATHS = {"local.properties", "google-services.json", ".env"}
# "owner/repo" and "owner/repo@v1.2.3" are GitHub coordinates, not paths.
GITHUB_HANDLE = re.compile(r"^[\w.-]+/[\w.-]+(@[\w.]+)?$")


def markdown_files() -> list[pathlib.Path]:
    files: set[pathlib.Path] = set()
    for pattern in MARKDOWN_GLOBS:
        files.update(ROOT.glob(pattern))
    return sorted(
        path for path in files
        if not any(path.relative_to(ROOT).as_posix().startswith(str(prefix))
                   for prefix in SKIP_PATHS)
    )


def known_basenames() -> set[str]:
    return {
        path.name
        for path in ROOT.rglob("*")
        if path.is_file() and ".git/" not in path.as_posix()
    }


def check_links(path: pathlib.Path, names: set[str]) -> list[str]:
    problems = []
    text = path.read_text(encoding="utf-8")
    for target in LINK_PATTERN.findall(text):
        clean = target.split("#", 1)[0].strip()
        if not clean or clean.startswith(EXTERNAL_PREFIXES):
            continue
        if (path.parent / clean).exists() or (ROOT / clean).exists():
            continue
        problems.append(f"{path.relative_to(ROOT)}: broken link -> {clean}")
    return problems


def looks_like_path(token: str) -> bool:
    if any(bad in token for bad in (" ", "<", ">", "*", "$", "...", "\\")):
        return False
    if token.startswith(EXTERNAL_PREFIXES) or token.startswith("~"):
        return False
    if token in IGNORED_PATHS or token.startswith(IGNORED_PREFIXES):
        return False
    # A bare extension such as `.md` in prose is not a path.
    if re.fullmatch(r"\.\w+", token):
        return False
    if "/" in token:
        if "/" == token[0]:
            return False
        # owner/repo (no file extension in the last segment) is a GitHub coordinate.
        if GITHUB_HANDLE.match(token) and not token.rsplit("/", 1)[-1].endswith(PATH_EXTENSIONS):
            return False
        return True
    return token.endswith(PATH_EXTENSIONS)


def resolves(candidate: str, doc: pathlib.Path) -> bool:
    for root in RESOLVE_ROOTS:
        if (ROOT / root / candidate).exists():
            return True
    return (doc.parent / candidate).exists()


def check_inline_paths(path: pathlib.Path, names: set[str]) -> list[str]:
    problems = []
    text = path.read_text(encoding="utf-8")
    for token in INLINE_CODE_PATTERN.findall(text):
        candidate = token.strip()
        if not looks_like_path(candidate):
            continue
        if resolves(candidate, path):
            continue
        if "/" not in candidate and candidate in names:
            continue
        problems.append(f"{path.relative_to(ROOT)}: missing path -> {candidate}")
    return problems


def main() -> int:
    names = known_basenames()
    problems: list[str] = []
    files = markdown_files()
    for path in files:
        problems.extend(check_links(path, names))
        problems.extend(check_inline_paths(path, names))

    if problems:
        print(f"{len(problems)} broken reference(s):")
        for problem in problems:
            print(f"  {problem}")
        return 1
    print(f"checked {len(files)} markdown files, no broken references")
    return 0


if __name__ == "__main__":
    sys.exit(main())
