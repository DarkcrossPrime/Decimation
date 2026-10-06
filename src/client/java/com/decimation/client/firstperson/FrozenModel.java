package com.decimation.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Vector3f;

/** Captured cuboid poses; subsequent setupAnim calls cannot alter deferred submissions. */
public final class FrozenModel extends Model<Void> {
    private FrozenModel(ModelPart root) { super(root, RenderTypes::entityCutout); }

    public static final class Builder {
        private final Map<String, ModelPart> cubes = new LinkedHashMap<>();
        public Builder add(PoseStack parent, ModelPart part) {
            if (!part.visible) return this;
            part.visit(parent, (pose, path, index, cube) -> {
                ModelPart owner = part;
                for (String name : path.split("/")) {
                    if (!name.isEmpty()) owner = owner.getChild(name);
                    if (!owner.visible) return;
                }
                if (owner.skipDraw) return;
                // TRS is exact here: shoulder rotation precedes the limb's final scale.
                var matrix = pose.pose();
                var position = matrix.getTranslation(new Vector3f()).mul(16);
                var rotation = matrix.getUnnormalizedRotation(new org.joml.Quaternionf()).getEulerAnglesZYX(new Vector3f());
                var scale = matrix.getScale(new Vector3f());
                var frozen = new ModelPart(List.of(cube), Map.of());
                frozen.setPos(position.x, position.y, position.z);
                frozen.setRotation(rotation.x, rotation.y, rotation.z);
                frozen.xScale = scale.x;frozen.yScale = scale.y;frozen.zScale = scale.z;
                cubes.put(Integer.toString(cubes.size()), frozen);
            });
            return this;
        }
        public FrozenModel build() { return new FrozenModel(new ModelPart(List.of(), Map.copyOf(cubes))); }
    }

    @Override public void setupAnim(Void ignored) { } // Model's default resets all captured poses.
}
