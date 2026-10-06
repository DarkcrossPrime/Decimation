package com.decimation.module.gun;

import com.decimation.module.gun.data.FireMode;
import com.decimation.module.gun.data.WeaponDefinition;

/** Immutable stack data. Reading it never changes an ItemStack. */
public record WeaponState(int magazine, boolean chambered, int fireModeIndex) {
    public WeaponState {
        if (magazine < 0 || fireModeIndex < 0) throw new IllegalArgumentException("negative weapon state");
    }

    public static WeaponState initial(WeaponDefinition definition) {
        return new WeaponState(definition.ammo().spawnLoaded() ? definition.ammo().capacity() : 0,
            definition.ammo().spawnLoaded() && definition.ammo().chamberCapacity() > 0, 0);
    }

    public WeaponState normalized(WeaponDefinition definition) {
        int rounds = Math.min(magazine, definition.ammo().capacity());
        boolean chamber = chambered && definition.ammo().chamberCapacity() > 0;
        int mode = fireModeIndex % definition.fireModes().size();
        return rounds == magazine && chamber == chambered && mode == fireModeIndex
            ? this : new WeaponState(rounds, chamber, mode);
    }

    public boolean canFire(WeaponDefinition definition) {
        return definition.ammo().chamberCapacity() > 0 ? chambered : magazine > 0;
    }

    public WeaponState consumeShot(WeaponDefinition definition) {
        if (!canFire(definition)) return this;
        if (definition.ammo().chamberCapacity() == 0) return new WeaponState(magazine - 1, false, fireModeIndex);
        return new WeaponState(Math.max(0, magazine - 1), magazine > 0, fireModeIndex);
    }

    public WeaponState reloaded(WeaponDefinition definition) {
        int capacity = definition.ammo().capacity();
        if (definition.ammo().chamberCapacity() == 0) return new WeaponState(capacity, false, fireModeIndex);
        // Preserve tactical +1 and the archived empty-reload chamber transfer.
        return new WeaponState(!chambered && capacity > 0 ? capacity - 1 : capacity, true, fireModeIndex);
    }

    public boolean isFull(WeaponDefinition definition) {
        return magazine == definition.ammo().capacity()
            && (definition.ammo().chamberCapacity() == 0 || chambered);
    }

    public WeaponState cycleFireMode(WeaponDefinition definition) {
        return new WeaponState(magazine, chambered, (fireModeIndex + 1) % definition.fireModes().size());
    }

    public FireMode fireMode(WeaponDefinition definition) { return definition.fireModes().get(fireModeIndex); }
    public long totalRounds() { return (long) magazine + (chambered ? 1 : 0); }
}
