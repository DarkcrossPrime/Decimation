#!/usr/bin/env python3
"""Compile canonical object-owned content into Minecraft 26.3 resource paths."""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import tempfile
from pathlib import Path


WEAPON_SOUND_FILES = {
    "fire": "fire",
    "fire_distant": "fire_distant",
    "fire_suppressed": "fire_suppressed",
    "magIn": "mag_in",
    "magOut": "mag_out",
    "rack": "rack",
    "insert_shell": "insert_shell",
}

ANIMATION_SOUND_CUES = {
    "MagIn": "mag_in",
    "MagOut": "mag_out",
    "Rack": "rack",
    "LoadShell": "insert_shell",
}


RESOURCE_LOCATION_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9/._-]+$")
SOUND_EVENT_RE = re.compile(r"^[a-z0-9/._-]+$")


def copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def write_item_model(output: Path, identifier: str) -> None:
    """Build the inventory icon; the client model-baking plugin supplies 3D weapon contexts."""
    assets = output / "assets" / "decimation"
    model = assets / "models" / "item" / f"{identifier}.json"
    model.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"decimation:item/{identifier}"},
    }, indent=2) + "\n", encoding="utf-8")
    item = assets / "items" / f"{identifier}.json"
    item.parent.mkdir(parents=True, exist_ok=True)
    item.write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": f"decimation:item/{identifier}"},
    }, indent=2) + "\n", encoding="utf-8")


def runtime_sound_relative(relative: Path) -> Path:
    """Return a Minecraft-safe runtime path while preserving canonical source filenames."""
    parts = list(relative.parts)
    if not parts:
        return relative
    filename = Path(parts[-1])
    mapped = WEAPON_SOUND_FILES.get(filename.stem)
    parts[-1] = f"{mapped or filename.stem.lower()}{filename.suffix.lower()}"
    return Path(*(part.lower() for part in parts[:-1]), parts[-1])


def build_legacy_sound_redirects(definition_paths: list[Path]) -> dict[str, str]:
    """Map pre-reorganization sound paths to their current object-owned runtime paths."""
    redirects: dict[str, str] = {}
    for definition_path in definition_paths:
        definition = json.loads(definition_path.read_text(encoding="utf-8"))
        identifier = definition["id"]
        current_sounds = [
            Path(asset) for asset in definition.get("assets", [])
            if asset.startswith("sounds/") and asset.lower().endswith(".ogg")
        ]
        if not current_sounds:
            continue

        for legacy in definition.get("legacy_sources", []):
            if not legacy.startswith("sounds/") or not legacy.lower().endswith(".ogg"):
                continue
            legacy_relative = Path(legacy).relative_to("sounds").with_suffix("")
            legacy_parts = tuple(part.lower() for part in legacy_relative.parts)
            candidates: list[tuple[int, Path]] = []
            for current in current_sounds:
                current_relative = Path(*current.parts[1:]).with_suffix("")
                current_parts = tuple(part.lower() for part in current_relative.parts)
                if (len(current_parts) <= len(legacy_parts)
                        and legacy_parts[-len(current_parts):] == current_parts):
                    candidates.append((len(current_parts), current))
                elif current_relative.name.lower() == legacy_relative.name.lower():
                    candidates.append((0, current))
            if not candidates:
                continue
            current = max(candidates, key=lambda candidate: candidate[0])[1]
            old_path = f"content/{identifier}/sounds/{legacy_relative.as_posix()}".lower()
            runtime = runtime_sound_relative(current).with_suffix("").as_posix()
            redirects[old_path] = f"content/{identifier}/{runtime}".lower()
    return redirects


def normalize_sound_entry(entry, redirects: dict[str, str]):
    def normalize_name(name: str) -> str:
        lowered = name.lower()
        if ":" not in lowered:
            return redirects.get(lowered, lowered)
        namespace, path = lowered.split(":", 1)
        return f"{namespace}:{redirects.get(path, path)}"

    if isinstance(entry, str):
        return normalize_name(entry)
    if isinstance(entry, dict):
        normalized = dict(entry)
        if isinstance(normalized.get("name"), str):
            normalized["name"] = normalize_name(normalized["name"])
        return normalized
    return entry


