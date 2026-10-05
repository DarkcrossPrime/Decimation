package com.decimation.module.gun;

import com.decimation.module.gun.data.WeaponDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Definition, identifier and default state are resolved once at registration. */
public final class WeaponItem extends Item {
    private final WeaponDefinition definition;
    private final Identifier identifier;
    private final WeaponState initialState;

    public WeaponItem(Properties properties, WeaponDefinition definition) {
        this(properties, definition, WeaponState.initial(definition));
    }

    private WeaponItem(Properties properties, WeaponDefinition definition, WeaponState initialState) {
        super(properties.component(WeaponComponents.STATE, initialState));
        this.definition = definition;
        this.identifier = Identifier.parse(definition.id());
        this.initialState = initialState;
    }

    public WeaponDefinition definition() { return definition; }
    public Identifier identifier() { return identifier; }
    public WeaponState state(ItemStack stack) {
        return stack.getOrDefault(WeaponComponents.STATE, initialState).normalized(definition);
    }
    public void writeState(ItemStack stack, WeaponState state) { stack.set(WeaponComponents.STATE, state.normalized(definition)); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) { return InteractionResult.CONSUME; }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * state(stack).totalRounds()
            / Math.max(1L, (long) definition.ammo().capacity() + definition.ammo().chamberCapacity()));
    }
    @Override public int getBarColor(ItemStack stack) { return 0xD84A3A; }
}
