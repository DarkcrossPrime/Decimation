package com.decimation.client.gun;

import com.decimation.module.gun.WeaponItem;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/** ADS optics presentation uses the same interpolated snapshot as the extracted weapon. */
public final class WeaponAdsPresentation {
    private WeaponAdsPresentation() { }
    public static float fieldOfView(float nativeFov, float progress) {
        return nativeFov * (1 - .1f * Math.clamp(Float.isFinite(progress) ? progress : 0, 0, 1));
    }
    public static boolean hideCrosshair(float ignoredProgress) { return true; }
    public static float progress(Camera camera, float partialTick) {
        var client = Minecraft.getInstance();var player = client.player;
        if (player == null || camera.entity() != player || camera.isDetached() || !client.options.getCameraType().isFirstPerson()
            || !player.isAlive() || player.isSpectator() || player.isSleeping()
            || !(player.getMainHandItem().getItem() instanceof WeaponItem weapon)) return 0;
        return ClientWeaponPresentation.aimProgress(partialTick);
    }
    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> { });
    }
}
