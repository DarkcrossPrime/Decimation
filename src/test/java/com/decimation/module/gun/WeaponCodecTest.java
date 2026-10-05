package com.decimation.module.gun;

import com.decimation.module.gun.data.WeaponCatalog;
import com.decimation.module.gun.data.WeaponSound;
import com.decimation.module.gun.network.WeaponCatalogPayload;
import com.decimation.module.gun.network.WeaponEvent;
import com.decimation.module.gun.network.WeaponEventPayload;
import com.decimation.module.gun.network.WeaponInputPayload;
import com.decimation.module.gun.network.WeaponCarryPayload;
import com.decimation.module.gun.network.WeaponSoundPayload;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Exercise the real persistent and network codecs with malformed wire data. */
public final class WeaponCodecTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        WeaponState state = new WeaponState(19, true, 1);
        var json = WeaponStateCodecs.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        check(WeaponStateCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(state), "persistent round trip");
        check(WeaponStateCodecs.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"magazine\":-1,\"chambered\":true,\"fire_mode\":0}")).error().isPresent(), "negative persisted ammo rejected");
        check(WeaponStateCodecs.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"magazine\":0,\"chambered\":false,\"fire_mode\":-1}")).error().isPresent(), "negative persisted mode rejected");
        roundTrip(WeaponStateCodecs.STREAM_CODEC, state, 11);
        Identifier id = Identifier.parse("decimation:honeybadger");
        for (int flags = 0; flags <= 31; flags++) roundTrip(WeaponInputPayload.CODEC, new WeaponInputPayload(8, id, flags), 132);
        for (int lowered = 0; lowered <= WeaponCarry.RAISE_TICKS; lowered++)
            roundTrip(WeaponCarryPayload.CODEC, new WeaponCarryPayload(new UUID(3, 4), id, lowered), 148);
        expectFailure(() -> new WeaponCarryPayload(new UUID(1, 2), id, -1));
        expectFailure(() -> new WeaponCarryPayload(new UUID(1, 2), id, 5));
        expectFailure(() -> new WeaponCarryPayload(new UUID(1, 2), Identifier.parse("minecraft:stone"), 0));
        for (WeaponEvent event : WeaponEvent.values()) roundTrip(WeaponEventPayload.CODEC,
            new WeaponEventPayload(new UUID(1, 2), id, 4, event, state, 100), 168);
        Identifier dimension = Identifier.parse("minecraft:overworld");
        for (int flags = 0; flags <= 31; flags++) roundTrip(WeaponInputPayload.CODEC,
            new WeaponInputPayload(8, id, flags, 1234, dimension), 280);
        roundTrip(WeaponInputPayload.CODEC, new WeaponInputPayload(8, Identifier.parse("decimation:" + "a".repeat(117)), 31,
            Integer.MAX_VALUE, Identifier.parse("test:" + "a".repeat(123))), 280);
        expectFailure(() -> new WeaponInputPayload(0, id, 0, -1, dimension));
        expectFailure(() -> new WeaponInputPayload(0, id, 0, 1, Identifier.parse("test:" + "a".repeat(124))));
        for (WeaponSound cue : WeaponSound.values()) roundTrip(WeaponSoundPayload.CODEC,
            new WeaponSoundPayload(id, cue, dimension, -12.75, 65.5, 199.125), 285);
        roundTrip(WeaponSoundPayload.CODEC, new WeaponSoundPayload(Identifier.parse("decimation:" + "a".repeat(117)),
            WeaponSound.FIRE, Identifier.parse("test:" + "a".repeat(123)), 0, 0, 0), 285);
        String text;
        try (var stream = WeaponCodecTest.class.getClassLoader().getResourceAsStream(WeaponCatalog.RESOURCE)) {
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        WeaponCatalog catalog = WeaponCatalog.read(new StringReader(text));
        WeaponCatalog compact = WeaponCatalog.read(new StringReader(JsonParser.parseString(text).toString()));
        check(catalog.fingerprint().equals(compact.fingerprint()), "catalogue whitespace does not break agreement");
        var changed = JsonParser.parseString(text).getAsJsonObject();
        changed.getAsJsonArray("weapons").get(0).getAsJsonObject().getAsJsonObject("ballistics").addProperty("damage", 14.1);
        check(!catalog.fingerprint().equals(WeaponCatalog.read(new StringReader(changed.toString())).fingerprint()), "gameplay change changes fingerprint");
        roundTrip(WeaponCatalogPayload.CODEC, new WeaponCatalogPayload(WeaponCatalogPayload.PROTOCOL, catalog.fingerprint()), 70);
        expectFailure(() -> new WeaponInputPayload(9, id, 0));
        expectFailure(() -> new WeaponInputPayload(0, id, 32));
        expectFailure(() -> new WeaponInputPayload(0, Identifier.parse("minecraft:stone"), 1));
        expectFailure(() -> new WeaponCatalogPayload(1, "bad"));
        expectFailure(() -> new WeaponSoundPayload(id, WeaponSound.FIRE, dimension, Double.NaN, 0, 0));
        expectFailure(() -> new WeaponSoundPayload(id, WeaponSound.FIRE, dimension, 0, Double.POSITIVE_INFINITY, 0));
        expectFailure(() -> new WeaponSoundPayload(Identifier.parse("minecraft:stone"), WeaponSound.FIRE, dimension, 0, 0, 0));
        expectFailure(() -> new WeaponSoundPayload(id, WeaponSound.FIRE, Identifier.parse("test:" + "a".repeat(124)), 0, 0, 0));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeByte(0);buffer.writeUtf(id.toString());buffer.writeByte(255);
            buffer.writeVarInt(1);buffer.writeUtf(dimension.toString());
            expectFailure(() -> WeaponInputPayload.CODEC.decode(buffer));
            buffer.clear();buffer.writeByte(0);buffer.writeUtf("decimation:" + "a".repeat(129));buffer.writeByte(0);
            expectFailure(() -> WeaponInputPayload.CODEC.decode(buffer));
            buffer.clear();buffer.writeUUID(new UUID(1, 2));buffer.writeUtf(id.toString());buffer.writeByte(0);buffer.writeByte(255);
            expectFailure(() -> WeaponEventPayload.CODEC.decode(buffer));
            buffer.clear();buffer.writeUUID(new UUID(1, 2));buffer.writeUtf(id.toString());buffer.writeByte(255);
            buffer.writeVarInt(1);buffer.writeByte(0);buffer.writeUtf(dimension.toString());buffer.writeFloat(0);
            buffer.writeVarInt(0);buffer.writeVarInt(0);buffer.writeBoolean(false);buffer.writeLong(0);
            expectFailure(() -> WeaponCarryPayload.CODEC.decode(buffer));
            buffer.clear();buffer.writeVarInt(-1);buffer.writeBoolean(false);buffer.writeVarInt(0);
            expectFailure(() -> WeaponStateCodecs.STREAM_CODEC.decode(buffer));
            buffer.clear();buffer.writeByte(0);
            expectFailure(() -> WeaponInputPayload.CODEC.decode(buffer));
            buffer.clear();buffer.writeUtf(id.toString());buffer.writeByte(255);
            expectFailure(() -> WeaponSoundPayload.CODEC.decode(buffer));
            buffer.clear();buffer.writeUtf(id.toString());buffer.writeByte(WeaponSound.FIRE.ordinal());
            buffer.writeUtf(dimension.toString());buffer.writeDouble(1);
            expectFailure(() -> WeaponSoundPayload.CODEC.decode(buffer));
            buffer.clear();buffer.writeUtf(id.toString());buffer.writeByte(WeaponSound.FIRE.ordinal());
            buffer.writeUtf(dimension.toString());buffer.writeDouble(0);buffer.writeDouble(0);buffer.writeDouble(Double.NaN);
            expectFailure(() -> WeaponSoundPayload.CODEC.decode(buffer));
            buffer.clear();buffer.writeUtf(id.toString());buffer.writeByte(WeaponSound.FIRE.ordinal());
            buffer.writeUtf("test:" + "a".repeat(124));
            expectFailure(() -> WeaponSoundPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
        System.out.println("Weapon codec checks passed: " + checks + " assertions; real persistence/payload codecs and malformed wire data.");
    }

    private static <T> void roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value, int maximumBytes) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            check(buffer.readableBytes() <= maximumBytes, "bounded payload size");
            check(codec.decode(buffer).equals(value) && !buffer.isReadable(), "exact wire round trip");
        } finally { buffer.release(); }
    }
    private static void check(boolean condition, String message) { checks++;if (!condition) throw new AssertionError(message); }
    private static void expectFailure(Runnable action) {
        try { action.run();throw new AssertionError("invalid wire data accepted"); }
        catch (RuntimeException expected) { checks++; }
    }
}
