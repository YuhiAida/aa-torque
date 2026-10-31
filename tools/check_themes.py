#!/usr/bin/env python3
"""Check that every app theme is complete and internally consistent.

The XML parsed here is repository-owned source (``res/values/*.xml``), not
untrusted input, so stdlib parsing is safe: there are no external entities to
resolve and the files are committed and reviewed.

A theme is four things that must agree by index:

  * a display name in the ``Themes`` array,
  * a thumbnail in ``ThemesThumbs``,
  * a style name in ``ThemeList``,
  * the style itself in ``res/values/styles.xml``.

``mapTheme()`` resolves a theme by looking up its name in ``Themes`` and reading
``ThemeList`` at the same index, so a single missing or misplaced entry silently
selects the wrong style. This checker fails when:

  1. the three arrays have different lengths,
  2. a style named in ``ThemeList`` is not defined,
  3. a style parent is not defined,
  4. a theme style does not set an attribute every theme needs,
  5. a style or thumbnail references a ``@drawable``/``@color`` that does not exist.

Stdlib only, no network. Exit code 0 when clean, 1 otherwise.

Usage:
    python3 tools/check_themes.py
"""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
ARRAYS_XML = RES / "values/arrays.xml"
STYLES_XML = RES / "values/styles.xml"
ARRAY_NAMES = ("Themes", "ThemesThumbs", "ThemeList")
# The attributes every theme sets today. Inheritance from AppTheme does not help here:
# these values differ per theme, so each one must be present in the theme style itself.
REQUIRED_ATTRS = (
    "themedBlankDialBackground",
    "themedDialBackground",
    "themedEmptyDialBackground",
    "themedNeedle",
    "themedNeedleColor",
    "themedStopWatchBackground",
    "sv_tickPadding",
    "sv_tickRotation",
)
RESOURCE_REF = re.compile(r"@(drawable|color|style)/([\w.]+)")
DRAWABLE_SUFFIXES = (".png", ".webp", ".jpg", ".jpeg", ".9.png", ".xml")


def read_array(array_name: str) -> list[str]:
    root = ET.parse(ARRAYS_XML).getroot()
    for element in root:
        if element.get("name") == array_name:
            return [(item.text or "").strip() for item in element.findall("item")]
    return []


def read_styles() -> dict[str, dict[str, str]]:
    root = ET.parse(STYLES_XML).getroot()
    styles: dict[str, dict[str, str]] = {}
    for style in root.findall("style"):
        name = style.get("name")
        if name is None:
            continue
        entry = {"parent": style.get("parent") or ""}
        for item in style.findall("item"):
            attr = item.get("name")
            if attr is None:
                continue
            entry[attr] = (item.text or "").strip()
        styles[name] = entry
    return styles


def read_color_names() -> set[str]:
    names: set[str] = set()
    for colors in RES.glob("values*/colors.xml"):
        for color in ET.parse(colors).getroot().findall("color"):
            name = color.get("name")
            if name:
                names.add(name)
    return names


def find_drawable(name: str) -> bool:
    for directory in RES.glob("drawable*"):
        if not directory.is_dir():
            continue
        for suffix in DRAWABLE_SUFFIXES:
            if (directory / f"{name}{suffix}").exists():
                return True
    return False


def main() -> int:
    arrays = {name: read_array(name) for name in ARRAY_NAMES}
    styles = read_styles()
    colors = read_color_names()
    problems: list[str] = []

    lengths = {name: len(arrays[name]) for name in ARRAY_NAMES}
    if len(set(lengths.values())) != 1:
        problems.append(f"theme arrays have different lengths: {lengths}")

    for index, style_ref in enumerate(arrays["ThemeList"]):
        display = arrays["Themes"][index] if index < len(arrays["Themes"]) else "?"
        style_name = style_ref.removeprefix("@style/")
        if style_name not in styles:
            problems.append(f"[{index}] {display}: style {style_ref} is not defined")
            continue
        style = styles[style_name]

        parent = style["parent"].removeprefix("@style/")
        if parent and parent not in styles and not parent.startswith("@"):
            problems.append(f"[{index}] {display}: parent {style['parent']} is not defined")

        for attr in REQUIRED_ATTRS:
            if attr not in style:
                problems.append(f"[{index}] {display}: missing {attr}")

        for kind, name in RESOURCE_REF.findall(" ".join(style.values())):
            if kind == "drawable" and not find_drawable(name):
                problems.append(f"[{index}] {display}: drawable @{name} does not exist")
            elif kind == "color" and name not in colors:
                problems.append(f"[{index}] {display}: color @{name} does not exist")
            elif kind == "style" and name not in styles:
                problems.append(f"[{index}] {display}: style @{name} does not exist")

    for index, thumb in enumerate(arrays["ThemesThumbs"]):
        match = RESOURCE_REF.fullmatch(thumb)
        if match and match.group(1) == "drawable" and not find_drawable(match.group(2)):
            problems.append(f"[{index}] thumbnail {thumb} does not exist")

    if problems:
        print(f"{len(problems)} theme problem(s):")
        for problem in problems:
            print(f"  {problem}")
        return 1
    print(f"checked {len(arrays['ThemeList'])} themes, no problems")
    return 0


if __name__ == "__main__":
    sys.exit(main())
