# 26.3 migration checkpoint

## Scope and branches

`main` is live, `migration` is the port, `1.20.1` is the playable reference.
Checkpoint 1 supplied the platform and environment. Checkpoint 2 added weapon and
ammunition registration. Checkpoint 3 added weapon state, server firing/reloads and
typed networking. Checkpoint 4 added positional weapon audio. Checkpoint 5 adds
baked OBJ weapons and first-person aiming/recoil. Checkpoint 6 restores captured
body/arm poses, equipment layers and the posed-hand weapon attachment. No remote
branch was changed: the source snapshot contains no `.git` directory.

Checkpoint 2 extended that foundation with validated catalogue loading and inventory
registration for four weapons and three ammunition items. Checkpoint 3 added firing
and reloading; checkpoint 4 supplied audio, checkpoint 5 added weapon visuals and
checkpoint 6 ports the body/arm rig. See each checkpoint's
notes below for acceptance and validation.

## Pinned toolchain

| Component | Version |
| --- | --- |
| Minecraft | 26.3 |
| Java source, compiler and Gradle JVM | 25 |
| Gradle wrapper | 9.6.0 |
| Loom | 1.17.21, `net.fabricmc.fabric-loom` |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |

These dependency versions were verified against Fabric's published Maven metadata
and the Fabric 26.3 guidance. Gradle's distribution hash is pinned in the wrapper.
JDK updates stay within major version 25 and are verified by the setup script.

References:
- https://fabricmc.net/2026/09/15/263.html
- https://docs.fabricmc.net/develop/loom/
- https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.17.21/
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0+26.3/

## Inventory of the supplied snapshot

The original source tree has 67 Java classes, including 11 client mixins. Content
contains 1,291 object definitions, 4 gameplay weapon definitions, 4,884 owned assets,
233 OBJ models, 388 DANIM animations, 203,584 OBJ positions and 152,654 faces counted
by the legacy auditor. Its `quads` counter counts face records, not validated
triangles or exclusively four-sided faces. Content occupies about 260 MiB on disk.

The four gameplay weapons are Honey Badger, FAMAS, Crossbow and the temporary
custom FAMAS. Most of the other asset definitions are not yet playable objects.
The original README's older object/model counts were stale.

| Original subsystem | Port approach |
| --- | --- |
| Definition data and presentation transforms | Preserve schema first; introduce validation/reload handling when loading it |
| Registry, item groups and stack NBT | Rewrite for current registries and synchronized data components |
| Server cadence, ammo, damage and shot resolution | Preserve behavior; adapt only after items and state exist |
| Three legacy networking channels | Typed payloads/codecs, server intent validation and bounded decoding |
| OBJ/DANIM | Preserve source assets; bake runtime representation at resource reload |
| Per-frame face traversal/normals/transform maps | Replace with indexed meshes and cached immutable per-part metadata |
| Framebuffer/body passes and 11 mixins | Rebuild against modern extraction/submission and Blaze3D |
| GLFW-style legacy input assumptions | Use vanilla input abstractions appropriate to SDL3 |
| HUD/audio | Current Fabric hooks and resources after gameplay/networking |
| Empty ambiance/vehicle/mob/etc. module shells | Reintroduce when their actual behavior exists |
| Importers/converters | Keep available; not used on the runtime hot path |

The pure `FirstPersonRigPose`, `FirstPersonRigSolver`, `FirstPersonSupportArmSolver`
and `ObjModel` classes remain as reference geometry/maths. `ObjModel` is an importer
representation, not the planned GPU mesh. Their existing tests pass on Java 25.
The remainder of the old platform classes and the old mixin configuration are
removed from this branch; the archive branch retains them for comparison.

## Existing audit findings: zero canonical content edits in this checkpoint

The strict audit reports 217 findings before and after this foundation:

- Six sound objects moved from `sound/sound/*` to `sound/*` retain their old IDs.
- 210 material texture references still point to legacy subdirectories.
- The duplicate-review manifest no longer matches all current exact duplicates.

The resource compiler still succeeds because definitions own the real assets and
the former weapon renderer used its explicit texture rather than those MTL paths.
That does not make the MTL paths safe for a future generic mesh importer. Repair or
resolve them at the generated/imported boundary before mesh baking; fix canonical
references where appropriate when the asset pipeline is ported.

`tools/migration/content-audit-baseline.json` records every diagnostic. The baseline
must not be regenerated automatically to absorb new failures. `auditContent` stays
strict. `auditMigrationContent` is a temporary no-new-errors gate wired into `check`.
The six absent legacy sound references remain filtered by the existing compiler.

## Runtime optimization contracts for the renderer port

- Parse OBJ/DANIM and resolve materials once on resource load/reload, never per frame.
- Triangulate and build indexed vertex buffers by material and animated part. A
  unique vertex includes position, UV and normal; do not weld across seams blindly.
- Cache bounds and support-hand grip anchors when baking. Update transforms only
  for the parts affected by animation; avoid rebuilding maps or recalculating
  static face normals each frame.
- Load the assets needed by registered content rather than eagerly parsing the
  entire historical library. A list of resources in a JAR does not require loading
  their meshes into memory.
- Keep extracted render state detached from live gameplay state and manage GPU
  buffer lifetimes across reloads. Use Blaze3D-compatible buffers/pipelines.
- Cache/validate definitions and resolve identifiers outside tick/frame hot paths.
- Keep weapon authority on the server. Replicate state changes with bounded
  payloads; measure traffic before introducing prediction or compression.
- Measure startup/reload time, frame CPU time, allocation rate, draw calls and GPU
  memory for the same scene before claiming performance wins.

Mesh baking can remove redundant work and invalid geometry. It cannot infer an
artist's intended topology or automatically solve intersecting surfaces.

## Parity sequence

1. Platform build, generated resources, client/server bootstrap (checkpoint 1).
2. Definition loading and validation; weapon/ammunition registry and item groups (checkpoint 2).
3. Weapon components/state, server behavior and typed networking (checkpoint 3).
4. Audio, basic weapon visuals, then modern GPU mesh baking.
5. Body/armor pass, camera and both arm attachments using the retained maths.
6. Input, ADS, animations and HUD; multiplayer and dedicated-server checks.
7. Profile and cleanup; compare against 1.20.1 and tag playable parity.

After multiplayer acceptance, profiling and safe cleanup, **menus are the first
post-migration priority**, using the original mod's UI as the reference rather
than vanilla menus. New vehicles, drones, mobs and atmosphere systems follow later.

## Checkpoint 2: catalogue and registry

`mod_version` is `0.2.0-migration.2`. The Gradle, JDK and Fabric pins are unchanged.
The generated weapon catalogue moves to format version 2 with an `ammunition` array.
Source `content/` files are unchanged, including the four weapon definitions and
the exact first-person transforms. The generator derives shared ammo item metadata
and preserves sixteen-item ammo stacks. FAMAS and Custom FAMAS share `famas_mag`
while retaining capacities of thirty and thirty-two on their own weapon definitions.

The catalogue is immutable and loads once on the common initializer. Its records
use validated namespaced strings independently of Minecraft bootstrap; registration
converts each ID to a Mojang `Identifier` and sets the item `ResourceKey` on its
properties before construction. Whole-catalogue validation and asset existence
checks precede item registration; registry conflicts are preflighted. Geometry and
animation files are not parsed during this stage. The empty module scaffold remains
excluded. New creative tabs use the current Fabric creative-tab API on both sides.

Optional defaults preserve the prior semantics: absent burst size is one, absent
spawn-loaded is false, hitscan-only projectile fields default to zero, and absent
sprint uses hip pose/arm rotations. Required fields and invalid values fail with a
catalogue/weapon diagnostic. This is bundled startup data, not yet a reloadable
server registry; client/server gameplay definition agreement will accompany the
networking port.

