package com.decimation.module.gun.data;

import java.util.Locale;

public enum WeaponSound {
    FIRE,
    FIRE_DISTANT,
    FIRE_SUPPRESSED,
    DRY_FIRE,
    FIRE_MODE,
    MAG_OUT,
    MAG_IN,
    RACK,
    INSERT_SHELL;

    public static WeaponSound parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
