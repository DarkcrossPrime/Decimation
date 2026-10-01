#!/usr/bin/env python3
"""Rebuild this temporary FAMAS's OBJ, atlas, icon and DANIM from supplied Blockbench data.

Run from any directory: python3 content/gun/rifle/temp/famas/_meta/convert.py
Requires Pillow for the inventory icon; runtime and Gradle builds do not run this script.
"""

import base64
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "_meta" / "source"
MODEL = json.loads((SOURCE / "famas.bbmodel").read_text())
ANIMATIONS = json.loads((SOURCE / "famas.animation.json").read_text())["animations"]
ELEMENTS = {element["uuid"]: element for element in MODEL["elements"]}
GROUPS = {group["uuid"]: group["name"] for group in MODEL["groups"]}
SCALE = 0.25
FACE_CORNERS = {
    "north": (0, 3, 2, 1), "east": (1, 2, 6, 5),
    "south": (5, 6, 7, 4), "west": (4, 7, 3, 0),
    "up": (3, 7, 6, 2), "down": (4, 0, 1, 5),
}


def rotate(point, pivot, angles):
    x, y, z = (point[i] - pivot[i] for i in range(3))
    for axis, angle in enumerate(angles):
        c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        if axis == 0:
            y, z = y * c - z * s, y * s + z * c
        elif axis == 1:
            x, z = x * c + z * s, -x * s + z * c
        else:
            x, y = x * c - y * s, x * s + y * c
    return tuple(v + pivot[i] for i, v in enumerate((x, y, z)))


def corners(element):
    x0, y0, z0 = element["from"]
    x1, y1, z1 = element["to"]
    positions = [(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),
                 (x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)]
    pivot = element.get("origin", (0, 0, 0))
    rotation = element.get("rotation", (0, 0, 0))
    return [rotate(p, pivot, rotation) for p in positions]


def elements_by_group():
    result = {}

    def walk(node, group="gun"):
        if isinstance(node, str):
            if group in {"gun", "scope", "magazine"}:
                result.setdefault(group, []).append(ELEMENTS[node])
            return
        name = GROUPS.get(node["uuid"], group)
        for child in node.get("children", []):
            walk(child, name if name != "bone" else group)

    for node in MODEL["outliner"]:
        walk(node)
    return result


