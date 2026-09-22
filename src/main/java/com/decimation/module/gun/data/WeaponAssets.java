package com.decimation.module.gun.data;

import net.minecraft.util.Identifier;

public record WeaponAssets(Identifier model, Identifier texture, Identifier itemTexture,
                           Identifier fireAnimation, Identifier reloadAnimation) { }
