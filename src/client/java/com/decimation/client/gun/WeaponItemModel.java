package com.decimation.client.gun;

import com.decimation.client.content.BakedObjMesh;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Vanilla inventory model plus a special, extracted 3D snapshot for other contexts. */
public final class WeaponItemModel implements ItemModel {
    private final ItemModel inventory;
    private final WeaponVisualData data;
    private final Identifier weapon;
    private final WeaponSpecialRenderer renderer;
    private final Material.Baked particle;
    private final Map<ItemDisplayContext, Supplier<Vector3fc[]>> extents = new EnumMap<>(ItemDisplayContext.class);

    public WeaponItemModel(ItemModel inventory, WeaponVisualData data, MaterialBaker materials) {
        this.inventory = inventory;this.data = data;weapon = Identifier.parse(data.definition().id());
        renderer = new WeaponSpecialRenderer(data);
        // The live SpriteGetter is not uploaded during baking. Resolve the generated
        // inventory icon from this reload's prepared atlases, as vanilla item models do.
        particle = materials.get(new Material(weapon.withPrefix("item/")), () -> "Decimation weapon " + weapon);
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            Matrix4f transform = WeaponViewTransforms.create(data.definition().presentation(), data.mesh().bounds(), context, WeaponMotion.Snapshot.REST);
            Vector3fc[] corners = corners(data.mesh().bounds(), transform);
            extents.put(context, () -> corners);
        }
    }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
        if (context == ItemDisplayContext.GUI) { inventory.update(state, stack, resolver, context, level, owner, seed);return; }
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var sample = ClientWeaponPresentation.sample(owner == null ? null : owner.asLivingEntity(), weapon, partialTick, context.firstPerson());
        var living = owner == null ? null : owner.asLivingEntity();
        boolean reloadInHand = sample.kind() == ClientWeaponPresentation.Kind.RELOAD && living instanceof net.minecraft.world.entity.Avatar
            && living.getMainHandItem().is(stack.getItem()) && (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && living.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND && living.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);
        var frame = new WeaponSpecialRenderer.Frame(WeaponViewTransforms.create(data.definition().presentation(), data.mesh().bounds(), context, sample.motion()), sample, reloadInHand);
        state.appendModelIdentityElement(this);
        state.setAnimated();
        var layer = state.newLayer();
        layer.setUsesBlockLight(true);
        layer.setParticleMaterial(particle);
        layer.setExtents(extents.get(context));
        layer.setupSpecialModel(renderer, frame);
        if (stack.hasFoil()) layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
    }

    public WeaponVisualData data() { return data; }
    public WeaponSpecialRenderer renderer() { return renderer; }

    private static Vector3fc[] corners(BakedObjMesh.Bounds bounds, Matrix4f matrix) {
        Vector3fc[] corners = new Vector3fc[8];
        for (int i = 0; i < corners.length; i++) corners[i] = matrix.transformPosition(new Vector3f(
            (i & 1) == 0 ? bounds.minX() : bounds.maxX(), (i & 2) == 0 ? bounds.minY() : bounds.maxY(),
            (i & 4) == 0 ? bounds.minZ() : bounds.maxZ()));
        return corners;
    }
}