`./gradlew verifyWeaponCatalog` runs automatically from `check`/`build`. The tests
load the actual generated catalogue, verify shared ammunition and pose fallback,
reject fifteen malformed catalogues, check locale-independent enum parsing,
immutability and missing-asset diagnostics. Existing arm tests still pass. The
migration audit remains at 217 known errors and zero new ones; all 6,201 canonical
files match the original upload.

All main and client Java sources compile with Java 25 against the published 26.3
client, Fabric Loader 0.19.5 and Fabric API 0.161.0+26.3 dependencies. For the direct
compile check, the creative-tab access changes published in Fabric's class-tweaker
file were applied using Fabric Loader's own class-tweaker implementation, as Loom
would do. No test Minecraft classes were substituted. Full Gradle execution and
an actual client/server launch remain unverified here because the runner blocks
Java's network access.

Apply the checkpoint 2 ZIP over the existing migration foundation. No deletions or
environment changes are needed. Do not rerun the checkpoint 1 source-removal script
after restoring the new data and item classes.

Local acceptance:

```bash
./gradlew clean build
./gradlew runClient
```

In creative mode, find the two Decimation tabs. Confirm four named weapon icons,
three ammunition icons, weapon stacks of one and ammo stacks of sixteen. `/give`
IDs are `decimation:honeybadger`, `decimation:famas`, `decimation:crossbow`,
`decimation:famas_custom`, `decimation:honey_badger_mag`, `decimation:famas_mag`
and `decimation:ammo_boltrounds_bullet`. Held weapons are flat inventory models at
this stage. The startup log reports four weapons and three ammunition items.

## Checkpoint 3: state, server gameplay and networking

`mod_version` is `0.2.0-migration.3`; toolchain pins and canonical `content/` files
remain unchanged. Overlay this ZIP on checkpoint 2. No files need deletion.

`WeaponState` is an immutable record stored in persistent, network-synchronized
`decimation:weapon_state`. Item properties supply cached spawn-loaded defaults.
Reads are side-effect free and normalize magazine/chamber/mode against that weapon's
definition. Writes occur on the logical server. Vanilla stack synchronization owns
persistent state; presentation event packets never overwrite client stack data.
Old 1.20.1 NBT and old saved worlds are not migrated; use the separate 26.3 run dirs.

The pure `WeaponCycle` keeps fractional tick deadlines, preserving 800/1000/400 RPM
over continuous fire at 20 TPS instead of losing credit at every shot. There is at
most one shot per player per server tick in this checkpoint; every current weapon
is below 1200 RPM. Semi presses survive cooldown, burst releases finish the existing
burst, and idle time cannot accumulate a volley. Empty automatic fire emits one dry
event per press. Switching the selected stack/dimension resets input and cancels
reload/burst while retaining the player's shot deadline. Death, disconnect and server
shutdown clear or cancel sessions. Cooldown is session data, not a saved component.

Reloads use server ticks and consume one matching ammo item only at completion.
Ammo availability is checked both at start and completion; removing the supply
cancels completion. Creative reloads are free. Tactical reloads retain the chambered
extra round, while an empty rifle reload transfers one round out of its new magazine
into the chamber, matching 1.20.1. Crossbow reloads one bolt. Held automatic input can
resume after reload completion; switching stacks discards that input.

Typed input payloads contain only a hotbar slot, weapon ID and four input/command
bits. The server verifies the selected main-hand item and player state, accepts at
most sixteen input messages per player per tick, and executes mode/reload commands
at most once each tick. Clients transmit edges and a twenty-tick held-input heartbeat;
the server clears held input after sixty ticks without accepted input. Release input
is still honored when the budget is exceeded. Damage, aim direction, spread, ammo,
cadence and hit resolution are server-owned. A protocol/SHA-256 catalogue handshake
at join gates input and rejects mismatched client/server builds. This checks gameplay
and presentation definitions, not the bytes of every asset; it is a compatibility
check, not trust in a client's gameplay calculations.

Hitscan makes one broad-phase entity query between the server eye position and the
nearest solid block. Collisions are sorted and each entity is damaged at most once
per shot, preserving entity penetration/retention, falloff and the upper-22% head
test. Vanilla armor and PvP rules remain active. Generated `decimation:bullet` damage
and its additive `minecraft:bypasses_cooldown` tag remove the melee hurt-frame gate;
automatic gun damage is therefore intentionally more consistent with configured
RPM than the archived player-attack damage source. No armor bypass is introduced.
Crossbow uses the actual 26.3 arrow entity with archived velocity/divergence and
base-damage semantics. It cannot yield free vanilla arrow pickups. Projectile falloff
and head multipliers were absent from the archive and remain absent in this port.

Client input uses vanilla SDL3-aware key mappings: left mouse fires, right mouse aims,
R reloads, B cycles fire mode. A client-only mixin cancels vanilla attack, continuous
mining and block/item use while a weapon is held, including air swings. Common
attack callbacks also reject vanilla block/melee attacks on the server. Menus release
input; only main-hand weapons are handled. Existing inventory textures still render
when held. ADS currently affects spread, with custom scope/FOV/recoil visuals pending.
The vanilla action bar and tooltips show ammo/mode/reloading as temporary feedback.
Audio, OBJ/DANIM rendering and the final HUD follow this gameplay checkpoint.

Checks added to `build`:

```bash
./gradlew verifyWeaponBehavior
./gradlew verifyWeaponCodecs
```

Behavior tests cover actual weapon definitions, chamber transfers, per-minute RPM,
semi/burst/automatic transitions, inherited switch cooldown, reload deadlines,
dry-event suppression and aim/falloff boundaries. Codec tests use the actual
persistent codec, Minecraft byte buffers and payload stream codecs for round trips,
size bounds, catalogue agreement, invalid flags/slots/enums/identifiers, oversized
strings, negative state and truncated input.

Validation performed for checkpoint 3: all main/client/test sources compile with
Java 25 against the genuine published Minecraft/Fabric dependencies, with the same
published creative-tab access transformation used for checkpoint 2. The new client
mixin also passes the real Mixin annotation processor; its three target methods
exist with matching signatures in the 26.3 client. Catalogue tests pass (four weapons,
three ammo items, fifteen malformed catalogues rejected), behavior tests pass
3,849 assertions and real codec tests pass 62 assertions. Both preserved arm suites
pass. Resource generation succeeds; the audit remains at 217 known findings and
zero new ones. These checks do not establish in-game mixin or multiplayer acceptance.

Local acceptance after `./gradlew clean build` and `./gradlew runClient`:

1. Fire each weapon in semi; holding left mouse fires only one shot. B through the
   other modes; verify FAMAS bursts of three and continuous automatic fire.
2. In survival, reload with matching ammo and count exactly one item consumed on
   completion. Try the wrong ammo, no ammo and removing ammo during the timer.
3. Compare an empty rifle reload with a tactical reload (20 vs 21 Honey Badger
   rounds, 30 vs 31 FAMAS rounds). Crossbow loads and spends one bolt.
4. Switch hotbar slots/drop/move a weapon while reloading or bursting; verify
   cancellation and no carried burst. Close/open inventory and disconnect while
   holding fire; input must stop rather than stick.
5. Drop/pick up weapons, move them through a container, save/reopen the world and
   check the same ammunition/fire mode. Bar/tooltip reads must not refill weapons.
6. Fire through empty space, toward walls, at a single target and toward aligned
   targets. Check falloff/head hits, one hit per target and entity penetration;
   solid blocks stop hitscan. Crossbow projectiles should fly with no arrow pickups.
7. Run a dedicated server and two clients with this same patch. Check ownership,
   survival ammo/state agreement and PvP-disabled/friendly-fire protection. Change
   a definition in one build and confirm the join fails with the mismatch message.

Dedicated/integrated runtime acceptance, real multiplayer latency and mixin
application still require these local runs. The runner cannot perform a full Loom
build/game launch because Java dependency downloads are blocked here; direct Java
compilation and deterministic/codec checks are reported separately.

## Checkpoint 4: positional weapon audio

