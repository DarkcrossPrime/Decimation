package com.decimation.module.gun;

import com.decimation.module.gun.data.FireMode;
import com.decimation.module.gun.data.WeaponDefinition;

/** Server input/cadence clock; independent of Minecraft for deterministic checks. */
public final class WeaponCycle {
    public enum Shot { NONE, FIRED, DRY_FIRE }
    private final WeaponDefinition definition;
    private final double interval;
    private boolean triggerHeld, pendingPress, aiming, dryLatched, reloading;
    private int aimTicks, burstRemaining, recoveryTicks;
    private final WeaponCarry carry = new WeaponCarry();
    private boolean relaxed;
    private double nextShotTick;
    private long lastShotTick = Long.MIN_VALUE;
    private long reloadEndsAt;

    public WeaponCycle(WeaponDefinition definition, double nextShotTick) {
        this.definition = definition;
        this.interval = 1200.0 / definition.rateOfFire();
        this.nextShotTick = nextShotTick;
    }

    public void input(boolean trigger, boolean aim) {
        if (trigger && !triggerHeld) pendingPress = true;
        if (!trigger) dryLatched = false;
        triggerHeld = trigger;
        aiming = aim;
    }

    public void input(boolean trigger, boolean aim, boolean relaxed) {
        this.relaxed = WeaponCarry.preference(relaxed, trigger);input(trigger, aim);
    }
    public int loweredTicks() { return carry.loweredTicks(); }

    public void advanceAim(boolean sprinting) {
        carry.tick(relaxed, triggerHeld || pendingPress || burstRemaining > 0 || recoveryTicks > 0, aiming, reloading);
        if (recoveryTicks > 0) recoveryTicks--;
        aimTicks = aiming && !sprinting && !reloading && carry.ready() ? Math.min(aimTicks + 1, Math.max(1, definition.handling().adsTicks())) : 0;
    }

    public float aimProgress() { return aimTicks / (float) Math.max(1, definition.handling().adsTicks()); }

    public Shot pollShot(long tick, WeaponState state) {
        if (reloading) { pendingPress = false; return Shot.NONE; }
        if (!carry.ready()) return Shot.NONE;
        FireMode mode = state.fireMode(definition);
        if (pendingPress && mode == FireMode.BURST) {
            if (burstRemaining == 0) burstRemaining = definition.burstSize();
            pendingPress = false;
        }
        boolean wants = switch (mode) {
            case SEMI -> pendingPress;
            case BURST -> burstRemaining > 0;
            case AUTOMATIC -> triggerHeld || pendingPress;
        };
        if (!wants || dryLatched || tick == lastShotTick || tick + 1.0e-8 < nextShotTick) return Shot.NONE;
        pendingPress = false;
        lastShotTick = tick;
        // Carry fractional tick cadence while firing; no accumulated idle catch-up.
        nextShotTick = !Double.isFinite(nextShotTick) || tick - nextShotTick >= interval
            ? tick + interval : nextShotTick + interval;
        if (!state.canFire(definition)) {
            burstRemaining = 0;
            dryLatched = true;
            return Shot.DRY_FIRE;
        }
        if (mode == FireMode.BURST) burstRemaining--;
        recoveryTicks = WeaponCarry.RAISE_TICKS;
        relaxed = false;
        return Shot.FIRED;
    }

    public boolean beginReload(long tick, WeaponState state) {
        if (reloading || state.isFull(definition)) return false;
        reloading = true;
        reloadEndsAt = tick + definition.reloadTicks();
        pendingPress = false;
        burstRemaining = 0;
        return true;
    }

    public boolean reloadDue(long tick) { return reloading && tick >= reloadEndsAt; }
    public boolean reloading() { return reloading; }
    public int reloadRemaining(long tick) { return reloading ? (int) Math.clamp(reloadEndsAt - tick, 0, definition.reloadTicks()) : 0; }
    public int reloadElapsed(long tick) { return reloadRemaining(tick) > 0 ? definition.reloadTicks() - reloadRemaining(tick) : 0; }
    public void endReload() { reloading = false; dryLatched = false; }
    public void fireModeChanged() { pendingPress = false; burstRemaining = 0; }
    public double nextShotTick() { return nextShotTick; }

    public void stopInput() {
        triggerHeld = pendingPress = aiming = dryLatched = false;
        aimTicks = burstRemaining = 0;
        relaxed = false;recoveryTicks = 0;
    }
}
