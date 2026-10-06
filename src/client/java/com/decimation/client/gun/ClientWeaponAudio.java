package com.decimation.client.gun;

import com.decimation.module.gun.WeaponSounds;
import com.decimation.module.gun.data.WeaponAudio;
import com.decimation.module.gun.network.WeaponSoundPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.sounds.SoundSource;

public final class ClientWeaponAudio {
    private ClientWeaponAudio() { }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WeaponSoundPayload.TYPE, (payload, context) -> {
            var level = context.client().level;
            if (level == null || !level.dimension().identifier().equals(payload.dimension())) return;
            var sound = WeaponSounds.get(payload.weapon(), payload.cue());
            if (sound == null) return;
            // Server timing already places the cue; no second distance-delay timer here.
            level.playLocalSound(payload.x(), payload.y(), payload.z(), sound, SoundSource.PLAYERS,
                WeaponAudio.volume(payload.cue()), 1, false);
        });
    }
}
