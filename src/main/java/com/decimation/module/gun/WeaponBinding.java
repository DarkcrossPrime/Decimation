package com.decimation.module.gun;

import java.util.Objects;

/** Identity, not equality: respawn and replacement stacks must never inherit held intents. */
public record WeaponBinding(Object owner, Object stack, int slot, Object level) {
    public WeaponBinding { Objects.requireNonNull(owner);Objects.requireNonNull(stack);Objects.requireNonNull(level); }
    public boolean matches(Object owner, Object stack, int slot, Object level) {
        return this.owner == owner && this.stack == stack && this.slot == slot && this.level == level;
    }
}
