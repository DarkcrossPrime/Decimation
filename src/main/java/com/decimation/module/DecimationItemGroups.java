package com.decimation.module;

import com.decimation.Decimation;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DecimationItemGroups {
    private DecimationItemGroups() { }

    public static void requireAvailable(String name) {
        if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id(name))) {
            throw new IllegalStateException("Creative tab already registered: " + name);
        }
    }

    public static void register(String name, Collection<? extends Item> items) {
        List<Item> entries = List.copyOf(items);
        if (entries.isEmpty()) return;
        ResourceKey<CreativeModeTab> key = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), id(name));
        CreativeModeTab tab = FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.decimation." + name))
            .icon(() -> new ItemStack(entries.getFirst()))
            .displayItems((parameters, output) -> entries.forEach(output::accept))
            .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
    }

    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Decimation.MOD_ID, name); }
}
