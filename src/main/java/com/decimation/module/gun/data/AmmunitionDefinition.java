package com.decimation.module.gun.data;

/** Ammo item identity is shared; weapon-specific capacity remains on AmmoDefinition. */
public record AmmunitionDefinition(String id, String displayName, int maxStackSize) {
    public AmmunitionDefinition {
        DefinitionValidation.identifier(id, "ammunition id");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("missing ammunition name");
        if (maxStackSize < 1 || maxStackSize > 64) throw new IllegalArgumentException("invalid ammunition stack size");
    }
}
