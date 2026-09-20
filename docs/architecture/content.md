# Content architecture

Decimation uses **object ownership** rather than asset-type ownership.

A Glock is one object directory. Its definition, unique model, unique textures, weapon-specific sounds and local animation metadata belong together. The same rule applies to vehicles, items, props, armor and future blocks.

```text
content/weapons/pistol/glock17/
├── definition.json
├── model.glb
├── animations/
│   └── set.json
├── textures/
│   └── glock17.png
└── sounds/
    ├── fire.ogg
    ├── fire_distant.ogg
    └── fire_suppressed.ogg
```

Exact data reused by multiple objects is not duplicated. It lives under:

```text
content/_shared/
├── models/
├── animations/
└── textures/
```

References to these files use `@shared/...`.

## Definitions, not classes

A new content variant should normally be a `definition.json`, not a new Java class. Java classes represent behavior families. Definitions represent individual content.

A distinct weapon can still have its own registry id while using the generic weapon runtime. The same principle will be used for blocks, items and vehicles.
