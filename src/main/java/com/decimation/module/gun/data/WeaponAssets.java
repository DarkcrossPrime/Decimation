package com.decimation.module.gun.data;

import net.minecraft.util.Identifier;

public record WeaponAssets(Identifier model, Identifier texture, Identifier fireAnimation,
                           Identifier reloadAnimation, Identifier fireSound) { }
