package com.decimation.module.gun;

/** Player-session budget survives weapon rebinding; held intents expire without heartbeats. */
public final class WeaponInputLease {
    public static final int MAX_PER_TICK = 16, TIMEOUT_TICKS = 60;
    private long budgetTick = Long.MIN_VALUE, renewedAt = Long.MIN_VALUE / 2;
    private int count;
    public boolean accept(long tick) {
        if (tick != budgetTick) { budgetTick = tick;count = 0; }
        if (count >= MAX_PER_TICK) return false;
        count++;renewedAt = tick;return true;
    }
    public boolean expired(long tick) { return tick - renewedAt >= TIMEOUT_TICKS; }
    public void release() { renewedAt = Long.MIN_VALUE / 2; }
}