The shared sound registry resolves 27 events once at startup. Clients use cached
event references and Minecraft's sound engine, including the Players volume slider
and resource reloads. No legacy decoder, sound buffer cache, client cue timer or
animation parsing is reintroduced on the runtime path. The server sends a typed
cue/weapon/dimension/position payload only to verified nearby listeners, with one
sample per recipient. The shooter hears one copy. Protocol 2 requires this patch
on both client and server; older builds receive a version/mismatch disconnect.

Unsuppressed shots select their distant sample at 32 blocks. Archived volumes are
preserved: near 4, distant 8, suppressed 2, other cues 1. Their vanilla attenuation
distances are 64, 128, 32 and 16 blocks respectively. Honey Badger always uses its
suppressed primary. Each shot reuses at most two payloads for nearby listeners;
there is no cloned recipient set or per-listener registry scan.

One cursor per active server reload reads the already-compiled cues. Honey Badger
and FAMAS use magazine out/in at ticks 5/40 and rack at 44; Custom FAMAS uses
9/28/37; Crossbow inserts at 40. The rack cue is skipped when a round remains
chambered. Cues due at the completion tick are emitted before finishing the reload.
Cancellation drops future cues when switching stacks/slots/dimensions, dying or
disconnecting. An already-started one-shot sound finishes normally. Dry-fire
suppression and mode request limits still follow the existing server state engine.

Eight weapon samples are stereo in canonical content. Minecraft positional playback
requires mono: the generator uses ffmpeg/libvorbis to downmix only runtime copies,
preserving source files and sample rates. All 28 referenced generated samples are
validated as mono Vorbis. Install ffmpeg on PATH before building; missing ffmpeg or
a conversion failure produces a clear build error. This is a build dependency and
adds no runtime decoding work beyond the vanilla sound engine.

`verifyWeaponAudio` is required by `check`/`build`. It checks exact source cue ticks,
empty/tactical reloads, duplicate suppression, cancellation, catch-up and boundary
cues, listener distance selection, volumes/ranges, sound event resolution and the
actual generated audio headers. The prior empty Gradle test-discovery fix remains;
all standalone verification programs still run through their JavaExec tasks.

Validation for checkpoint 4: common/client/test sources compile with Java 25 against
the genuine 26.3 Minecraft/Fabric dependencies. Audio passes 615 assertions; behavior
passes 3,849; real codecs pass 90, including all audio cue variants, maximum ID
lengths, finite coordinates, invalid enums and truncated/oversized packets. Catalogue
checks pass for four weapons/three ammo items and fifteen rejected malformed inputs.
Generation succeeds with eight downmixes and 28 mono samples. The migration audit
has 217 known findings and zero new ones. Canonical content remains unchanged.
A full Loom build/game launch and multiplayer audio acceptance remain local checks.

After building and launching locally:

1. Fire all four weapons; check a single firing sound per shot, with Honey Badger
   suppressed. Empty a weapon and hold fire; dry fire must not repeat every tick.
2. Cycle modes with B. Reload with R and compare empty and tactical rifle reloads;
   rack should sound only for the empty one. Crossbow should play its load cue.
3. Switch slots halfway through reload and listen past its original end; no pending
   insertion/rack should play. Repeat while changing dimension or disconnecting.
4. With two clients and the same build on a dedicated server, listen to FAMAS at
   approximately 10, 40 and 100 blocks. Check near/distant selection, attenuation,
   and no overlapping near/far copies. Honey Badger should fade within 32 blocks.
5. Adjust Players volume and use vanilla resource reload; sounds should remain
   functional. Aiming currently changes spread; ADS, recoil and 3D feedback are
   still pending the renderer checkpoint.

## Checkpoint 5: weapon geometry and visual feedback

This checkpoint uses Fabric's preparable model-loading plugin and Minecraft 26.3's
item model / special renderer / submit-node interfaces. It replaces only the four
weapons' baked item models, delegates GUI views to their original icon models and
supplies OBJ geometry for other views. The new client-only mixin targets the actual
`FirstPersonHandsAndItemsRenderer.submitHandsWithItems` method; regular items keep
vanilla rendering. Main-hand weapons suppress vanilla hands/offhand display and
swing transforms. There is no port of the old framebuffer/body pipeline yet.

The temporary camera attachment converts unchanged definition-owned hip/ADS/sprint
transforms to camera space, retaining the archived model-forward yaw and weapon
size/hip adjustments. Left-hand views mirror this attachment. Third-person views
use the definition-owned third-person transform; drops and displays are centered
with baked model bounds. This establishes geometry and feedback, not visual parity
with the accepted full body/arm pose. Scope/FOV behavior and breathing remain
pending. All retained rig/support solver sources are unchanged and both suites pass.

Resources are read from the model reload's own resource manager, so active resource
packs and F3+T replace the baked generation together. No persistent global model
cache can retain stale mesh/animation data. Invalid required geometry/animations
fail preparation with the weapon identifier. The four weapons load once per reload;
the other 229 OBJ files are not parsed merely because they exist in content.

During preparation, Newell normals are computed once per original polygon,
preserving the shared normal on warped quads. Quads remain four-corner primitives;
triangles are padded safely for the quad layer. Fourteen source zero-area faces are
skipped exactly as in the archive (FAMAS 4, Honey Badger 5, Crossbow 5, Custom FAMAS
0). Indexed attribute vertices/UVs and bounded sparse DANIM tick tracks are immutable.
Parts with identical fire/reload/rack motion are combined before rendering:

| Weapon | Source OBJ objects | Baked draw groups | Non-degenerate faces |
| --- | ---: | ---: | ---: |
| Crossbow | 162 | 2 | 967 |
| FAMAS | 133 | 1 | 794 |
| Honey Badger | 162 | 2 | 967 |
| Custom FAMAS | 3 | 2 | 1,298 |

One extracted frame captures the view transform, interpolated motion and animation
sample. Submit/draw callbacks use those snapshots and baked part references; they
do not read the live player, Minecraft singleton, network state or resource manager.
There is no per-face pose stack walk, normal calculation, animation scan or transform
hash map. Normal/glint/outline submissions use genuine quad render pipelines and
write their required vertex attributes. Vertices still stream through Minecraft's
managed feature renderer each frame; persistent GPU meshes are a later step. Draw
group reduction is measured, but runtime CPU/GPU timings and FPS are not yet measured.

Local aiming/sprint motion uses definition ADS timing; confirmed FIRED events add
archived recoil values and start fire animation. Reloads use source tracks and
overlay empty-reload rack motion near the end. Tactical reloads skip rack. Opening
menus releases ADS, switching slot/weapon/world clears local presentation, and
reload completion/cancellation removes the pending animation. Remote tracked players
can animate too; receipt-time starts preserve opening frames under latency. These
changes do not predict ammunition or damage and do not change protocol 2.

Some source tracks refer to absent OBJ groups: for example FAMAS has magazine/slide
tracks without matching `ammoModel0`/`slideModel*` objects. The files and mappings
remain unchanged; matching model/root tracks run and all guns receive recoil. Full
part mapping recovery is deferred until there is a visual/source reference. Arm
tracks remain compiled for the later body/arm port.

`verifyWeaponVisuals` is required by `build`/`check`. Validation passes 290,721
assertions over actual OBJ/DANIM assets, polygon boundaries, normals, indices,
batching, malformed data, sparse/fractional rotations, pose interpolation and recoil.
Recording collectors/consumers exercise real submission callbacks for normal,
glint and outline geometry, verifying complete attributes, corner counts, restored
pose stacks and deferred snapshot stability. Real 26.3 pipeline formats/topologies
are also checked. Main sources are unchanged apart from metadata; all current
client/test sources compile with Java 25 against the genuine published dependencies.
The new mixin passes the real Mixin annotation processor with its target signature
verified in the game jar. Audio (615), behavior (3,849), codec (90), catalogue and both
arm suites also pass. The content audit remains 217 known / zero new findings; all
6,201 canonical files match the original upload. The empty Gradle discovery fix is
retained. A full Loom build/game launch remains unverified in this runner.

Local acceptance after applying this patch on `migration`:

