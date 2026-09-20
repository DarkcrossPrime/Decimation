# The Last of Craft

**Decimation** is the internal development name for *The Last of Craft*, a Fabric mod targeting Minecraft 1.20.1.

## Baseline

- Minecraft 1.20.1
- Fabric
- Java 17
- Package root: `dev.decimation`
- Mod id: `decimation`
- Public name: *The Last of Craft*

## Project scope

The project is intentionally broader than its current weapon milestone. First-class areas include:

- environment, fog and atmospheric presentation
- player-side presentation, including a rendered first-person body/view system
- ambience and world feel
- blocks, items and survival/world interaction
- vehicles
- weapons and combat
- shared content/asset tooling

Weapons are being completed first because they give us a useful vertical slice across input, networking, item state, server authority and custom assets. They remain one subsystem of the project rather than the root architecture.

See `docs/architecture/project-systems.md`.

## Content architecture

The content tree is object-oriented: **one content object = one directory**.

```text
content/
├── weapons/<type>/<weapon>/
│   ├── definition.json
│   ├── model.glb
│   ├── animations/
│   ├── textures/
│   └── sounds/
├── vehicles/<vehicle>/...
├── attachments/<type>/<attachment>/...
├── armor/<type>/<piece>/...
├── backpacks/<backpack>/...
├── items/<item>/...
├── projectiles/...
├── props/<prop>/...
├── placeables/<placeable>/...
├── blocks/<block>/...
├── _shared/
└── migration/
```

Unique files live with the object that owns them. Genuinely shared or byte-identical files remain once under `content/_shared/`.

`src/main/resources/assets/decimation/` is reserved for mod-global Minecraft resources and runtime resources that do not yet have a clear object owner. Compiler output lives only in `build/generated/decimation-resources/`.

## Current milestone — 0.2.6 real weapon packs

The development-only guns and ammunition have been removed. The runtime is now exercised by three real converted content packs:

```text
decimation:glock17   9x19mm      SEMI
decimation:m4a4      5.56mm      SEMI / AUTO
decimation:akm       7.62x39mm   SEMI / AUTO
```

Each active gun is compiled from its existing object directory containing the actual GLB, texture, animation set and sounds. `.dweapon` v5 now carries runtime references to that complete asset pack, ready for the renderer/audio milestones.

Default controls:

```text
Left mouse  Trigger
R           Reload
B           Cycle fire mode
```

`R` and `B` are proper Minecraft key bindings and can be rebound.

Weapon cadence is entirely Decimation-owned; the vanilla item cooldown overlay is not used. Guns keep their ammo count in the tooltip but have no item bar. Detachable magazines use the green bar for their own round count.

Hold right-click on a magazine to load **one loose round per game tick**. Sneak-right-click still unloads it.

Pipeline:

```text
content/weapons/<type>/<weapon>/
    -> definition + GLB + animations + textures + sounds
    -> asset compiler
    -> .dweapon v5 + staged runtime assets
    -> WeaponCatalog
    -> generic WeaponItem / WeaponState
    -> WeaponRuntimeHandler
    -> WeaponFireHandler
```

## Creative inventory

Creative pages remain category-specific. The Weapons page is visible because weapon content exists. Other category pages are registered as their content systems come online; weapons are not intended to become the primary identity/page of the finished mod.

## Asset inventory

The 0.2.2 normalization remains intact:

- 197 GLB models
- 98 weapon animation sets
- 30 deduplicated animation clips
- 232 converted asset definitions
- 1,732 PNG textures
- 1,241 OGG sounds

See `docs/asset-inventory.md` and `content/migration/0.2.2_reorganization_report.json`.

## Build / run

```bash
./gradlew compileAssets
./gradlew runClient
```

`runClient` and `build` invoke `compileAssets` automatically.

Never edit:

```text
build/generated/decimation-resources/
```

## Architecture documents

- `docs/architecture/project-systems.md`
- `docs/architecture/content.md`
- `docs/architecture/assets.md`
- `docs/architecture/asset-pipeline.md`
- `docs/architecture/weapon-runtime.md`
- `docs/dweapon/README.md`
- `docs/asset-inventory.md`
