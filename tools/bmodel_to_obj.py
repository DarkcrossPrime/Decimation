#!/usr/bin/env python3
"""Convert Decimation Beardie ``.bmodel`` text models to Wavefront OBJ."""

from __future__ import annotations

import argparse
import math
import re
from dataclasses import dataclass, field
from pathlib import Path


NUMBER = r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[Ee][-+]?\d+)?[FfDd]?"
NAME = r"[A-Za-z0-9_()]+"


def number(value: str) -> float:
    return float(value.rstrip("FfDd"))


def rotate_xyz_then_translate(
    point: tuple[float, float, float],
    rotation: tuple[float, float, float],
    translation: tuple[float, float, float],
) -> tuple[float, float, float]:
    """Match ModelRenderer's OpenGL matrix: T * Rz * Ry * Rx."""
    x, y, z = point
    rx, ry, rz = rotation

    cy, sy = math.cos(rx), math.sin(rx)
    y, z = y * cy - z * sy, y * sy + z * cy

    cy, sy = math.cos(ry), math.sin(ry)
    x, z = x * cy + z * sy, -x * sy + z * cy

    cy, sy = math.cos(rz), math.sin(rz)
    x, y = x * cy - y * sy, x * sy + y * cy

    return x + translation[0], y + translation[1], z + translation[2]


@dataclass
class Part:
    name: str
    texture_offset: tuple[int, int] = (0, 0)
    rotation_point: tuple[float, float, float] = (0.0, 0.0, 0.0)
    rotation: tuple[float, float, float] = (0.0, 0.0, 0.0)
    vertices: list[tuple[float, float, float]] = field(default_factory=list)
    faces: list[tuple[int, int, int, int]] = field(default_factory=list)
    face_uvs: list[tuple[tuple[float, float], ...]] = field(default_factory=list)
    texture_size: tuple[float, float] = (64.0, 32.0)
    parent: str | None = None


def beardie_shape_vertices(
    origin: tuple[float, float, float],
    offsets: list[tuple[float, float, float]],
    dimensions: tuple[float, float, float],
) -> list[tuple[float, float, float]]:
    """Reproduce deci.n.a's custom-shape constructor exactly.

    The eight input vectors are offsets, not final cube corners.  The renderer
    adds selected width/height/depth dimensions to selected vectors, in a
    permuted order.
    """
    if len(offsets) != 8:
        raise ValueError(f"addShape requires eight offsets, got {len(offsets)}")

    ox, oy, oz = origin
    dx, dy, dz = dimensions
    a = [(x + ox, y + oy, z + oz) for x, y, z in offsets]

    return [
        (a[7][0], a[7][1], a[7][2]),
        (dx + a[6][0], a[6][1], a[6][2]),
        (dx + a[4][0], dy + a[4][1], a[4][2]),
        (a[5][0], dy + a[5][1], a[5][2]),
        (a[3][0], a[3][1], dz + a[3][2]),
        (dx + a[2][0], a[2][1], dz + a[2][2]),
        (dx + a[0][0], dy + a[0][1], dz + a[0][2]),
        (a[1][0], dy + a[1][1], dz + a[1][2]),
    ]


def box_vertices(
    origin: tuple[float, float, float], dimensions: tuple[float, float, float]
) -> list[tuple[float, float, float]]:
    x, y, z = origin
    dx, dy, dz = dimensions
    return [
        (x, y, z),
        (x + dx, y, z),
        (x + dx, y + dy, z),
        (x, y + dy, z),
        (x, y, z + dz),
        (x + dx, y, z + dz),
        (x + dx, y + dy, z + dz),
        (x, y + dy, z + dz),
    ]


# The six quads used by deci.n.a, expressed against the constructor-local
# vertices returned by beardie_shape_vertices/box_vertices.
QUADS = [
    (5, 1, 2, 6),
    (0, 4, 7, 3),
    (5, 4, 0, 1),
    (2, 3, 7, 6),
    (1, 0, 3, 2),
    (4, 5, 6, 7),
]