1. Run `./gradlew clean build` then `./gradlew runClient`. Check the four geometry bake
   lines and four `Bound animated OBJ item model` lines, with no model/texture errors.
   Check inventory icons remain correct.
2. Hold each weapon, aim with right mouse, sprint and fire. Check visible movement
   and recoil, no vanilla punch and no shredded geometry. Camera placement is an
   interim attachment; record any clipping/orientation issue for the body/arm step.
3. Reload empty and tactical rifles; switch slots midway to cancel. Matching
   magazine/root tracks should move, with the missing FAMAS groups limitation above.
4. Toggle the main arm setting, F5, drop/pick up the weapons, and place them in item
   frames. Verify texture, shape and orientation in each context.
5. Use F3+T, optionally with a weapon texture override resource pack, and verify
   the new resources appear with no stale mesh/animation generation.
6. Run two clients on a dedicated server; observe firing/reloading on the other
   player and check cancellation. Frame timing, resource reload during multiplayer
   and actual outline/glint appearance still require an in-game acceptance run.

## Checkpoint 5.1: item model binding correction

The first client acceptance log exposed four `Atlas not initialized` failures in
`WeaponItemModel` construction. Geometry preparation succeeded, but the after-bake
callback read the live `SpriteGetter` before atlas upload. Fabric retained the
original icon model after catching each callback error, disabling all OBJ visuals.

The wrapper now resolves its particle material once through the reload's prepared
`ModelBaker.materials()` cache, using the generated `decimation:item/<weapon>` icon.
It does not access the live atlas during baking or add per-frame sprite lookups.
Each successful binding logs `Bound animated OBJ item model: <weapon>`.

Client and test sources compile against the real 26.3/Fabric jars. The visual suite
passes 290,745 assertions, including the after-bake callback for all four weapons
with a live sprite getter that throws the exact acceptance error. Prepared particle
materials are supplied by a test fixture; this is not a GPU/client launch test.
GUI delegation, unrelated item identity and existing geometry/submission checks
also pass. Restart and run the in-game acceptance steps above to verify visuals.
No canonical content, weapon values or gameplay protocol changed.

## Checkpoint 5.2: third-person hand basis

Client acceptance confirms OBJ rendering, sprint/aim motion, DANIM tracks and
dropped geometry after the binding fix. First-person hip/ADS placement remains the
temporary camera attachment; dropped-item presentation still needs visual tuning.
The weapon was reported invisible from the one F5 view tested.

The third-person transform omitted the conversion into vanilla's item-in-hand
basis. After `ItemInHandLayer`'s -90-degree X and 180-degree Y rotations, the OBJ
muzzle axis pointed downward. Add a 90-degree X basis rotation before applying the
unchanged definition transform. The muzzle now points forward, with OBJ up aligned
to player-model up. This is an attachment correction; full third-person weapon arm
poses will follow separately.

The regression uses real 26.3 `PlayerModel` meshes and `translateToHand`, then the
adult vanilla hand-layer operations. It fails before the fix and passes afterward
for four weapons, left/right hands and classic/slim skins, including muzzle clearance
in front of the hand. Client/test compilation and 290,841 visual assertions pass.
This does not establish in-game F5 visibility: check both front and rear F5 views
after restarting, while turning and walking. If still absent, capture both views
and the current log so submission/occlusion can be distinguished from placement.
No canonical content, weapon values or gameplay changes are included.

## Checkpoint 6: captured body and weapon rig

Client acceptance established F5 visibility after 5.2, with unsuitable temporary
placement. This checkpoint ports the accepted rig rather than retuning content.
The local first-person frame captures torso/legs without head/hat/helmet, firing
arm, support arm, skin layers and chest/leg/foot armor. Classic and slim models are
baked once per entity-model generation. Vanilla equipment rendering retains dyes,
trims and foil effects. Ordinary items and occupied offhands have privately extracted
item states; vanilla camera hands are suppressed only when a body frame exists.
Spectating, death, sleeping, detached cameras and scoping leave no active body frame.

The accepted 3/16 camera offset, 0.1 look factor, 0.68 arm width, shoulder offsets,
upward clearance, measured-eye downward anchoring and hip size/length corrections
are preserved. Definition-owned hip/ADS/sprint arm rotations feed the firing arm;
recoil is applied there once. The gun uses that hand instead of the temporary camera
attachment. Its animated root produces the same-frame support target, with the
accepted one-pixel advance and 2.5-pixel drop. Support reach uses the unchanged
solver and is disabled when the offhand is occupied. Grip geometry is prepared once
per resource reload, alongside the existing OBJ/animation bake.

External players now receive weapon arm poses from extracted motion (local ADS/sprint,
remote hip). The 5.2 extra X-basis rotation is removed together with this raised-arm
pose; retaining both would point the gun upward. 26.3 sleeves and hats are nested
children and inherit their parent's pose, instead of copying it twice.

Draw submissions retain frozen cuboid transforms sharing the baked cube geometry.
Deferred animation setup cannot reset or reuse the player's live limb poses. A private
feature dispatcher and staged vertex buffer prevent interference with the world's
prepared frame. The rig target copies world depth before the torso pass, renders arms,
armor and gun against it, then blends its color above the torso through the native
RenderPearl blit. Targets rebuild on resolution/format changes and close on shutdown.
No old OpenGL framebuffer calls or per-frame OBJ parsing are used.

**Remaining layer limitation:** the native color blit does not merge rig depth into
the main depth buffer. World-depth testing occurs in the rig target, but later effects
that sample main depth still see torso/world depth at rig pixels. Custom depth
composition, translucent offhand ordering and visual parity need GPU acceptance.
Dropped-item tuning, final HUD, scope/FOV and breathing remain pending.

Validation: Java 25 client/test compilation against the actual 26.3/Fabric jars;
Mixin 0.8.7 annotation processing for all injection/accessor targets; 2,784 new
actual-cuboid assertions (classic/slim, left/right, -90 through +90 look angles,
nonuniform arm scale, hidden clothing, deferred model reuse); 290,861 weapon visual
assertions including four posed third-person weapons and prepared support grips.
The preserved rig/support solvers and catalogue/gameplay/codec/audio checks also pass.
Full Gradle build and GPU launch remain unverified here: the wrapper's distribution
download fails with `Network is unreachable`. All 6,201 canonical content files
remain byte-identical to the uploaded full repository.

Apply the patch over checkpoint 5.2 on `migration`, reload Gradle after the task
addition, then run `./gradlew clean build` and `./gradlew runClient`. Confirm the
`captured body/arm rig ready` log, then check:

1. All four weapons: hip, ADS, sprint, fire/recoil and reload/cancel. The support hand
   should follow the animated gun without lag; Custom FAMAS retains its own arm values.
2. Look down/up through the full pitch range, turn while standing and walking, crouch,
   jump and swim. Check torso visibility, gun placement and clipping.
3. Both main arms and classic/slim skins, clothing toggles, colored/trimmed/enchanted
   chest armor, leggings and boots. No first-person head or helmet should appear.
4. Fill the offhand, switch to ordinary items/empty hands, and check held-item visibility.
   Test first person and both F5 views; head/helmet stay normal in F5.
5. Stand against walls and near entities: rig pixels should remain world-occluded.
   Resize the window and reload resources; check for missing skins or stale geometry.

## Checkpoint 6.1: transparent rig clear

First client acceptance of checkpoint 6 showed arms over an obscured world. The
rig color target was cleared with `new Vector4f()`, whose JOML constructor sets
the fourth channel to 1. The native source-alpha blit therefore replaced every
uncovered scene pixel with opaque black. This was a compositing error, not a
camera selection. Clear the target explicitly to RGBA `(0, 0, 0, 0)`.

The new regression fails with the previous clear value and passes with the fix.
It checks the actual native blit pipeline's blend factors and destination-alpha
write mask, plus scene preservation at cleared pixels and opaque arm color.
All 2,789 body assertions pass, including the existing pose/clothing cases.
Java 25 client/test compilation passes against the actual 26.3 jars. This runner
still cannot launch the GPU client; restart locally and confirm that the world,
body and gun remain visible. No rig positioning, content or gameplay changes.

