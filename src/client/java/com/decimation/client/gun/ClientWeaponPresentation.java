package com.decimation.client.gun;

import com.decimation.module.gun.WeaponItem;
import com.decimation.module.gun.network.WeaponEvent;
import com.decimation.module.gun.network.WeaponEventPayload;
import com.decimation.module.gun.network.WeaponCarryPayload;
import com.decimation.module.gun.WeaponCarry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/** Presentation state belongs to the extraction/game thread; draw callbacks receive only snapshots. */
public final class ClientWeaponPresentation {
    private static final WeaponMotion motion = new WeaponMotion();
    private static final Map<UUID, Animation> animations = new HashMap<>();
    private static final Map<UUID, RemoteWeaponPose> carries = new HashMap<>();
    private static float lastYaw, lastPitch;
    private static ClientLevel level;
    private static net.minecraft.client.player.LocalPlayer player;
    private static WeaponItem held;
    private static int slot = -1;
    private ClientWeaponPresentation() { }

    public static void tick(Minecraft client, boolean ready) {
        if (client.level != level) { reset();level = client.level; }
        if (client.player == null || client.level == null) { reset();return; }
        if (client.player != player) {
            motion.reset();held = null;slot = -1;player = client.player;
            animations.remove(player.getUUID());
            lastYaw = player.getYRot();lastPitch = player.getXRot();
        }
        WeaponItem weapon = client.player.getMainHandItem().getItem() instanceof WeaponItem item ? item : null;
        int selected = client.player.getInventory().getSelectedSlot();
        if (weapon != held || selected != slot) {
            motion.reset();animations.remove(client.player.getUUID());held = weapon;slot = selected;
            lastYaw = client.player.getYRot();lastPitch = client.player.getXRot();
        }
        if (client.isPaused()) return;
        long tick = client.level.getGameTime();
        animations.entrySet().removeIf(entry -> {
            var owner = client.level.getPlayerByUUID(entry.getKey());
            var snapshot = carries.get(entry.getKey());
            long observed = snapshot == null ? entry.getValue().start : snapshot.receivedAt();
            return owner == null && tick >= observed + 40 || owner != null && (!owner.isAlive() || owner.getId() != entry.getValue().entityId)
                || tick >= entry.getValue().end || owner != null && (
                !(owner.getMainHandItem().getItem() instanceof WeaponItem item)
                || !item.identifier().equals(entry.getValue().weapon)) && tick >= observed + 40;
        });
        carries.entrySet().removeIf(entry -> {
            var owner = client.level.getPlayerByUUID(entry.getKey());
            var pose = entry.getValue();
            return owner == null && tick >= pose.receivedAt() + 40 || owner != null && (!owner.isAlive() || owner.getId() != pose.packet().entityId())
                || owner != null && (!(owner.getMainHandItem().getItem() instanceof WeaponItem item)
                || !item.identifier().equals(pose.packet().weapon())) && tick >= pose.receivedAt() + 40;
        });
        boolean playing = ready && weapon != null && client.isWindowActive() && client.gui.screen() == null && client.player.isAlive()
            && !client.player.isSpectator() && !client.player.isSleeping();
        boolean sprinting = playing && client.player.isSprinting();
        var active = animations.get(client.player.getUUID());
        boolean reloading = active != null && active.kind == Kind.RELOAD;
        motion.tick(playing && client.options.keyUse.isDown() && !sprinting && !reloading, sprinting,
            playing && (client.options.keyAttack.isDown() || active != null && active.kind == Kind.FIRE) && !reloading,
            weapon == null ? 1 : weapon.definition().handling().adsTicks(), playing && ClientWeaponController.relaxed(), reloading);
        var velocity = client.player.getDeltaMovement();
        float speed = client.player.onGround() ? (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) : 0;
        motion.updateAmbient(playing ? speed : 0, net.minecraft.util.Mth.wrapDegrees(client.player.getYRot() - lastYaw), client.player.getXRot() - lastPitch);
        lastYaw = client.player.getYRot();lastPitch = client.player.getXRot();
    }

