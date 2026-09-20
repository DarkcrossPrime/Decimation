# Weapon runtime

Weapon definitions describe individual guns. Java implements shared behavior families.

## Ownership

Persistent weapon state belongs to the gun ItemStack:

- rounds currently loaded in the inserted magazine
- selected fire mode

Detachable magazine state belongs to the magazine ItemStack:

- rounds currently inside the magazine

Transient action state belongs to the server runtime:

- trigger held/released
- rate-of-fire timer
- burst progress
- reload progress

This avoids using Minecraft's vanilla `ItemCooldownManager` as weapon logic. Guns therefore have no vanilla durability/cooldown bar. Detachable magazines own the green item bar, where it represents remaining rounds.

## Input path

```text
left mouse / R / B
    -> WeaponInputHandler + DecimationKeyBindings (client)
    -> small action packet
    -> WeaponRuntimeHandler (server)
    -> WeaponFireHandler for one discharge when allowed
```

Default controls:

- Left mouse: trigger
- `R`: reload
- `B`: cycle supported fire modes

## Rate of fire

Authoring definitions use RPM directly:

```json
"rate_of_fire": 600
```

The server converts RPM to a whole-tick interval. The client sends trigger edges only and never owns cadence.

## Reload and magazines

Weapons reference a detachable magazine definition:

```json
"magazine": "decimation:glock17_mag"
```

Magazine definitions own capacity and ammunition compatibility. Reloading is timed on the server. On completion, the runtime selects the fullest compatible spare magazine with more rounds than the currently inserted one, inserts it, and places the ejected magazine back into the same inventory slot with its remaining rounds preserved.

A weapon's tooltip still displays the loaded count, but the gun itself has no item bar.

Magazine controls:

- hold right-click magazine: load exactly one matching loose round per game tick
- release right-click: stop loading immediately
- sneak-right-click magazine: unload all rounds back into loose ammunition

## Canonical weapon packs

Gameplay data no longer lives in separate development-only weapon definitions. Active weapons are complete content packs. For example:

```text
content/weapons/pistol/glock17/
├── definition.json
├── model.glb
├── animations/set.json
├── textures/glock17.png
└── sounds/*.ogg
```

The same `definition.json` preserves converted asset metadata and carries the gameplay fields used to build `.dweapon`.

## Dry fire

Attempting to fire an empty weapon produces one dry-fire click per trigger press. Automatic fire latches the empty state so holding the trigger does not spam the sound.
