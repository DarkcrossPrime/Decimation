package com.decimation.client.firstperson;

import com.decimation.Decimation;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Renders a rig that ignores the local torso while retaining world occlusion. */
public final class FirstPersonRigLayer {
    private static Framebuffer activeTarget;
    private SimpleFramebuffer rig;
    private SimpleFramebuffer bodyDepth;
    private ShaderProgram compositeShader;

    public void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
            new Identifier(Decimation.MOD_ID, "first_person_composite"),
            VertexFormats.POSITION_TEXTURE, shader -> compositeShader = shader));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
    }

    /** Native entity/glint render layers may request another framebuffer during a rig draw. */
    public static Framebuffer activeTarget() { return activeTarget; }

    /** Must run before the local torso writes any depth. */
    public boolean captureWorldDepth(Framebuffer main) {
        if (compositeShader == null || !main.useDepthAttachment
            || main.textureWidth <= 0 || main.textureHeight <= 0) return false;
        try {
            ensureSize(main.textureWidth, main.textureHeight);
            rig.clear(MinecraftClient.IS_SYSTEM_MAC);
            rig.copyDepthFrom(main);
            return true;
        } finally {
            main.beginWrite(true);
        }
    }

    public void drawRig(Framebuffer main, Runnable draw) {
        activeTarget = rig;
        try {
            rig.beginWrite(true);
            draw.run();
        } finally {
            activeTarget = null;
            main.beginWrite(true);
        }
    }

    public void composite(Framebuffer main) {
        // Sample a separate copy: reading from the depth attachment being written
        // would create a framebuffer feedback loop with undefined results.
        bodyDepth.copyDepthFrom(main);
        main.beginWrite(true);
        compositeShader.addSampler("RigColor", rig.getColorAttachment());
        compositeShader.addSampler("RigDepth", rig.getDepthAttachment());
        compositeShader.addSampler("BodyDepth", bodyDepth.getDepthAttachment());

        ShaderProgram previousShader = RenderSystem.getShader();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        try {
            RenderSystem.setShader(() -> compositeShader);
            RenderSystem.enableBlend();
            // Entity translucency rendered over transparent black is premultiplied.
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            // Only this full-screen composite bypasses depth. The rig geometry
            // already passed normal world depth testing in its own framebuffer.
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.depthMask(true);
            BufferBuilder vertices = Tessellator.getInstance().getBuffer();
            vertices.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            vertices.vertex(-1, -1, 0).texture(0, 0).next();
            vertices.vertex( 1, -1, 0).texture(1, 0).next();
            vertices.vertex( 1,  1, 0).texture(1, 1).next();
            vertices.vertex(-1,  1, 0).texture(0, 1).next();
            BufferRenderer.drawWithGlobalProgram(vertices.end());
        } finally {
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.depthMask(depthWrite);
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                colorMask.get(2) != 0, colorMask.get(3) != 0);
        }
    }

    private void ensureSize(int width, int height) {
        if (rig != null && rig.textureWidth == width && rig.textureHeight == height) return;
        close();
        rig = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
        rig.setClearColor(0, 0, 0, 0);
        rig.setTexFilter(GL11.GL_NEAREST);
        bodyDepth = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
        bodyDepth.setTexFilter(GL11.GL_NEAREST);
    }

    private void close() {
        activeTarget = null;
        if (rig != null) { rig.delete(); rig = null; }
        if (bodyDepth != null) { bodyDepth.delete(); bodyDepth = null; }
    }
}
