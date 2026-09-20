# `.dweapon`

`.dweapon` is Decimation's compiled runtime weapon format.

## Version 7

Version 7 stores:

- format magic/version
- weapon id and display name
- weapon type
- compatible magazine id
- damage
- rate of fire in rounds per minute (RPM)
- reload time
- recoil
- spread
- range
- supported fire modes
- default fire mode
- burst size
- compiled 3D model resource
- compiled primary model texture resource
- compiled inventory icon resource
- compiled animation-set resource
- fire `SoundEvent` id
- optional distant-fire `SoundEvent` id
- optional suppressed-fire `SoundEvent` id
- optional reload magazine-out `SoundEvent` id
- optional reload magazine-in `SoundEvent` id
- optional reload rack/charging `SoundEvent` id

Magazine capacity is not duplicated inside `.dweapon`; it belongs to the referenced magazine definition.

Gameplay and assets live in the same weapon pack. A canonical weapon directory now looks like:

```text
content/weapons/pistol/glock17/
├── definition.json
├── model.glb
├── icon.png
├── animations/
│   └── set.json
├── textures/
│   └── glock17.png
└── sounds/
    ├── fire.ogg
    ├── fire_distant.ogg
    └── fire_suppressed.ogg
```

The definition explicitly references the files used at runtime. The compiler validates those references, stages the files into generated resources, creates the vanilla inventory item model from `icon.png`, and stores registered sound-event ids in the `.dweapon`. Weapon inventory icons are staged to the conventional `textures/item/weapon/<id>.png` runtime path. Reload phase sounds may reference local pack files or `@shared/...` assets under `content/_shared/`.

Compiled binaries are written to:

```text
build/generated/decimation-resources/assets/decimation/weapons/
```

Binary magic: `DWPN`.
