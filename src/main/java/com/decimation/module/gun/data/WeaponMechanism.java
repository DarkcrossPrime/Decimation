package com.decimation.module.gun.data;

import java.util.Locale;

public enum WeaponMechanism {
    HITSCAN,
    PROJECTILE;

    public static WeaponMechanism parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
