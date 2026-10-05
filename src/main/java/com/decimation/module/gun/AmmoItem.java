package com.decimation.module.gun;

import com.decimation.module.gun.data.AmmunitionDefinition;
import net.minecraft.world.item.Item;

public final class AmmoItem extends Item {
    private final AmmunitionDefinition definition;

    public AmmoItem(Properties properties, AmmunitionDefinition definition) {
        super(properties);
        this.definition = definition;
    }

    public AmmunitionDefinition definition() { return definition; }
}
