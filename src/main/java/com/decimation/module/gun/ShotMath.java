package com.decimation.module.gun;

import com.decimation.module.gun.data.BallisticsDefinition;
import com.decimation.module.gun.data.WeaponDefinition;

public final class ShotMath {
    private ShotMath() { }
    public static float spread(WeaponDefinition definition, float aimProgress) {
        float progress = Math.max(0, Math.min(1, aimProgress));
        return definition.handling().hipSpread()
            + (definition.handling().adsSpread() - definition.handling().hipSpread()) * progress;
    }
    public static float falloff(BallisticsDefinition definition, double distance) {
        if (distance <= definition.falloffStart()) return 1;
        double length = Math.max(0.001, definition.range() - definition.falloffStart());
        double progress = Math.min(1, (distance - definition.falloffStart()) / length);
        return (float) (1 + (definition.minimumMultiplier() - 1) * progress);
    }
}
