package com.decimation.module.gun;

import com.decimation.Decimation;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class WeaponComponents {
    private WeaponComponents() { }

    public static final DataComponentType<WeaponState> STATE = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(Decimation.MOD_ID, "weapon_state"),
        DataComponentType.<WeaponState>builder().persistent(WeaponStateCodecs.CODEC)
            .networkSynchronized(WeaponStateCodecs.STREAM_CODEC).build());

    public static void initialize() { }
}
