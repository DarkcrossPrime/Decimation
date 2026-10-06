# Decimation — Fabric 26.3 migration

This branch registers four weapons and three shared ammunition items, with
persistent stack components, server-owned firing/reloads, positional audio and typed networking.
Weapons now use baked OBJ geometry outside inventory views, with aiming/sprint
transitions, confirmed-shot recoil and matching DANIM tracks. Checkpoint 6 restores
the first-person body, skin/clothing and armor, with captured firing/support arm poses
and a gun attachment owned by the firing arm. The final HUD remains pending;
ammunition and mode use a temporary vanilla action-bar display. In-game placement
and the new GPU layer still require client acceptance.
The first-person
arm solvers and their regression tests survive unchanged because they do not
reference Minecraft APIs. The known-good playable implementation lives on `1.20.1`.

`main` remains the live version; all port work belongs on `migration`.

Current checkpoint: **6.19**, the post-acceptance source/log cleanup over 6.18.
LAN interruptions/mid-animation joins and server features have been accepted by
the developer; further multiplayer work is deferred. See `docs/cleanup-6.19.md`
for the removals and recoverable workspace cleanup. Flat rest/fire/ADS behavior
is unchanged; menus are next after migration closeout.

## Development environment

Requirements: Linux, Bash, Python 3.11+ (content tools), curl, tar and ffmpeg on PATH.
The resource compiler uses ffmpeg with the libvorbis encoder to downmix eight stereo
weapon samples for positional playback. Only generated copies change. On Debian or
Ubuntu, install it with `sudo apt install ffmpeg`; check with `ffmpeg -version`.
Use a JDK 25
and the checked-in Gradle 9.6.0 wrapper; a global Gradle installation is unnecessary.

```bash
bash tools/dev/setup.sh
source tools/dev/env.sh
./gradlew --version
./gradlew clean build
./gradlew genSources
./gradlew runClient
```

The setup script reuses a working JDK 25 or installs the latest Temurin 25 locally
under `.toolchains/jdk-25`, verifying its published SHA-256. Source `env.sh` in each
new terminal if you use that local installation; Bash and zsh are supported.
It does not edit your shell configuration or change system Java.

In IntelliJ IDEA, use 2025.3 or newer with Java 25 support. Set **Project SDK**,
**language level** and **Gradle JVM** to 25, and select **Gradle wrapper** as the
Gradle distribution. Point the SDK at `.toolchains/jdk-25` when using local setup.
Reload the Gradle project. Development worlds go to `run-26.3/`, and server files
go to `run-server-26.3/`; the old `run/` remains a legacy development directory.

`runClient` now Quick Plays the existing save folder `Decimation-Test`. Choose your
folder with `./gradlew runClient -PquickPlayWorld="Your save folder"`, or use
`-PquickPlayWorld=` for the title screen (including first-time world creation and
upcoming menu work). `-PquickPlayServer=localhost:25565` connects to a test server
instead. See `docs/multiplayer-6.17.md` for IntelliJ and two-client acceptance.

## Layout

- `content/`: unchanged canonical asset and definition tree.
- `src/main/`: common/server catalogue, registration and item classes; no client dependencies.
- `src/client/`: input, audio, baked weapon models, presentation, extracted body/arm layers and retained arm maths.
- `tools/`: content converters, resource compiler, audits and developer setup.
- `build/generated/resources/`: disposable generated resources; never edit here.
- `docs/migration-26.3.md`: audited inventory, parity sequence and runtime targets.

Minecraft 26.3 is unobfuscated. No Yarn or additional mappings dependency is used;
Loom uses the names shipped by Mojang. Fabric dependencies use `implementation`.

## Content and verification

The compiler keeps canonical paths, OBJ, DANIM, sound cues and weapon presentation
data intact. It emits modern `assets/decimation/items/*.json` definitions and flat
inventory models. The client model-baking plugin keeps those icons in GUI views and
supplies OBJ models for held weapons, drops and displays. Legacy postprocessing
shaders are retained in `content/` but excluded from the generated runtime pack.
The common initializer validates the generated catalogue and required resources,
then registers its items. Weapons stack to one; ammunition retains its legacy stack
size of sixteen. Registry IDs, capacities and presentation transforms are preserved.
The generated catalogue is now version 2 because it includes explicit ammunition
item metadata; canonical definition files keep their existing format and version.