def cuboid_uvs(
    texture_offset: tuple[int, int],
    texture_size: tuple[float, float],
    dimensions: tuple[float, float, float],
) -> list[tuple[tuple[float, float], ...]]:
    """Reproduce the six unfolded cuboid UV rectangles in deci.n.a/deci.n.c."""
    u, v = texture_offset
    width, height = texture_size
    dx, dy, dz = dimensions
    rectangles = [
        (u + dz + dx, v + dz, u + dz + dx + dz, v + dz + dy),
        (u, v + dz, u + dz, v + dz + dy),
        (u + dz, v, u + dz + dx, v + dz),
        (u + dz + dx, v + dz, u + dz + dx + dx, v),
        (u + dz, v + dz, u + dz + dx, v + dz + dy),
        (u + dz + dx + dz, v + dz, u + dz + dx + dz + dx, v + dz + dy),
    ]
    result = []
    for left, top, right, bottom in rectangles:
        result.append(
            (
                (right / width, 1.0 - top / height),
                (left / width, 1.0 - top / height),
                (left / width, 1.0 - bottom / height),
                (right / width, 1.0 - bottom / height),
            )
        )
    return result


def parse_bmodel(path: Path) -> dict[str, Part]:
    text = path.read_text(encoding="utf-8", errors="replace")
    parts: dict[str, Part] = {}
    width_match = re.search(rf"textureWidth\s*=\s*({NUMBER})", text)
    height_match = re.search(rf"textureHeight\s*=\s*({NUMBER})", text)
    texture_size = (
        number(width_match.group(1)) if width_match else 64.0,
        number(height_match.group(1)) if height_match else 32.0,
    )

    create = re.compile(
        rf"(?m)^\s*({NAME})\s*=\s*new\s+\w+\s*\(\s*this\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*\)\s*;"
    )
    for match in create.finditer(text):
        parts[match.group(1)] = Part(
            match.group(1),
            (int(number(match.group(2))), int(number(match.group(3)))),
            texture_size=texture_size,
        )

    per_part_size = re.compile(
        rf"(?m)^\s*({NAME})\.setTextureSize\s*\(\s*({NUMBER})\s*,\s*({NUMBER})\s*\)\s*;"
    )
    for match in per_part_size.finditer(text):
        if match.group(1) in parts:
            parts[match.group(1)].texture_size = (
                number(match.group(2)),
                number(match.group(3)),
            )

    shape = re.compile(
        rf"(?ms)^\s*({NAME})\.addShape\s*\(\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*"
        rf"new\s+float\s*\[\]\s*\[\]\s*\{{(.*?)\}}\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*\)\s*;"
    )
    vector = re.compile(
        rf"\{{\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*\}}"
    )
    for match in shape.finditer(text):
        name = match.group(1)
        if name not in parts:
            parts[name] = Part(name, texture_size=texture_size)
        offsets = [tuple(number(value) for value in item.groups()) for item in vector.finditer(match.group(5))]
        dimensions = tuple(number(match.group(index)) for index in (6, 7, 8))
        verts = beardie_shape_vertices(
            tuple(number(match.group(index)) for index in (2, 3, 4)),
            offsets,
            dimensions,
        )
        base = len(parts[name].vertices)
        parts[name].vertices.extend(verts)
        parts[name].faces.extend(tuple(base + index for index in quad) for quad in QUADS)
        parts[name].face_uvs.extend(
            cuboid_uvs(parts[name].texture_offset, parts[name].texture_size, dimensions)
        )

    box = re.compile(
        rf"(?m)^\s*({NAME})\.addBox\s*\(\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*\)\s*;"
    )
    for match in box.finditer(text):
        name = match.group(1)
        if name not in parts:
            parts[name] = Part(name, texture_size=texture_size)
        dimensions = tuple(number(match.group(index)) for index in (5, 6, 7))
        verts = box_vertices(
            tuple(number(match.group(index)) for index in (2, 3, 4)),
            dimensions,
        )
        base = len(parts[name].vertices)
        parts[name].vertices.extend(verts)
        parts[name].faces.extend(tuple(base + index for index in quad) for quad in QUADS)
        parts[name].face_uvs.extend(
            cuboid_uvs(parts[name].texture_offset, parts[name].texture_size, dimensions)
        )

    rotation_point = re.compile(
        rf"(?m)^\s*({NAME})\.setRotationPoint\s*\(\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*\)\s*;"
    )
    for match in rotation_point.finditer(text):
        if match.group(1) in parts:
            parts[match.group(1)].rotation_point = tuple(
                number(match.group(index)) for index in (2, 3, 4)
            )

    direct_rotation = re.compile(
        rf"(?m)^\s*({NAME})\.setRotation\s*\(\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*\)\s*;"
    )
    helper_rotation = re.compile(
        rf"(?m)^\s*setRotation\s*\(\s*({NAME})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*,\s*({NUMBER})\s*\)\s*;"
    )
    for regex in (direct_rotation, helper_rotation):
        for match in regex.finditer(text):
            if match.group(1) in parts:
                parts[match.group(1)].rotation = tuple(
                    number(match.group(index)) for index in (2, 3, 4)
                )

    child = re.compile(rf"(?m)^\s*({NAME})\.addChild\s*\(\s*({NAME})\s*\)\s*;")
    for match in child.finditer(text):
        parent_name, child_name = match.groups()
        if child_name in parts and parts[child_name].parent is None:
            parts[child_name].parent = parent_name

    parsed_geometry = sum(bool(part.faces) for part in parts.values())
    if not parts or not parsed_geometry:
        raise ValueError(f"No supported model geometry found in {path}")
    return parts


