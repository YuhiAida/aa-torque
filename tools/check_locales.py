#!/usr/bin/env python3
"""Check that every translation still covers the base strings.

The base file is ``app/src/main/res/values/strings.xml``. A locale may lag
behind, but the lag must not grow: each locale has a budget of missing keys in
``tools/locale_baseline.txt``, and this checker fails when a locale misses more
keys than its budget allows.

A key added to the base file after the baseline was taken is not charged to any
locale, so adding a string does not fail every other pull request. The baseline
records the base key count in its ``base-keys`` line, and that count raises the
allowance. A new key needs no edit to the numbers in that file, so two pull
requests cannot conflict there.

Filling keys never fails: a locale may sit below its budget until the budget is
lowered. Lower the number in the same change that adds the translations, so the
gap can only shrink.

The XML parsed here is repository-owned source, not untrusted input, so stdlib
parsing is safe: no external entities, and the files are committed and reviewed.

Two things are reported but do not fail the run:

  * a key a locale has that the base file lost,
  * a base key marked ``translatable="false"``, which no locale needs.

Stdlib only, no network. Exit code 0 when clean, 1 otherwise.

Usage:
    python3 tools/check_locales.py            # verify
    python3 tools/check_locales.py --print    # show the current counts
"""

import argparse
import pathlib
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
BASE = RES / "values/strings.xml"
BASELINE = ROOT / "tools/locale_baseline.txt"


def read_keys(path: pathlib.Path) -> tuple[set[str], set[str]]:
    """Return (translatable keys, keys the file holds at all) for one strings.xml."""
    root = ET.parse(path).getroot()
    translatable: set[str] = set()
    present: set[str] = set()
    for element in root:
        if element.tag != "string":
            continue
        name = element.get("name")
        if not name:
            continue
        present.add(name)
        if element.get("translatable") == "false":
            continue
        translatable.add(name)
    return translatable, present


def read_baseline() -> tuple[dict[str, int], int]:
    """Return (locale budgets, the base key count the budgets were measured against)."""
    budgets: dict[str, int] = {}
    base_keys: int | None = None
    for line in BASELINE.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("base-keys"):
            # A missing line used to read as zero, which raised every budget by the whole
            # base key count and let the gate pass. One line, always.
            if base_keys is not None:
                raise SystemExit("tools/locale_baseline.txt has more than one base-keys line")
            base_keys = int(line.split()[1])
            continue
        locale, _, budget = line.partition(" ")
        budgets[locale] = int(budget)
    if base_keys is None:
        raise SystemExit("tools/locale_baseline.txt has no base-keys line")
    return budgets, base_keys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--print",
        action="store_true",
        help="print the counts per locale and exit 0",
    )
    args = parser.parse_args()

    base_keys, _ = read_keys(BASE)
    budgets, baseline_base_keys = read_baseline()
    # Keys added after the baseline are not charged: no locale could have them yet, and
    # charging them would make every string change edit this file.
    added_since_baseline = max(0, len(base_keys) - baseline_base_keys)

    failures: list[str] = []
    notes: list[str] = []

    locale_files = sorted(RES.glob("values-*/strings.xml"))
    if not locale_files:
        print("No locale files found under app/src/main/res.", file=sys.stderr)
        return 1

    seen: set[str] = set()
    for path in locale_files:
        locale = path.parent.name.removeprefix("values-")
        seen.add(locale)
        keys, _ = read_keys(path)
        missing = base_keys - keys
        extra = keys - base_keys
        budget = budgets.get(locale)

        if args.print:
            allowance = None if budget is None else budget + added_since_baseline
            print(f"{locale}\tmissing={len(missing)}\tbudget={allowance}")
            continue

        if budget is None:
            failures.append(
                f"{locale}: no budget in tools/locale_baseline.txt "
                f"(it misses {len(missing)} keys)"
            )
            continue
        allowance = budget + added_since_baseline
        if len(missing) > allowance:
            worst = ", ".join(sorted(missing)[:5])
            failures.append(
                f"{locale}: misses {len(missing)} keys, budget is {allowance} "
                f"({budget} plus {added_since_baseline} added since the baseline) "
                f"(for example: {worst})"
            )
        if extra:
            notes.append(f"{locale}: {len(extra)} keys the base file does not have")

    if not args.print:
        for locale in sorted(set(budgets) - seen):
            failures.append(f"{locale}: budgeted in tools/locale_baseline.txt but has no file")

        for note in notes:
            print(f"note: {note}")
        if failures:
            print("Locale check failed:", file=sys.stderr)
            for failure in failures:
                print(f"  {failure}", file=sys.stderr)
            print(
                "\nTranslate the missing keys, or raise the budget in "
                "tools/locale_baseline.txt if the gap is accepted for now.",
                file=sys.stderr,
            )
            return 1
        print(f"Locale check passed: {len(locale_files)} locales within budget.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