## Checkpoint 6.2: support layering and hand attachment

Client acceptance confirms the world is visible after 6.1. Lower the first-person
gun one model pixel in its firing-hand attachment space, keeping the arm pose and
weapon scale unchanged. The support target follows that lowered gun. Move the
first-person support arm, nested sleeve, its chest-armor limb and occupied offhand
item to the same depth-tested pass as the torso. Only the firing arm, its armor
and gun remain in the composited rig pass. Main-hand selection mirrors these roles.

Third-person separation had a second centering error: 26.3's
`ItemTransform.NO_TRANSFORM.apply` still translates a special model by
`(-0.5, -0.5, -0.5)` inside the item layer. The earlier hand regression omitted that
operation. Cancel it once at the outer item-model level, outside left-hand mirroring.
The gun origin now meets the actual vanilla hand attachment for both arms and
classic/slim skins, with unchanged definition-owned weapon rotation and scale.
First-person direct rig submission does not use this item-layer centering path.

The external support arm rotates toward a point one model pixel ahead of the
firing hand's vanilla item origin, recalculated from the current firing-arm pose.
This applies whenever a main-hand weapon is posed, including an occupied offhand.
Its shoulder pivot and all three scale values remain unchanged: no sliding or
stretching in third person. A straight arm can only reach its normal length;
if the target lies farther away, the hand points toward it at that fixed reach.
First-person barrel-grip solving retains its separate existing reach behavior.

Validation: client/test compilation against actual 26.3 jars; 303,869 weapon visual
assertions, including the centering regression (fails before the fix), stationary
shoulders/unit limb scales, fixed support reach and target direction across four
weapons, both arms/skins, hip/ADS/sprint blends and head pitch/yaw. All 2,789 body
assertions pass. Comparing the new first-person attachment with checkpoint 6.1
passes 1,152 matrix checks: only the requested one-pixel lowering changes.
Canonical content and gameplay are untouched. GPU layering/placement still require
client acceptance: apply over 6.1, restart, and check support/torso occlusion, the
lowered gun and both F5 views while turning and aiming.

## Checkpoint 6.3: support grip half a pixel higher

Client acceptance of 6.2 likes the support shoulder and torso-layer result, but
reports the straight support arm crossing the torso when looking down. Keep that
layering and shoulder placement. Raise only the first-person support grip half a
model pixel toward the top of the barrel, reducing its underside drop from 2.5 to
2 pixels. The existing conversion through weapon presentation scale preserves
that half-pixel movement in hip, ADS and sprint poses. Forward grip advance,
firing arm, gun placement and third-person posing stay unchanged.

The straight shoulder-to-grip segment can intersect the torso at downward angles.
Preserving both endpoints while routing around the body would require a bent-arm
representation; this patch does not claim to resolve that clipping. Third-person
support shoulder and fixed limb length remain unchanged. No content or gameplay
changes. Apply over 6.2, restart and check the support hand on the barrel.

## Checkpoint 6.4: upper torso shares the arm pass

Partition the first-person torso at local model Y = 4: its upper four pixels,
matching jacket section and chest/leggings armor sections share the depth-tested
rig pass with both arms, their sleeves/armor, gun and occupied offhand item. The
lower torso and legs render underneath. Copy world depth before drawing the lower
body, preserving world occlusion while excluding belly depth from the arm pass.
The upper torso can still hide the shoulders at the collar. This is a rendering
partition, with no elbow bend or shoulder, grip, weapon-placement or third-person
pose changes. Empty-hand and ordinary-item body rendering remains unsplit.

Cache each cuboid's upper/lower exterior faces per model generation. Interpolate
UVs at the split, retain face winding/normals and add no internal cap faces.
Resource reload clears the geometry cache; deferred submissions retain captured
geometry and poses. No per-frame clipping and no content or gameplay changes.

Validation: all client/test sources compile against actual 26.3 jars on Java 25.
The 3,485 first-person body assertions cover classic/slim skins, jacket and both
armor inflations, preserved exterior area, interpolated UVs, cached reuse and
native rendering of captured split geometry after cache/pose changes. All 303,869
weapon visual assertions also pass. GPU compositing still requires local client
acceptance: apply over 6.3, restart, look down/up and aim with/without armor. Confirm
that collar occlusion remains while the support arm draws over the belly. The
four-pixel cut is an initial boundary to adjust from the visible result.

## Checkpoint 6.5: horizontal torso tops and lateral support grip

Replace checkpoint 6.4's upper four-pixel band with only the original horizontal
top face of each torso cuboid. Skin, jacket and chest/leggings armor top faces
share depth with both arms; every vertical face and bottom face remains in the
underlying body pass. Preserve each original polygon, including its exact UVs,
vertices, normals and winding. Cache the two face selections per model generation;
no polygon clipping or added faces are needed. Empty-hand rendering is unchanged.

Move the first-person support grip 0.4 model pixels right across the barrel in
the gun's local sideways axis. Divide by presentation size so the offset remains
0.4 pixels in hip, ADS and sprint poses. Reverse the local offset sign for the
mirrored left-hand attachment to preserve rightward movement relative to the
barrel's forward/up directions. Existing forward and vertical grip offsets,
shoulders, gun placement and third-person poses remain unchanged. No elbow bend
or content/gameplay edits. The reported mirrored weapon geometry is the next
renderer task and is not flipped in this patch.

Validation: client/test compilation against actual 26.3 jars on Java 25; all 3,129
body assertions pass, including top-only geometry, complete unchanged source
faces, skin/jacket/armor native emission and deferred reuse. A focused barrel-basis
check passes 288 assertions across four weapons, both firing hands and hip/ADS/
sprint blends: the sideways movement measures 0.4 pixels, points right and has no
forward/up component. GPU acceptance remains local: apply over 6.4, rebuild,
restart, check collar/support-arm occlusion while looking down and the lateral
grip placement while aiming and sprinting, with/without armor.

## Checkpoint 6.6: arm-only rig and weapon sideways reflection

Remove the torso face partition. The complete torso, jacket and torso armor
render in the underlying body pass; both arms, sleeves, chest-armor limbs, gun
and occupied offhand item remain in the rig pass. Delete the unused TorsoSlices
source and remove its snapshot mapper and tests. Retain existing arm placement
and the 0.4-pixel rightward support-grip adjustment from checkpoint 6.5.

Correct the reported mirrored 3D weapons by reflecting recovered OBJ Z, their
sideways axis, during resource preparation. Forward +X and upward +Y remain
unchanged. Reverse face/UV index order around the original fan anchor to preserve
outward winding and the same triangulation. Bake normals, bounds and support grip
from the corrected geometry. The mesh is converted once per resource generation;
draw-group batching and all canonical content files remain unchanged. First-person,
third-person, dropped and other 3D display contexts consume that same baked mesh.

Reflect sampled source animation transforms as well: translation (x,y,-z), rotations
(-pitch,-yaw,roll). Apply this to both root and part motions, including rack motion.
Converting after interpolation preserves the source shortest-arc choice, even at
exactly 180 degrees. The support target uses the same corrected root. Presentation
offsets, firing-hand placement, gameplay and ordinary skin rendering are unchanged.

Validation: all client/test sources compile against actual 26.3 jars on Java 25.
All 2,789 body and 336,637 weapon visual assertions pass. Added checks cover all
real weapons' reflected vertices/UVs, outward normals, bounds, grip positions and
retained batching. An asymmetric animated fixture verifies actual submitted root/
part transforms at fractional fire, reload and rack frames, including the
180-degree interpolation boundary. Existing third-person shoulder/reach, hand
attachment and deferred draw checks pass. GPU/client acceptance is still pending.

Apply over 6.5. From the repository root remove the obsolete source:

```sh
rm -f src/client/java/com/decimation/client/firstperson/TorsoSlices.java
```

