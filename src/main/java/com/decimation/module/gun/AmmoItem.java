package com.decimation.module.gun;

import net.minecraft.item.Item;

public final class AmmoItem extends Item {
    public AmmoItem() {
        super(new Settings().maxCount(16));
    }
}
