package com.decimation.module;

import com.decimation.Decimation;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Register one Decimation creative tab when its module has actual items to display. */
public final class DecimationItemGroups {
    private DecimationItemGroups() { }

    public static void register(String name, Collection<? extends Item> contents) {
        List<? extends Item> items = List.copyOf(contents);
        if (items.isEmpty()) return;
        Registry.register(Registries.ITEM_GROUP, new Identifier(Decimation.MOD_ID, name),
            FabricItemGroup.builder()
                .icon(() -> new ItemStack(items.get(0)))
                .displayName(Text.translatable("itemGroup.decimation." + name))
                .entries((context, entries) -> items.forEach(item -> entries.add(item)))
                .build());
    }
}