Rebuild and restart. Check an asymmetric weapon detail (ejection port/charging
handle) in first person, F5 and a dropped weapon; fire/reload each weapon and check
the support hand follows it. Confirm both arms draw over the complete torso.

## Checkpoint 6.7: larger third-person and sideways dropped weapons

Multiply the existing definition-owned scale by 2.5 in both third-person hand
contexts, preserving firing-hand attachment offsets and direction. Apply the
same multiplier to dropped weapons. In the GROUND context roll the model 90
degrees around its barrel axis; center its horizontal dimensions and place its
lowest transformed extent at zero. Item-model bounds are prepared from that
same transform for native dropped-item spacing and height calculations.

Capture a weapon marker into each ItemEntityRenderState during extraction,
resetting it for every reused state. Redirect the actual 26.3 renderer's spin
call to zero only for weapons. Other dropped items keep native rotation; native
bobbing, item-stack bundle rendering, lighting, outlines and entity submission
remain available. This changes the weapon's orientation, not dropped-item
physics. First-person rig, other display contexts and canonical content are
untouched.

Validation: all client/test sources compile against actual 26.3 jars on Java 25;
new mixins pass the Mixin 0.8.7 annotation processor. All 336,661 weapon visual
assertions pass, including real weapons' sideways ground bounds, uniform enlarged
scale and horizontal barrel/up axes. Comparing with 6.6 passes 5,256 checks for
2.5x third-person basis, unchanged hand attachment and other contexts/first-person
rig. Native injection targets and redirect argument signature are verified.
GPU/client acceptance is still pending: apply over 6.6, rebuild, restart, check
F5 with either main hand, then drop each weapon and verify its size, sideways
orientation and stopped rotation. No source deletions in this checkpoint.

## Checkpoint 6.8: third-person backset and grounded drops

Move the third-person weapon backward four model pixels along its barrel axis,
before weapon scaling, in both hand contexts. Keep checkpoint 6.7's 2.5x scale
and all arm poses. First-person attachment and grip placement are untouched.

For dropped weapons, replace the native renderer's hover translation with
(-horizontal-center-X, -lowest-Y, -horizontal-center-Z), measured from its actual
item-model bounds. Those bounds include vanilla's item-layer centering transform,
so the rendered gun is centered over the entity rather than shifted half a block
sideways. Its lowest point aligns with the entity's foot plane, with no sine bob
or fixed hover gap. Keep sideways orientation and stopped spin; physical item
movement and collision still determine the entity's position. Other dropped items
retain their native hover/bob. No canonical content edits or source deletions.

Validation: client/test compilation against actual 26.3 jars on Java 25 and new
redirect validation with the Mixin 0.8.7 processor. All 336,709 weapon visual
assertions pass. Third-person tests include actual vanilla hand/item transforms
for classic/slim skins and both hands, measuring the four-pixel backward offset.
Dropped-weapon tests use native item-layer bounds and centering, then independently
emit transformed corners for all four weapons across different hover inputs:
lowest Y and horizontal centers are zero, with no bobbing. Reused non-weapon
states preserve native translation. Native redirect targets and complete handler
signatures are checked. GPU acceptance remains local: apply over 6.7, rebuild,
restart, check F5 placement and drop the weapons onto a flat floor.

## Checkpoint 6.9: stable body anchor and lower third-person gun

Client acceptance confirms F5 placement after 6.8, but reports a first-person
torso/perspective jump when equipping a gun. Inspection found an item-dependent
body anchor: its model rotation used body yaw, while its three-pixel camera offset
switched from body yaw to weapon rig yaw when a gun model was bound. That changes
camera-relative torso position at nonzero head/body yaw, without changing torso
scale. Keep body rotation and offset on body yaw for every held item. The firing
rig retains its existing independent yaw and offset. Skin, jacket, legs and torso
armor all inherit the corrected body root. Projection is unchanged; this fixes
the identified offset coupling, with full visual symptom acceptance still pending.

Lower the third-person gun 1.5 model pixels in its firing-arm attachment space.
Retain the four-pixel barrel backset, 2.5x size, orientation and support-hand pose.
First-person weapon transform and dropped-weapon placement are unchanged. No
canonical content edits, shader/projection changes or source deletions.

Validation: all client/test sources compile against actual 26.3 jars on Java 25.
All 44,837 body assertions pass. New tests exercise the production body-base
calculation with controlled renderer rotation/scale callbacks, real classic/slim
cuboids, both hands, four weapons, hip/ADS/sprint, head yaw/pitch and standing/
crouched body fixtures. Body/jacket/leg snapshots and anchors remain identical
before and after weapon posing. All 336,709 weapon visual assertions pass with
the updated actual vanilla third-person hand attachment expectations. Comparing
with 6.8 passes 5,832 checks: exactly 1.5 pixels lower in third person, unchanged
backset/scale, other item contexts and first-person weapon rig matrices.

Apply over 6.8, rebuild and restart. Switch between empty hand, an ordinary item
and a gun while looking down and turning your head; check body continuity with
and without armor. Check the lowered gun in F5. GPU acceptance is local.

## Checkpoint 6.10: reload direction and complete magazine assemblies

Local acceptance confirms checkpoint 6.9's body continuity and gun placement.
Reload inspection found two independent issues: converted ANIB animation samples
retain the old model-Y-down coordinates even though the OBJ exporter flips Y,
and split magazine objects only received animation on their `ammoModel0` member.
Convert legacy sampled motion to Y-up and preserve its authored left-hand path;
convert rotations consistently after interpolation. Native/Blockbench clips keep
their existing Z-reflected convention. Root and component motion use the same
conversion, including the support target's animated root.

Resolve numbered `ammoModel` objects to the `ammoModel0` assembly track when they
have no explicit track. Explicit component tracks retain precedence. This moves
all three Honey Badger magazine objects and the complete crossbow bolt together.
Bind the regular FAMAS's actual magazine object, `gunModel26`, to `ammoModel0`
during mesh preparation; its pistol grip and remaining receiver stay separate.
The custom FAMAS already labels its complete magazine correctly. Bindings are
baked into draw groups, with no per-frame name lookup or canonical content edits.

Validation: client/test sources compile against actual Minecraft 26.3 jars on
Java 25. All 337,297 weapon visual assertions and 44,837 body assertions pass.
Checks cover both animation source conventions, fractional interpolation and
180-degree rotation ties, complete assembly coverage, explicit-track precedence,
and actual renderer submissions moving each weapon's magazine/bolt left and down
relative to the first-person gun root. Static mirroring, third-person placement,
dropped bounds and body anchor regression checks remain passing.

Apply over 6.9, rebuild and restart. Reload all four weapons: check the complete
magazine/bolt moves together toward the left in front of the player, and check
the root tilt and return to rest. GPU/client acceptance remains local. No source
deletions are required.

## Checkpoint 6.11: reverse reload tilt and animate the support arm

Local testing reports that both legacy reload root tilt axes still run backward:
the muzzle tips down and the barrel rolls the wrong way. Reverse only the ANIB
reload `Model` X/Z rotations. Preserve its Y rotation, translations, magazine
component convention and full assembly bindings from 6.10. Fire/rack and native
Blockbench transforms retain their existing conventions. The support grip uses
the same corrected root as the submitted weapon.

Retarget the authored `OffHand` reload samples onto the support arm after its
normal grip pose, in first person and third person. Hand positions are treated
as player-model pixels, with the authored frame-zero offset removed. Apply its
rotation/translation delta to the existing shoulder-to-hand reach, then rotate
the straight arm toward that result. Preserve shoulder coordinates and all limb
scales; the animation adds neither shoulder sliding nor length stretching. Both
firing hands and classic/slim skins use this path. Occupied offhands skip the
reload override. Skin and sleeve/armor snapshots use the resulting arm pose.

Third-person extraction now retains the immutable animation sample alongside
the existing motion snapshot, including remote players, and resets it to rest
when no weapon is held. Honey Badger, regular FAMAS and crossbow contain OffHand
tracks; the custom FAMAS has none and keeps its existing support behavior. No
canonical content edits or source deletions are required.

