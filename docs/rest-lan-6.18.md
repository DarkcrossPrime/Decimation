# Checkpoint 6.18 — flat rest hold and LAN acceptance

This ZIP is cumulative over **6.16**. It includes the entire 6.17 multiplayer,
Quick Play and preview-first cleanup update, so you can apply it directly over
your tested 6.16 or over 6.17. Extract at the repository root, reload Gradle in
IntelliJ, and rebuild. No canonical `content/` edits or file deletions.

## Rest pose

The barrel is horizontal across the torso, with a small gap for clothing/armor.
The firing arm rests at a natural downward/inward angle rather than following
the barrel. The weapon mount counters the wrist's angle **only while resting**,
blending smoothly into the existing ready/ADS/sprint poses. Both first-person and
F5 use this orientation, including the mirrored left-hand setting.

The first-person support solver still follows the real barrel grip. F5 now uses
that barrel after the rest counter-rotation, choosing a reachable point along it
for the fixed arm length. Shoulders stay attached and F5 arms do not stretch;
the short/barrel-end cases allow the width of the hand for contact. No arm bends,
torso overlay or new content transforms were added. Head-independent rest and
breathing/walking sway remain in place; ADS alignment is untouched.

## Rest selection behavior

| Action while G-rest is selected | Result |
| --- | --- |
| Fire input | Deselect rest, raise through the existing four-tick gate, then fire; releasing the trigger stays ready |
| ADS | Temporarily raise/aim; releasing ADS returns to rest |
| Reload | Temporarily raise/reload; retain the rest preference |
| G after firing | Select rest again |
| Slot/stack/world change or respawn | Existing reset behavior |

The local controller clears the preference when fire is requested, and a
server-confirmed shot also reconciles it. The server independently deselects rest
on fire input and on a successful shot. A dry-fire request exits rest too. No
client ammunition mutation or new firing prediction was introduced. Weapon
protocol remains 4; install the **same 6.18 build on both players** for acceptance.

## LAN test with your friend

1. Run `./gradlew clean build`. Install the plain 6.18 mod JAR from `build/libs/`
   (not the sources JAR), matching Fabric Loader/API and Minecraft 26.3 on both
   independent client instances. Use distinct player accounts.
2. Host a fresh migration test world and open it to LAN. Your friend joins from
   Multiplayer, or uses direct connect to your LAN address and the displayed port.
   Quick Play is still available for your usual solo Gradle test world.
3. Take turns as shooter and observer. Test all four weapons, right/left dominant
   arm, classic/slim skin, chest armor and F5. Check flat rest, head turning,
   walking/breathing and barrel contact. Fire from rest, release the trigger and
   verify ready persists; select G again, ADS and release to verify rest resumes.
4. Enter view/tracking range while your friend is already resting, aiming or
   halfway through an empty/tactical reload. Confirm correct pose and current
   reload phase, rather than restarting its opening frames.
5. Swap two identical guns, switch slots, drop a gun mid-reload, die/respawn,
   change dimensions and disconnect/rejoin. Watch for orphaned animation/sound,
   duplicate ammo or a held trigger that survives the reset. Test screen/focus
   loss while firing too.

If something fails, keep both client `latest.log` files and identify the weapon,
dominant hand, first-person/F5 view, action and what the observer saw. A screenshot
of the rest pose helps tune apparent arm/weapon placement. LAN acceptance is the
next live check; this runner has not executed a graphical two-player session or
a full Loom launch. Dedicated-server acceptance and measured profiling still
follow the checklist in `multiplayer-6.17.md`.

## Validation

Main, client and test sources compile on Java 25 against actual 26.3/Fabric
artifacts. Real-asset geometry tests verify horizontal torso-plane barrels for
all four weapons, both hands and classic/slim models; natural independent firing
arm angle; head-independent rest; unchanged shoulders/limb scales; and F5 hand
contact within hand thickness. Existing ready, ADS, reload and animation tests pass.

- Weapon visual suite: **407,990 assertions**.
- Weapon behavior suite: **4,222 assertions**, including fire exits rest and ADS returns to rest.
- Multiplayer contracts: **4,094 assertions**.
- Payload codecs: **204 assertions**.
- Dedicated-server linkage guard: **47 common classes**, no client-only references.
- Body suite: **44,837 assertions**.
- Cleanup safety: **6 isolated-worktree tests**.

This is readiness for your LAN test, not a claim that the live test has passed.
Cleanup remains prepared only. **Menus are still the first post-migration task**,
using the recovered original mod/menu references.
