package com.decimation.module.gun;

import com.decimation.module.gun.data.WeaponDefinition;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public final class WeaponItem extends Item {
    private final WeaponDefinition definition;

    public WeaponItem(WeaponDefinition definition) {
        super(new Settings().maxCount(1));
        this.definition = definition;
    }

    public WeaponDefinition definition() {
        return definition;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        WeaponState state = WeaponState.read(stack, definition);
        int maximum = Math.max(1, definition.ammo().capacity() + definition.ammo().chamberCapacity());
        return Math.round(13.0f * state.totalRounds() / maximum);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xD84A3A;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        WeaponState state = WeaponState.read(stack, definition);
        tooltip.add(Text.literal(state.totalRounds() + " / "
            + (definition.ammo().capacity() + definition.ammo().chamberCapacity()))
            .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.decimation.fire_mode",
            state.fireMode(definition).name()).formatted(Formatting.DARK_GRAY));
    }
}