def world_vertex(parts: dict[str, Part], part: Part, vertex: tuple[float, float, float]):
    chain: list[Part] = []
    current: Part | None = part
    visited: set[str] = set()
    while current is not None:
        if current.name in visited:
            raise ValueError(f"Cyclic child relationship at {current.name}")
        visited.add(current.name)
        chain.append(current)
        current = parts.get(current.parent) if current.parent else None

    point = vertex
    for node in chain:
        point = rotate_xyz_then_translate(point, node.rotation, node.rotation_point)
    return point


@dataclass(frozen=True)
class ConversionResult:
    parts: int
    vertices: int
    quads: int


def convert(
    source: Path, destination: Path, texture_reference: str | None = None
) -> ConversionResult:
    parts = parse_bmodel(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    material_name = destination.stem + "_material"
    mtl = destination.with_suffix(".mtl")

    lines = [
        f"# Converted from Decimation Beardie model: {source.name}",
        "# Coordinates retain the original Beardie model units.",
        f"mtllib {mtl.name}",
        f"usemtl {material_name}",
    ]
    vertex_offset = 1
    uv_offset = 1
    vertex_count = 0
    face_count = 0
    part_count = 0
    for part in parts.values():
        if not part.faces:
            continue
        part_count += 1
        lines.append(f"o {part.name}")
        for vertex in part.vertices:
            x, y, z = world_vertex(parts, part, vertex)
            # OBJ/DCC convention for review: Y is up, unlike Minecraft's model Y.
            lines.append(f"v {x:.7g} {-y:.7g} {z:.7g}")
            vertex_count += 1
        for face, face_uvs in zip(part.faces, part.face_uvs, strict=True):
            # Inverting Y changes handedness, so reverse winding.
            indices = [vertex_offset + index for index in reversed(face)]
            reversed_uvs = list(reversed(face_uvs))
            for u, v in reversed_uvs:
                lines.append(f"vt {u:.9g} {v:.9g}")
            lines.append(
                "f "
                + " ".join(
                    f"{vertex_index}/{uv_offset + corner}"
                    for corner, vertex_index in enumerate(indices)
                )
            )
            uv_offset += 4
            face_count += 1
        vertex_offset += len(part.vertices)

    destination.write_text("\n".join(lines) + "\n", encoding="utf-8")
    material_lines = [
        f"newmtl {material_name}",
        "Ka 0.08 0.08 0.08",
        "Kd 0.55 0.58 0.62",
        "Ks 0.15 0.15 0.15",
        "Ns 24",
        "illum 2",
    ]
    if texture_reference is not None:
        material_lines.append(f"map_Kd {texture_reference}")
    material_lines.append("")
    mtl.write_text(
        "\n".join(
            material_lines
        ),
        encoding="utf-8",
    )
    return ConversionResult(part_count, vertex_count, face_count)


def write_obj(
    source: Path, destination: Path, texture: Path | None = None
) -> tuple[int, int, int]:
    """Compatibility wrapper retained for the approved validation workflow."""
    result = convert(source, destination, texture.name if texture else None)
    return result.parts, result.vertices, result.quads


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--texture", help="Relative texture reference written to the MTL")
    args = parser.parse_args()
    result = convert(args.source, args.destination, args.texture)
    print(
        f"{args.source.name}: {result.parts} parts, "
        f"{result.vertices} vertices, {result.quads} quads"
    )


if __name__ == "__main__":
    main()
