# Checkpoint 6.17: multiplayer hardening and developer launches

Apply the incremental overlay **after 6.16**, at the repository root on `migration`.
Both client and server need this build: weapon protocol is now **4**. Never mix
6.17 with earlier migration builds. Java 25, Gradle 9.6.0 and the Fabric pins are
unchanged. No `content/` files, sight alignment, hand offsets or 6.16 rest/ADS
placement were changed. The 6.16 visual acceptance still needs your test.

## Multiplayer changes

- New observers receive authoritative carry, ADS progress and the **current reload
  phase**, including remaining time and empty-reload rack selection. This also
  seeds observers whose catalogue acknowledgement arrives after tracking starts.
  Duplicate acknowledgements cannot repeatedly request a snapshot sweep.
- Pose updates are sent on change, not every tick while idle. One immutable
  payload is reused for the owner and verified tracking observers. Reload phase
  advances locally from receipt time; different dimension clocks do not restart
  or prematurely finish it. Existing nearby positional sound routing stays intact;
  entering range mid-reload does not replay already-completed sound cues.
- Events, snapshots and input carry entity/dimension identity. Old respawn/world
  packets cannot affect a replacement body. Slot and held-weapon validation stays
  server-owned. Client presentation/HUD accepts only matching events; vanilla
  component synchronization remains the owner of ammunition state.
- Server bindings use **player, stack and world identity**, plus selected slot.
  Respawn with the very same stack still cancels old input/reload. Weapon cadence
  survives rebinding; switching cannot bypass the player's next-shot deadline.
- The 16-packet/tick input budget now belongs to the player session and survives
  rebinding. Sixty ticks without an accepted heartbeat release held intents.
  Focus loss, screens, sleep, death and spectator state suppress local inputs;
  dead/sleeping/spectator server players cannot fire. Reloading cannot grant ADS
  accuracy. Reload completion still consumes ammunition on the server only.

Rendering samples immutable cached state. No network handler parses models or
animations, and no renderer scans the server player list. Remote breathing is
procedural rather than replicated every frame. No measured FPS or latency claim
is made by this checkpoint.

## Straight-to-world Gradle launch

`runClient` and the generated IntelliJ client configuration now use Minecraft's
native Quick Play option. Default save folder:
`run-26.3/saves/Decimation-Test`.

```bash
./gradlew runClient
./gradlew runClient -PquickPlayWorld="Your existing save folder"
./gradlew runClient -PquickPlayWorld=
./gradlew runClient -PquickPlayServer=localhost:25565
```

Use the **folder name**, not the world's display name. Quick Play loads an existing
world; it does not create one. If it does not exist yet, launch with the empty
property, create a fresh 26.3 test world once, then set `quickPlayWorld` to its folder.
Do not open your archived 1.20.1 save for migration testing. To make the choice
persistent, add `quickPlayWorld=Your existing save folder` to your local
`gradle.properties`. Reload Gradle in IntelliJ after changing the configuration;
regenerate IDE runs with `./gradlew idea` if needed. A non-empty `quickPlayServer`
takes precedence over the world. Empty both properties for title/menu work.

## Validation performed here

All main, client and test Java sources compile using `--release 25` against the
actual 26.3 and pinned Fabric artifacts. Fabric's published access changes are
applied to the direct compilation classpath; no stub Minecraft classes are used.
The native Minecraft Quick Play flags and Loom's `programArgs` API were checked
against those artifacts.

| Suite | Result |
| --- | --- |
| Multiplayer contracts | 4,094 assertions |
| Common/server bytecode boundary | 47 classes; no client-only references |
| Weapon behavior/cadence | 4,202 assertions |
| Persistent and payload codecs | 204 assertions |
| Weapon audio | 620 assertions |
| Weapon visual/model/animation contracts | 406,550 assertions |
| Body/armor/classic/slim contracts | 44,837 assertions |
| Catalogue | 4 weapons, 3 ammo types; 15 invalid catalogues rejected |
| Existing pure arm solvers | Both suites passed |
| Recoverable cleanup | 6 isolated-worktree tests passed |
| Content audit | 217 unchanged known findings; zero new findings |

`verifyWeaponMultiplayer`, `verifyDedicatedServerBoundary` and
`verifyMigrationCleanup` are now dependencies of Gradle `check`.
The boundary test uses Java 25's class-file API, not an extra runtime dependency.
It is a static linkage guard, **not a dedicated-server boot test**. Full Loom
`build`, generated IntelliJ launch execution, actual dedicated-server boot,
two-client play, latency behavior and GPU profiling remain unverified in this
runner. Do not equate these deterministic checks with live multiplayer acceptance.

## Local acceptance: two real clients

Build once with `./gradlew clean build`, then install the same resulting mod and
matching Fabric API on both clients and the dedicated test server. Do not run two
clients against the same `run-26.3` directory; use a second independent instance
and a second authenticated player account. Keep normal server authentication.

Launch `./gradlew runServer` with an isolated test world. Review Minecraft's EULA
and perform its normal setup yourself; this patch does not accept it or change
server authentication. Use the title-screen launch or server Quick Play above.

| Scenario | Expected result |
| --- | --- |
| Player B joins while A is holding G-rest or ADS | B sees A's current pose without waiting for A to toggle again |
| B enters tracking range halfway through A's empty/tactical reload | Correct current magazine/root/hand phase; no replay from frame zero; rack only for empty rifle reload |
| A fires semi, burst, automatic and crossbow while B watches | One server ammunition decrement per shot; matching animation/audio; no vanilla attack or duplicate bolt pickup |
| A switches/drops a gun mid-reload, sleeps, dies or changes dimension | Reload/input cancel, no later refill or orphaned reload cue; B sees the new body/equipment correctly |
| A disconnects and reconnects, including with the same gun | No inherited held trigger, ADS, reload or old-body animation |
| A opens a screen or loses focus while holding fire | Held input releases; heartbeat timeout is the fallback if the release cannot arrive |
| Two identical gun stacks are swapped quickly | No cadence reset, magazine duplication or inherited reload |
| PvP/team rules, armor, cover, penetration and bolt collision | Existing server rules and damage attribution remain correct; verify with PvP both enabled and disabled |
| Observer leaves/re-enters range, unloads a dimension or reloads resources | Fresh snapshots recover presentation; stale entities do not keep a weapon pose |
| Earlier migration build or altered catalogue connects | Clear mismatch rejection before weapon inputs are trusted |

Also repeat ready/rest, sprint-fire, ADS and reload in F5 with classic/slim and
left/right dominant-arm settings, with and without chest armor. Run a short
multi-player firing soak, watch server tick time and packet volume, and retain
both client logs plus server `latest.log` if anything desynchronizes.

## After acceptance

Use [migration-cleanup.md](migration-cleanup.md) for **preview-first** cleanup.
Then menus are the **first post-migration work**, ahead of new gameplay modules.
Recovered reference archives include Requiem's `RenderMainMenu`, `RenderGuiHome`,
`RenderMenuServers`, `RenderGuiServerSlot` and `RenderSettingsGui` classes and menu
background assets. These are compiled legacy reference classes, not a ready-made
26.3 screen implementation. The supplied performance-mod `src.zip` also contains
legacy update-check and official-server-list suppression; preserve that separation
when reconstructing UI. Do not restore old update/network services merely to draw
a menu. Recover the original visual layout/assets, then port the home/server/settings
flows with scalable layout, keyboard navigation and current 26.3 screen APIs.
