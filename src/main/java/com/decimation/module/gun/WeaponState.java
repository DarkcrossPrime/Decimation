package com.decimation.module.gun;

import com.decimation.module.gun.data.FireMode;
import com.decimation.module.gun.data.WeaponDefinition;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public final class WeaponState {
    private static final String ROOT = "DecimationWeapon";
    private static final String INITIALIZED = "Initialized";
    private static final String MAGAZINE = "Magazine";
    private static final String CHAMBERED = "Chambered";
    private static final String FIRE_MODE = "FireMode";

    private int magazine;
    private boolean chambered;
    private int fireModeIndex;

    private WeaponState(int magazine, boolean chambered, int fireModeIndex) {
        this.magazine = magazine;
        this.chambered = chambered;
        this.fireModeIndex = fireModeIndex;
    }

    public static WeaponState read(ItemStack stack, WeaponDefinition definition) {
        NbtCompound root = stack.getOrCreateNbt();
        if (!root.contains(ROOT)) {
            WeaponState initial = definition.ammo().spawnLoaded()
                ? loaded(definition) : new WeaponState(0, false, 0);
            initial.write(stack, definition);
            return initial;
        }
        NbtCompound state = root.getCompound(ROOT);
        if (!state.getBoolean(INITIALIZED)) {
            WeaponState initial = definition.ammo().spawnLoaded()
                ? loaded(definition) : new WeaponState(0, false, 0);
            initial.write(stack, definition);
            return initial;
        }
        return new WeaponState(
            Math.max(0, Math.min(state.getInt(MAGAZINE), definition.ammo().capacity())),
            state.getBoolean(CHAMBERED) && definition.ammo().chamberCapacity() > 0,
            Math.floorMod(state.getInt(FIRE_MODE), definition.fireModes().size()));
    }

    private static WeaponState loaded(WeaponDefinition definition) {
        if (definition.ammo().capacity() == 0) {
            return new WeaponState(0, definition.ammo().chamberCapacity() > 0, 0);
        }
        int magazine = definition.ammo().capacity();
        boolean chambered = definition.ammo().chamberCapacity() > 0;
        return new WeaponState(magazine, chambered, 0);
    }

    public void write(ItemStack stack, WeaponDefinition definition) {
        NbtCompound state = new NbtCompound();
        state.putBoolean(INITIALIZED, true);
        state.putInt(MAGAZINE, Math.max(0, Math.min(magazine, definition.ammo().capacity())));
        state.putBoolean(CHAMBERED, chambered && definition.ammo().chamberCapacity() > 0);
        state.putInt(FIRE_MODE, Math.floorMod(fireModeIndex, definition.fireModes().size()));
        stack.getOrCreateNbt().put(ROOT, state);
    }

    public boolean canFire() {
        return chambered;
    }

    public boolean consumeShot() {
        if (!chambered) return false;
        chambered = false;
        if (magazine > 0) {
            magazine--;
            chambered = true;
        }
        return true;
    }

    public void reload(WeaponDefinition definition) {
        if (definition.ammo().capacity() == 0) {
            chambered = definition.ammo().chamberCapacity() > 0;
            return;
        }
        magazine = definition.ammo().capacity();
        if (!chambered && definition.ammo().chamberCapacity() > 0) {
            magazine--;
            chambered = true;
        }
    }

    public boolean isFull(WeaponDefinition definition) {
        int maximum = definition.ammo().capacity() + definition.ammo().chamberCapacity();
        return totalRounds() >= maximum;
    }

    public FireMode fireMode(WeaponDefinition definition) {
        return definition.fireModes().get(fireModeIndex);
    }

    public FireMode cycleFireMode(WeaponDefinition definition) {
        fireModeIndex = (fireModeIndex + 1) % definition.fireModes().size();
        return fireMode(definition);
    }

    public int totalRounds() {
        return magazine + (chambered ? 1 : 0);
    }

    public int magazine() { return magazine; }
    public boolean chambered() { return chambered; }
}
