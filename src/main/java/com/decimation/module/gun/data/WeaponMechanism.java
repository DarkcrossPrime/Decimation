package com.decimation.module.gun.data;

public enum WeaponMechanism {
    HITSCAN,
    PROJECTILE;

    public static WeaponMechanism parse(String value) {
        return valueOf(value.toUpperCase());
    }
}
