package com.decimation.module.gun;

import com.decimation.module.gun.data.WeaponCatalog;
import com.decimation.module.gun.data.WeaponDefinition;

/** Regression scenarios for ammunition semantics, tick cadence and input transitions. */
public final class WeaponBehaviorTest {
    private static int checks;

    public static void main(String[] args) {
        WeaponCatalog catalog = WeaponCatalog.load(WeaponBehaviorTest.class.getClassLoader());
        WeaponDefinition honey = catalog.get("decimation:honeybadger"), famas = catalog.get("decimation:famas");
        WeaponDefinition crossbow = catalog.get("decimation:crossbow"), custom = catalog.get("decimation:famas_custom");
        WeaponState state = WeaponState.initial(honey);
        check(state.totalRounds() == 21 && state.isFull(honey), "loaded Honey Badger has 20+1");
        for (int round = 0; round < 21; round++) {
            check(state.canFire(honey), "every loaded round can fire");
            state = state.consumeShot(honey);
        }
        check(!state.canFire(honey) && state.totalRounds() == 0, "no round can be duplicated");
        check(state.consumeShot(honey) == state, "empty fire does not mutate state");
        check(state.reloaded(honey).totalRounds() == 20, "empty reload moves one round into chamber");
        state = WeaponState.initial(honey).consumeShot(honey).reloaded(honey);
        check(state.totalRounds() == 21 && state.isFull(honey), "tactical reload retains chambered extra round");
        check(WeaponState.initial(crossbow).consumeShot(crossbow).reloaded(crossbow).totalRounds() == 1, "crossbow reloads one bolt");
        check(WeaponState.initial(famas).totalRounds() == 31 && WeaponState.initial(custom).totalRounds() == 33, "shared ammo does not merge capacities");
        state = new WeaponState(Integer.MAX_VALUE, true, Integer.MAX_VALUE).normalized(honey);
        check(state.magazine() == 20 && state.fireModeIndex() == 1, "command-created state is normalized per weapon");
        expectFailure(() -> new WeaponState(-1, false, 0));
        expectFailure(() -> new WeaponState(0, false, -1));

        // Simulate one real-time minute with an inexhaustible supply to isolate cadence.
        cadence(honey, 1, 800);cadence(famas, 2, 1000);cadence(custom, 1, 400);
        WeaponCycle semi = new WeaponCycle(crossbow, Double.NEGATIVE_INFINITY);
        state = WeaponState.initial(crossbow);
        semi.input(true, false);
        check(semi.pollShot(0, state) == WeaponCycle.Shot.FIRED, "semi shoots on edge");
        check(semi.pollShot(0, state) == WeaponCycle.Shot.NONE, "duplicate poll cannot bypass cadence");
        for (int tick = 1; tick < 50; tick++) check(semi.pollShot(tick, state) == WeaponCycle.Shot.NONE, "held semi cannot repeat");
        semi.input(false, false);semi.input(true, false);
        check(semi.pollShot(50, state) == WeaponCycle.Shot.FIRED, "released semi can fire again");

        semi = new WeaponCycle(crossbow, Double.NEGATIVE_INFINITY);
        semi.input(true, false);semi.pollShot(0, state);
        semi.input(false, false);semi.input(true, false);semi.input(false, false);
        for (int tick = 1; tick < 20; tick++) check(semi.pollShot(tick, state) == WeaponCycle.Shot.NONE, "queued semi respects cooldown");
        check(semi.pollShot(20, state) == WeaponCycle.Shot.FIRED, "rapid semi press is retained until due");

        WeaponCycle burst = new WeaponCycle(famas, Double.NEGATIVE_INFINITY);
        state = new WeaponState(30, true, 1);
        burst.input(true, false);
        int fired = 0;
        for (int tick = 0; tick < 20; tick++) {
            if (tick == 1) burst.input(false, false);
            if (burst.pollShot(tick, state) == WeaponCycle.Shot.FIRED) fired++;
        }
        check(fired == 3, "a burst finishes exactly three rounds after release");
        burst.input(true, false);burst.pollShot(20, state);burst.stopInput();
        for (int tick = 21; tick < 40; tick++) check(burst.pollShot(tick, state) == WeaponCycle.Shot.NONE, "expired input cancels pending burst");
        WeaponCycle switched = new WeaponCycle(honey, burst.nextShotTick());
        switched.input(true, false);
        check(switched.pollShot(20, WeaponState.initial(honey)) == WeaponCycle.Shot.NONE, "weapon switch retains player cooldown");

        WeaponCycle reload = new WeaponCycle(honey, Double.NEGATIVE_INFINITY);
        state = new WeaponState(0, false, 0);
        check(reload.beginReload(10, state), "empty reload can start");
        check(!reload.beginReload(11, state), "duplicate reload cannot restart clock");
        reload.input(true, true);
        check(reload.pollShot(20, state) == WeaponCycle.Shot.NONE, "cannot fire while reloading");
        check(!reload.reloadDue(66) && reload.reloadDue(67), "reload lasts exact configured ticks");
        reload.endReload();
        check(!reload.beginReload(68, WeaponState.initial(honey)), "full weapon cannot consume another magazine");

        WeaponCycle dry = new WeaponCycle(honey, Double.NEGATIVE_INFINITY);
        state = new WeaponState(0, false, 1);
        dry.input(true, false);
        check(dry.pollShot(0, state) == WeaponCycle.Shot.DRY_FIRE, "empty press has feedback");
        for (int tick = 1; tick < 100; tick++) check(dry.pollShot(tick, state) == WeaponCycle.Shot.NONE, "empty auto cannot spam dry packets");
        dry.input(false, false);dry.input(true, false);
        check(dry.pollShot(100, state) == WeaponCycle.Shot.DRY_FIRE, "new press can dry fire");
        WeaponCycle aim = new WeaponCycle(honey, Double.NEGATIVE_INFINITY);
        aim.input(false, true);
        for (int tick = 0; tick < 5; tick++) aim.advanceAim(false);
        check(close(aim.aimProgress(), 1), "ADS reaches full progress at configured time");
        aim.input(false, true);aim.advanceAim(false);
        check(close(aim.aimProgress(), 1), "heartbeat does not restart ADS");
        aim.advanceAim(true);
        check(close(aim.aimProgress(), 0), "sprinting removes ADS benefit");
        check(close(ShotMath.spread(honey, 0), 2.2f) && close(ShotMath.spread(honey, 1), 0.35f), "spread preserves hip and ADS values");
        check(close(ShotMath.falloff(honey.ballistics(), 28), 1), "full damage before falloff");
        check(close(ShotMath.falloff(honey.ballistics(), 110), 0.45f), "minimum damage at range");
        check(close(ShotMath.falloff(honey.ballistics(), 1000), 0.45f), "falloff cannot underflow beyond range");
        carryContract(catalog);
        System.out.println("Weapon behavior checks passed: " + checks + " assertions; 800/1000/400 RPM cadence preserved.");
    }

