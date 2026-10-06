package com.decimation.module.gun.data;

public record HandlingDefinition(float hipSpread, float adsSpread, int adsTicks,
                                 float recoilPitch, float recoilYaw) {
    public HandlingDefinition {
        DefinitionValidation.finite(hipSpread, adsSpread, recoilPitch, recoilYaw);
        if (hipSpread < 0 || adsSpread < 0 || adsTicks < 0 || recoilPitch < 0 || recoilYaw < 0) {
            throw new IllegalArgumentException("invalid weapon handling values");
        }
    }
}
