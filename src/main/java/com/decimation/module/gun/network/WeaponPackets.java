package com.decimation.module.gun.network;

import com.decimation.Decimation;
import com.decimation.module.gun.WeaponServerController;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public final class WeaponPackets {
    public static final Identifier ACTION = new Identifier(Decimation.MOD_ID, "weapon/action");
    public static final Identifier EVENT = new Identifier(Decimation.MOD_ID, "weapon/event");

    private WeaponPackets() { }

    public static void registerServerReceivers(WeaponServerController controller) {
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buffer, responseSender) -> {
            int ordinal = buffer.readVarInt();
            if (ordinal < 0 || ordinal >= WeaponAction.values().length) return;
            WeaponAction action = WeaponAction.values()[ordinal];
            server.execute(() -> controller.handle(player, action));
        });
    }
}
