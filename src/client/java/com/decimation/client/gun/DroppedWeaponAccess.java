package com.decimation.client.gun;

/** Extraction-owned marker, reset for every reused dropped-item render state. */
public interface DroppedWeaponAccess {
    boolean decimation$isWeapon();
    void decimation$setWeapon(boolean weapon);
}
