# Asset ownership

There are three places to understand.

```text
content/                                  editable object-owned source content
src/main/resources/assets/decimation/    mod-global/native Minecraft resources
build/generated/decimation-resources/    compiler-owned output
```

## `content/`

This is the source-of-truth for actual Decimation content. Unique models, textures, sounds and animation data live beside the object that owns them.

## `content/_shared/`

Only assets that are genuinely shared or deduplicated belong here. In 0.2.2 this includes shared GLB geometry, shared converted texture data and animation clips whose converted contents are identical across several weapons.

## `src/main/resources/assets/decimation/`

This is not the content library. It contains Minecraft-native global resources such as language data, `sounds.json`, and runtime resources that have not yet been assigned to a specific content object.

## `build/generated/decimation-resources/`

Disposable compiler output. The compiler recreates this directory and never edits `content/` or `src/main/resources/`.