    private static void carryContract(WeaponCatalog catalog) {
        for (var definition : catalog.definitions().values()) for (int mode = 0; mode < definition.fireModes().size(); mode++) {
            var cycle = new WeaponCycle(definition, Double.NEGATIVE_INFINITY);
            var loaded = new WeaponState(definition.ammo().capacity(), true, mode);
            cycle.input(false, false, true);
            for (int tick = 0; tick < 4; tick++) cycle.advanceAim(false);
            check(cycle.loweredTicks() == 4, "selection reaches relaxed carry");
            cycle.input(true, false, true);
            // Packet repetition cannot advance the pose or consume ammo before ready.
            for (int repeat = 0; repeat < 30; repeat++) {
                cycle.input(true, false, true);
                check(cycle.pollShot(10, loaded) == WeaponCycle.Shot.NONE, "relaxed carry cannot fire from repeated inputs/polls");
            }
            for (int tick = 10; tick < 13; tick++) {
                cycle.advanceAim(false);
                check(cycle.pollShot(tick, loaded) == WeaponCycle.Shot.NONE, "raise delay blocks every fire mode");
            }
            cycle.advanceAim(false);
            check(cycle.pollShot(13, loaded) == WeaponCycle.Shot.FIRED, "queued trigger fires when four-step raise finishes");
            check(cycle.pollShot(13, loaded) == WeaponCycle.Shot.NONE, "raise does not bypass same-tick cadence guard");
            cycle.input(false, false);
            for (int tick = 14; tick < 40; tick++) cycle.advanceAim(false);
            check(cycle.loweredTicks() == 0, "firing permanently exits rest after trigger release");
            cycle.stopInput();
            check(cycle.pollShot(30, loaded) == WeaponCycle.Shot.NONE, "disconnect/stale input clears queued firing");
            cycle.input(false, false, true);
            for (int tick = 0; tick < 4; tick++) cycle.advanceAim(false);
            cycle.input(false, true, true);cycle.advanceAim(false);
            check(cycle.aimProgress() == 0, "lowered carry has no ADS accuracy benefit");
            for (int tick = 0; tick < 3; tick++) cycle.advanceAim(false);
            check(cycle.loweredTicks() == 0 && cycle.aimProgress() > 0, "aiming raises before ADS benefit");
            cycle.input(false, false);
            for (int tick = 0; tick < 8; tick++) cycle.advanceAim(false);
            check(cycle.loweredTicks() == 4, "releasing ADS restores the selected rest preference");
        }
        var clock = new WeaponCarry();
        for (int tick = 0; tick < 4; tick++) clock.tick(true, false, false, false);
        clock.tick(true, true, false, false);
        for (int tick = 0; tick < 3; tick++) clock.tick(true, false, false, false);
        check(clock.ready(), "brief click still completes the raising motion");
        clock.reset();check(clock.fraction() == 0, "slot/world reset clears relaxed state");
        check(!WeaponCarry.preference(true, true), "fire deselects resting mode");
        check(WeaponCarry.preference(true, false), "non-fire actions retain resting mode");
    }

    private static void cadence(WeaponDefinition definition, int mode, int expected) {
        WeaponCycle cycle = new WeaponCycle(definition, Double.NEGATIVE_INFINITY);
        WeaponState supply = new WeaponState(definition.ammo().capacity(), true, mode);
        cycle.input(true, false);
        int shots = 0;
        for (int tick = 0; tick < 1200; tick++) {
            if (cycle.pollShot(tick, supply) == WeaponCycle.Shot.FIRED) shots++;
            check(cycle.pollShot(tick, supply) == WeaponCycle.Shot.NONE, "per-tick duplicate cannot fire");
        }
        check(shots == expected, definition.id() + " configured " + expected + " RPM, actual " + shots);
        cycle.stopInput();
        check(cycle.pollShot(10000, supply) == WeaponCycle.Shot.NONE, "no shot from stale input");
        cycle.input(true, false);
        check(cycle.pollShot(10000, supply) == WeaponCycle.Shot.FIRED, "long idle permits one immediate shot");
        check(cycle.pollShot(10000, supply) == WeaponCycle.Shot.NONE, "long idle does not accumulate a volley");
    }

    private static boolean close(float actual, float expected) { return Math.abs(actual - expected) < 0.0001f; }
    private static void check(boolean condition, String message) { checks++;if (!condition) throw new AssertionError(message); }
    private static void expectFailure(Runnable action) {
        try { action.run();throw new AssertionError("invalid state accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
}
