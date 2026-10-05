package com.decimation.module.gun;

import com.decimation.module.gun.network.WeaponCatalogPayload;
import com.decimation.module.gun.network.WeaponEvent;
import com.decimation.module.gun.network.WeaponEventPayload;
import com.decimation.module.gun.network.WeaponInputPayload;
import com.decimation.module.gun.network.WeaponCarryPayload;
import com.decimation.module.gun.network.WeaponSoundPayload;
import com.decimation.module.gun.data.WeaponAudio;
import com.decimation.module.gun.data.WeaponSound;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** All methods are called on the logical server thread. */
public final class WeaponServerController {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> verified = new HashSet<>();

    public void join(ServerPlayer player) {
        disconnect(player.getUUID());
        if (!ServerPlayNetworking.canSend(player, WeaponCatalogPayload.TYPE)
            || !ServerPlayNetworking.canSend(player, WeaponSoundPayload.TYPE)
            || !ServerPlayNetworking.canSend(player, WeaponCarryPayload.TYPE)
            || !ServerPlayNetworking.canSend(player, WeaponEventPayload.TYPE)) {
            player.connection.disconnect(Component.literal("This server requires the matching Decimation 26.3 build."));
            return;
        }
        ServerPlayNetworking.send(player, new WeaponCatalogPayload(WeaponCatalogPayload.PROTOCOL, GunModule.catalog().fingerprint()));
    }

    public void acceptCatalog(ServerPlayer player, WeaponCatalogPayload payload) {
        if (payload.protocol() != WeaponCatalogPayload.PROTOCOL || !payload.fingerprint().equals(GunModule.catalog().fingerprint())) {
            disconnect(player.getUUID());
            player.connection.disconnect(Component.literal("Decimation weapon definitions differ. Install the same build on client and server."));
            return;
        }
        if (!verified.add(player.getUUID())) return; // Repeated acknowledgements must not trigger snapshot sweeps.
        for (var entry : sessions.entrySet()) {
            var owner = player.level().getServer().getPlayerList().getPlayer(entry.getKey());
            var session = entry.getValue();
            if (owner != null && session.cycle != null && session.binding != null
                && session.binding.matches(owner, owner.getMainHandItem(), owner.getInventory().getSelectedSlot(), owner.level())
                && owner.isAlive() && !owner.isSpectator() && !owner.isSleeping()
                && (owner == player || PlayerLookup.tracking(owner).contains(player))) sendCarryTo(player, owner, session);
        }
    }

    public void handle(ServerPlayer player, WeaponInputPayload input) {
        if (!verified.contains(player.getUUID()) || !player.isAlive() || player.isSpectator() || player.isSleeping()) return;
        if (!input.matchesLifecycle(player.getId(), player.level().dimension().identifier())) return;
        ItemStack stack = player.getMainHandItem();
        if (input.slot() != player.getInventory().getSelectedSlot() || !(stack.getItem() instanceof WeaponItem weapon)
            || !input.weapon().equals(weapon.identifier())) return;
        long tick = now(player.level().getServer());
        Session session = sessions.computeIfAbsent(player.getUUID(), ignored -> new Session());
        bind(player, session, stack, weapon);
        if (!session.input.accept(tick)) {
            if (!input.trigger() && !input.aim()) session.cycle.stopInput();
            return;
        }
        session.cycle.input(input.trigger(), input.aim(), input.relaxed());
        WeaponState state = weapon.state(stack);
        if (input.reload() && session.lastReloadRequest != tick && hasAmmo(player, weapon)) {
            session.lastReloadRequest = tick;
            if (session.cycle.beginReload(tick, state)) {
                session.reloadAudio = new ReloadAudioTimeline(weapon.definition().audio(), tick,
                    !state.chambered() && weapon.definition().ammo().chamberCapacity() > 0);
                sendEvent(player, session, WeaponEvent.RELOAD_STARTED, state);
                sendReloadCues(player, session, tick);
            }
        }
        if (input.cycle() && session.lastModeRequest != tick && !session.cycle.reloading()) {
            session.lastModeRequest = tick;
            state = state.cycleFireMode(weapon.definition());
            weapon.writeState(stack, state);
            session.cycle.fireModeChanged();
            sendEvent(player, session, WeaponEvent.FIRE_MODE_CHANGED, state);
            sendSound(player, weapon, WeaponSound.FIRE_MODE);
        }
    }

