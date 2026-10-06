package com.decimation.module.gun;

import com.decimation.Decimation;
import com.decimation.module.gun.data.BallisticsDefinition;
import com.decimation.module.gun.data.WeaponDefinition;
import com.decimation.module.gun.data.WeaponMechanism;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ShotResolver {
    private static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath(Decimation.MOD_ID, "bullet"));
    private ShotResolver() { }

    public static void resolve(ServerPlayer player, ItemStack stack, WeaponDefinition definition, float aimProgress) {
        if (definition.mechanism() == WeaponMechanism.PROJECTILE) spawnProjectile(player, stack, definition, aimProgress);
        else resolveHitscan(player, definition, aimProgress);
    }

    private static void resolveHitscan(ServerPlayer player, WeaponDefinition definition, float aimProgress) {
        ServerLevel level = player.level();
        BallisticsDefinition ballistics = definition.ballistics();
        Vec3 start = player.getEyePosition(), direction = spreadDirection(player, definition, aimProgress);
        Vec3 maximumEnd = start.add(direction.scale(ballistics.range()));
        var block = level.clip(new ClipContext(start, maximumEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? maximumEnd : block.getLocation();
        // One broad-phase query; each collision is considered exactly once.
        List<EntityHit> hits = new ArrayList<>();
        for (Entity entity : level.getEntities(player, new AABB(start, end).inflate(1),
                candidate -> candidate instanceof LivingEntity && candidate.isAlive() && !candidate.isSpectator())) {
            LivingEntity living = (LivingEntity) entity;
            if (living instanceof Player target && !player.canHarmPlayer(target)) continue;
            AABB box = living.getBoundingBox().inflate(0.2);
            Vec3 position = box.contains(start) ? start : box.clip(start, end).orElse(null);
            if (position != null) hits.add(new EntityHit(living, position, start.distanceToSqr(position)));
        }
        hits.sort(Comparator.comparingDouble(EntityHit::distanceSquared));
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BULLET), player, player);
        float retained = 1;
        int count = (int) Math.min(hits.size(), (long) ballistics.penetrationCount() + 1);
        for (int index = 0; index < count; index++) {
            EntityHit hit = hits.get(index);
            float damage = ballistics.damage() * ShotMath.falloff(ballistics, Math.sqrt(hit.distanceSquared())) * retained;
            if (hit.position().y >= hit.entity().getBoundingBox().maxY - hit.entity().getBbHeight() * 0.22) {
                damage *= ballistics.headMultiplier();
            }
            hit.entity().hurtServer(level, source, damage);
            retained *= ballistics.penetrationRetention();
        }
    }

    private static void spawnProjectile(ServerPlayer player, ItemStack stack, WeaponDefinition definition, float aimProgress) {
        Arrow bolt = new Arrow(player.level(), player, new ItemStack(Items.ARROW), stack);
        bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0,
            definition.ballistics().projectileSpeed(),
            Math.max(definition.ballistics().projectileDivergence(), ShotMath.spread(definition, aimProgress)));
        bolt.setBaseDamage(definition.ballistics().damage());
        bolt.setCritArrow(false);
        // A bolt is backed by Decimation ammo; do not create recoverable vanilla arrows.
        bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
        player.level().addFreshEntity(bolt);
    }

    private static Vec3 spreadDirection(ServerPlayer player, WeaponDefinition definition, float aimProgress) {
        float spread = ShotMath.spread(definition, aimProgress);
        float pitch = player.getXRot() + (float) (player.getRandom().nextGaussian() * spread * 0.35);
        float yaw = player.getYRot() + (float) (player.getRandom().nextGaussian() * spread * 0.35);
        return Vec3.directionFromRotation(pitch, yaw);
    }

    private record EntityHit(LivingEntity entity, Vec3 position, double distanceSquared) { }
}
