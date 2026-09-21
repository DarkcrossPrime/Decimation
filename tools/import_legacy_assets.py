#!/usr/bin/env python3
"""Import the legacy Decimation asset pack into object-owned directories."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

from anib_to_danim import convert as convert_animation
from bmodel_to_obj import convert as convert_model


MODEL_TYPES = {
    "guns": "gun",
    "armor": "armor",
    "attachments": "attachment",
    "backpacks": "backpack",
    "bullets": "projectile",
    "items": "item",
    "misc": "misc",
    "placeable": "placeable",
    "props": "block",
    "vehicles": "vehicle",
}


def slug(value: str) -> str:
    value = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", value)
    value = re.sub(r"[^a-zA-Z0-9]+", "_", value).strip("_").lower()
    return value or "unnamed"


def key(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def json_write(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=False) + "\n", encoding="utf-8")


@dataclass
class ContentObject:
    identifier: str
    kind: str
    legacy_ids: set[str] = field(default_factory=set)
    assets: list[str] = field(default_factory=list)
    sources: list[str] = field(default_factory=list)


class Importer:
    def __init__(self, source: Path, destination: Path):
        self.source = source.resolve()
        self.destination = destination.resolve()
        self.objects: dict[str, ContentObject] = {}
        self.by_key: dict[str, list[str]] = defaultdict(list)
        self.source_map: dict[str, str] = {}
        self.sound_map: dict[str, str] = {}
        self.model_jobs: list[tuple[Path, ContentObject]] = []
        self.animation_jobs: list[tuple[Path, ContentObject]] = []
        self.copy_jobs: list[tuple[Path, ContentObject, str]] = []
        self.stats = defaultdict(int)

    def object(self, identifier: str, kind: str, *legacy_ids: str) -> ContentObject:
        identifier = "/".join(slug(part) for part in identifier.split("/") if part)
        result = self.objects.get(identifier)
        if result is None:
            result = ContentObject(identifier, kind)
            self.objects[identifier] = result
        for legacy_id in legacy_ids:
            normalized = key(legacy_id)
            if normalized:
                result.legacy_ids.add(legacy_id)
                if identifier not in self.by_key[normalized]:
                    self.by_key[normalized].append(identifier)
        return result

    def model_object(self, relative: Path) -> ContentObject:
        parts = relative.parts
        family = parts[0]
        kind = MODEL_TYPES[family]
        name = relative.stem
        categories = list(parts[1:-1])
        if family == "vehicles" and key(name) == "btr70turret":
            name = "btr70"
        if family == "misc" and key(name) in {"turrethead", "turretstand"}:
            name = "turret"
        category = "/".join(slug(part) for part in categories)
        identifier = "/".join(filter(None, (kind, category, slug(name))))
        return self.object(identifier, kind, relative.stem, name)

    def find_named(self, value: str, preferred_kind: str | None = None) -> ContentObject | None:
        choices = self.by_key.get(key(value), [])
        if preferred_kind:
            choices = [choice for choice in choices if self.objects[choice].kind == preferred_kind] or choices
        if not choices:
            return None
        return self.objects[sorted(choices, key=lambda item: (item.count("/"), item))[0]]

    def discover_models(self) -> None:
        root = self.source / "models"
        for path in sorted(root.rglob("*.bmodel")):
            relative = path.relative_to(root)
            obj = self.model_object(relative)
            self.model_jobs.append((path, obj))
            self.source_map[f"models/{relative.as_posix()}"] = obj.identifier

    def classify_animation(self, relative: Path) -> ContentObject:
        gun_id = relative.parts[0]
        found = self.find_named(gun_id, "gun")
        return found or self.object(f"gun/legacy/{gun_id}", "gun", gun_id)

    def explicit_asset_match(self, relative: Path, asset_type: str) -> ContentObject | None:
        p = relative.parts
        if asset_type == "texture":
            if len(p) >= 4 and p[:2] == ("model", "guns"):
                return self.find_named(p[3], "gun")
            if len(p) >= 4 and p[:2] == ("items", "gun"):
                return self.find_named(Path(p[3]).stem, "gun")
            if len(p) >= 4 and p[:2] == ("model", "attachments"):
                return self.find_named(p[3], "attachment")
            if len(p) >= 3 and p[:2] == ("items", "attach"):
                return self.find_named(Path(p[-1]).stem, "attachment")
        if asset_type == "sound" and len(p) >= 4 and p[0] == "guns":
            return self.find_named(p[2], "gun")
        return None

    def matched_asset(self, relative: Path, asset_type: str) -> ContentObject | None:
        explicit = self.explicit_asset_match(relative, asset_type)
        if explicit:
            return explicit
        candidates = [Path(part).stem for part in reversed(relative.parts)]
        for candidate in candidates:
            found = self.find_named(candidate)
            if found:
                return found
        return None

    def fallback_object(self, relative: Path, asset_type: str) -> ContentObject:
        parts = list(relative.parts)
        stem = slug(relative.stem)
        first = slug(parts[0])
        second = slug(parts[1]) if len(parts) > 2 else "shared"
        if asset_type == "texture":
            kind = {
                "blocks": "block",
                "gui": "hud",
                "particle": "ambiance",
                "weather": "ambiance",
                "items": "item",
                "model": "visual",
            }.get(parts[0], "visual")
            if parts[:2] == ["model", "entities"]:
                kind = "mob"
            if parts[0] == "gui":
                identifier = f"hud/gui/{second}"
            elif parts[0] in {"particle", "weather"}:
                group = second if len(parts) > 2 else re.sub(r"_?\d+$", "", stem)
                identifier = f"ambiance/{first}/{group}"
            elif parts[:2] == ["model", "entities"]:
                group = slug(parts[2]) if len(parts) > 3 else stem
                if group == "mob" and len(parts) > 4:
                    group = f"mob/{slug(parts[3])}"
                identifier = f"mob/model/{group}"
            elif parts[0] == "blocks" and len(parts) > 2:
                identifier = f"block/textures/{second}"
            else:
                identifier = f"{kind}/{first}/{second}/{stem}"
        else:
            kind = {
                "blocks": "block",
                "environment": "ambiance",
                "gui": "hud",
                "mob": "mob",
                "vehicle": "vehicle",
                "entity": "entity",
                "items": "item",
                "guns": "gun",
            }.get(parts[0], "sound")
            group = slug(parts[-2]) if len(parts) > 1 else "shared"
            identifier = f"{kind}/sound/{group}"
        return self.object(identifier, kind, relative.stem)

    def queue_assets(self) -> None:
        animations = self.source / "animations"
        for path in sorted(animations.rglob("*.anib")):
            relative = path.relative_to(animations)
            obj = self.classify_animation(relative)
            self.animation_jobs.append((path, obj))
            self.source_map[f"animations/{relative.as_posix()}"] = obj.identifier

        for directory, asset_type in (("textures", "texture"), ("sounds", "sound")):
            root = self.source / directory
            for path in sorted(item for item in root.rglob("*") if item.is_file()):
                relative = path.relative_to(root)
                obj = self.matched_asset(relative, asset_type) or self.fallback_object(relative, asset_type)
                target = f"{directory}/{relative.as_posix()}"
                self.copy_jobs.append((path, obj, target))
                source_name = f"{directory}/{relative.as_posix()}"
                self.source_map[source_name] = obj.identifier
                if asset_type == "sound" and path.suffix.lower() == ".ogg":
                    self.sound_map[relative.with_suffix("").as_posix()] = f"content/{obj.identifier}/{target[:-4]}"

    def destination_for(self, obj: ContentObject, relative: str) -> Path:
        return self.destination / obj.identifier / relative

    def copy_queued(self) -> None:
        for source, obj, relative in self.copy_jobs:
            target = self.destination_for(obj, relative)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            obj.assets.append(relative)
            obj.sources.append(source.relative_to(self.source).as_posix())
            self.stats[source.suffix.lower().lstrip(".") or "extensionless"] += 1

    def convert_models(self) -> None:
        for source, obj in self.model_jobs:
            name = slug(source.stem)
            obj_dir = self.destination / obj.identifier
            texture_candidates = sorted(
                asset for asset in obj.assets if asset.lower().endswith((".png", ".jpg"))
            )
            texture = None
            exact = [asset for asset in texture_candidates if key(Path(asset).stem) == key(source.stem)]
            selected = exact[0] if exact else (texture_candidates[0] if texture_candidates else None)
            if selected:
                texture = Path("..", selected).as_posix()
            target = obj_dir / "models" / f"{name}.obj"
            result = convert_model(source, target, texture)
            obj.assets.extend([f"models/{name}.obj", f"models/{name}.mtl"])
            obj.sources.append(source.relative_to(self.source).as_posix())
            self.stats["obj"] += 1
            self.stats["mtl"] += 1
            self.stats["model_parts"] += result.parts
            self.stats["model_vertices"] += result.vertices
            self.stats["model_quads"] += result.quads

    def convert_animations(self) -> None:
        for source, obj in self.animation_jobs:
            target_name = slug(source.stem) + ".danim.json"
            relative = f"animations/{target_name}"
            data = convert_animation(source, self.destination_for(obj, relative))
            obj.assets.append(relative)
            obj.sources.append(source.relative_to(self.source).as_posix())
            self.stats["danim_json"] += 1
            self.stats["animation_frames"] += data["length"]
            self.stats["synthetic_frames"] += sum(bool(frame.get("synthetic")) for frame in data["frames"])

    def import_system_files(self) -> None:
        system = self.destination / "_system"
        for source in sorted((self.source / "lang").glob("*.lang")):
            values = {}
            for raw in source.read_text(encoding="utf-8-sig", errors="replace").splitlines():
                line = raw.strip()
                if line and not line.startswith("#") and "=" in line:
                    name, value = line.split("=", 1)
                    values[name.strip()] = value.strip()
            json_write(system / "lang" / f"{source.stem.lower()}.json", values)
        shaders = self.source / "shaders"
        if shaders.exists():
            shutil.copytree(shaders, system / "shaders", dirs_exist_ok=True)
        vanilla = self.source.parent / "minecraft"
        if vanilla.exists():
            for source in vanilla.rglob("*"):
                if source.is_file():
                    relative = source.relative_to(vanilla)
                    parts = list(relative.parts)
                    if len(parts) > 1 and parts[:2] == ["textures", "blocks"]:
                        parts[1] = "block"
                    target = system / "minecraft" / Path(*parts)
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(source, target)

    def remap_sounds(self) -> list[str]:
        source_file = self.source / "sounds.json"
        original = json.loads(source_file.read_text(encoding="utf-8"))
        missing: set[str] = set()
        mapped = {}
        for event, definition in original.items():
            updated = dict(definition)
            sounds = []
            for sound in definition.get("sounds", []):
                if isinstance(sound, str):
                    name = sound
                    extra = None
                else:
                    extra = dict(sound)
                    name = extra.get("name", "")
                legacy = name.split(":", 1)[-1]
                replacement = self.sound_map.get(legacy)
                if replacement is None:
                    missing.add(legacy)
                    replacement = f"missing/{legacy}"
                new_name = f"decimation:{replacement}"
                if extra is None:
                    sounds.append(new_name)
                else:
                    extra["name"] = new_name
                    sounds.append(extra)
            updated["sounds"] = sounds
            mapped[event] = updated
        json_write(self.destination / "_meta" / "sound_events.json", mapped)
        return sorted(missing)

    def write_definitions(self) -> None:
        for obj in sorted(self.objects.values(), key=lambda item: item.identifier):
            obj.assets = sorted(set(obj.assets))
            obj.sources = sorted(set(obj.sources))
            definition = {
                "format": "decimation:content_object",
                "version": 1,
                "id": obj.identifier,
                "type": obj.kind,
                "assets": obj.assets,
                "legacy_sources": obj.sources,
            }
            json_write(self.destination / obj.identifier / "definition.json", definition)

    def write_metadata(self, missing_sounds: list[str]) -> None:
        json_write(self.destination / "_meta" / "import_map.json", dict(sorted(self.source_map.items())))
        hashes: dict[str, list[str]] = defaultdict(list)
        for path in sorted(self.destination.rglob("*")):
            if path.is_file() and "_meta" not in path.parts and path.name != "definition.json":
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                hashes[digest].append(path.relative_to(self.destination).as_posix())
        duplicates = [
            {"sha256": digest, "files": files, "disposition": "retained_object_local_ownership"}
            for digest, files in sorted(hashes.items())
            if len(files) > 1
        ]
        json_write(self.destination / "_meta" / "duplicate_review.json", duplicates)
        summary = {
            "format": "decimation:import_summary",
            "version": 1,
            "objects": len(self.objects),
            "statistics": dict(sorted(self.stats.items())),
            "missing_sound_sources": missing_sounds,
            "duplicate_groups": len(duplicates),
            "excluded_legacy_files": [
                "models/armor/vest/testvest.obj",
                "models/armor/vest/testvest.obj.mtl",
            ],
        }
        json_write(self.destination / "_meta" / "import_summary.json", summary)

    def run(self) -> dict:
        self.destination.mkdir(parents=True, exist_ok=True)
        self.discover_models()
        self.queue_assets()
        self.copy_queued()
        self.convert_models()
        self.convert_animations()
        self.import_system_files()
        missing = self.remap_sounds()
        self.write_definitions()
        self.write_metadata(missing)
        return json.loads((self.destination / "_meta" / "import_summary.json").read_text())


def safe_replace(destination: Path) -> None:
    resolved = destination.resolve()
    if len(resolved.parts) < 6 or resolved.name in {"", "/", "work"}:
        raise ValueError(f"Refusing unsafe replacement target: {resolved}")
    if resolved.exists():
        shutil.rmtree(resolved)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Legacy assets/deci directory")
    parser.add_argument("destination", type=Path, help="Canonical content directory")
    parser.add_argument("--replace", action="store_true", help="Replace the destination first")
    args = parser.parse_args()
    if args.replace:
        safe_replace(args.destination)
    summary = Importer(args.source, args.destination).run()
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