    public void tick(MinecraftServer server) {
        long tick = now(server);
        // Only players who have used a weapon have a session; no online-player allocation sweep.
        for (var iterator = sessions.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) { iterator.remove(); continue; }
            Session session = entry.getValue();
            ItemStack stack = player.getMainHandItem();
            if (!player.isAlive() || player.isSpectator() || player.isSleeping() || !(stack.getItem() instanceof WeaponItem weapon)) {
                unbind(player, session);
                continue;
            }
            bind(player, session, stack, weapon);
            if (session.input.expired(tick)) session.cycle.stopInput();
            session.cycle.advanceAim(player.isSprinting());
            WeaponState state = weapon.state(stack);
            sendReloadCues(player, session, tick);
            if (session.cycle.reloadDue(tick)) {
                boolean consumed = consumeAmmo(player, weapon);
                session.cycle.endReload();
                cancelReloadAudio(session);
                if (consumed) {
                    state = state.reloaded(weapon.definition());
                    weapon.writeState(stack, state);
                }
                sendEvent(player, session, consumed ? WeaponEvent.RELOAD_COMPLETED : WeaponEvent.RELOAD_CANCELLED, state);
            }
            switch (session.cycle.pollShot(tick, state)) {
                case FIRED -> {
                    state = state.consumeShot(weapon.definition());
                    weapon.writeState(stack, state);
                    ShotResolver.resolve(player, stack, weapon.definition(), session.cycle.aimProgress());
                    sendShotSound(player, weapon);
                    sendEvent(player, session, WeaponEvent.FIRED, state);
                }
                case DRY_FIRE -> {
                    sendSound(player, weapon, WeaponSound.DRY_FIRE);
                    sendEvent(player, session, WeaponEvent.DRY_FIRE, state);
                }
                case NONE -> { }
            }
            if (session.sentCarry != session.cycle.loweredTicks() || session.sentAim != session.cycle.aimProgress()
                || session.sentReload != session.cycle.reloading()) {
                session.sentCarry = session.cycle.loweredTicks();session.sentAim = session.cycle.aimProgress();session.sentReload = session.cycle.reloading();
                sendCarry(player, session);
            }
        }
    }

    private void bind(ServerPlayer player, Session session, ItemStack stack, WeaponItem weapon) {
        int slot = player.getInventory().getSelectedSlot();
        if (session.binding != null && session.binding.matches(player, stack, slot, player.level())) return;
        unbind(player, session);
        session.binding = new WeaponBinding(player, stack, slot, player.level());session.stack = stack;session.weapon = weapon;session.slot = slot;
        session.cycle = new WeaponCycle(weapon.definition(), session.nextShotTick);session.sentCarry = -1;session.sentAim = -1;session.sentReload = false;
    }

    private void unbind(ServerPlayer player, Session session) {
        if (session.cycle != null) {
            if (session.cycle.reloading()) sendEvent(player, session, WeaponEvent.RELOAD_CANCELLED, session.weapon.state(session.stack));
            session.nextShotTick = session.cycle.nextShotTick();
        }
        cancelReloadAudio(session);
        session.input.release();session.binding = null;session.stack = null;session.weapon = null;session.cycle = null;
    }

    private static boolean hasAmmo(ServerPlayer player, WeaponItem weapon) {
        if (player.getAbilities().instabuild) return true;
        AmmoItem ammo = GunModule.ammunition().get(weapon.definition().ammo().itemId());
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(ammo)) return true;
        return false;
    }

    private void sendReloadCues(ServerPlayer player, Session session, long tick) {
        if (session.reloadAudio == null) return;
        WeaponSound cue;
        while ((cue = session.reloadAudio.nextDue(tick)) != null) sendSound(player, session.weapon, cue);
    }

    private static void cancelReloadAudio(Session session) {
        if (session.reloadAudio != null) session.reloadAudio.cancel();
        session.reloadAudio = null;
    }

    private void sendShotSound(ServerPlayer source, WeaponItem weapon) {
        WeaponAudio audio = weapon.definition().audio();
        WeaponSoundPayload primary = soundPayload(source, weapon, audio.shot());
        WeaponSoundPayload distant = audio.shot() != WeaponSound.FIRE_SUPPRESSED
            && audio.sound(WeaponSound.FIRE_DISTANT) != null
            ? soundPayload(source, weapon, WeaponSound.FIRE_DISTANT) : primary;
        for (ServerPlayer recipient : PlayerLookup.around(source.level(), source.position(), audio.maximumShotRange())) {
            if (recipient == source) continue;
            double distance = Math.sqrt(source.distanceToSqr(recipient));
            WeaponSound cue = audio.shotForDistance(distance);
            if (distance <= WeaponAudio.range(cue)) sendSoundTo(recipient, cue == WeaponSound.FIRE_DISTANT ? distant : primary);
        }
        sendSoundTo(source, primary);
    }

    private void sendSound(ServerPlayer source, WeaponItem weapon, WeaponSound cue) {
        if (weapon.definition().audio().sound(cue) == null) return;
        WeaponSoundPayload payload = soundPayload(source, weapon, cue);
        for (ServerPlayer recipient : PlayerLookup.around(source.level(), source.position(), WeaponAudio.range(cue))) {
            if (recipient != source) sendSoundTo(recipient, payload);
        }
        sendSoundTo(source, payload);
    }

    private void sendSoundTo(ServerPlayer recipient, WeaponSoundPayload payload) {
        if (verified.contains(recipient.getUUID()) && ServerPlayNetworking.canSend(recipient, WeaponSoundPayload.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    private static WeaponSoundPayload soundPayload(ServerPlayer source, WeaponItem weapon, WeaponSound cue) {
        var position = source.getEyePosition();
        return new WeaponSoundPayload(weapon.identifier(), cue, source.level().dimension().identifier(),
            position.x, position.y, position.z);
    }

    private static boolean consumeAmmo(ServerPlayer player, WeaponItem weapon) {
        if (player.getAbilities().instabuild) return true;
        AmmoItem ammo = GunModule.ammunition().get(weapon.definition().ammo().itemId());
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.isEmpty() && candidate.is(ammo)) {
                candidate.shrink(1);inventory.setChanged();return true;
            }
        }
        return false;
    }

    private void sendEvent(ServerPlayer owner, Session session, WeaponEvent event, WeaponState state) {
        WeaponEventPayload payload = new WeaponEventPayload(owner.getUUID(), session.weapon.identifier(), session.slot, event, state,
            now(owner.level().getServer()), owner.getId(), owner.level().dimension().identifier());
        for (ServerPlayer recipient : PlayerLookup.tracking(owner)) {
            if (recipient != owner && verified.contains(recipient.getUUID()) && ServerPlayNetworking.canSend(recipient, WeaponEventPayload.TYPE)) ServerPlayNetworking.send(recipient, payload);
        }
        if (verified.contains(owner.getUUID()) && ServerPlayNetworking.canSend(owner, WeaponEventPayload.TYPE)) ServerPlayNetworking.send(owner, payload);
    }

    public void startTracking(net.minecraft.world.entity.Entity entity, ServerPlayer observer) {
        if (!(entity instanceof ServerPlayer owner)) return;
        var session = sessions.get(owner.getUUID());
        if (session != null && session.cycle != null && session.binding.matches(owner, owner.getMainHandItem(), owner.getInventory().getSelectedSlot(), owner.level())
            && owner.isAlive() && !owner.isSpectator() && !owner.isSleeping()) sendCarryTo(observer, owner, session);
    }
    private void sendCarry(ServerPlayer owner, Session session) {
        var payload = carryPayload(owner, session); // Reuse one immutable snapshot for every observer.
        for (var observer : PlayerLookup.tracking(owner)) if (observer != owner) sendCarryTo(observer, payload);
        sendCarryTo(owner, payload);
    }
    private void sendCarryTo(ServerPlayer observer, ServerPlayer owner, Session session) {
        sendCarryTo(observer, carryPayload(owner, session));
    }
    private void sendCarryTo(ServerPlayer observer, WeaponCarryPayload payload) {
        if (verified.contains(observer.getUUID()) && ServerPlayNetworking.canSend(observer, WeaponCarryPayload.TYPE))
            ServerPlayNetworking.send(observer, payload);
    }
    private static WeaponCarryPayload carryPayload(ServerPlayer owner, Session session) {
        long tick = now(owner.level().getServer());
        int remaining = session.cycle.reloadRemaining(tick);
        boolean rack = remaining > 0 && !session.weapon.state(session.stack).chambered() && session.weapon.definition().ammo().chamberCapacity() > 0;
        return new WeaponCarryPayload(owner.getUUID(), session.weapon.identifier(), session.cycle.loweredTicks(), owner.getId(), session.slot,
            owner.level().dimension().identifier(), session.cycle.aimProgress(), session.cycle.reloadElapsed(tick), remaining, rack, tick);
    }

    private static long now(MinecraftServer server) { return server.overworld().getLevelData().getGameTime(); }
    public void disconnect(UUID player) { sessions.remove(player);verified.remove(player); }
    public void clear() { sessions.clear();verified.clear(); }

    private static final class Session {
        private ItemStack stack;
        private WeaponBinding binding;
        private WeaponItem weapon;
        private int slot, sentCarry = -1;
        private float sentAim = -1;
        private boolean sentReload;
        private final WeaponInputLease input = new WeaponInputLease();
        private WeaponCycle cycle;
        private ReloadAudioTimeline reloadAudio;
        private double nextShotTick = Double.NEGATIVE_INFINITY;
        private long lastReloadRequest = Long.MIN_VALUE, lastModeRequest = Long.MIN_VALUE;
    }
}
