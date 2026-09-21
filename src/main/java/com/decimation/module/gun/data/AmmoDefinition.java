package com.decimation.module.gun.data;

import net.minecraft.util.Identifier;

public record AmmoDefinition(Identifier itemId, int capacity, int chamberCapacity, boolean spawnLoaded) {
    public AmmoDefinition {
        if (capacity < 0 || chamberCapacity < 0 || chamberCapacity > 1) {
            throw new IllegalArgumentException("invalid weapon ammunition capacity");
        }
    }
}
