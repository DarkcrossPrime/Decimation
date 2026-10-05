package com.decimation.module.gun;

import com.decimation.Decimation;
import com.decimation.module.DecimationItemGroups;
import com.decimation.module.gun.data.AmmunitionDefinition;
import com.decimation.module.gun.data.WeaponCatalog;
import com.decimation.module.gun.data.WeaponDefinition;
import com.decimation.module.gun.network.WeaponPackets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionResult;

/** All common registration happens once after validating the complete catalogue. */
public final class GunModule {
    private static WeaponCatalog catalog;
    private static Map<String, WeaponItem> weapons = Map.of();
    private static Map<String, AmmoItem> ammunition = Map.of();

    private GunModule() { }

    public static void initialize() {
        if (catalog != null) throw new IllegalStateException("Gun module already initialized");
        WeaponCatalog loaded = WeaponCatalog.load(GunModule.class.getClassLoader());
        // Preflight conflicts before modifying either registry.
        for (String id : loaded.definitions().keySet()) requireAvailable(id);
        for (String id : loaded.ammunition().keySet()) requireAvailable(id);
        DecimationItemGroups.requireAvailable("weapons");
        DecimationItemGroups.requireAvailable("ammunition");
        WeaponComponents.initialize();
        WeaponSounds.initialize(loaded);

        Map<String, AmmoItem> ammoItems = new LinkedHashMap<>();
        for (AmmunitionDefinition definition : loaded.ammunition().values()) {
            ResourceKey<Item> key = itemKey(definition.id());
            AmmoItem item = new AmmoItem(new Item.Properties().setId(key).stacksTo(definition.maxStackSize()), definition);
            Registry.register(BuiltInRegistries.ITEM, key, item);
            ammoItems.put(definition.id(), item);
        }
        Map<String, WeaponItem> weaponItems = new LinkedHashMap<>();
        for (WeaponDefinition definition : loaded.definitions().values()) {
            ResourceKey<Item> key = itemKey(definition.id());
            WeaponItem item = new WeaponItem(new Item.Properties().setId(key).stacksTo(1), definition);
            Registry.register(BuiltInRegistries.ITEM, key, item);
            weaponItems.put(definition.id(), item);
        }
        ammunition = Collections.unmodifiableMap(ammoItems);
        weapons = Collections.unmodifiableMap(weaponItems);
        catalog = loaded;
        DecimationItemGroups.register("weapons", weapons.values());
        DecimationItemGroups.register("ammunition", ammunition.values());
        WeaponServerController controller = new WeaponServerController();
        WeaponPackets.initialize(controller);
        ServerTickEvents.END_SERVER_TICK.register(controller::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> controller.clear());
        AttackBlockCallback.EVENT.register((player, level, hand, position, direction) ->
            player.getMainHandItem().getItem() instanceof WeaponItem ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
            player.getMainHandItem().getItem() instanceof WeaponItem ? InteractionResult.FAIL : InteractionResult.PASS);
        Decimation.LOGGER.info("Registered {} weapons and {} ammunition items for 26.3", weapons.size(), ammunition.size());
    }

    private static ResourceKey<Item> itemKey(String id) {
        return ResourceKey.create(Registries.ITEM, Identifier.parse(id));
    }

    private static void requireAvailable(String id) {
        if (BuiltInRegistries.ITEM.containsKey(Identifier.parse(id))) {
            throw new IllegalStateException("Item already registered: " + id);
        }
    }

    public static Map<String, WeaponItem> weapons() { return weapons; }
    public static Map<String, AmmoItem> ammunition() { return ammunition; }
    public static WeaponCatalog catalog() {
        if (catalog == null) throw new IllegalStateException("Gun module is not initialized");
        return catalog;
    }
}
