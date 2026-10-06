package com.decimation.module.gun.network;

import com.decimation.Decimation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Input only: no client position, damage, ammo count or cadence. */
public record WeaponInputPayload(int slot, Identifier weapon, int flags, int entityId, Identifier dimension) implements CustomPacketPayload {
    public static final int TRIGGER = 1, AIM = 2, RELOAD = 4, CYCLE = 8, RELAXED = 16;
    public static final Type<WeaponInputPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Decimation.MOD_ID, "weapon/input"));
    public static final StreamCodec<FriendlyByteBuf, WeaponInputPayload> CODEC = StreamCodec.of(
        (buffer, value) -> {
            buffer.writeByte(value.slot());
            buffer.writeUtf(value.weapon().toString(), 128);
            buffer.writeByte(value.flags());
            buffer.writeVarInt(value.entityId());buffer.writeUtf(value.dimension().toString(), 128);
        }, buffer -> new WeaponInputPayload(buffer.readUnsignedByte(), Identifier.parse(buffer.readUtf(128)), buffer.readUnsignedByte(),
            buffer.readVarInt(), Identifier.parse(buffer.readUtf(128))));

    public WeaponInputPayload {
        if (slot < 0 || slot > 8 || flags < 0 || flags > 31 || weapon == null
            || !Decimation.MOD_ID.equals(weapon.getNamespace()) || weapon.toString().length() > 128
            || entityId < 0 || dimension == null || dimension.toString().length() > 128) {
            throw new IllegalArgumentException("invalid weapon input");
        }
    }

    public boolean matchesLifecycle(int entityId, Identifier dimension) { return this.entityId == entityId && this.dimension.equals(dimension); }

    public boolean relaxed() { return (flags & RELAXED) != 0; }
    public boolean trigger() { return (flags & TRIGGER) != 0; }
    public boolean aim() { return (flags & AIM) != 0; }
    public boolean reload() { return (flags & RELOAD) != 0; }
    public boolean cycle() { return (flags & CYCLE) != 0; }
    @Override public Type<WeaponInputPayload> type() { return TYPE; }
}
