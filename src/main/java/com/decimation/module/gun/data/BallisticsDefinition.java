package com.decimation.module.gun.data;

public record BallisticsDefinition(float damage, double range, double falloffStart,
                                   float minimumMultiplier, float headMultiplier,
                                   int penetrationCount, float penetrationRetention,
                                   float projectileSpeed, float projectileDivergence) {
    public BallisticsDefinition {
        DefinitionValidation.finite(damage, range, falloffStart, minimumMultiplier,
            headMultiplier, penetrationRetention, projectileSpeed, projectileDivergence);
        if (damage <= 0 || range <= 0 || falloffStart < 0 || falloffStart > range) {
            throw new IllegalArgumentException("invalid weapon ballistics");
        }
        if (minimumMultiplier <= 0 || minimumMultiplier > 1 || headMultiplier < 1
            || penetrationCount < 0 || penetrationRetention <= 0 || penetrationRetention > 1
            || projectileSpeed < 0 || projectileDivergence < 0) {
            throw new IllegalArgumentException("invalid weapon damage multipliers");
        }
    }
}
