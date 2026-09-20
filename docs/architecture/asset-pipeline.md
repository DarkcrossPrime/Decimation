# Asset pipeline

Source layout is human-oriented; generated layout is runtime-oriented.

```text
content/<category>/<object>/
├── definition.json
├── model.glb
├── animations/
├── textures/
└── sounds/
        |
        | ./gradlew compileAssets
        v
build/generated/decimation-resources/assets/decimation/
├── weapons/*.dweapon
├── models3d/content/**
├── animations/content/**
├── textures/content/**
└── sounds/content/**
```

Gameplay weapon definitions are discovered recursively as `content/weapons/**/definition.json`. Converted asset-only weapon definitions are retained in the same layout but are not compiled to `.dweapon` until gameplay fields are added.

Object-owned OGG files are staged under the generated Minecraft `sounds/` tree. `src/main/resources/assets/decimation/sounds.json` already points at those generated paths for the objects normalized in 0.2.2.

The compiler owns only `build/generated/decimation-resources/`.
