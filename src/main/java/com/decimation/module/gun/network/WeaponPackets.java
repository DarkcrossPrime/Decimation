package com.decimation.module.gun.network;

import com.decimation.module.gun.WeaponServerController;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class WeaponPackets {
    private WeaponPackets() { }

    public static void initialize(WeaponServerController controller) {
        PayloadTypeRegistry.serverboundPlay().register(WeaponInputPayload.TYPE, WeaponInputPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WeaponCatalogPayload.TYPE, WeaponCatalogPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WeaponCatalogPayload.TYPE, WeaponCatalogPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WeaponEventPayload.TYPE, WeaponEventPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WeaponSoundPayload.TYPE, WeaponSoundPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WeaponCarryPayload.TYPE, WeaponCarryPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register(controller::startTracking);
        // Fabric typed play handlers already run on their logical game thread.
        ServerPlayNetworking.registerGlobalReceiver(WeaponInputPayload.TYPE,
            (payload, context) -> controller.handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(WeaponCatalogPayload.TYPE,
            (payload, context) -> controller.acceptCatalog(context.player(), payload));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> controller.join(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> controller.disconnect(handler.player.getUUID()));
    }
}
