package com.decimation.module.gun;

import com.decimation.module.gun.data.FireMode;
import com.decimation.module.gun.data.WeaponDefinition;
import com.decimation.module.gun.data.WeaponSound;
import com.decimation.module.gun.data.WeaponSoundCue;
import com.decimation.module.gun.network.WeaponAction;
import com.decimation.module.gun.network.WeaponEvent;
import com.decimation.module.gun.network.WeaponPackets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class WeaponServerController {
    private static final double TICKS_PER_MINUTE = 20.0 * 60.0;
    private final Map<UUID, ControlState> controls = new HashMap<>();

    public void handle(ServerPlayerEntity player, WeaponAction action) {
        ControlState control = controls.computeIfAbsent(player.getUuid(), ignored -> new ControlState());
        switch (action) {
            case TRIGGER_DOWN -> {
                if (!control.triggerHeld) control.triggerPressed = true;
                control.triggerHeld = true;
            }
            case TRIGGER_UP -> control.triggerHeld = false;
            case AIM_DOWN -> {
                control.aiming = true;
                control.aimTicks = 0;
            }
            case AIM_UP -> {
                control.aiming = false;
                control.aimTicks = 0;
            }
            case RELOAD -> startReload(player, control);
            case CYCLE_FIRE_MODE -> cycleFireMode(player, control);
        }
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) tickPlayer(player);
        controls.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
    }

    private void tickPlayer(ServerPlayerEntity player) {
        ControlState control = controls.computeIfAbsent(player.getUuid(), ignored -> new ControlState());
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof WeaponItem weapon)) {
            cancelReload(player, control);
            control.resetInput();
            return;
        }
        WeaponDefinition definition = weapon.definition();
        if (control.aiming) control.aimTicks++;
        if (control.reloading) {
            if (control.reloadStack != stack || !control.reloadWeapon.equals(definition.id())) {
                cancelReload(player, control);
            } else {
                emitReloadSounds(player, definition,
                    player.getWorld().getTime() - control.reloadStartedAt, control);
                if (player.getWorld().getTime() >= control.reloadEndsAt) {
                    completeReload(player, stack, definition, control);
                }
            }
        }
        if (control.reloading) {
            control.triggerPressed = false;
            return;
        }

        WeaponState state = WeaponState.read(stack, definition);
        FireMode mode = state.fireMode(definition);
        if (control.triggerPressed && mode == FireMode.BURST) control.burstRemaining = definition.burstSize();
        boolean wantsShot = switch (mode) {
            case SEMI -> control.triggerPressed;
            case BURST -> control.burstRemaining > 0;
            case AUTOMATIC -> control.triggerHeld;
        };
        control.triggerPressed = false;
        if (!wantsShot) {
            control.fireBudget = Math.min(1.0, control.fireBudget + definition.rateOfFire() / TICKS_PER_MINUTE);
            return;
        }

        control.fireBudget = Math.min(1.0, control.fireBudget + definition.rateOfFire() / TICKS_PER_MINUTE);
        if (control.fireBudget < 1.0) return;
        control.fireBudget -= 1.0;
        if (!state.consumeShot()) {
            control.burstRemaining = 0;
            sendSound(player, definition, WeaponSound.DRY_FIRE);
            sendEvent(player, definition, state, WeaponEvent.DRY_FIRE);
            return;
        }
        if (mode == FireMode.BURST) control.burstRemaining--;
        state.write(stack, definition);
        float aimProgress = control.aiming
            ? Math.min(1.0f, control.aimTicks / (float) Math.max(1, definition.handling().adsTicks())) : 0;
        ShotResolver.resolve(player, definition, aimProgress);
        sendShotSound(player, definition);
        sendEvent(player, definition, state, WeaponEvent.FIRED);
    }

    private void startReload(ServerPlayerEntity player, ControlState control) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof WeaponItem weapon) || control.reloading) return;
        WeaponDefinition definition = weapon.definition();
        WeaponState state = WeaponState.read(stack, definition);
        if (state.isFull(definition) || !hasAmmunition(player, definition)) return;
        control.reloading = true;
        control.reloadStack = stack;
        control.reloadWeapon = definition.id();
        control.reloadStartedAt = player.getWorld().getTime();
        control.reloadEndsAt = player.getWorld().getTime() + definition.reloadTicks();
        control.reloadCueIndex = 0;
        control.reloadNeedsRack = !state.chambered() && definition.ammo().chamberCapacity() > 0;
        control.triggerHeld = false;
        control.burstRemaining = 0;
        sendEvent(player, definition, state, WeaponEvent.RELOAD_STARTED);
    }

    private static void emitReloadSounds(ServerPlayerEntity player, WeaponDefinition definition,
                                         long elapsed, ControlState control) {
        while (control.reloadCueIndex < definition.audio().reloadCues().size()) {
            WeaponSoundCue cue = definition.audio().reloadCues().get(control.reloadCueIndex);
            if (cue.tick() > elapsed) return;
            if (cue.sound() != WeaponSound.RACK || control.reloadNeedsRack) {
                sendSound(player, definition, cue.sound());
            }
            control.reloadCueIndex++;
        }
    }

    private void completeReload(ServerPlayerEntity player, ItemStack stack,
                                WeaponDefinition definition, ControlState control) {
        if (!consumeAmmunition(player, definition)) {
            cancelReload(player, control);
            return;
        }
        WeaponState state = WeaponState.read(stack, definition);
        state.reload(definition);
        state.write(stack, definition);
        control.clearReload();
        sendEvent(player, definition, state, WeaponEvent.RELOAD_COMPLETED);
    }

    private void cycleFireMode(ServerPlayerEntity player, ControlState control) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof WeaponItem weapon) || control.reloading) return;
        WeaponDefinition definition = weapon.definition();
        WeaponState state = WeaponState.read(stack, definition);
        state.cycleFireMode(definition);
        state.write(stack, definition);
        sendSound(player, definition, WeaponSound.FIRE_MODE);
        sendEvent(player, definition, state, WeaponEvent.FIRE_MODE_CHANGED);
    }

    private static boolean hasAmmunition(ServerPlayerEntity player, WeaponDefinition definition) {
        return player.getAbilities().creativeMode
            || player.getInventory().contains(new ItemStack(GunModule.ammo(definition.ammo().itemId())));
    }

    private static boolean consumeAmmunition(ServerPlayerEntity player, WeaponDefinition definition) {
        if (player.getAbilities().creativeMode) return true;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack candidate = inventory.getStack(slot);
            if (candidate.isOf(GunModule.ammo(definition.ammo().itemId()))) {
                candidate.decrement(1);
                return true;
            }
        }
        return false;
    }

    private void cancelReload(ServerPlayerEntity player, ControlState control) {
        if (!control.reloading) return;
        if (control.reloadStack != null && control.reloadStack.getItem() instanceof WeaponItem weapon) {
            sendEvent(player, weapon.definition(), WeaponState.read(control.reloadStack, weapon.definition()),
                WeaponEvent.RELOAD_CANCELLED);
        }
        control.clearReload();
    }

    private static void sendShotSound(ServerPlayerEntity owner, WeaponDefinition definition) {
        for (ServerPlayerEntity recipient : soundRecipients(owner)) {
            WeaponSound cue = definition.audio().shotForDistance(recipient.distanceTo(owner));
            sendSoundPacket(recipient, owner, definition, cue);
        }
    }

    private static void sendSound(ServerPlayerEntity owner, WeaponDefinition definition, WeaponSound cue) {
        for (ServerPlayerEntity recipient : soundRecipients(owner)) {
            sendSoundPacket(recipient, owner, definition, cue);
        }
    }

    private static Set<ServerPlayerEntity> soundRecipients(ServerPlayerEntity owner) {
        Set<ServerPlayerEntity> recipients = new LinkedHashSet<>(PlayerLookup.tracking(owner));
        recipients.add(owner);
        return recipients;
    }

    private static void sendSoundPacket(ServerPlayerEntity recipient, ServerPlayerEntity source,
                                        WeaponDefinition definition, WeaponSound cue) {
        if (definition.audio().sound(cue) == null) return;
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeIdentifier(definition.id());
        buffer.writeVarInt(cue.ordinal());
        buffer.writeDouble(source.getX());
        buffer.writeDouble(source.getEyeY());
        buffer.writeDouble(source.getZ());
        ServerPlayNetworking.send(recipient, WeaponPackets.SOUND, buffer);
    }

    private static void sendEvent(ServerPlayerEntity owner, WeaponDefinition definition,
                                  WeaponState state, WeaponEvent event) {
        for (ServerPlayerEntity recipient : PlayerLookup.tracking(owner)) {
            ServerPlayNetworking.send(recipient, WeaponPackets.EVENT, eventBuffer(owner, definition, state, event));
        }
        ServerPlayNetworking.send(owner, WeaponPackets.EVENT, eventBuffer(owner, definition, state, event));
    }

    private static PacketByteBuf eventBuffer(ServerPlayerEntity owner, WeaponDefinition definition,
                                             WeaponState state, WeaponEvent event) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(owner.getUuid());
        buffer.writeIdentifier(definition.id());
        buffer.writeVarInt(event.ordinal());
        buffer.writeVarInt(state.totalRounds());
        buffer.writeVarInt(state.fireMode(definition).ordinal());
        buffer.writeLong(owner.getWorld().getTime());
        return buffer;
    }

    private static final class ControlState {
        private boolean triggerHeld;
        private boolean triggerPressed;
        private boolean aiming;
        private int aimTicks;
        private int burstRemaining;
        private double fireBudget = 1.0;
        private boolean reloading;
        private ItemStack reloadStack;
        private net.minecraft.util.Identifier reloadWeapon;
        private long reloadStartedAt;
        private long reloadEndsAt;
        private int reloadCueIndex;
        private boolean reloadNeedsRack;

        private void clearReload() {
            reloading = false;
            reloadStack = null;
            reloadWeapon = null;
            reloadStartedAt = 0;
            reloadEndsAt = 0;
            reloadCueIndex = 0;
            reloadNeedsRack = false;
        }

        private void resetInput() {
            triggerHeld = false;
            triggerPressed = false;
            aiming = false;
            aimTicks = 0;
            burstRemaining = 0;
        }
    }
}
