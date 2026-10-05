package com.decimation.client.gun;

import com.decimation.client.content.BakedObjMesh;
import com.decimation.client.content.DanimFrames;
import com.decimation.client.content.ObjModel;
import com.decimation.module.gun.data.WeaponDefinition;
import com.decimation.client.firstperson.FirstPersonSupportArmSolver;

/** One immutable resource-generation snapshot, retained by its baked item model. */
public record WeaponVisualData(WeaponDefinition definition, BakedObjMesh mesh,
                               DanimFrames fire, DanimFrames reload, DanimFrames rack,
                               FirstPersonSupportArmSolver.Grip supportGrip, FirstPersonSupportArmSolver.Grip reloadGrip, WeaponSightLine sightLine) {
    /** Convert recovered sideways coordinates once, before geometry, bounds and grip preparation. */
    public static WeaponVisualData prepare(WeaponDefinition definition, ObjModel source,
                                           DanimFrames fire, DanimFrames reload, DanimFrames rack) {
        var vertices = source.vertices().stream().map(v -> new ObjModel.Vertex(v.x(), v.y(), -v.z())).toList();
        var faces = source.faces().stream().map(face -> {
            int[] positions = face.vertexIndices().clone(), textures = face.textureIndices().clone();
            // Retain the fan anchor and reverse the rest: same triangles, outward winding after reflection.
            for (int i = 1, j = positions.length - 1; i < j; i++, j--) {
                int index = positions[i];positions[i] = positions[j];positions[j] = index;
                index = textures[i];textures[i] = textures[j];textures[j] = index;
            }
            return new ObjModel.Face(face.object(), positions, textures);
        }).toList();
        var model = new ObjModel(vertices, source.textureCoordinates(), faces);
        // The archived FAMAS OBJ labels its complete bullpup magazine as a static gun object.
        var bindings = definition.id().equals("decimation:famas") ? java.util.Map.of("gunModel26", "ammoModel0") : java.util.Map.<String, String>of();
        return new WeaponVisualData(definition, BakedObjMesh.bake(model, fire, reload, rack, bindings), fire, reload, rack,
            FirstPersonSupportArmSolver.midBarrelGrip(model), magazineGrip(model, definition.id()), WeaponSightLine.prepare(model, definition.id()));
    }

    private static FirstPersonSupportArmSolver.Grip magazineGrip(ObjModel model, String id) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (var face : model.faces()) {
            boolean magazine = id.equals("decimation:famas") ? face.object().equals("gunModel26")
                : face.object().startsWith("ammoModel") || face.object().equals("magazine");
            if (!magazine) continue;
            for (int index : face.vertexIndices()) {
                var v = model.vertices().get(index);
                minX = Math.min(minX, v.x());maxX = Math.max(maxX, v.x());
                minY = Math.min(minY, v.y());maxY = Math.max(maxY, v.y());
                minZ = Math.min(minZ, v.z());maxZ = Math.max(maxZ, v.z());
            }
        }
        return Float.isFinite(minX) ? new FirstPersonSupportArmSolver.Grip((minX + maxX) / 2, (minY + maxY) / 2, minZ) : null;
    }

    public WeaponVisualData(WeaponDefinition definition, BakedObjMesh mesh, DanimFrames fire, DanimFrames reload, DanimFrames rack,
                            FirstPersonSupportArmSolver.Grip supportGrip, FirstPersonSupportArmSolver.Grip reloadGrip) {
        this(definition, mesh, fire, reload, rack, supportGrip, reloadGrip, null);
    }
    public WeaponVisualData(WeaponDefinition definition, BakedObjMesh mesh, DanimFrames fire, DanimFrames reload, DanimFrames rack,
                            FirstPersonSupportArmSolver.Grip supportGrip) {
        this(definition, mesh, fire, reload, rack, supportGrip, null, null);
    }

    public WeaponVisualData(WeaponDefinition definition, BakedObjMesh mesh, DanimFrames fire, DanimFrames reload, DanimFrames rack) {
        this(definition, mesh, fire, reload, rack, null, null, null);
    }
}