    public static boolean accept(Minecraft client, WeaponEventPayload event) {
        if (client.level == null || client.player == null || client.level != level || !client.level.dimension().identifier().equals(event.dimension())) return false;
        var owner = client.level.getPlayerByUUID(event.owner());
        if (owner == null || owner.getId() != event.entityId() || !owner.isAlive() || !(owner.getMainHandItem().getItem() instanceof WeaponItem weapon)
            || !weapon.identifier().equals(event.weapon())) return false;
        boolean local = owner == client.player;
        if (local && client.player.getInventory().getSelectedSlot() != event.slot()) return false;
        var latest = carries.get(event.owner());
        if (latest != null && event.serverTick() < latest.packet().serverTick()) return false;
        long start = client.level.getGameTime(); // Receipt time preserves the opening frames under latency.
        if (event.event() == WeaponEvent.FIRED) {
            var model = client.getModelManager().getItemModel(event.weapon());
            int length = model instanceof WeaponItemModel itemModel ? itemModel.data().fire().length() : 2;
            animations.put(event.owner(), new Animation(event.weapon(), Kind.FIRE, start, start + Math.max(4, length), false, event.entityId()));
            if (local) motion.fired(weapon.definition().handling().recoilPitch(),
                (client.level.getRandom().nextBoolean() ? 1 : -1) * weapon.definition().handling().recoilYaw());
        } else if (event.event() == WeaponEvent.RELOAD_STARTED) {
            animations.put(event.owner(), new Animation(event.weapon(), Kind.RELOAD, start,
                start + weapon.definition().reloadTicks(), !event.state().chambered() && weapon.definition().ammo().chamberCapacity() > 0, event.entityId()));
        } else if (event.event() == WeaponEvent.RELOAD_COMPLETED || event.event() == WeaponEvent.RELOAD_CANCELLED) {
            animations.remove(event.owner());
        }
        return true;
    }

    public static void acceptCarry(Minecraft client, WeaponCarryPayload packet) {
        if (client.level == null || !client.level.dimension().identifier().equals(packet.dimension())) return;
        if (client.level != level) { reset();level = client.level; }
        var owner = client.level.getPlayerByUUID(packet.owner());
        if (owner != null && (owner.getId() != packet.entityId() || !owner.isAlive())) return;
        if (owner == client.player && owner != null && client.player.getInventory().getSelectedSlot() != packet.slot()) return;
        var previous = carries.get(packet.owner());
        long tick = client.level.getGameTime();
        var next = RemoteWeaponPose.accept(packet, tick, previous);
        if (next == previous) return;
        carries.put(packet.owner(), next);
        if (packet.reloadRemaining() > 0) {
            animations.put(packet.owner(), new Animation(packet.weapon(), Kind.RELOAD, tick - packet.reloadElapsed(),
                tick + packet.reloadRemaining(), packet.rack(), packet.entityId()));
        } else {
            var active = animations.get(packet.owner());
            if (active != null && active.kind == Kind.RELOAD) animations.remove(packet.owner());
        }
    }

    public static float aimProgress(float partialTick) {
        var pose = motion.sample(partialTick);return pose.aim() * (1 - pose.sprint());
    }

    public static Sample sample(LivingEntity owner, Identifier weapon, float partialTick, boolean firstPerson) {
        if (owner == null || owner.level() != level) return Sample.REST;
        Animation animation = animations.get(owner.getUUID());
        float delta = Float.isFinite(partialTick) ? Math.clamp(partialTick, 0, 1) : 0;
        boolean active = animation != null && animation.entityId == owner.getId() && animation.weapon.equals(weapon) && level.getGameTime() < animation.end;
        boolean reload = active && animation.kind == Kind.RELOAD;
        WeaponMotion.Snapshot pose;
        if (firstPerson && owner == Minecraft.getInstance().player) {
            pose = motion.sample(delta, level.getGameTime() + delta, reload ? 1 : 0);
        } else {
            var carry = carries.get(owner.getUUID());
            boolean matched = carry != null && carry.matches(owner.getUUID(), owner.getId(), level.dimension().identifier(), weapon);
            float lowered = matched ? carry.carry(level.getGameTime(), delta) : 0;
            float aimed = matched && !owner.isSprinting() ? carry.aim(level.getGameTime(), delta) : 0;
            var velocity = owner.getDeltaMovement();
            float moving = owner.onGround() ? (float) Math.min(1, Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 4) : 0;
            pose = new WeaponMotion.Snapshot(aimed, owner.isSprinting() ? 1 : 0, 0, 0, active && animation.kind == Kind.FIRE ? 1 : 0, lowered,
                WeaponAmbientMotion.wave(level.getGameTime() + delta, moving, 0, 0, aimed, reload ? 1 : 0));
        }
        if (!active) {
            return new Sample(pose, Kind.NONE, 0, false);
        }
        return new Sample(pose, animation.kind, Math.max(0, level.getGameTime() - animation.start + delta), animation.rack);
    }

    public static void reset() { animations.clear();carries.clear();motion.reset();lastYaw = lastPitch = 0;level = null;player = null;held = null;slot = -1; }
    public enum Kind { NONE, FIRE, RELOAD }
    public record Sample(WeaponMotion.Snapshot motion, Kind kind, float frame, boolean rack) {
        public static final Sample REST = new Sample(WeaponMotion.Snapshot.REST, Kind.NONE, 0, false);
    }
    private record Animation(Identifier weapon, Kind kind, long start, long end, boolean rack, int entityId) { }
}
