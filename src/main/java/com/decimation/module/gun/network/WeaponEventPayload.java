package com.decimation.module.gun.network;

import com.decimation.Decimation;
import com.decimation.module.gun.WeaponState;
import com.decimation.module.gun.WeaponStateCodecs;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Presentation notification; persistent state also uses vanilla stack synchronization. */
public record WeaponEventPayload(UUID owner, Identifier weapon, int slot, WeaponEvent event,
                                 WeaponState state, long serverTick, int entityId, Identifier dimension) implements CustomPacketPayload {
    private static final WeaponEvent[] EVENTS = WeaponEvent.values();
    public static final Type<WeaponEventPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Decimation.MOD_ID, "weapon/event"));
    public static final StreamCodec<FriendlyByteBuf, WeaponEventPayload> CODEC = StreamCodec.of(
        (buffer, value) -> {
            buffer.writeUUID(value.owner());
            buffer.writeUtf(value.weapon().toString(), 128);
            buffer.writeByte(value.slot());
            buffer.writeByte(value.event().ordinal());
            WeaponStateCodecs.STREAM_CODEC.encode(buffer, value.state());
            buffer.writeLong(value.serverTick());
            buffer.writeVarInt(value.entityId());buffer.writeUtf(value.dimension().toString(), 128);
        }, buffer -> {
            UUID owner = buffer.readUUID();
            Identifier weapon = Identifier.parse(buffer.readUtf(128));
            int slot = buffer.readUnsignedByte(), event = buffer.readUnsignedByte();
            if (event >= EVENTS.length) throw new IllegalArgumentException("invalid weapon event");
            return new WeaponEventPayload(owner, weapon, slot, EVENTS[event],
                WeaponStateCodecs.STREAM_CODEC.decode(buffer), buffer.readLong(), buffer.readVarInt(), Identifier.parse(buffer.readUtf(128)));
        });

    public WeaponEventPayload {
        Objects.requireNonNull(owner);Objects.requireNonNull(weapon);Objects.requireNonNull(event);Objects.requireNonNull(state);Objects.requireNonNull(dimension);
        if (slot < 0 || slot > 8 || !Decimation.MOD_ID.equals(weapon.getNamespace()) || weapon.toString().length() > 128
            || entityId < 0 || serverTick < 0 || dimension.toString().length() > 128) {
            throw new IllegalArgumentException("invalid weapon event identity");
        }
    }

    @Override public Type<WeaponEventPayload> type() { return TYPE; }
}
