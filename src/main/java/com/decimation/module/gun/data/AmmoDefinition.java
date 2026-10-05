package com.decimation.module.gun.data;

public record AmmoDefinition(String itemId, int capacity, int chamberCapacity, boolean spawnLoaded) {
    public AmmoDefinition {
        DefinitionValidation.identifier(itemId, "ammunition item");
        if (capacity < 0 || chamberCapacity < 0 || chamberCapacity > 1 || capacity + (long) chamberCapacity == 0) {
            throw new IllegalArgumentException("invalid weapon ammunition capacity");
        }
    }
}