def sound_entry_exists(entry, output: Path) -> bool:
    if isinstance(entry, dict) and entry.get("type", "file") != "file":
        return True
    sound_name = entry if isinstance(entry, str) else entry.get("name")
    if not isinstance(sound_name, str) or ":" not in sound_name:
        return False
    namespace, path = sound_name.split(":", 1)
    if namespace != "decimation":
        return True
    return (output / "assets" / namespace / "sounds" / f"{path}.ogg").is_file()


def normalize_sound_events(events: dict, redirects: dict[str, str], output: Path) -> tuple[dict, int]:
    normalized = {}
    skipped = 0
    for event_id, definition in events.items():
        value = dict(definition)
        entries = [normalize_sound_entry(entry, redirects) for entry in value.get("sounds", [])]
        valid_entries = [entry for entry in entries if sound_entry_exists(entry, output)]
        skipped += len(entries) - len(valid_entries)
        if entries and not valid_entries:
            continue
        value["sounds"] = valid_entries
        normalized[event_id.lower()] = value
    return normalized, skipped


def validate_runtime_sound_events(events: dict, output: Path) -> None:
    for event_id, definition in events.items():
        if not SOUND_EVENT_RE.fullmatch(event_id):
            raise ValueError(f"invalid Minecraft sound event id {event_id!r}")
        for entry in definition.get("sounds", []):
            sound_name = entry if isinstance(entry, str) else entry.get("name")
            if not isinstance(sound_name, str) or not RESOURCE_LOCATION_RE.fullmatch(sound_name):
                raise ValueError(f"invalid Minecraft sound resource {sound_name!r} in {event_id}")
            if not isinstance(entry, dict) or entry.get("type", "file") == "file":
                namespace, path = sound_name.split(":", 1)
                if namespace == "decimation":
                    sound_file = output / "assets" / namespace / "sounds" / f"{path}.ogg"
                    if not sound_file.is_file():
                        raise ValueError(f"missing runtime sound {sound_name} for {event_id}")


def vorbis_channels(path: Path) -> int:
    with path.open("rb") as stream:
        header = stream.read(4096)
    marker = header.find(b"\x01vorbis")
    if marker < 0 or marker + 16 > len(header) or header[marker + 11] == 0:
        raise ValueError(f"missing Ogg Vorbis identification header: {path}")
    return header[marker + 11]


def prepare_weapon_audio(events: dict, weapons: list[dict], output: Path) -> tuple[int, int]:
    """Positional audio must be mono. Convert only disposable runtime copies."""
    files: set[Path] = set()
    for weapon in weapons:
        for event_id in weapon["audio"]["sounds"].values():
            namespace, event = event_id.split(":", 1)
            if namespace != "decimation":
                continue
            definition = events.get(event)
            if definition is None or not definition.get("sounds"):
                raise ValueError(f"weapon {weapon['registry_id']} has no samples for {event_id}")
            for entry in definition["sounds"]:
                if isinstance(entry, dict) and entry.get("type", "file") != "file":
                    raise ValueError(f"weapon sound {event_id} must reference direct audio samples")
                name = entry if isinstance(entry, str) else entry["name"]
                sample_namespace, sample = name.split(":", 1)
                if sample_namespace == "decimation":
                    files.add(output / "assets" / sample_namespace / "sounds" / f"{sample}.ogg")
    downmixed = 0
    for path in sorted(files):
        if vorbis_channels(path) == 1:
            continue
        ffmpeg = shutil.which("ffmpeg")
        if ffmpeg is None:
            raise RuntimeError("Weapon positional audio requires ffmpeg to downmix generated copies. "
                               "Install ffmpeg and rerun prepareContentResources.")
        temporary = path.with_name(path.stem + ".mono.ogg")
        try:
            result = subprocess.run([
                ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                "-i", str(path), "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", str(temporary),
            ], capture_output=True, text=True)
            if result.returncode != 0:
                raise RuntimeError(f"ffmpeg could not downmix {path}: {result.stderr.strip()}")
            if vorbis_channels(temporary) != 1:
                raise ValueError(f"ffmpeg output is not mono: {temporary}")
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)
        downmixed += 1
    return len(files), downmixed


