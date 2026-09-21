package com.decimation.module.gun;

import com.decimation.Decimation;
import com.decimation.module.DecimationModule;
import com.decimation.module.gun.data.WeaponCatalog;
import com.decimation.module.gun.data.WeaponDefinition;
import com.decimation.module.gun.network.WeaponPackets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public final class GunModule implements DecimationModule {
    private static final Identifier ID = new Identifier(Decimation.MOD_ID, "guns");
    private static final Map<Identifier, WeaponItem> WEAPONS = new LinkedHashMap<>();
    private static final Map<Identifier, AmmoItem> AMMUNITION = new LinkedHashMap<>();
    private static WeaponCatalog catalog;

    public Identifier id() { return ID; }

    public void initialize() {
        catalog = WeaponCatalog.load();
        for (WeaponDefinition definition : catalog.definitions().values()) {
            WeaponItem weapon = Registry.register(Registries.ITEM, definition.id(), new WeaponItem(definition));
            WEAPONS.put(definition.id(), weapon);
            AMMUNITION.computeIfAbsent(definition.ammo().itemId(), ammoId ->
                Registry.register(Registries.ITEM, ammoId, new AmmoItem()));
            Identifier soundId = new Identifier(Decimation.MOD_ID, "weapon." + definition.id().getPath() + ".fire");
            Registry.register(Registries.SOUND_EVENT, soundId, SoundEvent.of(soundId));
        }

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            WEAPONS.values().forEach(entries::add);
            AMMUNITION.values().forEach(entries::add);
        });
        WeaponServerController controller = new WeaponServerController();
        WeaponPackets.registerServerReceivers(controller);
        ServerTickEvents.END_SERVER_TICK.register(controller::tick);
        AttackBlockCallback.EVENT.register((player, world, hand, position, direction) ->
            player.getStackInHand(hand).getItem() instanceof WeaponItem ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
            player.getStackInHand(hand).getItem() instanceof WeaponItem ? ActionResult.FAIL : ActionResult.PASS);
        Decimation.LOGGER.info("Initialized {} production weapons and {} ammunition items",
            WEAPONS.size(), AMMUNITION.size());
    }

    public static Map<Identifier, WeaponItem> weapons() {
        return Collections.unmodifiableMap(WEAPONS);
    }

    public static Item ammo(Identifier id) {
        Item item = AMMUNITION.get(id);
        if (item == null) throw new IllegalArgumentException("Unknown ammunition item: " + id);
        return item;
    }

    public static WeaponCatalog catalog() {
        if (catalog == null) throw new IllegalStateException("Gun module is not initialized");
        return catalog;
    }
}