```bash
./gradlew prepareContentResources
./gradlew auditMigrationContent
./gradlew verifyFirstPersonRig
./gradlew verifyFirstPersonBody
./gradlew verifyWeaponCatalog
./gradlew verifyWeaponBehavior
./gradlew verifyWeaponCodecs
./gradlew verifyWeaponAudio
./gradlew verifyWeaponVisuals
./gradlew verifyWeaponMultiplayer
./gradlew verifyDedicatedServerBoundary
./gradlew verifyMigrationCleanup
./gradlew auditContent
```

`build` runs catalogue, behavior, codec, audio, visual, arm, multiplayer-contract,
common/server-boundary and cleanup-safety checks, plus the migration audit. These
do not replace a real dedicated-server/two-client acceptance session. Catalogue checks
run without Minecraft bootstrap: shared ammo identity, per-weapon capacity,
optional sprint fallback, invalid IDs/numbers, collisions and missing assets.
The strict `auditContent` currently
fails on **217 pre-existing findings** from the uploaded snapshot; see the migration
document. The migration audit compares exact normalized diagnostics with a frozen
baseline and fails on any new diagnostic. Resolved errors are accepted and reported.
It does not silently redefine those legacy issues as valid content.

Only the generator script and canonical inputs invalidate resource generation;
unchanged resources can remain up to date. Archives use reproducible ordering and
ignore file timestamps. Weapon resource preparation bakes quad normals, UVs,
indexed attribute vertices and animation tick samples once per model reload.
Source objects sharing identical motion tracks are merged into 1–2 draw groups per
weapon. Runtime submission walks those baked groups, with no OBJ/DANIM parsing,
normal calculation or per-face transform map. Vertices still stream through
Minecraft's managed renderer; persistent GPU meshes and FPS measurements follow.

For this checkpoint, launch a test world and check **Decimation Weapons** and
**Decimation Ammunition**. Left mouse fires; hold right mouse to aim, **R** reloads
and **B** cycles fire mode. Bindings can be changed under Controls → Decimation.
Only the main-hand weapon is supported. Vanilla punching, mining and block use are
suppressed while it is held. ADS changes server spread and now visibly moves the
weapon onto the recovered sight line with a small FOV reduction. Dedicated scope
optics remain separate work. Confirmed shots
kick the weapon and play its fire animation; reloads play matching part/root tracks.

Weapon state saves in `decimation:weapon_state`, synchronizes through vanilla stack
components, and survives moving/dropping the stack. Fresh items preserve spawn-loaded
definitions. Survival reloads consume one matching ammo item on completion; creative
reloads are free. Switching stacks or dimensions, death and disconnect cancel active
reload/input. Tactical reloads retain the chambered extra round. Client/server
catalogue fingerprints must agree at join before weapon input is accepted.

Weapon audio is selected and timed by the server. Firing emits one near or distant
sample per listener; Honey Badger stays suppressed. Dry fire, mode cycling and
reload cues use the existing assets. Empty rifle reloads play the rack cue; tactical
reloads with a chambered round skip it. Switching weapons or dimensions cancels
future reload cues. Playback uses the vanilla Players sound category and resource
reload handling. No animation parsing, sound decoding or definition lookup runs
per frame. Client and server must both use this checkpoint (weapon protocol 4).

Hitscan stops at solid blocks, sorts one entity-query result along the ray and applies
configured entity penetration, falloff and head multipliers. Bullets keep vanilla
armor and PvP rules, with a generated damage type that bypasses melee hurt cooldown
so automatic RPM can affect damage. Crossbow uses the current vanilla arrow entity
and its archived speed/base-damage semantics; bolts cannot produce recoverable
vanilla arrows. A custom bolt renderer/entity can follow with visuals.

Honey Badger, FAMAS, Crossbow and Custom FAMAS keep their inventory icons and use
their OBJ models when held/dropped/displayed. While holding a main-hand weapon,
vanilla first-person hands are replaced by the captured body and arms. An occupied
offhand keeps its normal item/arm pose and disables the support-grip solve.
The recovered FAMAS magazine assembly mapping is applied during baking; source
OBJ/DANIM files stay intact. Truly absent animated parts are still preserved
without fabricated geometry. All weapons receive confirmed-shot recoil.
Test aiming, firing, reload cancellation, left-hand settings, F5, drops, item frames
and vanilla resource reload. See
`docs/migration-26.3.md` for the multiplayer acceptance checklist.
Definitions load once from bundled generated resources. Gameplay definition reloads
will follow separately; pressing the vanilla resource-reload shortcut does not reload
gameplay data. The handshake checks definitions, not every texture or animation byte.
