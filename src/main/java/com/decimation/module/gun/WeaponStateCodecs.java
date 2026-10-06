package com.decimation.module.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class WeaponStateCodecs {
    private WeaponStateCodecs() { }

    public static final Codec<WeaponState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("magazine").forGetter(WeaponState::magazine),
        Codec.BOOL.fieldOf("chambered").forGetter(WeaponState::chambered),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("fire_mode").forGetter(WeaponState::fireModeIndex)
    ).apply(instance, WeaponState::new));

    public static final StreamCodec<FriendlyByteBuf, WeaponState> STREAM_CODEC = StreamCodec.of(
        (buffer, state) -> {
            buffer.writeVarInt(state.magazine());
            buffer.writeBoolean(state.chambered());
            buffer.writeVarInt(state.fireModeIndex());
        }, buffer -> new WeaponState(buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt()));
}
