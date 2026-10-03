# Decimation — Fabric 26.3

This is a new Fabric project built from the supplied Decimation asset archive. The
legacy obfuscated Java/classes are deliberately excluded. Only reusable assets and
format knowledge were carried forward.

## Layout

- `content/` — canonical authoring tree. Every object owns its model, material,
  textures, animations, audio, and `definition.json` in one directory.
- `src/` — clean Java 17 Fabric code, split into common and client source sets.
- `tools/` — permanent `.bmodel`/`.anib` converters, legacy importer, runtime
  resource compiler, and structural auditor.
- `build/generated/resources/` — generated conventional Minecraft resource layout;
  never edit this directory directly.

The module scaffold covers ambiance, guns, vehicles, HUD, blocks, and mobs without
making any one subsystem the identity of the mod.

## Weapons

The weapon module is the production architecture, not a temporary test-item layer.
The first playable catalogue entries are the recovered **AAC Honey Badger PDW**,
**FAMAS**, and **TAC-15 Crossbow**. Their canonical `definition.json` files own both
their recovered assets and their gameplay data; adding another gun does not require
a one-off Java item class.

The server owns trigger cadence, chamber/magazine state, fire modes, reload timing,
ammo consumption, spread, raycasts/projectiles, penetration, falloff, hit location,
and damage. Clients send input intent and locally render the recovered OBJ/DANIM
assets from synchronized weapon events. Weapon NBT persists magazine, chamber, and
selected fire mode on each individual stack.

Default controls:

- `Left mouse` — trigger
- `Right mouse` — aim down sights
- `R` — reload
- `B` — cycle fire mode

## First-person body

First person uses the local player's real articulated model instead of Minecraft's
floating arms. The skin, slim/classic arm geometry, skin overlays, limb animation,
crouching, held items, and body armor therefore share the same rig as third person.
The head, hat layer, helmet, and head-mounted items are omitted only during the
local first-person render. Spectator, sleeping, and third-person views retain
vanilla rendering.

Firearms use a two-handed body pose and are rendered from the character's held-item
attachment. Each weapon owns its hip, ADS, sprint, and third-person model transforms plus
its main-hand and support-hand rotations under `weapon.presentation` in its canonical
`definition.json`; the renderer smoothly interpolates the first-person weapon and arm
poses using that weapon's ADS timing. Sparse firing and reload keyframes are interpolated
between render frames. Vanilla attacks and block breaking are suppressed
while a weapon is held, so the trigger input does not also play Minecraft's punch swing.

For a quick creative test, take the three weapons and their matching magazines or
bolts from the Decimation Weapons and Decimation Ammunition tabs. Creative players
do not consume reload items. Additional tabs are registered when their modules have
playable items to display.

## Build

Requires Java 17.

```bash
./gradlew auditContent
./gradlew build
./gradlew runClient
```

Gradle compiles the object-centric `content/` tree into runtime resources before
`processResources`, so canonical assets remain grouped by object while Fabric sees
the paths it expects.

## Re-import supplied legacy assets

```bash
python3 tools/import_legacy_assets.py /path/to/assets/deci content --replace
python3 tools/audit_assets.py content
```

Standalone conversion:

```bash
python3 tools/bmodel_to_obj.py model.bmodel model.obj --texture texture.png
python3 tools/anib_to_danim.py animation.anib animation.danim.json
```

## Conversion status

- 232 `.bmodel` files converted to OBJ/MTL.
- 386 `.anib` files converted to versioned DANIM JSON.
- 25,219 model parts, 201,808 vertices, and 151,356 quads audited.
- 1,290 canonical content objects.
- Zero legacy `.bmodel` or `.anib` files remain in `content/`.

Six audio names referenced by the old `sounds.json` were not present in the supplied
archive and are recorded as unresolved in `content/_meta/import_summary.json`; no
replacement audio was invented.
