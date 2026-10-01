#!/usr/bin/env python3
"""Audit canonical Decimation content for ownership and format integrity."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def audit_obj(path: Path, errors: list[str], totals: Counter) -> None:
    vertices = uvs = faces = 0
    for number, raw in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
        line = raw.strip()
        if line.startswith("v "):
            vertices += 1
        elif line.startswith("vt "):
            uvs += 1
        elif line.startswith("f "):
            faces += 1
            corners = line.split()[1:]
            if len(corners) < 3:
                errors.append(f"{path}: face on line {number} has fewer than three corners")
            for corner in corners:
                fields = corner.split("/")
                try:
                    vi = int(fields[0])
                    ti = int(fields[1]) if len(fields) > 1 and fields[1] else None
                except ValueError:
                    errors.append(f"{path}: invalid face index on line {number}")
                    continue
                if vi == 0 or abs(vi) > vertices:
                    errors.append(f"{path}: vertex index {vi} out of range on line {number}")
                if ti is not None and (ti == 0 or abs(ti) > uvs):
                    errors.append(f"{path}: texture index {ti} out of range on line {number}")
        elif line.startswith("mtllib "):
            target = path.parent / line.split(maxsplit=1)[1]
            if not target.is_file():
                errors.append(f"{path}: missing material library {target.name}")
    if not vertices or not faces:
        errors.append(f"{path}: OBJ has no usable geometry")
    totals["vertices"] += vertices
    totals["quads"] += faces


def audit_mtl(path: Path, errors: list[str]) -> None:
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if line.startswith("map_Kd "):
            target = (path.parent / line.split(maxsplit=1)[1]).resolve()
            if not target.is_file():
                errors.append(f"{path}: missing diffuse texture {line.split(maxsplit=1)[1]}")


def audit_danim(path: Path, errors: list[str], totals: Counter) -> None:
    totals["danim"] += 1
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"{path}: invalid JSON: {exc}")
        return
    if value.get("format") != "decimation:danim" or value.get("version") != 1:
        errors.append(f"{path}: unsupported DANIM header")
    frames = value.get("frames", [])
    if value.get("length") != len(frames):
        errors.append(f"{path}: declared length does not match frame array")
    if [frame.get("index") for frame in frames] != list(range(len(frames))):
        errors.append(f"{path}: frame indexes are not sequential")
    totals["frames"] += len(frames)
    totals["synthetic_frames"] += sum(bool(frame.get("synthetic")) for frame in frames)


def audit_weapon_transform(value, location: str, errors: list[str]) -> None:
    if not isinstance(value, dict):
        errors.append(f"{location}: transform must be an object")
        return
    for name in ("translation", "rotation"):
        vector = value.get(name)
        if (not isinstance(vector, list) or len(vector) != 3
                or any(not isinstance(component, (int, float)) for component in vector)):
            errors.append(f"{location}: {name} must contain three numbers")
    scale = value.get("scale")
    if not isinstance(scale, (int, float)) or scale <= 0:
        errors.append(f"{location}: scale must be a positive number")


def audit_weapon_arm_pose(value, location: str, errors: list[str]) -> None:
    if not isinstance(value, dict):
        errors.append(f"{location}: arms must be an object")
        return
    for name in ("main_hand", "off_hand"):
        rotation = value.get(name)
        if (not isinstance(rotation, list) or len(rotation) != 3
                or any(not isinstance(component, (int, float)) for component in rotation)):
            errors.append(f"{location}: {name} must contain three numbers")


def audit_weapon_presentation(value, definition_path: Path, errors: list[str]) -> None:
    location = f"{definition_path}: weapon presentation"
    if not isinstance(value, dict):
        errors.append(f"{location} is missing")
        return
    first_person = value.get("first_person")
    if not isinstance(first_person, dict):
        errors.append(f"{location}.first_person must be an object")
    else:
        pose_names = ("hip", "ads", "sprint") if "sprint" in first_person else ("hip", "ads")
        for pose_name in pose_names:
            pose = first_person.get(pose_name)
            pose_location = f"{location}.first_person.{pose_name}"
            audit_weapon_transform(pose, pose_location, errors)
            if isinstance(pose, dict):
                audit_weapon_arm_pose(pose.get("arms"), f"{pose_location}.arms", errors)
    audit_weapon_transform(value.get("third_person"), f"{location}.third_person", errors)


def run(root: Path) -> tuple[dict, list[str]]:
    root = root.resolve()
    errors: list[str] = []
    totals = Counter()
    forbidden = sorted(list(root.rglob("*.bmodel")) + list(root.rglob("*.anib")))
    if forbidden:
        errors.append(f"legacy formats remain: {len(forbidden)}")

    owned: dict[Path, str] = {}
    definitions = sorted(root.rglob("definition.json"))
    kinds = Counter()
    weapon_registry_ids: set[str] = set()
    for definition_path in definitions:
        try:
            definition = json.loads(definition_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"{definition_path}: invalid definition: {exc}")
            continue
        expected = definition_path.parent.relative_to(root).as_posix()
        if definition.get("id") != expected:
            errors.append(f"{definition_path}: id {definition.get('id')!r} should be {expected!r}")
        kinds[definition.get("type", "unknown")] += 1
        weapon = definition.get("weapon")
        if weapon is not None:
            totals["weapon_definitions"] += 1
            required = {"registry_id", "display_name", "mechanism", "ammo", "fire_modes",
                        "rate_of_fire", "reload_ticks", "ballistics", "handling"}
            missing = sorted(required - weapon.keys())
            if missing:
                errors.append(f"{definition_path}: weapon definition is missing {', '.join(missing)}")
            registry_id = weapon.get("registry_id")
            if not isinstance(registry_id, str) or not re.fullmatch(r"[a-z0-9_./-]+", registry_id):
                errors.append(f"{definition_path}: invalid weapon registry_id {registry_id!r}")
            elif registry_id in weapon_registry_ids:
                errors.append(f"{definition_path}: duplicate weapon registry_id {registry_id}")
            else:
                weapon_registry_ids.add(registry_id)
            if weapon.get("mechanism") not in {"hitscan", "projectile"}:
                errors.append(f"{definition_path}: unsupported weapon mechanism")
            modes = weapon.get("fire_modes", [])
            if not modes or any(mode not in {"semi", "burst", "automatic"} for mode in modes):
                errors.append(f"{definition_path}: invalid weapon fire modes")
            audit_weapon_presentation(weapon.get("presentation"), definition_path, errors)
        for relative_name in definition.get("assets", []):
            asset = (definition_path.parent / relative_name).resolve()
            try:
                asset.relative_to(definition_path.parent.resolve())
            except ValueError:
                errors.append(f"{definition_path}: asset escapes its object: {relative_name}")
                continue
            if not asset.is_file():
                errors.append(f"{definition_path}: missing asset {relative_name}")
            elif asset in owned:
                errors.append(f"{asset}: claimed by both {owned[asset]} and {expected}")
            else:
                owned[asset] = expected

    for path in sorted(root.rglob("*")):
        if not path.is_file() or "_meta" in path.parts or "_system" in path.parts or path.name == "definition.json":
            continue
        if path.resolve() not in owned:
            errors.append(f"unowned asset: {path.relative_to(root)}")
        suffix = path.suffix.lower()
        totals[suffix.lstrip(".") or "extensionless"] += 1
        if suffix == ".obj":
            audit_obj(path, errors, totals)
        elif suffix == ".mtl":
            audit_mtl(path, errors)
        elif path.name.endswith(".danim.json"):
            audit_danim(path, errors, totals)

    actual_groups: dict[str, list[str]] = defaultdict(list)
    for path in sorted(root.rglob("*")):
        if path.is_file() and "_meta" not in path.parts and path.name != "definition.json":
            actual_groups[digest(path)].append(path.relative_to(root).as_posix())
    actual_duplicates = {name: files for name, files in actual_groups.items() if len(files) > 1}
    review_path = root / "_meta" / "duplicate_review.json"
    try:
        review = json.loads(review_path.read_text(encoding="utf-8"))
        reviewed = {item["sha256"]: item["files"] for item in review}
        if reviewed != actual_duplicates:
            errors.append("duplicate_review.json does not match the current exact duplicate set")
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as exc:
        errors.append(f"invalid duplicate review: {exc}")

    summary = {
        "objects": len(definitions),
        "object_types": dict(sorted(kinds.items())),
        "owned_assets": len(owned),
        "obj_models": totals["obj"],
        "danim_animations": totals["danim"],
        "weapon_definitions": totals["weapon_definitions"],
        "vertices": totals["vertices"],
        "quads": totals["quads"],
        "animation_frames": totals["frames"],
        "synthetic_frames": totals["synthetic_frames"],
        "duplicate_groups_reviewed": len(actual_duplicates),
        "errors": len(errors),
    }
    return summary, errors


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("content", type=Path)
    args = parser.parse_args()
    summary, errors = run(args.content)
    print(json.dumps(summary, indent=2))
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    raise SystemExit(1 if errors else 0)


if __name__ == "__main__":
    main()
