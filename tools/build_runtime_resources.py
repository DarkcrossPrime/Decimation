#!/usr/bin/env python3
"""Compile canonical object-owned content into Fabric resource-pack paths."""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path


def copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def build(content: Path, output: Path) -> dict:
    content = content.resolve()
    output = output.resolve()
    if output.exists():
        shutil.rmtree(output, ignore_errors=True)
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
            compiled_weapon["assets"] = {
                "model": f"decimation:content/{identifier}/models/{object_root.name}.obj",
                "texture": f"decimation:content/{identifier}/textures/model/{object_root.name}.png",
                "fire_animation": f"decimation:content/{identifier}/animations/{object_root.name}_fire.danim.json",
                "reload_animation": f"decimation:content/{identifier}/animations/{object_root.name}_reload1.danim.json",
                "fire_sound": f"decimation:content/{identifier}/sounds/fire.ogg",
            }
            weapons.append(compiled_weapon)
        for relative_name in definition["assets"]:
            relative = Path(relative_name)
            source = object_root / relative
            if relative.parts[0] == "sounds":
                destination = output / "assets" / "decimation" / "sounds" / "content" / identifier / relative
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
            item_model = output / "assets" / "decimation" / "models" / "item" / f"{registry_id}.json"
            item_model.parent.mkdir(parents=True, exist_ok=True)
            item_model.write_text(json.dumps({"parent": "builtin/entity"}, indent=2) + "\n", encoding="utf-8")

            ammo_id = weapon["ammo"]["item"]
            ammo_definition = content / "item" / "items" / "ammo" / ammo_id / "definition.json"
            if ammo_definition.is_file():
                ammo_data = json.loads(ammo_definition.read_text(encoding="utf-8"))
                ammo_root = ammo_definition.parent
                ammo_icon_name = next(name for name in ammo_data["assets"]
                                      if name.startswith("textures/item/") and name.endswith(".png"))
                copy(ammo_root / ammo_icon_name,
                     output / "assets" / "decimation" / "textures" / "item" / f"{ammo_id}.png")
                ammo_model = output / "assets" / "decimation" / "models" / "item" / f"{ammo_id}.json"
                ammo_model.parent.mkdir(parents=True, exist_ok=True)
                ammo_model.write_text(json.dumps({
                    "parent": "minecraft:item/generated",
                    "textures": {"layer0": f"decimation:item/{ammo_id}"},
                }, indent=2) + "\n", encoding="utf-8")

    system = content / "_system"
    if (system / "lang").exists():
        shutil.copytree(system / "lang", output / "assets" / "decimation" / "lang", dirs_exist_ok=True)
    if (system / "shaders").exists():
        shutil.copytree(system / "shaders", output / "assets" / "decimation" / "shaders", dirs_exist_ok=True)
    if (system / "minecraft").exists():
        shutil.copytree(system / "minecraft", output / "assets" / "minecraft", dirs_exist_ok=True)
    sound_events_path = content / "_meta" / "sound_events.json"
    sound_events = (json.loads(sound_events_path.read_text(encoding="utf-8"))
                    if sound_events_path.exists() else {})
    for weapon in weapons:
        registry_id = weapon["registry_id"]
        content_id = weapon["content_id"]
        sound_events[f"weapon.{registry_id}.fire"] = {
            "category": "player",
            "sounds": [f"decimation:content/{content_id}/sounds/fire"],
        }
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
    language["key.decimation.fire_mode"] = "Cycle fire mode"
    language_path.parent.mkdir(parents=True, exist_ok=True)
    language_path.write_text(json.dumps(language, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

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
        "version": 1,
        "weapons": weapons,
    }, indent=2) + "\n", encoding="utf-8")
    return {"objects": len(objects), "models": len(models), "animations": len(animations),
            "weapons": len(weapons)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--content", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.content, args.output), indent=2))


if __name__ == "__main__":
    main()
