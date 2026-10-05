package com.decimation.client.gun;

import com.decimation.module.gun.WeaponCarry;
import com.decimation.module.gun.network.WeaponCarryPayload;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/** Receipt-relative timeline: dimension clocks need not agree with the server's global clock. */
public record RemoteWeaponPose(WeaponCarryPayload packet, long receivedAt, float previousCarry, float previousAim) {
    public static RemoteWeaponPose accept(WeaponCarryPayload packet, long now, RemoteWeaponPose previous) {
        if (previous != null && (packet.serverTick() < previous.packet.serverTick() || packet.equals(previous.packet))) return previous;
        boolean same = previous != null && previous.packet.owner().equals(packet.owner())
            && previous.packet.entityId() == packet.entityId() && previous.packet.slot() == packet.slot()
            && previous.packet.dimension().equals(packet.dimension()) && previous.packet.weapon().equals(packet.weapon());
        return new RemoteWeaponPose(packet, now, same ? previous.carry(now, 0) : packet.loweredTicks() / (float) WeaponCarry.RAISE_TICKS,
            same ? previous.aim(now, 0) : packet.aim());
    }
    public boolean matches(UUID owner, int entityId, Identifier dimension, Identifier weapon) {
        return packet.owner().equals(owner) && packet.entityId() == entityId && packet.dimension().equals(dimension) && packet.weapon().equals(weapon);
    }
    public float carry(long now, float delta) { return lerp(previousCarry, packet.loweredTicks() / (float) WeaponCarry.RAISE_TICKS, now, delta); }
    public float aim(long now, float delta) { return lerp(previousAim, packet.aim(), now, delta); }
    public boolean reloading(long now) { return packet.reloadRemaining() > 0 && now - receivedAt < packet.reloadRemaining(); }
    public float reloadFrame(long now, float delta) { return packet.reloadElapsed() + Math.max(0, now - receivedAt + delta); }
    private float lerp(float from, float to, long now, float delta) { return from + (to - from) * Math.clamp(now - receivedAt + delta, 0, 1); }
}