def animation_sound_cues(animation_path: Path) -> list[dict]:
    animation = json.loads(animation_path.read_text(encoding="utf-8"))
    cues: list[dict] = []
    for frame in animation.get("frames", []):
        marker = frame.get("sound")
        if marker is None:
            continue
        sound = ANIMATION_SOUND_CUES.get(marker)
        if sound is None:
            raise ValueError(f"unknown animation sound marker {marker!r} in {animation_path}")
        cues.append({"tick": frame["index"], "sound": sound})
    return cues


def reload_sound_cues(object_root: Path, object_name: str, reload_ticks: int) -> list[dict]:
    reload_animation = object_root / "animations" / f"{object_name}_reload1.danim.json"
    cues = animation_sound_cues(reload_animation)

    rack_animation = object_root / "animations" / f"{object_name}_rack.danim.json"
    if rack_animation.is_file():
        rack = json.loads(rack_animation.read_text(encoding="utf-8"))
        rack_length = int(rack.get("length", 0))
        rack_offset = max(0, reload_ticks - rack_length)
        for cue in animation_sound_cues(rack_animation):
            if cue["sound"] == "rack":
                cues.append({"tick": rack_offset + cue["tick"], "sound": "rack"})

    return sorted(cues, key=lambda cue: cue["tick"])


