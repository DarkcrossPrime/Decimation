package com.decimation.module.gun;

import com.decimation.module.gun.data.BallisticsDefinition;
import com.decimation.module.gun.data.WeaponDefinition;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class ShotResolver {
    private ShotResolver() { }

    public static void resolve(ServerPlayerEntity player, WeaponDefinition definition, float aimProgress) {
        if (definition.mechanism() == com.decimation.module.gun.data.WeaponMechanism.PROJECTILE) {
            spawnProjectile(player, definition, aimProgress);
        } else {
            resolveHitscan(player, definition, aimProgress);
        }
    }

    private static void resolveHitscan(ServerPlayerEntity player, WeaponDefinition definition, float aimProgress) {
        BallisticsDefinition ballistics = definition.ballistics();
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d direction = spreadDirection(player, definition, aimProgress);
        Vec3d maximumEnd = start.add(direction.multiply(ballistics.range()));
        BlockHitResult blockHit = player.getWorld().raycast(new RaycastContext(start, maximumEnd,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d end = blockHit.getType() == HitResult.Type.MISS ? maximumEnd : blockHit.getPos();

        int remainingHits = ballistics.penetrationCount() + 1;
        float retainedDamage = 1.0f;
        Vec3d cursor = start;
        while (remainingHits-- > 0) {
            Optional<EntityHit> target = nearestEntity(player, cursor, end);
            if (target.isEmpty()) break;
            EntityHit hit = target.get();
            double distance = start.distanceTo(hit.position());
            float damage = ballistics.damage() * falloff(ballistics, distance) * retainedDamage;
            if (isHeadshot(hit.entity(), hit.position())) damage *= ballistics.headMultiplier();
            hit.entity().damage(player.getDamageSources().playerAttack(player), damage);
            retainedDamage *= ballistics.penetrationRetention();
            cursor = hit.position().add(direction.multiply(0.01));
        }
    }

    private static Optional<EntityHit> nearestEntity(ServerPlayerEntity player, Vec3d start, Vec3d end) {
        Box search = player.getBoundingBox().stretch(end.subtract(start)).expand(1.0);
        return player.getWorld().getOtherEntities(player, search,
                entity -> entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator())
            .stream()
            .map(entity -> entity.getBoundingBox().expand(0.2).raycast(start, end)
                .map(position -> new EntityHit((LivingEntity) entity, position)).orElse(null))
            .filter(hit -> hit != null)
            .min(Comparator.comparingDouble(hit -> start.squaredDistanceTo(hit.position())));
    }

    private static void spawnProjectile(ServerPlayerEntity player, WeaponDefinition definition, float aimProgress) {
        ServerWorld world = player.getServerWorld();
        ArrowEntity bolt = new ArrowEntity(world, player);
        float spread = spread(definition, aimProgress);
        bolt.setVelocity(player, player.getPitch(), player.getYaw(), 0,
            definition.ballistics().projectileSpeed(),
            Math.max(definition.ballistics().projectileDivergence(), spread));
        bolt.setDamage(definition.ballistics().damage());
        bolt.setCritical(false);
        world.spawnEntity(bolt);
    }

    private static Vec3d spreadDirection(ServerPlayerEntity player, WeaponDefinition definition, float aimProgress) {
        float spread = spread(definition, aimProgress);
        float pitch = player.getPitch() + (float) (player.getRandom().nextGaussian() * spread * 0.35);
        float yaw = player.getYaw() + (float) (player.getRandom().nextGaussian() * spread * 0.35);
        return Vec3d.fromPolar(pitch, yaw);
    }

    private static float spread(WeaponDefinition definition, float aimProgress) {
        float progress = Math.max(0, Math.min(1, aimProgress));
        return definition.handling().hipSpread()
            + (definition.handling().adsSpread() - definition.handling().hipSpread()) * progress;
    }

    private static float falloff(BallisticsDefinition definition, double distance) {
        if (distance <= definition.falloffStart()) return 1.0f;
        double length = Math.max(0.001, definition.range() - definition.falloffStart());
        double progress = Math.min(1.0, (distance - definition.falloffStart()) / length);
        return (float) (1.0 + (definition.minimumMultiplier() - 1.0) * progress);
    }

    private static boolean isHeadshot(LivingEntity entity, Vec3d impact) {
        return impact.y >= entity.getBoundingBox().maxY - entity.getHeight() * 0.22;
    }

    private record EntityHit(LivingEntity entity, Vec3d position) { }
}
