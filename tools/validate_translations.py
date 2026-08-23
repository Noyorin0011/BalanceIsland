#!/usr/bin/env python3
"""Validate one translated Android strings.xml against a source file."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PLACEHOLDER = re.compile(r"%(?:\d+\$)?[a-zA-Z]|%%")


def load_strings(path: Path) -> tuple[dict[str, str], list[str]]:
    root = ET.parse(path).getroot()
    values: dict[str, str] = {}
    duplicates: list[str] = []
    for item in root.findall("string"):
        name = item.attrib.get("name", "")
        if name in values:
            duplicates.append(name)
        values[name] = "".join(item.itertext())
    return values, duplicates


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: python3 validate_translations.py SOURCE_XML TARGET_XML")
        return 2

    source_path = Path(sys.argv[1])
    target_path = Path(sys.argv[2])
    try:
        source, source_duplicates = load_strings(source_path)
        target, target_duplicates = load_strings(target_path)
    except (OSError, ET.ParseError) as error:
        print(f"FAIL: XML could not be read: {error}")
        return 1

    errors: list[str] = []
    if source_duplicates:
        errors.append(f"source duplicate keys: {sorted(set(source_duplicates))}")
    if target_duplicates:
        errors.append(f"target duplicate keys: {sorted(set(target_duplicates))}")

    missing = sorted(source.keys() - target.keys())
    extra = sorted(target.keys() - source.keys())
    if missing:
        errors.append(f"missing keys: {missing}")
    if extra:
        errors.append(f"extra keys: {extra}")

    for key in sorted(source.keys() & target.keys()):
        source_placeholders = PLACEHOLDER.findall(source[key])
        target_placeholders = PLACEHOLDER.findall(target[key])
        if source_placeholders != target_placeholders:
            errors.append(
                f"{key}: placeholders {target_placeholders} != {source_placeholders}"
            )

    if "configure_api" in target and not target["configure_api"].startswith(" "):
        errors.append("configure_api: required leading space is missing")

    if errors:
        print("FAIL")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"PASS: {len(target)} resource keys validated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
