# Temporary custom FAMAS

This directory is intentionally isolated from the original `gun/rifle/famas`.
The weapon registers as `decimation:famas_custom` and uses the existing
`decimation:famas_mag` item. Copy the entire `content/gun/rifle/temp/famas/`
directory into the project; the content compiler discovers its `definition.json`.

The supplied Blockbench/Gecko model and animation JSON are kept in `source/`.
`convert.py` produces `models/famas.obj`, a model texture and icon, and compatible
fire/reload DANIM clips. To regenerate, run the script with Pillow installed.
The runtime consumes only the files listed in `definition.json`. No Java changes
or permanent conversion step is required.

The source has no firing animation; the converted gun uses a short recoil clip.
The source reload swaps between two magazine meshes, while this renderer only
supports one mesh. The compatible reload animates that mesh out and back, with
the supplied `magOut`, `magIn`, and `rack` sounds. Inspect placement, arm pose,
and sound timing in game before moving the gun out of `temp/`.