def convert():
    (ROOT / "models").mkdir(exist_ok=True)
    (ROOT / "animations").mkdir(exist_ok=True)
    (ROOT / "textures/model").mkdir(parents=True, exist_ok=True)
    (ROOT / "textures/item").mkdir(parents=True, exist_ok=True)
    texture = Image.open(SOURCE / "famas.png").convert("RGBA")
    texture.save(ROOT / "textures/model/famas.png")
    width, height = MODEL["resolution"]["width"], MODEL["resolution"]["height"]
    lines = ["# Blockbench FAMAS; coordinates converted to the existing weapon OBJ axes.",
             "mtllib famas.mtl", "usemtl famas_material"]
    sides = []
    vi = ti = 0
    for group, elements in elements_by_group().items():
        lines.append(f"o {group}")
        for element in elements:
            positions = corners(element)
            # Blockbench uses -Z as muzzle forward; this renderer uses +X.
            vertices = [(-z * SCALE, (y - 20) * SCALE, x * SCALE)
                        for x, y, z in positions]
            for x, y, z in vertices:
                lines.append(f"v {x:.7f} {y:.7f} {z:.7f}")
            for face, indices in FACE_CORNERS.items():
                spec = element["faces"].get(face)
                if not spec or spec.get("texture") is None or "uv" not in spec:
                    continue
                u0, v0, u1, v1 = spec["uv"]
                uv = ((u0, v1), (u0, v0), (u1, v0), (u1, v1))
                for u, v in uv:
                    lines.append(f"vt {u / width:.8f} {1 - v / height:.8f}")
                lines.append("f " + " ".join(f"{vi+i+1}/{ti+k+1}"
                         for k, i in enumerate(indices)))
                sides.append((group, [vertices[i] for i in indices], uv))
                ti += 4
            vi += 8
    (ROOT / "models/famas.obj").write_text("\n".join(lines) + "\n")
    (ROOT / "models/famas.mtl").write_text(
        "newmtl famas_material\nKa 0.08 0.08 0.08\nKd 1 1 1\n"
        "illum 1\nmap_Kd ../textures/model/famas.png\n")

    # Side silhouette with colors sampled from the provided texture atlas.
    image = Image.new("RGBA", (96, 48), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for group, points, uv in sorted(sides, key=lambda s: sum(p[2] for p in s[1])/4):
        if group == "scope" or abs(points[0][2] - points[1][2]) < 1e-5:
            u = max(0, min(texture.width-1, int(sum(p[0] for p in uv)/4 * texture.width/width)))
            v = max(0, min(texture.height-1, int(sum(p[1] for p in uv)/4 * texture.height/height)))
            color = texture.getpixel((u, v))
            if color[3] > 0:
                poly = [(round(48+(p[0]-1)*3.6), round(24-p[1]*3.6)) for p in points]
                draw.polygon(poly, fill=tuple(max(64, c) for c in color[:3]) + (255,))
    bounds = image.getbbox()
    if bounds:
        image = image.crop(bounds)
        image.thumbnail((58, 48), Image.Resampling.LANCZOS)
        icon = Image.new("RGBA", (64, 64))
        icon.alpha_composite(image, ((64-image.width)//2, (64-image.height)//2))
        image = icon
    image.save(ROOT / "textures/item/famas.png")

    # The supplied Gecko animation has no fire clip. Recoil uses the native DANIM
    # schema; reload uses the source magazine keyframes and native sound markers.
    fire = {"format": "decimation:danim", "version": 1, "static": False,
            "hand": 0, "length": 4, "source": "famas.bbmodel (recoil)",
            "frames": [{"index": i, "transforms": {"Model": {
                "position": [0, -d, 0], "rotation": [0, 0, -d * 11]}}}
                for i, d in enumerate((0, 0.11, 0.055, 0))]}
    (ROOT / "animations/famas_fire.danim.json").write_text(json.dumps(fire, indent=2)+"\n")
    source = ANIMATIONS["animation.model.reload"]
    keys = source["bones"]["magazine"]

    def sample(channel, time):
        data = keys.get(channel, {})
        ordered = sorted((float(k), v["vector"] if isinstance(v, dict) else v)
                         for k, v in data.items() if k != "vector")
        if "vector" in data:
            return data["vector"]
        if not ordered:
            return [0, 0, 0]
        if time <= ordered[0][0]:
            return ordered[0][1]
        for (t0, p0), (t1, p1) in zip(ordered, ordered[1:]):
            if time <= t1:
                alpha = (time-t0)/(t1-t0)
                return [a+(b-a)*alpha for a, b in zip(p0, p1)]
        return ordered[-1][1]

    frames = []
    for tick in range(43):
        pos = sample("position", tick/20)
        rotation = sample("rotation", tick/20)
        displacement = [-pos[2]*SCALE, pos[1]*SCALE, pos[0]*SCALE]
        if tick >= 29:
            # The source swaps to a second magazine mesh. This OBJ has one,
            # so ease that mesh back to its resting position before completion.
            displacement = [component * (42-tick)/13 for component in displacement]
        frame = {"index": tick, "transforms": {"magazine": {
            "position": displacement, "rotation": [0, 0, 0]}}}
        if tick == 9:
            frame["sound"] = "MagOut"
        elif tick == 28:
            frame["sound"] = "MagIn"
        elif tick == 37:
            frame["sound"] = "Rack"
        frames.append(frame)
    reload_clip = {"format": "decimation:danim", "version": 1, "static": False,
                   "hand": 0, "length": 43, "source": "famas.animation.json:animation.model.reload",
                   "frames": frames}
    (ROOT / "animations/famas_reload1.danim.json").write_text(
        json.dumps(reload_clip, indent=2)+"\n")


if __name__ == "__main__":
    convert()