Validation: all client/test sources compile against actual Minecraft 26.3 jars
on Java 25. All 365,103 weapon visual assertions and 44,837 body assertions pass.
New checks verify positive muzzle elevation and reversed barrel roll on the real
legacy clips, root/component independence, support movement at fractional frames,
the authored return key, unchanged shoulder/length/width/depth, both hands and
skin types, and unchanged idle/firing behavior. Existing magazine direction/full
assembly, body anchor, F5 placement and grounded-drop checks remain passing.

Apply over 6.10, rebuild and restart. Reload Honey Badger/FAMAS/crossbow in first
person and F5: check upward muzzle tilt, the reversed barrel roll and the moving
support hand returning to its grip. Confirm the complete magazine still withdraws
left/down. Check an occupied offhand too. Visual acceptance remains local; the
straight-arm retarget cannot reproduce an elbow bend or arbitrary wrist positions.

## Checkpoint 6.12: magazine handling, coupled reload and running fire

Reduce the authored support-hand translation and rotation deltas to one fifth
of 6.11. Prepare a magazine grip from the complete assembly's bounds once during
resource baking, and blend the support target from its tuned idle grip to that
point using the OffHand action, including the magazine's component motion.
Crossbow hide-frame sentinel translations are excluded from hand targeting.
Keep support shoulders anchored and third-person limb scales unchanged pending
the team's visual decision. The custom FAMAS has no OffHand timeline and retains
its existing support behavior. The straight-arm solver still cannot reach every
arbitrary distant point while keeping a fixed shoulder and length in F5.

Move the reload root rotation into the firing arm's attachment basis for both
first person and third person, preserving its shoulder and limb scales. Native
wrist orientation, mirrored handedness and the display rotation are included;
display scale is removed before solving the limb rotation. Capture that posed
hand for the weapon, and mark its frame so the special renderer applies the
remaining root translation and component tracks without rotating the gun again.
The firing hand and gun now turn together instead of separating during reload.
Magazine/rack part motion and the corrected tilt directions remain intact.
An occupied offhand skips only the support override, not the firing attachment.

Replace the archived lowered sprint presentation at runtime with an across-body
carry: the firing arm turns inward, with a small tucked weapon offset. Introduce
a separate running-fire blend that raises the weapon to its existing forward
hip-fire attachment while sprint remains active and ADS stays disabled. Trigger
input blends in over two ticks; releasing blends back over four ticks. Confirmed
running shots show the firing pose immediately. Reload suppresses trigger-driven
running fire. Slot/world resets clear the added state. Remote sprint presentation
uses owner sprint state and received shot animation; short shot clips retain a
four-tick presentation window to reduce carry/fire flicker between shots. Server
shot rules, cadence and bullet direction are unchanged: firing while sprinting
was already supported, with no ADS accuracy benefit.

Delay the server reload RACK cue by one tick (50 ms at normal tick rate). Source
cue definitions, magazine sounds and completion ticks are unchanged; conditional
racking, duplicate suppression and cancellation still apply. No client timer or
new audio asset is introduced. Canonical content and network formats are untouched.
Apply the same rebuilt code on client and server for multiplayer audio timing.

Validation: common/client/test sources compile against actual Minecraft 26.3 jars
on Java 25. Weapon visual checks cover actual across-body barrel direction for
both hands, the running-fire transition/reset, root transfer at fractional frames
for classic/slim skins and multiple head pitches, actual renderer submissions
without double tilt, magazine grip movement/return, and unchanged F5 support
shoulders/length. All 400,632 weapon visual assertions, 44,837 body assertions,
620 audio assertions and 3,849 gameplay assertions pass. Original 800/1000/400
RPM cadence remains verified. GPU acceptance remains local.

Apply over 6.11, rebuild and restart. Test reload in first person and F5; verify
the firing hand stays on the grip, support handling is lower/closer and the
magazine still moves left/down. Run with no trigger, fire while continuing to run,
release the trigger and stop running; check both hands and an occupied offhand.
Compare an empty-chamber reload's slightly later rack sound. No deletions needed.

## Checkpoint 6.13 — ADS and final reload-hand offset

Apply this combined patch over 6.12. During the authored reload hand action,
move the support target one player-model pixel down and half a pixel right
relative to the weapon. Normalize offsets by the captured weapon scale and mirror
the sideways offset for left-handed play. Idle/fire grips and third-person
shoulder anchoring and arm length are preserved.

Prepare immutable rear/front sight references from each reflected model when
resources bake. First-person ADS solves a rigid camera-relative adjustment from
the neutral firing attachment, interpolating the rear sight to the camera center
and orienting the sight line along camera forward. Move the firing arm and gun
with the same adjustment. Apply recoil and coupled reload rotation afterwards,
so aiming cannot cancel their motion. The body anchor and camera position are
unchanged. Sight references are inferred from the actual iron-sight/optic objects;
their final visual centering and eye relief need local acceptance.

Hook Camera.calculateFov before its projection is cached, blending to a modest
10 percent lower world FOV at full ADS. Wrap Fabric's native crosshair element,
hiding it at 95 percent aim progress and preserving native visibility otherwise.
Only the living local player in first person receives these effects. ADS releases
smoothly during reload; sprint and running fire keep their existing behavior.
No content, server gameplay, network formats, dropped or F5 weapon transforms
are changed, and no files need deletion.

Validation: all client and test sources compile against actual 26.3 dependencies
on Java 25. Mixin annotation processing validates the native calculateFov(F)F
injection target. All 402,146 weapon visual assertions and 44,837 body assertions
pass, including real sight anchors for all four weapons, both hands, multiple
camera orientations, alignment interpolation, rigid scale/handedness, animation
remaining visible after alignment, exact reload target offsets, FOV interpolation
and crosshair handover. A full Loom build/GPU launch remains unverified here.

Rebuild and restart locally. Test hip-to-ADS and release for every weapon, look
up/down, walk/crouch, and compare both hands. Confirm sight centering, comfortable
eye relief, visible recoil, reload returning to hip, crosshair restoration, running
fire, and unchanged F5/body framing. Test the reload-hand adjustment in first
person and F5; a fixed-length F5 arm can only turn toward the shifted target.

## Checkpoint 6.14 — accepted ADS and final grip tuning

Apply over 6.13. Shift the held weapon half a player-model pixel left in its
hand attachment, before left-hand mirroring, for first person and F5. Dropped
and display transforms are unchanged. ADS still solves from the resulting neutral
mount, keeping the accepted sight alignment while the firing hand moves with it.

Lower the support target during the reload hand action by one additional
player-model pixel: the cumulative offset is now two pixels down and half a pixel
right on the magazine. Idle/fire grips, shoulder anchoring, and F5 arm length
remain unchanged. No canonical content changes or deletions.

Validation: client sources compile on Java 25 against actual Minecraft 26.3;
402,146 weapon visual and 44,837 first-person body assertions pass. The real-model
ADS checks still cover both hands and camera angles, and reload checks now assert
the cumulative two-pixel downward offset. Final grip placement needs local visual
acceptance; the 6.13 ADS framing was accepted in game by the user.

## Checkpoint 6.15 — breathing, sway, relaxed carry and F5 aim

Apply over 6.14. Install this build on both client and server: the catalogue
handshake protocol is now 3. The input flags add RELAXED without changing the
packet layout; a new bounded clientbound carry-pose payload is registered.

Press G (rebindable in Controls) to toggle relaxed carry for the selected weapon.
The weapon rests across the body using the accepted sprint carry attachment.
Aiming, firing or reloading raises it; releasing actions returns it to relaxed
carry. Four tick steps from fully relaxed to ready cost approximately 200 ms
at 20 TPS, plus normal input latency. Partial lowering costs only the remaining
steps. The server owns readiness and gates all fire modes before ammunition,
cadence or shot resolution. A short trigger press stays queued while raising;
repeated packets cannot advance the clock. A short post-shot ready hold avoids
lowering between shots. Raising starts when relaxed carry is toggled off too,
so that toggling cannot bypass the cost. Weapon/slot/world changes reset the
selection. Existing input leases, reload cancellation and cadence guards remain.

