package com.decimation.client.gun;

import com.decimation.module.gun.GunModule;
import com.decimation.module.gun.WeaponItem;
import com.decimation.module.gun.WeaponState;
import com.decimation.module.gun.data.WeaponDefinition;
import com.decimation.module.gun.data.WeaponSound;
import com.decimation.module.gun.network.WeaponAction;
import com.decimation.module.gun.network.WeaponEvent;
import com.decimation.module.gun.network.WeaponPackets;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ClientWeaponController {
    public static final ClientWeaponController INSTANCE = new ClientWeaponController();
    private final KeyBinding reload = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.decimation.reload", InputUtil.Type.KEYSYM, 82, "key.categories.decimation"));
    private final KeyBinding fireMode = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.decimation.fire_mode", InputUtil.Type.KEYSYM, 66, "key.categories.decimation"));
    private boolean triggerSent;
    private boolean aimSent;
    private ActiveAnimation animation;
    private float recoilPitch;
    private float recoilYaw;
    private float adsProgress;

    private ClientWeaponController() { }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderHud(drawContext));
        ClientPlayNetworking.registerGlobalReceiver(WeaponPackets.EVENT,
            (client, handler, buffer, responseSender) -> {
                UUID owner = buffer.readUuid();
                Identifier weapon = buffer.readIdentifier();
                int eventOrdinal = buffer.readVarInt();
                int ammunition = buffer.readVarInt();
                int mode = buffer.readVarInt();
                long serverTick = buffer.readLong();
                if (eventOrdinal < 0 || eventOrdinal >= WeaponEvent.values().length) return;
                client.execute(() -> accept(client, owner, weapon, WeaponEvent.values()[eventOrdinal],
                    ammunition, mode, serverTick));
            });
        ClientPlayNetworking.registerGlobalReceiver(WeaponPackets.SOUND,
            (client, handler, buffer, responseSender) -> {
                Identifier weapon = buffer.readIdentifier();
                int cueOrdinal = buffer.readVarInt();
                double x = buffer.readDouble();
                double y = buffer.readDouble();
                double z = buffer.readDouble();
                if (cueOrdinal < 0 || cueOrdinal >= WeaponSound.values().length) return;
                client.execute(() -> playSound(client, weapon, WeaponSound.values()[cueOrdinal], x, y, z));
            });
    }

    private static void renderHud(net.minecraft.client.gui.DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        ItemStack stack = client.player.getMainHandStack();
        if (!(stack.getItem() instanceof WeaponItem weapon)) return;
        WeaponState state = WeaponState.read(stack, weapon.definition());
        String ammunition = Integer.toString(state.totalRounds());
        String mode = state.fireMode(weapon.definition()).name();
        int right = client.getWindow().getScaledWidth() - 12;
        int baseline = client.getWindow().getScaledHeight() - 32;
        drawContext.drawTextWithShadow(client.textRenderer, ammunition,
            right - client.textRenderer.getWidth(ammunition), baseline, 0xF1F1F1);
        drawContext.drawTextWithShadow(client.textRenderer, mode,
            right - client.textRenderer.getWidth(mode), baseline - 11, 0xAAAAAA);
    }

    private void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            triggerSent = false;
            aimSent = false;
            return;
        }
        ItemStack stack = client.player.getMainHandStack();
        boolean holdingWeapon = stack.getItem() instanceof WeaponItem;
        boolean trigger = holdingWeapon && client.options.attackKey.isPressed();
        boolean aiming = holdingWeapon && client.options.useKey.isPressed();
        if (trigger != triggerSent) {
            send(trigger ? WeaponAction.TRIGGER_DOWN : WeaponAction.TRIGGER_UP);
            triggerSent = trigger;
        }
        if (aiming != aimSent) {
            send(aiming ? WeaponAction.AIM_DOWN : WeaponAction.AIM_UP);
            aimSent = aiming;
        }
        while (reload.wasPressed()) if (holdingWeapon) send(WeaponAction.RELOAD);
        while (fireMode.wasPressed()) if (holdingWeapon) send(WeaponAction.CYCLE_FIRE_MODE);
        int adsTicks = holdingWeapon ? ((WeaponItem) stack.getItem()).definition().handling().adsTicks() : 1;
        float adsStep = 1.0f / Math.max(1, adsTicks);
        adsProgress = Math.max(0, Math.min(1, adsProgress + (aiming ? adsStep : -adsStep)));
        recoilPitch *= 0.72f;
        recoilYaw *= 0.65f;
        if (animation != null && animation.finished(client)) animation = null;
    }

    private void accept(MinecraftClient client, UUID owner, Identifier weaponId, WeaponEvent event,
                        int ammunition, int mode, long serverTick) {
        if (client.player == null || !client.player.getUuid().equals(owner)) return;
        ItemStack stack = client.player.getMainHandStack();
        if (!(stack.getItem() instanceof WeaponItem weapon) || !weapon.definition().id().equals(weaponId)) return;
        WeaponDefinition definition = weapon.definition();
        switch (event) {
            case FIRED -> {
                animation = new ActiveAnimation(definition.assets().fireAnimation(), client.world.getTime(), 2);
                recoilPitch += definition.handling().recoilPitch();
                recoilYaw += (client.world.random.nextBoolean() ? 1 : -1) * definition.handling().recoilYaw();
            }
            case RELOAD_STARTED -> animation = new ActiveAnimation(
                definition.assets().reloadAnimation(), client.world.getTime(), definition.reloadTicks());
            case RELOAD_CANCELLED -> animation = null;
            default -> { }
        }
    }

    private static void playSound(MinecraftClient client, Identifier weaponId, WeaponSound cue,
                                  double x, double y, double z) {
        if (client.world == null) return;
        WeaponDefinition definition = GunModule.catalog().get(weaponId);
        if (definition == null) return;
        Identifier soundId = definition.audio().sound(cue);
        if (soundId == null || !Registries.SOUND_EVENT.containsId(soundId)) return;
        SoundEvent sound = Registries.SOUND_EVENT.get(soundId);
        float volume = switch (cue) {
            case FIRE_DISTANT -> 8.0f;
            case FIRE -> 4.0f;
            case FIRE_SUPPRESSED -> 2.0f;
            default -> 1.0f;
        };
        client.world.playSound(x, y, z, sound, SoundCategory.PLAYERS, volume, 1.0f, false);
    }

    private static void send(WeaponAction action) {
        var buffer = PacketByteBufs.create();
        buffer.writeVarInt(action.ordinal());
        ClientPlayNetworking.send(WeaponPackets.ACTION, buffer);
    }

    public ActiveAnimation animation() { return animation; }
    public float recoilPitch() { return recoilPitch; }
    public float recoilYaw() { return recoilYaw; }
    public float adsProgress() { return adsProgress; }

    public record ActiveAnimation(Identifier id, long startTick, int length) {
        public int frame(MinecraftClient client) {
            return (int) Math.max(0, client.world.getTime() - startTick);
        }

        public boolean finished(MinecraftClient client) {
            return client.world == null || frame(client) >= length;
        }
    }
}
