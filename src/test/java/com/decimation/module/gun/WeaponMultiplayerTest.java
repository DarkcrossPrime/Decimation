package com.decimation.module.gun;

import com.decimation.client.gun.RemoteWeaponPose;
import com.decimation.module.gun.data.WeaponCatalog;
import com.decimation.module.gun.network.WeaponCarryPayload;
import com.decimation.module.gun.network.WeaponInputPayload;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** Deterministic multiplayer contracts; no graphical client or live server required. */
public final class WeaponMultiplayerTest {
    private static int checks;
    private static final UUID OWNER = new UUID(1, 2);
    private static final Identifier WORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier WEAPON = Identifier.parse("decimation:honeybadger");

    public static void main(String[] args) {
        var lease = new WeaponInputLease();
        check(lease.expired(0), "new session has no held input");
        for (int i = 0; i < 16; i++) check(lease.accept(100), "bounded input accepted");
        check(!lease.accept(100), "seventeenth packet rejected");
        lease.release();
        check(lease.expired(100), "rebinding releases held intent");
        check(!lease.accept(100), "slot cycling cannot reset the per-player budget");
        check(lease.accept(101), "next tick gets a new budget");
        check(!lease.expired(160) && lease.expired(161), "lease expires at exactly sixty ticks");
        for (long tick = 200; tick < 2000; tick += 20) {
            check(lease.accept(tick), "heartbeat accepted");
            check(!lease.expired(tick + 59), "heartbeat maintains lease");
        }
        Object owner = new Object(), stack = new Object(), level = new Object();
        var binding = new WeaponBinding(owner, stack, 2, level);
        check(binding.matches(owner, stack, 2, level), "same live binding persists");
        check(!binding.matches(new Object(), stack, 2, level), "respawn releases even the identical stack");
        check(!binding.matches(owner, new Object(), 2, level), "replacement stack releases");
        check(!binding.matches(owner, stack, 3, level), "slot change releases");
        check(!binding.matches(owner, stack, 2, new Object()), "dimension change releases");
        var intent = new WeaponInputPayload(2, WEAPON, 1, 42, WORLD);
        check(intent.matchesLifecycle(42, WORLD), "live input identity accepted");
        check(!intent.matchesLifecycle(43, WORLD), "delayed pre-respawn input rejected");
        check(!intent.matchesLifecycle(42, Identifier.parse("minecraft:the_nether")), "delayed pre-dimension input rejected");

        var catalog = WeaponCatalog.load(WeaponMultiplayerTest.class.getClassLoader());
        for (var definition : catalog.definitions().values()) {
            var cycle = new WeaponCycle(definition, Double.NEGATIVE_INFINITY);
            check(cycle.beginReload(100, new WeaponState(0, false, 0)), "reload starts");
            cycle.input(false, true);
            for (int age = 0; age < definition.reloadTicks(); age++) {
                cycle.advanceAim(false);
                check(cycle.aimProgress() == 0, "reload cannot grant ADS accuracy");
                check(cycle.reloadElapsed(100 + age) == age, "authoritative reload phase");
                check(cycle.reloadRemaining(100 + age) == definition.reloadTicks() - age, "authoritative remaining time");
                var packet = new WeaponCarryPayload(OWNER, Identifier.parse(definition.id()), age % 5, 42, 2,
                    WORLD, 0, age, definition.reloadTicks() - age, true, 5000 + age);
                wire(packet);
                for (long receipt : new long[]{7, 1_000_000}) {
                    var pose = RemoteWeaponPose.accept(packet, receipt, null);
                    check(pose.reloadFrame(receipt, 0) == age, "late observer resumes instead of restarting");
                    check(pose.reloading(receipt), "reload active at receipt");
                    check(!pose.reloading(receipt + packet.reloadRemaining()), "reload ends on receipt-relative clock");
                    check(pose.matches(OWNER, 42, WORLD, packet.weapon()), "correct lifecycle matches");
                    check(!pose.matches(OWNER, 43, WORLD, packet.weapon()), "old respawn entity rejected");
                    check(!pose.matches(OWNER, 42, Identifier.parse("minecraft:the_nether"), packet.weapon()), "wrong dimension rejected");
                }
            }
            check(cycle.reloadRemaining(100 + definition.reloadTicks()) == 0, "expired reload clamps to zero");
            cycle.endReload();
            check(cycle.reloadElapsed(1000) == 0, "finished reload clears phase");
        }
        var first = RemoteWeaponPose.accept(packet(100, 42, 0, 0), 10, null);
        var next = RemoteWeaponPose.accept(packet(101, 42, 4, 1), 11, first);
        check(next.carry(11, .5f) == .5f && next.aim(11, .5f) == .5f, "pose interpolates one client tick");
        check(next.carry(12, 0) == 1 && next.aim(12, 0) == 1, "pose reaches authoritative endpoint");
        check(RemoteWeaponPose.accept(packet(99, 42, 0, 0), 12, next) == next, "stale snapshot ignored");
        check(RemoteWeaponPose.accept(next.packet(), 20, next) == next, "duplicate snapshot cannot restart its timeline");
        var swapped = RemoteWeaponPose.accept(new WeaponCarryPayload(OWNER, WEAPON, 0, 42, 3, WORLD, 0, 0, 0, false, 102), 12, next);
        check(swapped.carry(12, 0) == 0 && swapped.aim(12, 0) == 0, "new slot seeds directly");
        var replaced = RemoteWeaponPose.accept(new WeaponCarryPayload(OWNER, Identifier.parse("decimation:famas"), 0, 42, 2,
            WORLD, 0, 0, 0, false, 102), 12, next);
        check(replaced.carry(12, 0) == 0 && replaced.aim(12, 0) == 0, "new weapon seeds directly");
        var respawn = RemoteWeaponPose.accept(packet(102, 43, 0, 0), 12, next);
        check(respawn.carry(12, 0) == 0 && respawn.aim(12, 0) == 0, "new body never blends the old body's pose");
        check(RemoteWeaponPose.accept(packet(0, 43, 0, 0), 0, null).packet().serverTick() == 0, "reconnect resets ordering");
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -.01f, 1.01f})
            failure(() -> packet(0, 42, 0, invalid));
        failure(() -> new WeaponCarryPayload(OWNER, WEAPON, 0, -1, 0, WORLD, 0, 0, 0, false, 0));
        failure(() -> new WeaponCarryPayload(OWNER, WEAPON, 0, 1, 9, WORLD, 0, 0, 0, false, 0));
        failure(() -> new WeaponCarryPayload(OWNER, WEAPON, 0, 1, 0, WORLD, 0, 1, 0, false, 0));
        failure(() -> new WeaponCarryPayload(OWNER, WEAPON, 0, 1, 0, WORLD, 0, 0, 0, true, 0));
        failure(() -> new WeaponCarryPayload(OWNER, WEAPON, 0, 1, 0, WORLD, 0, Integer.MAX_VALUE, 1, true, 0));
        wire(new WeaponCarryPayload(OWNER, Identifier.parse("decimation:" + "a".repeat(117)), 4, Integer.MAX_VALUE, 8,
            Identifier.parse("test:" + "a".repeat(123)), 1, 100, 200, true, Long.MAX_VALUE));
        System.out.println("Multiplayer contracts passed: " + checks + " assertions.");
    }
    private static WeaponCarryPayload packet(long tick, int entity, int carry, float aim) {
        return new WeaponCarryPayload(OWNER, WEAPON, carry, entity, 2, WORLD, aim, 0, 0, false, tick);
    }
    private static void wire(WeaponCarryPayload packet) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WeaponCarryPayload.CODEC.encode(buffer, packet);
            check(buffer.readableBytes() <= 405, "snapshot stays bounded");
            check(WeaponCarryPayload.CODEC.decode(buffer).equals(packet), "full snapshot wire round trip");
            check(buffer.readableBytes() == 0, "codec consumes exactly one payload");
        } finally { buffer.release(); }
    }
    private static void failure(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException expected) { checks++;return; }
        throw new AssertionError("invalid snapshot accepted");
    }
    private static void check(boolean condition, String message) { checks++;if (!condition) throw new AssertionError(message); }
}
