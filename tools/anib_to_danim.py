#!/usr/bin/env python3
"""Convert legacy Decimation ``.anib`` timelines to explicit JSON."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


NUMBER = r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[Ee][-+]?\d+)?[FfDd]?"
FRAME = re.compile(r"^(?:(\d+)REPEAT\s+)?(?:(RAND|SWITCH|LOAD|TRYBOLT)\s+)?Frame\s*\{$")
VECTOR = re.compile(rf"^(Pos|Rot):\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*;$")
SCALAR = re.compile(rf"^Shake:\s*({NUMBER})\s*;$")
SOUND = re.compile(r"^PlaySound:\s*([^;]+)\s*;$")
BLOCK = re.compile(r"^([^{}]+?)\s*\{$")
ACTION_NAMES = {
    "RAND": "random",
    "SWITCH": "switch",
    "LOAD": "load",
    "TRYBOLT": "try_bolt",
}


def _number(value: str) -> int | float:
    result = float(value.rstrip("FfDd"))
    return int(result) if result.is_integer() else result


def parse(source: Path) -> dict:
    lines = [line.strip() for line in source.read_text(encoding="utf-8-sig", errors="replace").splitlines()]
    declared = None
    hand = 0
    is_static = False
    for line in lines:
        if line == "STATIC":
            is_static = True
        elif line.startswith("Length:"):
            declared = int(line.split(":", 1)[1].strip())
        elif line.startswith("Hand:"):
            hand = int(line.split(":", 1)[1].strip())
    if declared is None:
        raise ValueError(f"Missing Length header in {source}")

    try:
        start = lines.index("START") + 1
        end = lines.index("END", start)
    except ValueError as exc:
        raise ValueError(f"Missing START/END markers in {source}") from exc

    header_directives = [
        line.rstrip(";")
        for line in lines[: start - 1]
        if line and line != "STATIC" and not line.startswith(("Length:", "Hand:", "-"))
    ]
    frames: list[dict] = []
    pending: dict = {}
    pending_directives: list[str] = []
    index = start
    while index < end:
        line = lines[index]
        if not line or set(line) == {"-"}:
            index += 1
            continue
        if line == "Frame SKIP":
            frames.append({"index": len(frames), "skip": True})
            index += 1
            continue
        match = FRAME.match(line)
        if not match:
            scalar = SCALAR.match(line)
            sound = SOUND.match(line)
            if scalar:
                pending["shake"] = _number(scalar.group(1))
            elif sound:
                pending["sound"] = sound.group(1).strip()
            else:
                pending_directives.append(line.rstrip(";"))
            index += 1
            continue

        frame: dict = {"index": len(frames), "transforms": {}}
        frame.update(pending)
        pending = {}
        if pending_directives:
            frame["directives"] = pending_directives
            pending_directives = []
        if match.group(1):
            frame["repeat"] = int(match.group(1))
        if match.group(2):
            frame["action"] = ACTION_NAMES[match.group(2)]
        index += 1
        directives: list[str] = list(frame.pop("directives", []))
        while index < end and lines[index] != "}":
            line = lines[index]
            if not line:
                index += 1
                continue
            scalar = SCALAR.match(line)
            sound = SOUND.match(line)
            block = BLOCK.match(line)
            if scalar:
                frame["shake"] = _number(scalar.group(1))
                index += 1
            elif sound:
                frame["sound"] = sound.group(1).strip()
                index += 1
            elif block:
                name = block.group(1).strip()
                transform: dict = {}
                index += 1
                while index < end and lines[index] != "}":
                    vector = VECTOR.match(lines[index])
                    if not vector:
                        raise ValueError(
                            f"Unexpected transform line in {source}:{index + 1}: {lines[index]!r}"
                        )
                    key = "position" if vector.group(1) == "Pos" else "rotation"
                    transform[key] = [_number(vector.group(i)) for i in range(2, 5)]
                    index += 1
                if index >= end:
                    raise ValueError(f"Unclosed transform {name!r} in {source}")
                frame["transforms"][name] = transform
                index += 1
            else:
                directives.append(line.rstrip(";"))
                index += 1
        if index >= end:
            raise ValueError(f"Unclosed frame in {source}")
        if directives:
            frame["directives"] = directives
        frames.append(frame)
        index += 1

    if len(frames) > declared:
        raise ValueError(f"{source} declares {declared} frames but contains {len(frames)}")
    while len(frames) < declared:
        frames.append({"index": len(frames), "synthetic": True, "transforms": {}})

    result = {
        "format": "decimation:danim",
        "version": 1,
        "source": source.name,
        "static": is_static,
        "length": declared,
        "hand": hand,
        "frames": frames,
    }
    if header_directives:
        result["directives"] = header_directives
    return result


def convert(source: Path, destination: Path) -> dict:
    data = parse(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return data


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    data = convert(args.source, args.destination)
    synthetic = sum(bool(frame.get("synthetic")) for frame in data["frames"])
    print(f"{args.source.name}: {data['length']} frames ({synthetic} padded)")


if __name__ == "__main__":
    main()
