package com.decimation.module.gun.network;

import com.decimation.Decimation;
import com.decimation.module.gun.data.WeaponSound;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-selected cue and position; dimension prevents late sounds leaking across a level change. */
public record WeaponSoundPayload(Identifier weapon, WeaponSound cue, Identifier dimension,
                                 double x, double y, double z) implements CustomPacketPayload {
    private static final WeaponSound[] CUES = WeaponSound.values();
    public static final Type<WeaponSoundPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Decimation.MOD_ID, "weapon/sound"));
    public static final StreamCodec<FriendlyByteBuf, WeaponSoundPayload> CODEC = StreamCodec.of(
        (buffer, value) -> {
            buffer.writeUtf(value.weapon().toString(), 128);
            buffer.writeByte(value.cue().ordinal());
            buffer.writeUtf(value.dimension().toString(), 128);
            buffer.writeDouble(value.x());buffer.writeDouble(value.y());buffer.writeDouble(value.z());
        }, buffer -> {
            Identifier weapon = Identifier.parse(buffer.readUtf(128));
            int cue = buffer.readUnsignedByte();
            if (cue >= CUES.length) throw new IllegalArgumentException("invalid weapon sound cue");
            return new WeaponSoundPayload(weapon, CUES[cue], Identifier.parse(buffer.readUtf(128)),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        });

    public WeaponSoundPayload {
        Objects.requireNonNull(weapon);Objects.requireNonNull(cue);Objects.requireNonNull(dimension);
        if (!Decimation.MOD_ID.equals(weapon.getNamespace()) || weapon.toString().length() > 128 || dimension.toString().length() > 128
            || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("invalid weapon sound payload");
        }
    }

    @Override public Type<WeaponSoundPayload> type() { return TYPE; }
}