def build(content: Path, output: Path) -> dict:
    content = content.resolve()
    output = output.resolve()
    if not content.is_dir():
        raise ValueError(f"missing canonical content directory: {content}")
    if output.is_relative_to(content) or content.is_relative_to(output):
        raise ValueError("runtime output must not overlap canonical content")
    if output.exists():
        index = output / "assets" / "decimation" / "content" / "index.json"
        if any(output.iterdir()):
            if not index.is_file() or json.loads(index.read_text(encoding="utf-8")).get("format") != "decimation:content_index":
                raise ValueError(f"refusing to replace a non-generated directory: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    # A failed conversion must preserve the last complete generated pack.
    with tempfile.TemporaryDirectory(prefix=f".{output.name}-", dir=output.parent,
                                     ignore_cleanup_errors=True) as temporary:
        staged = Path(temporary) / "resources"
        summary = compile_resources(content, staged)
        previous = Path(temporary) / "previous"
        if output.exists():
            output.rename(previous)
        try:
            staged.rename(output)
        except OSError:
            if previous.exists():
                previous.rename(output)
            raise
        return summary


def compile_resources(content: Path, output: Path) -> dict:
    output.mkdir(parents=True, exist_ok=True)

    models: list[str] = []
    animations: list[str] = []
    objects: list[str] = []
    weapons: list[dict] = []
    definitions = sorted(content.rglob("definition.json"))
    for definition_path in definitions:
        definition = json.loads(definition_path.read_text(encoding="utf-8"))
        identifier = definition["id"]
        objects.append(identifier)
        copy(definition_path, output / "data" / "decimation" / "definitions" / f"{identifier}.json")
        object_root = definition_path.parent
        weapon = definition.get("weapon")
        if weapon is not None:
            compiled_weapon = dict(weapon)
            compiled_weapon["content_id"] = identifier
            registry_id = weapon["registry_id"]
            item_icon = next((name for name in definition["assets"]
                              if name.startswith("textures/item/") and name.endswith(".png")), None)
            if item_icon is None:
                raise ValueError(f"weapon {identifier} has no item icon")
            compiled_weapon["assets"] = {
                "model": f"decimation:content/{identifier}/models/{object_root.name}.obj",
                "texture": f"decimation:content/{identifier}/textures/model/{object_root.name}.png",
                "item_texture": f"decimation:textures/item/{registry_id}.png",
                "fire_animation": f"decimation:content/{identifier}/animations/{object_root.name}_fire.danim.json",
                "reload_animation": f"decimation:content/{identifier}/animations/{object_root.name}_reload1.danim.json",
            }
            sounds: dict[str, str] = {
                "dry_fire": "decimation:weapon.dry_fire",
                "fire_mode": "decimation:weapon.fire_mode",
            }
            for relative_name in definition["assets"]:
                relative = Path(relative_name)
                if relative.parts[0] != "sounds" or relative.suffix.lower() != ".ogg":
                    continue
                cue = WEAPON_SOUND_FILES.get(relative.stem)
                if cue is not None:
                    sounds[cue] = f"decimation:weapon.{registry_id}.{cue}"
            compiled_weapon["audio"] = {
                "shot": weapon.get("shot_sound", "fire"),
                "distant_threshold": weapon.get("distant_sound_threshold", 32.0),
                "sounds": sounds,
                "reload_cues": reload_sound_cues(object_root, object_root.name, weapon["reload_ticks"]),
            }
            weapons.append(compiled_weapon)
        for relative_name in definition["assets"]:
            relative = Path(relative_name)
            source = object_root / relative
            if relative.parts[0] == "sounds":
                runtime_relative = runtime_sound_relative(relative)
                destination = output / "assets" / "decimation" / "sounds" / "content" / identifier / runtime_relative
            else:
                destination = output / "assets" / "decimation" / "content" / identifier / relative
            copy(source, destination)
            resource = f"decimation:content/{identifier}/{relative.as_posix()}"
            if relative.suffix.lower() == ".obj":
                models.append(resource)
            elif relative.name.endswith(".danim.json"):
                animations.append(resource)

        if weapon is not None:
            registry_id = weapon["registry_id"]
            icon = next((object_root / name for name in definition["assets"]
                         if name.startswith("textures/item/") and name.endswith(".png")), None)
            if icon is None:
                raise ValueError(f"weapon {identifier} has no item icon")
            copy(icon, output / "assets" / "decimation" / "textures" / "item" / f"{registry_id}.png")
            write_item_model(output, registry_id)

            ammo_id = weapon["ammo"]["item"]
            ammo_definition = content / "item" / "items" / "ammo" / ammo_id / "definition.json"
            if not ammo_definition.is_file():
                raise ValueError(f"weapon {identifier} references missing ammunition {ammo_id}")
            if ammo_definition.is_file():
                ammo_data = json.loads(ammo_definition.read_text(encoding="utf-8"))
                ammo_root = ammo_definition.parent
                ammo_icon_name = next((name for name in ammo_data["assets"]
                                      if name.startswith("textures/item/") and name.endswith(".png")), None)
                if ammo_icon_name is None:
                    raise ValueError(f"ammunition {ammo_id} has no item icon")
                copy(ammo_root / ammo_icon_name,
                     output / "assets" / "decimation" / "textures" / "item" / f"{ammo_id}.png")
                write_item_model(output, ammo_id)

    system = content / "_system"
    if (system / "lang").exists():
        shutil.copytree(system / "lang", output / "assets" / "decimation" / "lang", dirs_exist_ok=True)
    # Keep legacy shaders in canonical content. They require a modern rendering port
    # before being included in a 26.3 resource pack.
    if (system / "minecraft").exists():
        shutil.copytree(system / "minecraft", output / "assets" / "minecraft", dirs_exist_ok=True)
    sound_events_path = content / "_meta" / "sound_events.json"
    legacy_sound_redirects = build_legacy_sound_redirects(definitions)
    sound_events, skipped_missing_sounds = normalize_sound_events(
        json.loads(sound_events_path.read_text(encoding="utf-8")) if sound_events_path.exists() else {},
        legacy_sound_redirects,
        output,
    )
    sound_events["weapon.dry_fire"] = {
        "sounds": ["decimation:content/gun/sound/guns/sounds/empty"],
    }
    sound_events["weapon.fire_mode"] = {
        "sounds": [
            "decimation:content/gun/sound/guns/sounds/firemode1",
            "decimation:content/gun/sound/guns/sounds/firemode2",
        ],
    }
    for weapon in weapons:
        registry_id = weapon["registry_id"]
        content_id = weapon["content_id"]
        for cue, sound_id in weapon["audio"]["sounds"].items():
            if cue in {"dry_fire", "fire_mode"}:
                continue
            source_name = next((source for source, mapped in WEAPON_SOUND_FILES.items() if mapped == cue), None)
            if source_name is None:
                raise ValueError(f"no source sound mapping for {cue}")
            runtime_name = runtime_sound_relative(Path("sounds") / f"{source_name}.ogg").stem
            sound_events[sound_id.split(":", 1)[1]] = {
                "sounds": [f"decimation:content/{content_id}/sounds/{runtime_name}"],
            }
    validate_runtime_sound_events(sound_events, output)
    weapon_sound_files, weapon_sounds_downmixed = prepare_weapon_audio(sound_events, weapons, output)
    sounds_output = output / "assets" / "decimation" / "sounds.json"
    sounds_output.parent.mkdir(parents=True, exist_ok=True)
    sounds_output.write_text(json.dumps(sound_events, indent=2) + "\n", encoding="utf-8")

    language_path = output / "assets" / "decimation" / "lang" / "en_us.json"
    language = json.loads(language_path.read_text(encoding="utf-8")) if language_path.exists() else {}
    for weapon in weapons:
        language[f"item.decimation.{weapon['registry_id']}"] = weapon["display_name"]
        ammo_id = weapon["ammo"]["item"]
        language.setdefault(f"item.decimation.{ammo_id}", ammo_id.replace("_", " ").title())
    language["tooltip.decimation.fire_mode"] = "Fire mode: %s"
    language["key.categories.decimation"] = "Decimation"
    language["key.decimation.reload"] = "Reload weapon"
    language["key.decimation.relaxed_carry"] = "Toggle relaxed carry"
    language["hud.decimation.relaxed"] = "Relaxed carry"
    language["hud.decimation.ready"] = "Weapon ready"
    language["key.decimation.fire_mode"] = "Cycle fire mode"
    language["key.category.decimation.controls"] = "Decimation"
    language["hud.decimation.weapon"] = "%s | %s"
    language["hud.decimation.reloading"] = " | Reloading"
    language["death.attack.decimation.bullet"] = "%1$s was shot by %2$s"
    language["death.attack.decimation.bullet.player"] = "%1$s was shot by %2$s"
    language_path.parent.mkdir(parents=True, exist_ok=True)
    language_path.write_text(json.dumps(language, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    # Bullets retain vanilla armor/PvP rules, but rapid fire must not be gated by
    # vanilla melee invulnerability frames. All new resources are generated.
    damage_type = output / "data" / "decimation" / "damage_type" / "bullet.json"
    damage_type.parent.mkdir(parents=True, exist_ok=True)
    damage_type.write_text(json.dumps({
        "message_id": "decimation.bullet", "scaling": "when_caused_by_living_non_player",
        "exhaustion": 0.1, "effects": "hurt",
    }, indent=2) + "\n", encoding="utf-8")
    cooldown_tag = output / "data" / "minecraft" / "tags" / "damage_type" / "bypasses_cooldown.json"
    cooldown_tag.parent.mkdir(parents=True, exist_ok=True)
    cooldown_tag.write_text(json.dumps({"replace": False, "values": ["decimation:bullet"]}, indent=2) + "\n", encoding="utf-8")

    # Shared ammo items are registered once; magazine/chamber capacities remain
    # weapon-specific (the two FAMAS definitions intentionally differ).
    ammunition = []
    seen_ammunition = set()
    for weapon in weapons:
        ammo_id = weapon["ammo"]["item"]
        if ammo_id not in seen_ammunition:
            seen_ammunition.add(ammo_id)
            ammunition.append({
                "registry_id": ammo_id,
                "display_name": language[f"item.decimation.{ammo_id}"],
                "max_stack_size": 16,
            })

    index = {
        "format": "decimation:content_index",
        "version": 1,
        "objects": objects,
        "models": models,
        "animations": animations,
    }
    index_path = output / "assets" / "decimation" / "content" / "index.json"
    index_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
    weapon_index = output / "data" / "decimation" / "weapons" / "index.json"
    weapon_index.parent.mkdir(parents=True, exist_ok=True)
    weapon_index.write_text(json.dumps({
        "format": "decimation:weapon_catalog",
        "version": 2,
        "weapons": weapons,
        "ammunition": ammunition,
    }, indent=2) + "\n", encoding="utf-8")
    return {"objects": len(objects), "models": len(models), "animations": len(animations),
            "weapons": len(weapons), "skipped_missing_sounds": skipped_missing_sounds,
            "weapon_sound_files": weapon_sound_files, "weapon_sounds_downmixed": weapon_sounds_downmixed}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--content", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.content, args.output), indent=2))


if __name__ == "__main__":
    main()
