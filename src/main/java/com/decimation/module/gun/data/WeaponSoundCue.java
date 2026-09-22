package com.decimation.module.gun.data;

public record WeaponSoundCue(int tick, WeaponSound sound) {
    public WeaponSoundCue {
        if (tick < 0) throw new IllegalArgumentException("weapon sound cue tick cannot be negative");
    }
}
