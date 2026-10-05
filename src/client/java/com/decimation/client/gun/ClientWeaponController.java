package com.decimation.client.gun;

import com.decimation.module.gun.GunModule;
import com.decimation.module.gun.WeaponItem;
import com.decimation.module.gun.WeaponState;
import com.decimation.module.gun.network.WeaponCatalogPayload;
import com.decimation.module.gun.network.WeaponEvent;
import com.decimation.module.gun.network.WeaponEventPayload;
import com.decimation.module.gun.network.WeaponInputPayload;
import com.decimation.module.gun.network.WeaponCarryPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/** Poll vanilla key mappings (SDL3); transmit changes and a one-second hold heartbeat. */
public final class ClientWeaponController {
    private static final int HEARTBEAT_TICKS = 20;
    private final KeyMapping reload;
    private final KeyMapping fireMode, relaxedCarry;
    private static boolean relaxed;
    public static boolean relaxed() { return relaxed; }
    private boolean ready, reloading;
    private WeaponItem lastWeapon;
    private int lastSlot = -1, lastFlags, heartbeat;
    private ClientLevel level;
    private net.minecraft.client.player.LocalPlayer player;

    public ClientWeaponController() {
        KeyMapping.Category category = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("decimation", "controls"));
        reload = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.decimation.reload", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, category));
        fireMode = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.decimation.fire_mode", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, category));
        relaxedCarry = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.decimation.relaxed_carry", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, category));
    }

    public void register() {
        ClientPlayNetworking.registerGlobalReceiver(WeaponCatalogPayload.TYPE, (payload, context) -> {
            if (payload.protocol() != WeaponCatalogPayload.PROTOCOL || !payload.fingerprint().equals(GunModule.catalog().fingerprint())) {
                ready = false;
                context.client().getConnection().getConnection().disconnect(Component.literal(
                    "Decimation weapon definitions differ. Install the same build on client and server."));
                return;
            }
            ClientPlayNetworking.send(new WeaponCatalogPayload(WeaponCatalogPayload.PROTOCOL, GunModule.catalog().fingerprint()));
            ready = true;
        });
        ClientPlayNetworking.registerGlobalReceiver(WeaponEventPayload.TYPE, (payload, context) -> accept(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(WeaponCarryPayload.TYPE, (payload, context) -> ClientWeaponPresentation.acceptCarry(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ItemTooltipCallback.EVENT.register((stack, context, flags, tooltip) -> {
            if (stack.getItem() instanceof WeaponItem weapon) {
                WeaponState state = weapon.state(stack);
                tooltip.add(Component.literal(state.totalRounds() + " / "
                    + ((long) weapon.definition().ammo().capacity() + weapon.definition().ammo().chamberCapacity())).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.decimation.fire_mode", state.fireMode(weapon.definition()).name()).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }

    private void tick(Minecraft client) {
        boolean reloadPressed = false, modePressed = false, carryPressed = false;
        while (reload.consumeClick()) reloadPressed = true;
        while (fireMode.consumeClick()) modePressed = true;
        while (relaxedCarry.consumeClick()) carryPressed = true;
        if (client.level != level || client.player != player) {
            lastWeapon = null;lastSlot = -1;lastFlags = -1;heartbeat = 0;reloading = relaxed = false;level = client.level;player = client.player;
        }
        if (!ready || client.player == null || client.getConnection() == null || client.isPaused()) {
            ClientWeaponPresentation.tick(client, ready);return;
        }
        var stack = client.player.getMainHandItem();
        WeaponItem weapon = stack.getItem() instanceof WeaponItem held ? held : null;
        int slot = client.player.getInventory().getSelectedSlot();
        if (!client.player.isAlive() || client.player.isSpectator()) relaxed = false;
        boolean playing = client.isWindowActive() && client.gui.screen() == null && client.player.isAlive() && !client.player.isSpectator() && !client.player.isSleeping();
        if (weapon != lastWeapon || slot != lastSlot) {
            if (lastWeapon != null) send(lastWeapon, lastSlot, 0);
            lastWeapon = weapon;lastSlot = slot;lastFlags = -1;heartbeat = 0;reloading = relaxed = false;
        }
        if (playing && weapon != null && carryPressed) {
            relaxed = !relaxed;
            client.gui.hud.setOverlayMessage(Component.translatable(relaxed ? "hud.decimation.relaxed" : "hud.decimation.ready"), false);
        }
        if (playing && weapon != null) relaxed = com.decimation.module.gun.WeaponCarry.preference(relaxed, client.options.keyAttack.isDown());
        ClientWeaponPresentation.tick(client, ready);
        if (weapon == null) return;
        int flags = playing && weapon != null ? (client.options.keyAttack.isDown() ? WeaponInputPayload.TRIGGER : 0)
            | (client.options.keyUse.isDown() && !client.player.isSprinting() ? WeaponInputPayload.AIM : 0) | (relaxed ? WeaponInputPayload.RELAXED : 0) : 0;

        int commands = playing ? (reloadPressed ? WeaponInputPayload.RELOAD : 0) | (modePressed ? WeaponInputPayload.CYCLE : 0) : 0;
        boolean heartbeatDue = ++heartbeat >= HEARTBEAT_TICKS;
        if (flags != lastFlags || commands != 0 || (flags != 0 && heartbeatDue)) {
            send(weapon, slot, flags | commands);
            lastFlags = flags;heartbeat = 0;
        }
        if (heartbeatDue) { heartbeat = 0;showState(client, weapon, weapon.state(stack)); }
    }

    private static void send(WeaponItem weapon, int slot, int flags) {
        var client = Minecraft.getInstance();
        if (client.player != null && client.level != null && ClientPlayNetworking.canSend(WeaponInputPayload.TYPE))
            ClientPlayNetworking.send(new WeaponInputPayload(slot, weapon.identifier(), flags, client.player.getId(), client.level.dimension().identifier()));
    }

    private void accept(Minecraft client, WeaponEventPayload event) {
        if (!ClientWeaponPresentation.accept(client, event)) return;
        if (client.player == null || !client.player.getUUID().equals(event.owner())) return;
        var stack = client.player.getMainHandItem();
        if (client.player.getInventory().getSelectedSlot() != event.slot() || !(stack.getItem() instanceof WeaponItem weapon)
            || !weapon.identifier().equals(event.weapon())) return;
        if (event.event() == WeaponEvent.RELOAD_STARTED) reloading = true;
        if (event.event() == WeaponEvent.FIRED) relaxed = false;
        if (event.event() == WeaponEvent.RELOAD_COMPLETED || event.event() == WeaponEvent.RELOAD_CANCELLED) reloading = false;
        // Do not overwrite client stack components from presentation packets; vanilla sync owns those.
        showState(client, weapon, event.state().normalized(weapon.definition()));
    }

    private void showState(Minecraft client, WeaponItem weapon, WeaponState state) {
        Component message = Component.translatable("hud.decimation.weapon", state.totalRounds(), state.fireMode(weapon.definition()).name());
        if (reloading) message = message.copy().append(Component.translatable("hud.decimation.reloading"));
        client.gui.hud.setOverlayMessage(message, false);
    }

    private void reset() {
        ready = reloading = relaxed = false;lastWeapon = null;lastSlot = -1;lastFlags = heartbeat = 0;level = null;player = null;
        ClientWeaponPresentation.reset();
    }

    public static boolean suppressesVanillaInput() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.player.getMainHandItem().getItem() instanceof WeaponItem;
    }
}
