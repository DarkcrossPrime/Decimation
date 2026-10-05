package com.decimation.module.gun.network;

import com.decimation.Decimation;
import com.decimation.module.gun.WeaponCarry;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Authoritative pose snapshot, including late-observer reload phase and lifecycle identity. */
public record WeaponCarryPayload(UUID owner, Identifier weapon, int loweredTicks, int entityId, int slot,
                                 Identifier dimension, float aim, int reloadElapsed, int reloadRemaining,
                                 boolean rack, long serverTick) implements CustomPacketPayload {
    public WeaponCarryPayload(UUID owner, Identifier weapon, int loweredTicks) {
        this(owner, weapon, loweredTicks, 0, 0, Identifier.parse("minecraft:overworld"), 0, 0, 0, false, 0);
    }
    public static final Type<WeaponCarryPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Decimation.MOD_ID, "weapon/carry"));
    public static final StreamCodec<FriendlyByteBuf, WeaponCarryPayload> CODEC = StreamCodec.of(
        (buffer, value) -> {
            buffer.writeUUID(value.owner());buffer.writeUtf(value.weapon().toString(), 128);buffer.writeByte(value.loweredTicks());
            buffer.writeVarInt(value.entityId());buffer.writeByte(value.slot());buffer.writeUtf(value.dimension().toString(), 128);
            buffer.writeFloat(value.aim());buffer.writeVarInt(value.reloadElapsed());buffer.writeVarInt(value.reloadRemaining());
            buffer.writeBoolean(value.rack());buffer.writeLong(value.serverTick());
        }, buffer -> new WeaponCarryPayload(buffer.readUUID(), Identifier.parse(buffer.readUtf(128)), buffer.readUnsignedByte(),
            buffer.readVarInt(), buffer.readUnsignedByte(), Identifier.parse(buffer.readUtf(128)), buffer.readFloat(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readLong()));
    public WeaponCarryPayload {
        Objects.requireNonNull(owner);Objects.requireNonNull(weapon);Objects.requireNonNull(dimension);
        if (loweredTicks < 0 || loweredTicks > WeaponCarry.RAISE_TICKS || !Decimation.MOD_ID.equals(weapon.getNamespace())
            || weapon.toString().length() > 128 || dimension.toString().length() > 128 || entityId < 0 || slot < 0 || slot > 8
            || !Float.isFinite(aim) || aim < 0 || aim > 1 || reloadElapsed < 0 || reloadRemaining < 0
            || (long) reloadElapsed + reloadRemaining > Integer.MAX_VALUE || serverTick < 0
            || reloadRemaining == 0 && (reloadElapsed != 0 || rack)) throw new IllegalArgumentException("invalid carry pose");
    }
    @Override public Type<WeaponCarryPayload> type() { return TYPE; }
}