Send carry updates only on state changes, to the owner and tracking players.
Seed the pose when an observer starts tracking and after catalogue verification,
so a player already resting does not appear ready to newly arriving observers.
Local poses predict the shared tick clock; remote changes interpolate over one
tick. No periodic full-player pose scan or per-frame networking is added.

Breathing uses a centered 4.4-second cycle. Ground movement adds gentle sway/bob;
smoothed, bounded view deltas add a little weapon lag. ADS reduces these offsets
to 20 percent. Reload suppresses ambient motion. First-person alignment solves
from the neutral attachment first, then applies ambient motion to the firing arm
and gun together; recoil and reload coupling still follow. The support arm follows
the resulting weapon target. Camera position/body anchor, mesh baking, dropped
poses and source assets are unchanged. Third-person arms also receive breathing
and sway, with fixed shoulders and fixed support-arm length.

Calibrate the third-person ready weapon to two degrees below head pitch for all
four models; keep vanilla head rotation intact. The archived first-person arm
pitch made regular rifles point upward in F5. First-person ADS anchors and FOV
are unchanged. Hide the native crosshair globally, including non-weapon items
and empty hands, as requested; other HUD elements are preserved.

Validation: actual Minecraft 26.3 common/client/test sources compile with Java 25;
runtime resources regenerate for 1,291 definitions and four weapons, including
the new control/status translations. All 405,962 visual, 44,837 body, 4,202 gameplay,
136 codec and 620 audio assertions pass. Checks cover both hands/classic/slim,
head-pitch calibration, fixed F5 limb length, carry interpolation/reset, all fire
modes waiting for ready, pending presses and repeated inputs, unchanged
800/1000/400 RPM cadence, bounded carry payloads/malformed inputs, reduced and
centered breathing, rigid ambient transforms, reload suppression and existing
ADS/reload/renderer submissions. Full Loom/GPU and multiplayer acceptance are
still local checks, not verified by the headless tests.

Test locally: stand still, walk, turn, ADS, reload and sprint/fire for every weapon;
compare F5 ready pose at level/up/down pitch and check armor/slim skins. Toggle G,
briefly click fire and hold fire, toggle back while lowering, aim from relaxed,
reload from relaxed, change slots, die/respawn and change dimensions. In multiplayer,
check an observer entering tracking range while you are already relaxed. Tune the
motion amplitudes and carry framing only after this visual test. No content changes
or file deletions. This completes the requested presentation step; dedicated-server
acceptance, GPU profiling and final migration cleanup remain separate acceptance
work.

## Checkpoint 6.16 — body-anchored relaxed pose and ADS arm appearance

Apply over 6.15. Relaxed carry now uses a dedicated, body-relative chest pose
rather than the sprint pose. Head yaw/pitch influence fades to zero with the
relaxed fraction in both first-person and F5 arms. The first-person rig parent,
shoulder solver and safety pitch also stop tracking the head while fully relaxed.
Turning the camera can change which part of the body is visible, but does not
rotate the resting arms with it. The dedicated mount keeps the barrel forward
of the shoulder across all four source models and both hands.

Keep breathing and walking sway. Remove look-delta lag while relaxed and blend
ambient motion axes from the camera to body orientation; full rest uses only the
body basis, including while looking up/down or behind. Raising smoothly restores
the accepted ready/ADS tracking. Sprint carry and its running-fire state remain
separate from relaxed carry. The server carry clock/raise delay is unchanged.

The ADS alignment is rigid and does not double model scale. Compensate the
close-camera appearance by blending the firing skin's X/Z thickness from .68
at hip to .34 at full ADS, retaining full arm length. Reset the reused firing
limb to unit scale before capturing the gun mount; apply skin thickness only
thereafter so gun size, sight anchors and alignment stay unchanged. Copy runtime
arm scales into the armor model too, since storePose does not retain them.
Support skin thickness and existing grip tuning are unchanged. Final apparent
arm size still needs local visual acceptance.

Validation: client sources compile against actual 26.3 dependencies on Java 25.
Real-model tests cover classic/slim and both hands, identical relaxed F5 limb
angles across head directions, body-only FP yaw and breathing axes, forward
barrel direction for every model, preserved breathing without view lag, ADS skin
narrowing, and the existing ADS/reload/motion geometry contracts. No canonical
content edits or deletions. Rebuild and test G while turning/looking up/down,
walking, crouching, switching to ready and ADS, and wearing chest armor.

## Checkpoint 6.17 — multiplayer lifecycle, late observers and Quick Play

Protocol 4 adds entity/dimension identity to weapon inputs and presentation, and
late-observer snapshots now include ADS and in-progress reload timing. Server
session rebinding cancels held intents on respawn even with the same stack; input
quotas survive weapon switching. Existing 6.16 placement and content are untouched.
See [multiplayer-6.17.md](multiplayer-6.17.md) for implementation, exact validation
limits, Quick Play configuration and the two-client acceptance matrix.

Cleanup is **prepared, not applied**. The stale 61-file removal manifest has been
reduced to the 33 obsolete paths; all 28 active overlapping paths are protected.
The existing wrapper previews by default, and explicit apply makes recoverable
backups instead of deleting sources. See [migration-cleanup.md](migration-cleanup.md).
No archive/live branch is changed. Menu restoration comes first after migration
acceptance; the recovered original-menu reference files are retained separately.

## Checkpoint 6.18 — flat torso rest and fire/ADS preference

The resting weapon counters the firing wrist angle to lie horizontally across
the torso in first-person and F5. The firing arm uses a natural independent
angle; support hands seek the barrel, with fixed F5 shoulders/arm length.
Fire deselects G-rest, while ADS temporarily raises the gun and retains the rest
preference. Accepted ADS/ready alignment and canonical content remain unchanged.

The 6.18 overlay includes 6.17 and applies directly over the tested 6.16.
See [rest-lan-6.18.md](rest-lan-6.18.md) for the exact behavior and planned LAN
acceptance with a second player. Cleanup remains preview-only until applied;
menus remain first after migration acceptance.

## Environment and server acceptance

Run `bash tools/dev/setup.sh`, source `tools/dev/env.sh`, then `./gradlew build`.
Confirm `./gradlew --version` reports Gradle 9.6.0 and Java 25. Generate game sources
with `./gradlew genSources`. Create a fresh 26.3 test world once using
`./gradlew runClient -PquickPlayWorld=`, then set its save folder as `quickPlayWorld`
for subsequent direct launches. Look for initialization and four-weapons/three-ammunition registration
message. Dedicated-server acceptance uses `./gradlew runServer`
with the ordinary Minecraft EULA setup and a separate test world.

## Checkpoint 1 validation history

The project-local installer downloaded and verified Temurin 25.0.4.1, then reused it
successfully on a second invocation. Both Bash and zsh environment activation work.
Gradle 9.6.0 reports Java 25.0.4.1, and the wrapper was regenerated by Gradle 9.6.0
with the official binary distribution checksum. The two bootstrap entrypoints
compile with `javac --release 25` against Fabric Loader 0.19.5 and SLF4J 2.0.17.
The existing arm-solver checks pass on Java 25. Resource generation succeeds for
all 1,291 definitions; all seven generated item definitions resolve to generated
models and actual icon textures. Every one of the 6,201 canonical content files
matches the uploaded ZIP, with no additional canonical files.

A full Loom build and game launch are **not verified in this runner**. Java's
network requests cannot reach the dependency repositories here (`Network is
unreachable` / proxy connection refused), even though the dependency coordinates
and example project configuration were verified separately. This runner-specific
proxy configuration is not included in the patch. Run the local acceptance commands
above to establish the first real client/server boot checkpoint.

The added GitHub workflow builds pushes and pull requests targeting `migration`
with JDK 25. It has not been executed on GitHub from this workspace.
