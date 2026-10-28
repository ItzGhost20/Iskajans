package com.example.spawnerfinder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public final class Render {
    private Render() {}

    public static void draw(RenderLevelStageEvent e, List<Target> targets, Settings.Module cfg) {
        if (targets.isEmpty()) return;
        if (!cfg.boxes && !cfg.tracers && !cfg.labels) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        Matrix4f m = ps.last().pose();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        // translucent fill
        if (cfg.boxes) {
            RenderSystem.disableCull();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Target t : targets) {
                float[] c = Colors.of(t.type());
                AABB b = t.box();
                fillBox(bb, m,
                        (float) (b.minX - cam.x), (float) (b.minY - cam.y), (float) (b.minZ - cam.z),
                        (float) (b.maxX - cam.x), (float) (b.maxY - cam.y), (float) (b.maxZ - cam.z),
                        c[0], c[1], c[2], 0.20f);
            }
            Tesselator.getInstance().end();
            RenderSystem.enableCull();
        }

        // outlines + tracers
        if (cfg.boxes || cfg.tracers) {
            RenderSystem.lineWidth(2.5f);
            bb.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            Vector3f lv = e.getCamera().getLookVector();
            for (Target t : targets) {
                float[] c = Colors.of(t.type());
                AABB b = t.box();
                float x0 = (float) (b.minX - cam.x), y0 = (float) (b.minY - cam.y), z0 = (float) (b.minZ - cam.z);
                float x1 = (float) (b.maxX - cam.x), y1 = (float) (b.maxY - cam.y), z1 = (float) (b.maxZ - cam.z);
                if (cfg.boxes) outline(bb, m, x0, y0, z0, x1, y1, z1, c[0], c[1], c[2]);
                if (cfg.tracers) {
                    line(bb, m, lv.x(), lv.y(), lv.z(),
                            (x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f, c[0], c[1], c[2], 0.9f);
                }
            }
            Tesselator.getInstance().end();
            RenderSystem.lineWidth(1f);
        }

        // floating labels
        if (cfg.labels) {
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            for (Target t : targets) {
                AABB b = t.box();
                double dx = (b.minX + b.maxX) / 2.0 - cam.x;
                double dy = b.maxY + 0.5 - cam.y;
                double dz = (b.minZ + b.maxZ) / 2.0 - cam.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                float s = (float) Math.max(0.025, dist * 0.0035);

                ps.pushPose();
                ps.translate(dx, dy, dz);
                ps.mulPose(mc.gameRenderer.getMainCamera().rotation());
                ps.scale(-s, -s, s);
                float w = mc.font.width(t.label());
                mc.font.drawInBatch(t.label(), -w / 2f, 0f, 0xFF000000 | Colors.rgb(t.type()), false,
                        ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0x66000000, 0xF000F0);
                ps.popPose();
            }
            buf.endBatch();
        }

        RenderSystem.enableDepthTest();
    }

    private static void fillBox(BufferBuilder bb, Matrix4f m,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float r, float g, float b, float a) {
        quad(bb, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad(bb, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        quad(bb, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        quad(bb, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        quad(bb, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
        quad(bb, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
    }

    private static void quad(BufferBuilder bb, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        bb.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        bb.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        bb.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        bb.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
    }

    private static void outline(BufferBuilder bb, Matrix4f m,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float r, float g, float b) {
        line(bb, m, x0, y0, z0, x1, y0, z0, r, g, b, 1f);
        line(bb, m, x1, y0, z0, x1, y0, z1, r, g, b, 1f);
        line(bb, m, x1, y0, z1, x0, y0, z1, r, g, b, 1f);
        line(bb, m, x0, y0, z1, x0, y0, z0, r, g, b, 1f);
        line(bb, m, x0, y1, z0, x1, y1, z0, r, g, b, 1f);
        line(bb, m, x1, y1, z0, x1, y1, z1, r, g, b, 1f);
        line(bb, m, x1, y1, z1, x0, y1, z1, r, g, b, 1f);
        line(bb, m, x0, y1, z1, x0, y1, z0, r, g, b, 1f);
        line(bb, m, x0, y0, z0, x0, y1, z0, r, g, b, 1f);
        line(bb, m, x1, y0, z0, x1, y1, z0, r, g, b, 1f);
        line(bb, m, x1, y0, z1, x1, y1, z1, r, g, b, 1f);
        line(bb, m, x0, y0, z1, x0, y1, z1, r, g, b, 1f);
    }

    private static void line(BufferBuilder bb, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        bb.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
        bb.vertex(m, x2, y2, z2).color(r, g, b, a).endVertex();
    }
}
