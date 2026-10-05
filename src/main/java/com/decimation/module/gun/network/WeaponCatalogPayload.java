package com.decimation.module.gun.network;

import com.decimation.Decimation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One exchange at join ensures presentation definitions match server gameplay. */
public record WeaponCatalogPayload(int protocol, String fingerprint) implements CustomPacketPayload {
    public static final int PROTOCOL = 4;
    public static final Type<WeaponCatalogPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Decimation.MOD_ID, "weapon/catalog"));
    public static final StreamCodec<FriendlyByteBuf, WeaponCatalogPayload> CODEC = StreamCodec.of(
        (buffer, value) -> { buffer.writeVarInt(value.protocol());buffer.writeUtf(value.fingerprint(), 64); },
        buffer -> new WeaponCatalogPayload(buffer.readVarInt(), buffer.readUtf(64)));

    public WeaponCatalogPayload {
        if (protocol < 0 || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid weapon catalogue handshake");
        }
    }

    @Override public Type<WeaponCatalogPayload> type() { return TYPE; }
}
