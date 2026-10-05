package com.decimation.client.gun;

import com.decimation.client.content.BakedObjMesh;
import com.decimation.client.content.DanimFrames;
import com.decimation.client.content.WavefrontObjLoader;
import com.decimation.module.gun.data.WeaponCatalog;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Real source assets plus malformed geometry and interpolation boundaries; no graphics context required. */
public final class WeaponVisualTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        ClassLoader loader = WeaponVisualTest.class.getClassLoader();
        WeaponCatalog catalog = WeaponCatalog.load(loader);
        for (var weapon : catalog.definitions().values()) {
            var obj = WavefrontObjLoader.load(reader(loader, weapon.assets().model()));
            var fire = animation(loader, weapon.assets().fireAnimation());
            var reload = animation(loader, weapon.assets().reloadAnimation());
            String rackId = weapon.assets().fireAnimation().replace("_fire.danim.json", "_rack.danim.json");
            var rack = loader.getResource(path(rackId)) == null ? DanimFrames.empty() : animation(loader, rackId);
            var visual = WeaponVisualData.prepare(weapon, obj, fire, reload, rack);
            var mesh = visual.mesh();
            mirroredGeometryContract(obj, BakedObjMesh.bake(obj, fire, reload, rack), visual);
            long objects = obj.faces().stream().map(face -> face.object()).distinct().count();
            check(mesh.parts().size() <= objects, "batching cannot increase source object count");
            int knownDegenerateFaces = switch (weapon.id()) {
                case "decimation:famas" -> 4;
                case "decimation:honeybadger", "decimation:crossbow" -> 5;
                case "decimation:famas_custom" -> 0;
                default -> throw new AssertionError("missing source geometry baseline");
            };
            check(mesh.skippedFaces() == knownDegenerateFaces, "only the same zero-area faces as the archived renderer are skipped");
            int corners = 0, vertices = 0;
            for (var part : mesh.parts()) {
                corners += part.cornerCount();vertices += part.vertices().size();
                check(part.cornerCount() % 4 == 0, "draw groups have complete quad primitives");
                check(part.tracks().fire().frames().size() == fire.length() + 1, "fire track bound");
                check(part.tracks().reload().frames().size() == reload.length() + 1, "reload track bound");
                for (var vertex : part.vertices()) {
                    check(Float.isFinite(vertex.x()) && Float.isFinite(vertex.y()) && Float.isFinite(vertex.z())
                        && Float.isFinite(vertex.u()) && Float.isFinite(vertex.v()), "finite baked attributes");
                    near(vertex.nx() * vertex.nx() + vertex.ny() * vertex.ny() + vertex.nz() * vertex.nz(), 1, "unit Newell normal");
                }
            }
            int expectedCorners = obj.faces().stream().mapToInt(face -> face.vertexIndices().length == 4 ? 4 : (face.vertexIndices().length - 2) * 4).sum()
                - knownDegenerateFaces * 4;
            check(corners == expectedCorners, "source polygons preserve draw primitive boundaries");
            check(vertices <= corners, "vertex attributes deduplicated within draw group");
            if (objects > 10) check(mesh.parts().size() <= 8, "recovered rifle/crossbow objects consolidate into bounded groups");
            check(visual.supportGrip() != null, "support grip prepared once from real OBJ faces");
            modelBindingContract(visual);
            rendererContract(visual);
            mirroredAnimationContract(visual);
            reloadAssemblyContract(obj, visual);
            reloadTiltAndSupportContract(visual);
            handlingContract(visual);
            adsContract(visual);
            carryPoseContract(visual);
            staticRelaxedContract(visual);
            var frozen = mesh.parts().getFirst().tracks().reload().frames();
            try { frozen.clear();throw new AssertionError("mutable baked animation"); } catch (UnsupportedOperationException expected) { checks++; }
            System.out.println(weapon.id() + ": " + objects + " source objects -> " + mesh.parts().size()
                + " draw groups; " + obj.faces().size() + " faces; " + vertices + " unique attribute vertices / " + corners + " corners.");

            var hip = WeaponViewTransforms.create(weapon.presentation(), mesh.bounds(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, WeaponMotion.Snapshot.REST);
            var ads = WeaponViewTransforms.create(weapon.presentation(), mesh.bounds(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new WeaponMotion.Snapshot(1, 0, 0, 0));
            check(ads.m30() < hip.m30(), "aim transition moves weapon toward camera center");
            var left = WeaponViewTransforms.create(weapon.presentation(), mesh.bounds(), ItemDisplayContext.FIRST_PERSON_LEFT_HAND, WeaponMotion.Snapshot.REST);
            Vector3f rightPoint = hip.transformPosition(new Vector3f(2, 3, 4));
            Vector3f leftPoint = left.transformPosition(new Vector3f(2, 3, 4));
            near(rightPoint.x, -leftPoint.x, "left/right attachment mirrored");
            near(rightPoint.y, leftPoint.y, "left/right attachment retains height");
            near(rightPoint.z, leftPoint.z, "left/right attachment retains depth");
            for (ItemDisplayContext context : ItemDisplayContext.values()) {
                var matrix = WeaponViewTransforms.create(weapon.presentation(), mesh.bounds(), context, WeaponMotion.Snapshot.REST);
                for (float value : matrix.get(new float[16])) check(Float.isFinite(value), "finite context transform");
            }
            thirdPersonHandContract(visual);
            thirdPersonSupportContract(visual);
            droppedWeaponContract(visual);
        }

        ambientContract();

        String quad = "v 0 0 0\nv 1 0 0\nv 1 1 0.2\nv 0 1 0\nf 1 2 3 4\n";
        var empty = DanimFrames.empty();
        var warped = BakedObjMesh.bake(WavefrontObjLoader.load(new StringReader(quad)), empty, empty, empty).parts().getFirst();
        check(warped.cornerCount() == 4, "warped source quad stays one quad");
        for (int i = 1; i < 4; i++) {
            near(warped.corner(i).nx(), warped.corner(0).nx(), "shared polygon X normal");
            near(warped.corner(i).ny(), warped.corner(0).ny(), "shared polygon Y normal");
            near(warped.corner(i).nz(), warped.corner(0).nz(), "shared polygon Z normal");
        }
        var triangle = BakedObjMesh.bake(WavefrontObjLoader.load(new StringReader(
            "v 0 0 0\nv 1 0 0\nv 0 1 0\nf -3 -2 -1\n")), empty, empty, empty).parts().getFirst();
        check(triangle.cornerCount() == 4 && triangle.corner(2).equals(triangle.corner(3)), "triangle safely padded on quad layer");
        for (String invalid : List.of(quad.replace("f 1", "f 0"), quad.replace("f 1", "f 99"),
            quad.replace("v 0 0 0", "v NaN 0 0"), "v 1 2\n", quad.replace("f 1 2 3 4", "f 1/1 2/1 3/1 4/1"))) {
            try { WavefrontObjLoader.load(new StringReader(invalid));throw new AssertionError("malformed OBJ accepted"); }
            catch (java.io.IOException expected) { checks++; }
        }
        var degenerate = WavefrontObjLoader.load(new StringReader("v 0 0 0\nv 0 0 0\nv 0 0 0\nf 1 2 3\n"));
        try { BakedObjMesh.bake(degenerate, empty, empty, empty);throw new AssertionError("empty baked mesh accepted"); }
        catch (IllegalArgumentException expected) { checks++; }

        String sparse = "{\"format\":\"decimation:danim\",\"version\":1,\"length\":6,\"static\":false,\"frames\":["
            + "{\"index\":2,\"transforms\":{\"part\":{\"position\":[2,0,0],\"rotation\":[0,170,0]}}},"
            + "{\"index\":4,\"transforms\":{\"part\":{\"position\":[4,0,0],\"rotation\":[0,-170,0]}}}]}";
        var track = DanimFrames.load(new StringReader(sparse)).track("part");
        near(track.sample(1).x(), 1, "sparse initial ramp");
        near(track.sample(2.5f).x(), 2.5f, "fractional sparse sample");
        near(track.sample(3).yaw(), 180, "rotation takes shortest angular path");
        near(track.sample(6).x(), 0, "non-static track returns to rest");
        check(track.sample(100).equals(track.sample(6)) && track.sample(-1).equals(track.sample(0)), "animation sampling bounded");
        check(DanimFrames.load(new StringReader(sparse)).track("absent").sample(3).equals(DanimFrames.Motion.IDENTITY), "missing part track stays static");
        for (String invalid : List.of(sparse.replace("\"length\":6", "\"length\":1"), sparse.replace("\"length\":6", "\"length\":6.5"),
            sparse.replace("\"index\":4", "\"index\":2"), sparse.replace("[2,0,0]", "[2,0]"), sparse.replace("[2,0,0]", "[1e100,0,0]"))) {
            try { DanimFrames.load(new StringReader(invalid));throw new AssertionError("malformed DANIM accepted"); }
            catch (RuntimeException expected) { checks++; }
        }

        WeaponMotion motion = new WeaponMotion();
        for (int i = 0; i < 5; i++) motion.tick(true, false, 5);
        near(motion.sample(1).aim(), 1, "aim reaches target on definition deadline");
        motion.tick(false, true, 5);
        check(motion.sample(1).aim() < 1 && motion.sample(1).sprint() > 0, "sprint releases ADS");
        motion.fired(1.1f, -0.45f);
        near(motion.sample(0).recoilPitch(), 1.1f, "confirmed shot kick visible immediately");
        motion.tick(false, false, 5);
        check(motion.sample(1).recoilPitch() < motion.sample(0).recoilPitch(), "recoil recovers between ticks");
        for (int i = 0; i < 40; i++) motion.tick(false, false, 5);
        check(motion.sample(1).recoilPitch() < 0.0001f && motion.sample(1).aim() == 0, "motion settles at rest");
        motion.reset();
        check(motion.sample(1).equals(WeaponMotion.Snapshot.REST), "weapon/world switch clears visual motion");
        for (int i = 0; i < 4; i++) motion.tick(false, true, false, 5);
        near(motion.sample(1).sprintCarry(), 1, "running defaults to tucked carry");
        motion.tick(true, true, true, 5);
        near(motion.sample(1).runningFire(), .5f, "running fire blends in over two ticks");
        near(motion.sample(.5f).runningFire(), .25f, "running fire interpolates fractional extraction");
        motion.tick(true, true, true, 5);
        near(motion.sample(1).runningFire(), 1, "trigger reaches running hip-fire pose");
        near(motion.sample(1).sprint(), 1, "running fire keeps sprint active");
        near(motion.sample(1).aim(), 0, "running fire never gains ADS");
        near(motion.sample(1).sprintCarry(), 0, "trigger clears tucked carry without cancelling running");
        for (int i = 0; i < 4; i++) motion.tick(false, true, false, 5);
        near(motion.sample(1).sprintCarry(), 1, "trigger release returns smoothly to sprint carry");
        motion.fired(1, 0);
        near(motion.sample(0).runningFire(), 1, "confirmed running shot immediately restores firing pose");
        motion.reset();
        check(motion.sample(1).equals(WeaponMotion.Snapshot.REST), "world/slot changes clear running-fire state too");
        System.out.println("Weapon visual checks passed: " + checks + " assertions; model binding before atlas upload, real OBJ/DANIM, batching, normals, malformed assets and pose motion.");
    }

    private record PositionUv(float x, float y, float z, float u, float v) { }
    private static void mirroredGeometryContract(com.decimation.client.content.ObjModel obj, BakedObjMesh source, WeaponVisualData data) {
        var expected = new java.util.HashMap<PositionUv, java.util.List<BakedObjMesh.Vertex>>();
        for (var part : source.parts()) for (var vertex : part.vertices()) expected.computeIfAbsent(
            new PositionUv(vertex.x(), vertex.y(), -vertex.z(), vertex.u(), vertex.v()), ignored -> new java.util.ArrayList<>()).add(vertex);
        for (var part : data.mesh().parts()) {
            for (var vertex : part.vertices()) {
                var matches = expected.get(new PositionUv(vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v()));
                check(matches != null, "sideways reflection preserves source forward/up coordinates and attached UVs");
                check(matches.stream().anyMatch(v -> Math.abs(vertex.nx() - v.nx()) < .0001f
                    && Math.abs(vertex.ny() - v.ny()) < .0001f && Math.abs(vertex.nz() + v.nz()) < .0001f),
                    "reflected outward winding produces the corresponding reflected normal");
            }
        }
        check(data.mesh().parts().size() == source.parts().size() + (data.definition().id().equals("decimation:famas") ? 1 : 0),
            "reflection retains batching, with one recovered FAMAS magazine group");
        near(data.mesh().bounds().minX(), source.bounds().minX(), "reflection retains stock bounds");
        near(data.mesh().bounds().maxX(), source.bounds().maxX(), "reflection retains muzzle bounds");
        near(data.mesh().bounds().minY(), source.bounds().minY(), "reflection retains underside bounds");
        near(data.mesh().bounds().maxY(), source.bounds().maxY(), "reflection retains upper bounds");
        near(data.mesh().bounds().minZ(), -source.bounds().maxZ(), "reflected bounds include former opposite side");
        near(data.mesh().bounds().maxZ(), -source.bounds().minZ(), "reflected bounds include former near side");
        var grip = com.decimation.client.firstperson.FirstPersonSupportArmSolver.midBarrelGrip(obj);
        near(data.supportGrip().x(), grip.x(), "reflected support grip retains barrel position");
        near(data.supportGrip().y(), grip.y(), "reflected support grip retains underside height");
        near(data.supportGrip().z(), -grip.z(), "support grip follows reflected geometry");
    }

    private static void mirroredAnimationContract(WeaponVisualData real) throws Exception {
        mirroredAnimationContract(real, false);
        mirroredAnimationContract(real, true);
    }
    private static void mirroredAnimationContract(WeaponVisualData real, boolean modelYDown) throws Exception {
        // Asymmetric fixture, all rotation axes, and an exact 180-degree shortest-arc tie.
        var obj = WavefrontObjLoader.load(new StringReader("o moving\nv 0 0 .2\nv 1 0 .3\nv 1 1 .4\nv 0 1 .1\nf 1 2 3 4\n"));
        var root = new DanimFrames.Track(List.of(new DanimFrames.Motion(.1f, .2f, .3f, 23, 17, -31),
            new DanimFrames.Motion(-.2f, .4f, -.6f, 203, -47, 62)), modelYDown);
        var part = new DanimFrames.Track(List.of(new DanimFrames.Motion(.4f, -.5f, .6f, -11, 35, 28),
            new DanimFrames.Motion(-.7f, .8f, .9f, 56, -80, 14)), modelYDown);
        var animation = new DanimFrames(1, java.util.Map.of("Model", root, "moving", part), DanimFrames.empty().identity());
        var data = WeaponVisualData.prepare(real.definition(), obj, animation, animation, animation);
        var baked = data.mesh().parts().getFirst();
        near(baked.corner(0).z(), -.2f, "asymmetric model changes sides");
        near(baked.corner(1).x(), 0, "reflection reverses winding while retaining fan anchor");
        near(baked.corner(1).y(), 1, "second corner is former final corner");
        var renderer = new WeaponSpecialRenderer(data);
        for (var sample : List.of(
            new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.FIRE, .5f, false),
            new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.FIRE, 1, false),
            new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, .5f, false),
            new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD,
                real.definition().reloadTicks() - .5f, true))) {
            var submitted = new java.util.ArrayList<org.joml.Matrix4f>();
            var collector = (net.minecraft.client.renderer.SubmitNodeCollector) java.lang.reflect.Proxy.newProxyInstance(
                WeaponVisualTest.class.getClassLoader(), new Class<?>[] {net.minecraft.client.renderer.SubmitNodeCollector.class},
                (proxy, method, args) -> { submitted.add(new org.joml.Matrix4f(((com.mojang.blaze3d.vertex.PoseStack) args[0]).last().pose()));return null; });
            renderer.submit(new WeaponSpecialRenderer.Frame(new org.joml.Matrix4f(), sample), new com.mojang.blaze3d.vertex.PoseStack(),
                collector, 0xf000f0, 0, false, 0);
            boolean correctedRoot = modelYDown && sample.kind() == ClientWeaponPresentation.Kind.RELOAD;
            var originalRoot = new org.joml.Matrix4f();originalRoot.mul(sourceRootMotion(root.sample(sample.frame()), correctedRoot));
            float rackFrame = sample.frame() - Math.max(0, data.definition().reloadTicks() - data.rack().length());
            if (sample.rack() && rackFrame >= 0) originalRoot.mul(sourceMotion(root.sample(rackFrame)));
            var original = new org.joml.Matrix4f(originalRoot).mul(sourceMotion(part.sample(sample.frame())));
            if (sample.rack() && rackFrame >= 0) original.mul(sourceMotion(part.sample(rackFrame)));
            var reflection = new org.joml.Matrix4f().scale(1, modelYDown ? -1 : 1, modelYDown ? 1 : -1);
            var expected = new org.joml.Matrix4f(reflection).mul(original).mul(reflection);
            check(submitted.size() == 1, "reflected fixture retains one draw group");
            var actual = submitted.getFirst();
            for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++) near(actual.get(column, row), expected.get(column, row),
                "actual draw pose reflects root and part animation together at fractional frames");
            var gripPose = new com.mojang.blaze3d.vertex.PoseStack();WeaponSpecialRenderer.applyRoot(gripPose, data, sample);
            var expectedRoot = new org.joml.Matrix4f(reflection).mul(originalRoot).mul(reflection);
            for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++) near(gripPose.last().pose().get(column, row), expectedRoot.get(column, row),
                "support grip shares exactly the reflected animated root");
        }
    }
    private static void reloadAssemblyContract(com.decimation.client.content.ObjModel obj, WeaponVisualData data) {
        boolean custom = data.definition().id().equals("decimation:famas_custom");
        String trackName = custom ? "magazine" : "ammoModel0";
        var reload = data.reload().track(trackName);
        check(reload.modelYDown() != custom, "ANIB coordinates and native Blockbench clips are distinguished by source metadata");
        int expectedCorners = 0;
        for (var face : obj.faces()) {
            boolean assembly = switch (data.definition().id()) {
                case "decimation:famas" -> face.object().equals("gunModel26");
                case "decimation:famas_custom" -> face.object().equals("magazine");
                default -> face.object().startsWith("ammoModel");
            };
            if (assembly) expectedCorners += face.vertexIndices().length == 4 ? 4 : (face.vertexIndices().length - 2) * 4;
        }
        int actualCorners = data.mesh().parts().stream().filter(part -> part.tracks().reload().equals(reload)).mapToInt(BakedObjMesh.Part::cornerCount).sum();
        check(expectedCorners > 0 && actualCorners == expectedCorners, "complete magazine/bolt assembly moves, with no static gun faces included");
        if (!custom && !data.definition().id().equals("decimation:famas")) {
            obj.faces().stream().map(com.decimation.client.content.ObjModel.Face::object).filter(name -> name.startsWith("ammoModel")).distinct()
                .forEach(name -> check(data.reload().track(name) == reload, "numbered ammo objects inherit the assembly track"));
        }
        var sample = new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, custom ? 12 : 10, false);
        var mount = WeaponViewTransforms.rig(data.definition().presentation(), WeaponMotion.Snapshot.REST, false);
        var root = new com.mojang.blaze3d.vertex.PoseStack();root.mulPose(mount);WeaponSpecialRenderer.applyRoot(root, data, sample);
        var origin = root.last().pose().transformPosition(new Vector3f());
        var right = root.last().pose().transformDirection(new Vector3f(0, 0, 1)).normalize();
        var up = root.last().pose().transformDirection(new Vector3f(0, 1, 0)).normalize();
        var submitted = new java.util.ArrayList<org.joml.Matrix4f>();
        var collector = (net.minecraft.client.renderer.SubmitNodeCollector) java.lang.reflect.Proxy.newProxyInstance(
            WeaponVisualTest.class.getClassLoader(), new Class<?>[] {net.minecraft.client.renderer.SubmitNodeCollector.class},
            (proxy, method, args) -> { submitted.add(new org.joml.Matrix4f(((com.mojang.blaze3d.vertex.PoseStack) args[0]).last().pose()));return null; });
        new WeaponSpecialRenderer(data).submit(new WeaponSpecialRenderer.Frame(mount, sample), new com.mojang.blaze3d.vertex.PoseStack(), collector, 0xf000f0, 0, false, 0);
        for (int i = 0; i < data.mesh().parts().size(); i++) if (data.mesh().parts().get(i).tracks().reload().equals(reload)) {
            var delta = submitted.get(i).transformPosition(new Vector3f()).sub(origin);
            check(delta.dot(right) < 0, "actual magazine extraction travels left of the barrel in right-hand view");
            check(delta.dot(up) < 0, "actual magazine extraction travels downward out of the weapon");
        }
        var own = new DanimFrames.Track(List.of(new DanimFrames.Motion(1, 2, 3, 0, 0, 0)));
        var fixture = new DanimFrames(0, java.util.Map.of("ammoModel0", reload, "ammoModel1", own), DanimFrames.empty().identity());
        check(fixture.track("ammoModel1") == own, "explicit child animation overrides inherited assembly motion");
        check(fixture.track("gunModel0") == fixture.identity(), "assembly fallback does not move unrelated gun parts");
    }

    private static org.joml.Matrix4f sourceMotion(DanimFrames.Motion motion) {
        return new org.joml.Matrix4f().translate(motion.x(), motion.y(), motion.z()).rotateX((float) Math.toRadians(motion.pitch()))
            .rotateY((float) Math.toRadians(motion.yaw())).rotateZ((float) Math.toRadians(motion.roll()));
    }

    private static org.joml.Matrix4f sourceRootMotion(DanimFrames.Motion motion, boolean correctedReload) {
        if (!correctedReload) return sourceMotion(motion);
        return sourceMotion(new DanimFrames.Motion(motion.x(), motion.y(), motion.z(), -motion.pitch(), motion.yaw(), -motion.roll()));
    }

    private static void reloadTiltAndSupportContract(WeaponVisualData data) {
        var rest = ClientWeaponPresentation.Sample.REST;
        boolean legacy = data.reload().track("Model").modelYDown();
        if (legacy) {
            // OBJ +X is the barrel. Elevation is Z rotation; barrel roll is X rotation.
            var roll = new com.mojang.blaze3d.vertex.PoseStack();
            WeaponSpecialRenderer.applyRoot(roll, data, new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, 5, false));
            var top = roll.last().pose().transformDirection(new Vector3f(0, 1, 0));
            check(top.z > 0, "reload barrel roll reverses the former negative rotation");
            var tilt = new com.mojang.blaze3d.vertex.PoseStack();
            WeaponSpecialRenderer.applyRoot(tilt, data, new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, 15, false));
            var barrel = tilt.last().pose().transformDirection(new Vector3f(1, 0, 0));
            check(barrel.y > 0, "reload raises the muzzle instead of tipping downward");
        }
        for (boolean slim : new boolean[] {false, true}) for (var main : net.minecraft.world.entity.HumanoidArm.values()) {
            for (boolean firstPerson : new boolean[] {false, true}) {
                var model = new net.minecraft.client.model.player.PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(
                    net.minecraft.client.model.player.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
                com.decimation.client.firstperson.WeaponPlayerPose.external(model, main, data.definition().presentation(), WeaponMotion.Snapshot.REST);
                var arm = model.getArm(main.getOpposite());
                if (firstPerson) { arm.xScale = arm.zScale = .68f;arm.yScale = 1.35f; }
                var original = new net.minecraft.client.model.geom.PartPose(arm.x, arm.y, arm.z, arm.xRot, arm.yRot, arm.zRot, arm.xScale, arm.yScale, arm.zScale);
                var staticTip = supportTip(arm);
                com.decimation.client.firstperson.ReloadSupportArm.apply(arm, data, rest, main == net.minecraft.world.entity.HumanoidArm.LEFT);
                check(supportTip(arm).distance(staticTip) < .00001f, "idle support pose is unchanged");
                boolean moved = false;
                for (float frame = 0; frame <= data.reload().length(); frame += .5f) {
                    arm.loadPose(original);
                    var sample = new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, frame, false);
                    com.decimation.client.firstperson.ReloadSupportArm.apply(arm, data, sample, main == net.minecraft.world.entity.HumanoidArm.LEFT);
                    near(arm.x, original.x(), "reload support shoulder X stays attached");
                    near(arm.y, original.y(), "reload support shoulder Y stays attached");
                    near(arm.z, original.z(), "reload support shoulder Z stays attached");
                    near(arm.xScale, original.xScale(), "reload preserves support width");
                    near(arm.yScale, original.yScale(), "reload never stretches support reach");
                    near(arm.zScale, original.zScale(), "reload preserves support depth");
                    var tip = supportTip(arm);
                    near(tip.length(), staticTip.length(), "animated hand stays on a fixed-length reach from its shoulder: " + data.definition().id() + "/" + frame + "/" + tip.length() + "/" + staticTip.length());
                    check(Float.isFinite(arm.xRot) && Float.isFinite(arm.yRot), "fractional reload hand pose remains finite");
                    if (frame == 0) check(tip.distance(staticTip) < .00001f, "frame zero preserves tuned support grip");
                    if (frame == 10) moved = tip.distance(staticTip) > .01f;
                    if (legacy && frame == 55) check(tip.distance(staticTip) < .00001f, "authored return key restores the support grip");
                }
                check(moved == data.reload().tracks().containsKey("OffHand"), "support moves only for an authored OffHand track");
                arm.loadPose(original);
                com.decimation.client.firstperson.ReloadSupportArm.apply(arm, data, new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.FIRE, 10, false), main == net.minecraft.world.entity.HumanoidArm.LEFT);
                check(supportTip(arm).distance(staticTip) < .00001f, "reload retargeting does not alter firing arm support");
            }
        }
    }

    private static Vector3f supportTip(net.minecraft.client.model.geom.ModelPart arm) {
        return new org.joml.Matrix4f().rotateZYX(arm.zRot, arm.yRot, arm.xRot).transformDirection(new Vector3f(0, 10 * arm.yScale / 16, 0));
    }

    private static void handlingContract(WeaponVisualData data) {
        var running = new WeaponMotion.Snapshot(0, 1, 0, 0, 0);
        var runningFire = new WeaponMotion.Snapshot(0, 1, 0, 0, 1);
        var hipArms = com.decimation.client.firstperson.WeaponPlayerPose.interpolate(data.definition().presentation(), WeaponMotion.Snapshot.REST);
        var carryArms = com.decimation.client.firstperson.WeaponPlayerPose.interpolate(data.definition().presentation(), running);
        var fireArms = com.decimation.client.firstperson.WeaponPlayerPose.interpolate(data.definition().presentation(), runningFire);
        check(!carryArms.equals(hipArms), "sprint carry rotates across the body rather than translating off-camera");
        check(fireArms.equals(hipArms), "running fire restores forward hip-fire arms with sprint still active");
        var carry = WeaponViewTransforms.rig(data.definition().presentation(), running, false);
        var hip = WeaponViewTransforms.rig(data.definition().presentation(), WeaponMotion.Snapshot.REST, false);
        var fire = WeaponViewTransforms.rig(data.definition().presentation(), runningFire, false);
        for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) near(fire.get(c, r), hip.get(c, r), "running fire restores hip weapon attachment without entering ADS");
        check(!carry.equals(hip), "carry retains its own tucked weapon attachment");
        var submitted = new java.util.ArrayList<org.joml.Matrix4f>();
        var collector = (net.minecraft.client.renderer.SubmitNodeCollector) java.lang.reflect.Proxy.newProxyInstance(
            WeaponVisualTest.class.getClassLoader(), new Class<?>[] {net.minecraft.client.renderer.SubmitNodeCollector.class},
            (proxy, method, args) -> { submitted.add(new org.joml.Matrix4f(((com.mojang.blaze3d.vertex.PoseStack) args[0]).last().pose()));return null; });
        var attached = new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, 10, false);
        new WeaponSpecialRenderer(data).submit(new WeaponSpecialRenderer.Frame(new org.joml.Matrix4f(), attached, true),
            new com.mojang.blaze3d.vertex.PoseStack(), collector, 0xf000f0, 0, false, 0);
        check(submitted.size() == data.mesh().parts().size(), "attached reload draws each baked group once");
        for (int i = 0; i < submitted.size(); i++) {
            var track = data.mesh().parts().get(i).tracks().reload();
            var reflection = new org.joml.Matrix4f().scale(1, track.modelYDown() ? -1 : 1, track.modelYDown() ? 1 : -1);
            var expected = new org.joml.Matrix4f(reflection).mul(sourceMotion(track.sample(10))).mul(reflection);
            for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) near(submitted.get(i).get(c, r), expected.get(c, r), "actual attached submission keeps component motion and omits duplicate root tilt");
        }

        if (data.reloadGrip() != null && data.reload().tracks().containsKey("OffHand")) {
            var idle = new Vector3f(data.supportGrip().x(), data.supportGrip().y(), data.supportGrip().z());
            var atStart = com.decimation.client.firstperson.ReloadSupportArm.target(data,
                new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, 0, false), new Vector3f(idle));
            check(atStart.distance(idle) < .00001f, "reload hand starts at its tuned idle grip");
            var moving = com.decimation.client.firstperson.ReloadSupportArm.target(data,
                new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, 10, false), new Vector3f(idle));
            var mag = data.reloadGrip();
            near(moving.x, mag.x(), "support target is centered on the magazine rather than the barrel");
            near(moving.y, mag.y() - 3, "support target follows actual magazine extraction height");
            near(moving.z, mag.z() - 2, "support target follows actual magazine extraction side");
            var returned = com.decimation.client.firstperson.ReloadSupportArm.target(data,
                new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, 55, false), new Vector3f(idle));
            check(returned.distance(idle) < .00001f, "return key hands control back to the normal grip");
        }
        for (boolean slim : new boolean[] {false, true}) for (var main : net.minecraft.world.entity.HumanoidArm.values()) {
            boolean left = main == net.minecraft.world.entity.HumanoidArm.LEFT;
            var model = new net.minecraft.client.model.player.PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(
                net.minecraft.client.model.player.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
            com.decimation.client.firstperson.WeaponPlayerPose.external(model, main, data.definition().presentation(), running);
            var carryHand = new com.mojang.blaze3d.vertex.PoseStack();model.translateToHand(null, main, carryHand);
            carryHand.rotateDegrees(com.mojang.math.Axis.XP, -90);carryHand.rotateDegrees(com.mojang.math.Axis.YP, 180);
            carryHand.mulPose(WeaponViewTransforms.rig(data.definition().presentation(), running, left));
            var barrelCarry = carryHand.last().pose().transformDirection(new Vector3f(1, 0, 0)).normalize();
            check(barrelCarry.x * (left ? -1 : 1) > .5f && Math.abs(barrelCarry.x) > Math.abs(barrelCarry.z), "actual sprint barrel points across toward the torso center for either firing hand");
            for (boolean firstPerson : new boolean[] {false, true}) for (var motion : List.of(WeaponMotion.Snapshot.REST, running, runningFire)) {
                for (float view : new float[] {-60, 0, 60}) {
                    model.resetPose();model.head.xRot = (float) Math.toRadians(view);
                    com.decimation.client.firstperson.WeaponPlayerPose.external(model, main, data.definition().presentation(), motion);
                    var arm = model.getArm(main);
                    var original = new net.minecraft.client.model.geom.PartPose(arm.x, arm.y, arm.z, arm.xRot, arm.yRot, arm.zRot, arm.xScale, arm.yScale, arm.zScale);
                    var attachment = new org.joml.Matrix4f().rotateX(-(float) Math.PI / 2).rotateY((float) Math.PI);
                    attachment.mul(firstPerson ? WeaponViewTransforms.rig(data.definition().presentation(), motion, left)
                        : WeaponViewTransforms.create(data.definition().presentation(), data.mesh().bounds(), left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, motion));
                    var unit = new org.joml.Matrix3f(attachment);var column = new Vector3f();
                    for (int i = 0; i < 3; i++) unit.setColumn(i, unit.getColumn(i, column).normalize());
                    var before = new org.joml.Matrix3f().rotationZYX(arm.zRot, arm.yRot, arm.xRot).mul(unit);
                    for (float frame : new float[] {0, 5.5f, 10, 20, 35, 44.5f, 50, 55}) {
                        arm.loadPose(original);
                        var sample = new ClientWeaponPresentation.Sample(motion, ClientWeaponPresentation.Kind.RELOAD, frame, true);
                        var root = new com.mojang.blaze3d.vertex.PoseStack();WeaponSpecialRenderer.applyRoot(root, data, sample);
                        var expected = new org.joml.Matrix3f(before).mul(new org.joml.Matrix3f(root.last().pose()));
                        check(com.decimation.client.firstperson.ReloadFiringArm.apply(arm, data, sample, left, firstPerson), "reload root moves into the firing-hand attachment");
                        var actual = new org.joml.Matrix3f().rotationZYX(arm.zRot, arm.yRot, arm.xRot).mul(unit);
                        for (int c = 0; c < 3; c++) for (int r = 0; r < 3; r++) near(actual.get(c, r), expected.get(c, r), "arm and gun share one reload rotation in both views");
                        near(arm.x, original.x(), "coupled reload retains firing shoulder X");near(arm.y, original.y(), "coupled reload retains firing shoulder Y");near(arm.z, original.z(), "coupled reload retains firing shoulder Z");
                        near(arm.yScale, original.yScale(), "coupled reload does not stretch the firing arm");
                        var consumed = new com.mojang.blaze3d.vertex.PoseStack();WeaponSpecialRenderer.applyRoot(consumed, data, sample, true);
                        var noRotation = new org.joml.Matrix3f(consumed.last().pose());
                        check(noRotation.equals(new org.joml.Matrix3f(), .00001f), "gun renderer consumes only remaining translation, never a second root tilt");
                    }
                    if (!firstPerson) {
                        model.resetPose();com.decimation.client.firstperson.WeaponPlayerPose.external(model, main, data.definition().presentation(), motion);
                        var support = model.getArm(main.getOpposite());
                        var shoulder = support.storePose();
                        var restSupport = supportTip(support);
                        com.decimation.client.firstperson.WeaponPlayerPose.reload(model, main, data,
                            new ClientWeaponPresentation.Sample(motion, ClientWeaponPresentation.Kind.RELOAD, 0, false), true);
                        check(supportTip(support).distance(restSupport) < .0001f, "third-person reload opening retains the existing tuned support pose");
                        model.resetPose();com.decimation.client.firstperson.WeaponPlayerPose.external(model, main, data.definition().presentation(), motion);
                        com.decimation.client.firstperson.WeaponPlayerPose.reload(model, main, data,
                            new ClientWeaponPresentation.Sample(motion, ClientWeaponPresentation.Kind.RELOAD, 20, true), true);
                        near(support.x, shoulder.x(), "third-person magazine seek keeps support shoulder X");
                        near(support.y, shoulder.y(), "third-person magazine seek keeps support shoulder Y");
                        near(support.z, shoulder.z(), "third-person magazine seek keeps support shoulder Z");
                        near(support.yScale, 1, "third-person magazine seek never stretches support arm");
                    }
                }
            }
        }
    }

    private static void modelBindingContract(WeaponVisualData data) {
        var id = net.minecraft.resources.Identifier.parse(data.definition().id());
        var prepared = java.util.Map.of(id, data);
        var atlas = new net.minecraft.client.renderer.texture.SpriteLoader.Preparations(
            16, 16, 0, null, java.util.Map.of(), java.util.concurrent.CompletableFuture.completedFuture(null));
        int[] materialReads = {0}, inventoryUpdates = {0};
        var materials = new net.minecraft.client.resources.model.sprite.MaterialBaker(atlas, atlas) {
            @Override public net.minecraft.client.resources.model.sprite.Material.Baked get(
                net.minecraft.client.resources.model.sprite.Material material, net.minecraft.client.resources.model.ModelDebugName name) {
                materialReads[0]++;
                check(material.sprite().equals(id.withPrefix("item/")), "particle uses generated inventory icon, not canonical content path");
                check(WeaponVisualTest.class.getClassLoader().getResource("assets/" + id.getNamespace()
                    + "/textures/item/" + id.getPath() + ".png") != null, "generated particle icon exists");
                return new net.minecraft.client.resources.model.sprite.Material.Baked(null, false);
            }
        };
        var baker = (net.minecraft.client.resources.model.ModelBaker) java.lang.reflect.Proxy.newProxyInstance(
            WeaponVisualTest.class.getClassLoader(), new Class<?>[] {net.minecraft.client.resources.model.ModelBaker.class},
            (proxy, method, args) -> {
                if (method.getName().equals("materials")) return materials;
                throw new AssertionError("Unexpected block model baker call: " + method.getName());
            });
        var baking = new net.minecraft.client.renderer.item.ItemModel.BakingContext(baker, null,
            sprite -> { throw new NullPointerException("Atlas not initialized"); }, null, null, null);
        net.minecraft.client.renderer.item.ItemModel inventory = (state, stack, resolver, context, level, owner, seed) -> inventoryUpdates[0]++;
        var context = new net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier.AfterBakeItem.Context() {
            @Override public net.minecraft.resources.Identifier itemId() { return id; }
            @Override public net.minecraft.client.renderer.item.ItemModel.Unbaked sourceModel() { return null; }
            @Override public net.minecraft.client.renderer.item.ItemModel.BakingContext bakingContext() { return baking; }
            @Override public org.joml.Matrix4fc transformation() { return new org.joml.Matrix4f(); }
        };
        var bound = WeaponModelLoading.afterBake(prepared, inventory, context);
        check(bound instanceof WeaponItemModel && ((WeaponItemModel) bound).data() == data,
            "after-bake hook binds OBJ model even while live atlas rejects all sprite reads");
        check(materialReads[0] == 1, "particle resolved once from prepared material baker");
        bound.update(new net.minecraft.client.renderer.item.ItemStackRenderState(), null, null, ItemDisplayContext.GUI, null, null, 0);
        check(inventoryUpdates[0] == 1 && materialReads[0] == 1, "GUI retains original inventory model without extra material lookup");
        check(WeaponModelLoading.afterBake(java.util.Map.of(), inventory, context) == inventory,
            "unrelated item models retain original identity");
    }

    private static void thirdPersonHandContract(WeaponVisualData data) {
        for (boolean slim : new boolean[] {false, true}) {
            var mesh = net.minecraft.client.model.player.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, slim);
            var root = net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64).bakeRoot();
            var player = new net.minecraft.client.model.player.PlayerModel(root, slim);
            for (var arm : net.minecraft.world.entity.HumanoidArm.values()) {
                player.resetPose();
                com.decimation.client.firstperson.WeaponPlayerPose.external(player, arm,
                    data.definition().presentation(), WeaponMotion.Snapshot.REST);
                var poses = new com.mojang.blaze3d.vertex.PoseStack();
                float originalX = player.getArm(arm).x;
                player.translateToHand(null, arm, poses);
                near(player.getArm(arm).x, originalX, "slim hand attachment restores model pivot");
                // Exact adult ItemInHandLayer hand-space operations from 26.3.
                poses.rotateDegrees(com.mojang.math.Axis.XP, -90);
                poses.rotateDegrees(com.mojang.math.Axis.YP, 180);
                poses.translate((arm == net.minecraft.world.entity.HumanoidArm.LEFT ? -1f : 1f) / 16, 2f / 16, -10f / 16);
                var handOrigin = poses.last().pose().transformPosition(new Vector3f());
                var handUp = poses.last().pose().transformDirection(new Vector3f(0, 1, 0)).normalize();
                var handRight = poses.last().pose().transformDirection(new Vector3f(1, 0, 0)).normalize();
                // Special models also receive this centering operation inside LayerRenderState.submit.
                net.minecraft.client.resources.model.cuboid.ItemTransform.NO_TRANSFORM.apply(arm == net.minecraft.world.entity.HumanoidArm.LEFT, poses.last());
                var context = arm == net.minecraft.world.entity.HumanoidArm.LEFT
                    ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                poses.mulPose(WeaponViewTransforms.create(data.definition().presentation(), data.mesh().bounds(), context, WeaponMotion.Snapshot.REST));
                var gunOrigin = poses.last().pose().transformPosition(new Vector3f());
                var forward = poses.last().pose().transformDirection(new Vector3f(1, 0, 0)).normalize();
                var attachmentOffset = new Vector3f(gunOrigin).sub(handOrigin);
                var expectedOffset = new Vector3f(forward).mul(-4f / 16).fma(-1.5f / 16, handUp)
                    .fma((arm == net.minecraft.world.entity.HumanoidArm.LEFT ? .5f : -.5f) / 16, handRight);
                near(attachmentOffset.x, expectedOffset.x, "third-person attachment retains backset/lowering and shifts half a pixel left X");
                near(attachmentOffset.y, expectedOffset.y, "third-person attachment retains backset/lowering and shifts half a pixel left Y");
                near(attachmentOffset.z, expectedOffset.z, "third-person attachment retains backset/lowering and shifts half a pixel left Z");
                var up = poses.last().pose().transformDirection(new Vector3f(0, 1, 0)).normalize();
                var rotation = data.definition().presentation().firstPersonHipArms().mainHand();
                float mirror = arm == net.minecraft.world.entity.HumanoidArm.LEFT ? -1 : 1;
                var expectedForward = new Vector3f(0, 1, 0).rotateX((float) Math.toRadians(-88))
                    .rotateY((float) Math.toRadians(rotation.yaw() * mirror)).rotateZ((float) Math.toRadians(rotation.roll() * mirror));
                near(forward.x, expectedForward.x, "muzzle inherits definition-owned firing arm yaw");
                near(forward.y, expectedForward.y, "muzzle inherits definition-owned firing arm pitch");
                near(forward.z, expectedForward.z, "muzzle inherits definition-owned firing arm roll");
                check(forward.z < -.8f, "muzzle faces out from front of posed player");
                check(up.y < -.8f, "OBJ up matches posed player-model up");
                var muzzle = poses.last().pose().transformPosition(new Vector3f(data.mesh().bounds().maxX(), 0, 0));
                check(muzzle.z < -4f / 16, "muzzle projects clear of hand and torso front plane");
            }
        }
    }

    private static final class DroppedState extends net.minecraft.client.renderer.entity.state.ItemEntityRenderState
        implements DroppedWeaponAccess {
        private boolean weapon;
        public boolean decimation$isWeapon() { return weapon; }
        public void decimation$setWeapon(boolean value) { weapon = value; }
    }
    private static void droppedWeaponContract(WeaponVisualData data) throws Exception {
        var bounds = data.mesh().bounds();
        var ground = WeaponViewTransforms.create(data.definition().presentation(), bounds, ItemDisplayContext.GROUND, WeaponMotion.Snapshot.REST);
        var rawCorners = new java.util.ArrayList<Vector3f>();
        var extents = new java.util.ArrayList<org.joml.Vector3fc>();
        float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            var raw = new Vector3f((corner & 1) == 0 ? bounds.minX() : bounds.maxX(),
                (corner & 2) == 0 ? bounds.minY() : bounds.maxY(), (corner & 4) == 0 ? bounds.minZ() : bounds.maxZ());
            rawCorners.add(raw);
            var point = ground.transformPosition(new Vector3f(raw));extents.add(point);
            low = Math.min(low, point.y);high = Math.max(high, point.y);
        }
        near(low, 0, "sideways dropped model rests at its transformed lower extent");
        float size = data.definition().presentation().thirdPerson().scale() * 2.5f;
        near(high - low, (bounds.maxZ() - bounds.minZ()) * size, "dropped model height is scaled width, not upright height");
        var barrel = ground.transformDirection(new Vector3f(1, 0, 0));
        var top = ground.transformDirection(new Vector3f(0, 1, 0));
        near(barrel.y, 0, "dropped barrel lies horizontally");
        near(top.y, 0, "dropped model's former upright axis lies horizontally");
        near(barrel.length(), size, "dropped weapons use enlarged uniform scale");
        near(top.length(), size, "sideways orientation retains uniform scale");
        var state = new DroppedState();state.decimation$setWeapon(true);
        var context = net.minecraft.client.renderer.item.ItemStackRenderState.class.getDeclaredField("displayContext");
        context.setAccessible(true);context.set(state.item, ItemDisplayContext.GROUND);
        state.item.newLayer().setExtents(() -> extents.toArray(org.joml.Vector3fc[]::new));
        var hook = java.util.Arrays.stream(com.decimation.client.mixin.ItemEntityRendererMixin.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("decimation$groundWeapon")).findFirst().orElseThrow();
        hook.setAccessible(true);
        var mixin = new com.decimation.client.mixin.ItemEntityRendererMixin() { };
        for (float bobHeight : new float[] {.1f, .3f, .5f}) {
            state.decimation$setWeapon(true);
            var poses = new com.mojang.blaze3d.vertex.PoseStack();
            hook.invoke(mixin, poses, 0f, bobHeight, 0f, state, poses, null, null);
            // Independently reproduce the actual item-layer transform before special-model submission.
            net.minecraft.client.resources.model.cuboid.ItemTransform.NO_TRANSFORM.apply(false, poses.last());
            poses.mulPose(ground);
            float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
            float maxX = Float.NEGATIVE_INFINITY, maxZ = maxX;
            for (var corner : rawCorners) {
                var point = poses.last().pose().transformPosition(new Vector3f(corner));
                minX = Math.min(minX, point.x);maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);minZ = Math.min(minZ, point.z);maxZ = Math.max(maxZ, point.z);
            }
            near(minY, 0, "actual dropped item-layer geometry has no hover gap or bobbing");
            near((minX + maxX) / 2, 0, "actual dropped geometry centers on entity X");
            near((minZ + maxZ) / 2, 0, "actual dropped geometry centers on entity Z");
            state.decimation$setWeapon(false);
            var other = new com.mojang.blaze3d.vertex.PoseStack();
            hook.invoke(mixin, other, .2f, bobHeight, -.3f, state, other, null, null);
            check(other.last().pose().equals(new org.joml.Matrix4f().translate(.2f, bobHeight, -.3f)),
                "reused non-weapon state retains vanilla hover and centering");
        }
    }

    private static void thirdPersonSupportContract(WeaponVisualData data) {
        for (boolean slim : new boolean[] {false, true}) {
            var mesh = net.minecraft.client.model.player.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, slim);
            var player = new net.minecraft.client.model.player.PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64).bakeRoot(), slim);
            for (var main : net.minecraft.world.entity.HumanoidArm.values()) {
                for (float aim : new float[] {0, .5f, 1}) {
                    for (float sprint : new float[] {0, .5f, 1}) {
                        for (float pitch : new float[] {-60, 0, 60}) {
                            for (float yaw : new float[] {-50, 0, 50}) {
                                player.resetPose();
                                player.head.xRot = (float) Math.toRadians(pitch);
                                player.head.yRot = (float) Math.toRadians(yaw);
                                player.root().yRot = .2f;player.root().y = 1.5f;
                                var support = player.getArm(main.getOpposite());
                                support.y += 3;support.z += 4; // Non-standing shoulder must retain its extracted pivot.
                                var original = support.storePose();
                                var motion = new WeaponMotion.Snapshot(aim, sprint, 0, 0);
                                com.decimation.client.firstperson.WeaponPlayerPose.external(player, main, data.definition().presentation(), motion);
                                near(support.x, original.x(), "third-person support shoulder X never slides");
                                near(support.y, original.y(), "third-person support shoulder Y never slides");
                                near(support.z, original.z(), "third-person support shoulder Z never slides");
                                near(support.xScale, 1, "third-person support width never stretches");
                                near(support.yScale, 1, "third-person support length never stretches");
                                near(support.zScale, 1, "third-person support depth never stretches");
                                var target = com.decimation.client.firstperson.WeaponPlayerPose.supportTarget(player, main);
                                var limbPose = new com.mojang.blaze3d.vertex.PoseStack();support.translateAndRotate(limbPose);
                                var shoulder = new Vector3f(support.x, support.y, support.z).div(16);
                                var tip = limbPose.last().pose().transformPosition(new Vector3f(0, 10f / 16, 0));
                                near(tip.distance(shoulder), 10f / 16, "support reach remains ten pixels at every view and motion");
                                var expected = new Vector3f(target).sub(shoulder).normalize();
                                var actual = new Vector3f(tip).sub(shoulder).normalize();
                                near(actual.dot(expected), 1, "fixed-length support hand points at this frame's firing wrist target");
                                var hand = new com.mojang.blaze3d.vertex.PoseStack();
                                player.translateToHand(null, main, hand);
                                hand.rotateDegrees(com.mojang.math.Axis.XP, -90);hand.rotateDegrees(com.mojang.math.Axis.YP, 180);
                                hand.translate((main == net.minecraft.world.entity.HumanoidArm.LEFT ? -1f : 1f) / 16, 2f / 16, -10f / 16);
                                var wrist = hand.last().pose().transformPosition(new Vector3f());
                                var root = new com.mojang.blaze3d.vertex.PoseStack();player.root().translateAndRotate(root);
                                var targetInRoot = root.last().pose().transformPosition(new Vector3f(target));
                                near(targetInRoot.distance(wrist), 1f / 16, "support target is one pixel ahead of actual firing hand");
                                var forward = hand.last().pose().transformDirection(new Vector3f(0, 0, -1)).normalize();
                                near(targetInRoot.sub(wrist).normalize().dot(forward), 1, "support target advances along firing arm forward axis");
                            }
                        }
                    }
                }
            }
        }
    }

    private static InputStreamReader reader(ClassLoader loader, String id) {
        var stream = loader.getResourceAsStream(path(id));
        check(stream != null, "visual resource exists: " + id);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
    private static void rendererContract(WeaponVisualData data) {
        record Captured(com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                        net.minecraft.client.renderer.rendertype.RenderType type,
                        net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer draw) { }
        var renderer = new WeaponSpecialRenderer(data);
        for (int variant = 0; variant < 3; variant++) {
            var submitted = new java.util.ArrayList<Captured>();
            var collector = (net.minecraft.client.renderer.SubmitNodeCollector) java.lang.reflect.Proxy.newProxyInstance(
                WeaponVisualTest.class.getClassLoader(), new Class<?>[] {net.minecraft.client.renderer.SubmitNodeCollector.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("submitCustomGeometry")) throw new AssertionError("unexpected renderer submission");
                    submitted.add(new Captured(((com.mojang.blaze3d.vertex.PoseStack) arguments[0]).last().copy(),
                        (net.minecraft.client.renderer.rendertype.RenderType) arguments[1],
                        (net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer) arguments[2]));
                    return null;
                });
            var poses = new com.mojang.blaze3d.vertex.PoseStack();
            var transform = new org.joml.Matrix4f().translate(2, 3, 4).scale(0.025f);
            renderer.submit(new WeaponSpecialRenderer.Frame(transform, ClientWeaponPresentation.Sample.REST), poses, collector,
                0x00f000f0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, variant == 1, variant == 2 ? 0xff112233 : 0);
            check(poses.last().pose().equals(new org.joml.Matrix4f()), "renderer restores caller pose stack");
            check(submitted.size() == data.mesh().parts().size() * (variant == 2 ? 2 : 1), "one submission per draw group and optional outline");
            poses.translate(100, 100, 100);
            transform.translate(100, 100, 100); // Simulate producer reuse before the deferred draw.
            int corners = 0;
            for (Captured captured : submitted) {
                check(captured.type().primitiveTopology() == com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS,
                    "real render pipeline uses quad topology");
                check(!captured.type().format().contains("UV3"), "pipeline needs only attributes the renderer writes");
                near(captured.pose().pose().m30(), 2, "deferred geometry keeps its extracted pose");
                int[] emitted = {0}, attributes = {0};
                int required = captured.type().isOutline() ? 3 : 31;
                var consumer = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    WeaponVisualTest.class.getClassLoader(), new Class<?>[] {com.mojang.blaze3d.vertex.VertexConsumer.class},
                    (proxy, method, arguments) -> {
                        if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, arguments);
                        switch (method.getName()) {
                            case "addVertex" -> {
                                if (emitted[0] > 0) check((attributes[0] & required) == required, "complete vertex attributes before next vertex");
                                attributes[0] = 0;emitted[0]++;
                                for (Object coordinate : arguments) check(Float.isFinite((Float) coordinate), "finite transformed draw position");
                            }
                            case "setColor" -> attributes[0] |= 1;
                            case "setUv" -> attributes[0] |= 2;
                            case "setUv1" -> attributes[0] |= 4;
                            case "setUv2" -> attributes[0] |= 8;
                            case "setNormal" -> attributes[0] |= 16;
                            default -> throw new AssertionError("unexpected vertex attribute: " + method.getName());
                        }
                        return proxy;
                    });
                captured.draw().render(captured.pose(), consumer);
                check((attributes[0] & required) == required, "final vertex has complete attributes");
                corners += emitted[0];
            }
            int expected = data.mesh().parts().stream().mapToInt(part -> part.cornerCount()).sum() * (variant == 2 ? 2 : 1);
            check(corners == expected, "actual draw callback emits every baked corner exactly once per pass");
        }
    }
    private static DanimFrames animation(ClassLoader loader, String id) throws java.io.IOException {
        try (var input = reader(loader, id)) { return DanimFrames.load(input); }
    }
    private static String path(String id) { String[] resource = id.split(":", 2);return "assets/" + resource[0] + "/" + resource[1]; }
    private static void staticRelaxedContract(WeaponVisualData data) {
        var relaxed = new WeaponMotion.Snapshot(0, 0, 0, 0, 0, 1, WeaponAmbientMotion.Pose.NONE);
        var body = new org.joml.Matrix4f().rotateY(.4f).scale(-1, -1, 1);
        var breathing = new WeaponAmbientMotion.Pose(.2f, .1f, .3f, .003f);
        org.joml.Matrix4f referenceAmbient = null;
        for (boolean slim : new boolean[] {false, true}) for (var main : net.minecraft.world.entity.HumanoidArm.values()) {
            var mesh = net.minecraft.client.model.player.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, slim);
            var player = new net.minecraft.client.model.player.PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64).bakeRoot(), slim);
            org.joml.Vector3f referenceFiring = null, referenceSupport = null;
            for (float headYaw : new float[] {-2, 0, 2}) for (float headPitch : new float[] {-1.4f, 0, 1.4f}) {
                player.resetPose();player.head.yRot = headYaw;player.head.xRot = headPitch;
                com.decimation.client.firstperson.WeaponPlayerPose.external(player, main, data.definition().presentation(), relaxed);
                var firing = player.getArm(main);var support = player.getArm(main.getOpposite());
                var firingAngles = new Vector3f(firing.xRot, firing.yRot, firing.zRot);
                var supportAngles = new Vector3f(support.xRot, support.yRot, support.zRot);
                if (referenceFiring == null) { referenceFiring = firingAngles;referenceSupport = supportAngles; }
                check(firingAngles.equals(referenceFiring) && supportAngles.equals(referenceSupport), "both relaxed F5 arms ignore head yaw and pitch");
                near(com.decimation.client.firstperson.WeaponRigPolicy.yaw(37, headYaw * 57, 1), 37, "relaxed FP parent follows only body yaw");
                var view = new org.joml.Quaternionf().rotationYXZ(headYaw, headPitch, 0);
                var ambient = com.decimation.client.firstperson.WeaponRigPolicy.ambient(breathing, body,
                    new Vector3f(0, 0, -1).rotate(view), new Vector3f(0, 1, 0).rotate(view), 1);
                if (referenceAmbient == null) referenceAmbient = ambient;
                check(ambient.equals(referenceAmbient, .00001f), "relaxed FP breathing axes ignore the camera direction");
                var hand = new com.mojang.blaze3d.vertex.PoseStack();
                // Actual first-person firing pose plus native hand operations and gun mount.
                com.decimation.client.firstperson.WeaponPlayerPose.apply(firing,
                    com.decimation.client.firstperson.WeaponPlayerPose.interpolate(data.definition().presentation(), relaxed).mainHand(),
                    main == net.minecraft.world.entity.HumanoidArm.LEFT, 0, 0, 8, 0);
                player.translateToHand(null, main, hand);
                hand.rotateDegrees(com.mojang.math.Axis.XP, -90);hand.rotateDegrees(com.mojang.math.Axis.YP, 180);
                hand.mulPose(WeaponViewTransforms.rig(data.definition().presentation(), relaxed, main == net.minecraft.world.entity.HumanoidArm.LEFT));
                var barrel = hand.last().pose().transformDirection(new Vector3f(1, 0, 0)).normalize();
                near(barrel.y, 0, "relaxed FP barrel is horizontal");
                near(barrel.z, 0, "relaxed FP barrel lies in the torso plane");
                near(barrel.x, main == net.minecraft.world.entity.HumanoidArm.LEFT ? -1 : 1, "relaxed FP barrel points across the chest");
                var armDirection = new org.joml.Matrix4f().rotationZYX(firing.zRot, firing.yRot, firing.xRot).transformDirection(new Vector3f(0, 1, 0)).normalize();
                check(Math.abs(armDirection.dot(barrel)) < .9f, "firing arm is angled independently of the resting barrel");
                com.decimation.client.firstperson.WeaponPlayerPose.external(player, main, data.definition().presentation(), relaxed);
                var third = new com.mojang.blaze3d.vertex.PoseStack();player.translateToHand(null, main, third);
                third.rotateDegrees(com.mojang.math.Axis.XP, -90);third.rotateDegrees(com.mojang.math.Axis.YP, 180);
                third.mulPose(WeaponViewTransforms.create(data.definition().presentation(), data.mesh().bounds(), main == net.minecraft.world.entity.HumanoidArm.LEFT
                    ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, relaxed));
                var thirdBarrel = third.last().pose().transformDirection(new Vector3f(1, 0, 0)).normalize();
                near(thirdBarrel.y, 0, "relaxed F5 barrel is horizontal");near(thirdBarrel.z, 0, "relaxed F5 barrel lies in the torso plane");
                float shoulderX = support.x, shoulderY = support.y, shoulderZ = support.z;
                com.decimation.client.firstperson.WeaponPlayerPose.restSupport(player, main, data, relaxed);
                near(support.x, shoulderX, "rest support shoulder X stays attached");near(support.y, shoulderY, "rest support shoulder Y stays attached");
                near(support.z, shoulderZ, "rest support shoulder Z stays attached");near(support.yScale, 1, "rest F5 support arm never stretches");
                var contact = com.decimation.client.firstperson.WeaponPlayerPose.restSupportTarget(player, main, data, relaxed);
                var wrist = new org.joml.Matrix4f().translation(support.x / 16, support.y / 16, support.z / 16)
                    .rotateZYX(support.zRot, support.yRot, support.xRot).transformPosition(new Vector3f(0, 10f / 16, 0));
                check(wrist.distance(contact) * 16 < 1.5f, "fixed-length rest support hand reaches the real barrel within hand thickness");
            }
        }
        var ambient = new WeaponAmbientMotion();ambient.tick(0, 8, -8);
        var staticBreathing = WeaponAmbientMotion.wave(15, 0, 0, 0, 0, 0);
        check(ambient.sample(15, 1, 0, 0, 0).equals(staticBreathing), "relaxed breathing retains waves without view lag");
        near(com.decimation.client.firstperson.WeaponRigPolicy.skinThickness(0), .68f, "hip firing skin retains accepted thickness");
        check(com.decimation.client.firstperson.WeaponRigPolicy.skinThickness(1) < com.decimation.client.firstperson.WeaponRigPolicy.skinThickness(0), "ADS firing skin narrows instead of getting larger");
    }

    private static void carryPoseContract(WeaponVisualData data) {
        for (boolean slim : new boolean[] {false, true}) for (var main : net.minecraft.world.entity.HumanoidArm.values()) {
            var mesh = net.minecraft.client.model.player.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, slim);
            var root = net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64).bakeRoot();
            var player = new net.minecraft.client.model.player.PlayerModel(root, slim);
            for (float pitch : new float[] {-1.2f, 0, 1.2f}) {
                player.resetPose();player.head.xRot = pitch;
                float shoulderX = player.getArm(main).x, shoulderY = player.getArm(main).y;
                com.decimation.client.firstperson.WeaponPlayerPose.external(player, main, data.definition().presentation(), WeaponMotion.Snapshot.REST);
                near(player.getArm(main).xRot, pitch + (float) Math.toRadians(-88), "F5 ready weapon follows head pitch two degrees below straight");
                near(player.head.xRot, pitch, "F5 weapon correction does not turn the player's head");
                var carry = new WeaponMotion.Snapshot(0, 0, 0, 0, 0, 1, WeaponAmbientMotion.Pose.NONE);
                var hipMount = WeaponViewTransforms.rig(data.definition().presentation(), WeaponMotion.Snapshot.REST, main == net.minecraft.world.entity.HumanoidArm.LEFT);
                var carryMount = WeaponViewTransforms.rig(data.definition().presentation(), carry, main == net.minecraft.world.entity.HumanoidArm.LEFT);
                check(!carryMount.equals(hipMount), "relaxed carry uses the across-body weapon mount");
                com.decimation.client.firstperson.WeaponPlayerPose.external(player, main, data.definition().presentation(), carry);
                near(player.getArm(main).x, shoulderX, "relaxed carry retains firing shoulder X");
                near(player.getArm(main).y, shoulderY, "relaxed carry retains firing shoulder Y");
                near(player.getArm(main.getOpposite()).yScale, 1, "relaxed support arm keeps fixed F5 length");
            }
        }
    }

    private static void ambientContract() {
        double sumPitch = 0, sumYaw = 0, sumHeight = 0;
        for (int tick = 0; tick < 880; tick++) {
            var hip = WeaponAmbientMotion.wave(tick, 0, 0, 0, 0, 0);
            var ads = WeaponAmbientMotion.wave(tick, 0, 0, 0, 1, 0);
            near(ads.pitch(), hip.pitch() * .2f, "ADS reduces breathing to twenty percent");
            near(ads.height(), hip.height() * .2f, "ADS reduces translation to twenty percent");
            sumPitch += hip.pitch();sumYaw += hip.yaw();sumHeight += hip.height();
            check(WeaponAmbientMotion.wave(tick, 1, 8, -8, 1, 1).equals(WeaponAmbientMotion.Pose.NONE), "reload suppresses all ambient motion");
            var matrix = WeaponAmbientMotion.cameraTransform(hip, new Vector3f(0, 0, -1), new Vector3f(0, 1, 0));
            near(matrix.determinant(), 1, "ambient motion preserves gun/arm scale");
        }
        check(Math.abs(sumPitch) < .0001 && Math.abs(sumYaw) < .0001 && Math.abs(sumHeight) < .0001, "breathing stays centered over complete cycles");
        var motion = new WeaponMotion();
        for (int tick = 0; tick < 4; tick++) motion.tick(false, false, false, 5, true, false);
        near(motion.sample(1).relaxed(), 1, "visual carry reaches the server four-step endpoint");
        for (int tick = 0; tick < 3; tick++) {
            motion.tick(true, false, false, 5, true, false);
            near(motion.sample(1).aim(), 0, "visual ADS waits until carry is ready");
        }
        motion.tick(true, false, false, 5, true, false);
        check(motion.sample(1).relaxed() == 0 && motion.sample(1).aim() > 0, "visual carry raises before ADS starts");
        for (int tick = 0; tick < 4; tick++) motion.tick(false, false, false, 5, true, false);
        motion.fired(1, 0);near(motion.sample(1).relaxed(), 0, "confirmed shot reconciles carry to the ready position");
        motion.reset();check(motion.sample(1).equals(WeaponMotion.Snapshot.REST), "carry and ambient reset with the weapon/world");
    }

    private static void adsContract(WeaponVisualData data) {
        var sight = data.sightLine();
        check(sight != null && sight.front().x() > sight.rear().x(), "real weapon has forward-facing sight anchors");
        for (boolean left : new boolean[] {false, true}) {
            for (float yaw : new float[] {-2, 0, 1.7f}) for (float pitch : new float[] {-1.3f, 0, 1.3f}) {
                var camera = new org.joml.Quaternionf().rotationYXZ(yaw, pitch, 0);
                var forward = new Vector3f(0, 0, -1).rotate(camera);
                var up = new Vector3f(0, 1, 0).rotate(camera);
                for (float aim : new float[] {0, .5f, 1}) {
                    var neutral = new org.joml.Matrix4f().translate(.3f, -.4f, -.2f).rotate(camera).rotateX(-1.1f)
                        .mul(WeaponViewTransforms.rig(data.definition().presentation(), new WeaponMotion.Snapshot(aim, 0, 0, 0), left));
                    var before = new org.joml.Matrix4f(neutral);
                    var adjustment = com.decimation.client.firstperson.WeaponAdsAlignment.solve(neutral, sight, forward, up, aim);
                    check(neutral.equals(before), "ADS does not mutate the captured mount");
                    near(adjustment.determinant(), 1, "ADS adjustment retains scale and handedness");
                    var aligned = new org.joml.Matrix4f(adjustment).mul(neutral);
                    var rear = aligned.transformPosition(sight.rear().vector());
                    var expected = neutral.transformPosition(sight.rear().vector()).lerp(new Vector3f(forward).mul(sight.eyeRelief()), aim);
                    check(rear.distance(expected) < .0001f, "ADS rear sight approaches camera center smoothly");
                    if (aim == 0) check(adjustment.equals(new org.joml.Matrix4f()), "hip position is unchanged");
                    if (aim == 1) {
                        var direction = aligned.transformPosition(sight.front().vector()).sub(rear).normalize();
                        check(direction.distance(forward) < .0001f, "both sights align with camera forward at full ADS");
                        var animated = new org.joml.Matrix4f(aligned).rotateZ(.08f);
                        var animatedDirection = animated.transformDirection(new Vector3f(sight.front().vector()).sub(sight.rear().vector())).normalize();
                        check(animatedDirection.distance(direction) > .01f, "animation after neutral alignment remains visible");
                    }
                    var sample = new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.RELOAD, 10, false);
                    var target = new Vector3f(1, 2, 3);var original = new Vector3f(target);
                    com.decimation.client.firstperson.ReloadSupportArm.offset(target, data, sample, aligned, left);
                    var delta = aligned.transformPosition(target).sub(aligned.transformPosition(new Vector3f(original)));
                    if (data.reload().tracks().containsKey("OffHand")) {
                        near(delta.dot(aligned.transformDirection(new Vector3f(0, 1, 0)).normalize()), -2f / 16, "reload target two model pixels down at full hand action");
                        near(delta.dot(aligned.transformDirection(new Vector3f(0, 0, 1)).normalize()), (left ? -.5f : .5f) / 16, "reload target half a model pixel right for either hand");
                    } else check(delta.length() < .00001f, "no authored hand action means no offset");
                    var idle = new Vector3f(1, 2, 3);
                    com.decimation.client.firstperson.ReloadSupportArm.offset(idle, data,
                        new ClientWeaponPresentation.Sample(WeaponMotion.Snapshot.REST, ClientWeaponPresentation.Kind.FIRE, 10, false), aligned, left);
                    check(idle.equals(original), "reload offset never changes shooting grip");
                }
            }
        }
        for (float fov : new float[] {70, 90, 110}) {
            near(WeaponAdsPresentation.fieldOfView(fov, 0), fov, "hip FOV unchanged");
            near(WeaponAdsPresentation.fieldOfView(fov, .5f), fov * .95f, "FOV transition interpolates");
            near(WeaponAdsPresentation.fieldOfView(fov, 1), fov * .9f, "full ADS modest zoom");
            near(WeaponAdsPresentation.fieldOfView(fov, Float.NaN), fov, "invalid ADS progress retains native FOV");
        }
        check(WeaponAdsPresentation.hideCrosshair(0) && WeaponAdsPresentation.hideCrosshair(1), "crosshair is always hidden");
    }

    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < 0.0001f, message); }
    private static void check(boolean condition, String message) { checks++;if (!condition) throw new AssertionError(message); }
}
