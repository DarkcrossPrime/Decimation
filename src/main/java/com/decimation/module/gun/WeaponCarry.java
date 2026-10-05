package com.decimation.module.gun;

/** Shared tick clock: four ticks from fully relaxed to ready; never advanced by packets. */
public final class WeaponCarry {
    public static final int RAISE_TICKS = 4;
    private int lowered;
    private boolean raising;
    /** Fire exits rest; ADS/reload temporarily raise it without changing the preference. */
    public static boolean preference(boolean selected, boolean trigger) { return selected && !trigger; }
    public void tick(boolean selected, boolean trigger, boolean aim, boolean reload) {
        raising |= !selected || trigger || aim || reload;
        lowered = Math.clamp(lowered + (selected && !raising ? 1 : -1), 0, RAISE_TICKS);
        if (lowered == 0) raising = false;
    }
    public int loweredTicks() { return lowered; }
    public float fraction() { return lowered / (float) RAISE_TICKS; }
    public boolean ready() { return lowered == 0; }
    public void reset() { lowered = 0;raising = false; }
}
