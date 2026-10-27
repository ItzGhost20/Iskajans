package com.example.spawnerfinder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = "spawnerfinder", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents {

    record Found(BlockPos pos, String type) {}

    static boolean enabled = true;
    static List<Found> found = new ArrayList<>();
    static int tickCounter = 0;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        while (KeyBinds.TOGGLE.consumeClick()) {
            enabled = !enabled;
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("Spawner ESP: " + (enabled ? "ON" : "OFF")), true);
            }
        }

        if (!enabled || mc.level == null || mc.player == null) {
            found = new ArrayList<>();
            return;
        }
        if (++tickCounter % 20 != 0) return;

        List<Found> list = new ArrayList<>();
        int r = mc.options.getEffectiveRenderDistance();
        int pcx = mc.player.chunkPosition().x;
        int pcz = mc.player.chunkPosition().z;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                ChunkAccess ca = mc.level.getChunkSource()
                        .getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (!(ca instanceof LevelChunk lc)) continue;
                for (BlockEntity be : lc.getBlockEntities().values()) {
                    if (be instanceof SpawnerBlockEntity sp) {
                        String id = sp.saveWithoutMetadata()
                                .getCompound("SpawnData")
                                .getCompound("entity")
                                .getString("id");
                        id = id.isEmpty() ? "unknown" : id.replace("minecraft:", "");
                        list.add(new Found(be.getBlockPos(), id));
                    }
                }
            }
        }
        found = list;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (!enabled || found.isEmpty()) return;
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Vec3 cam = e.getCamera().getPosition();
        Matrix4f m = e.getPoseStack().last().pose();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(3f);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (Found f : found) {
            int h = f.type().hashCode();
            float r = Math.max(0.35f, ((h >> 16) & 0xFF) / 255f);
            float g = Math.max(0.35f, ((h >> 8) & 0xFF) / 255f);
            float b = Math.max(0.35f, (h & 0xFF) / 255f);

            float x0 = (float) (f.pos().getX() - cam.x);
            float y0 = (float) (f.pos().getY() - cam.y);
            float z0 = (float) (f.pos().getZ() - cam.z);
            box(bb, m, x0, y0, z0, x0 + 1, y0 + 1, z0 + 1, r, g, b);
        }

        Tesselator.getInstance().end();
        RenderSystem.lineWidth(1f);
        RenderSystem.enableDepthTest();
    }

    @SubscribeEvent
    public static void onHud(RenderGuiOverlayEvent.Post e) {
        if (!enabled || found.isEmpty()) return;
        if (e.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<Found> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingDouble(f -> f.pos().distSqr(mc.player.blockPosition())));

        int y = 10;
        for (int i = 0; i < Math.min(8, sorted.size()); i++) {
            Found f = sorted.get(i);
            int dist = (int) Math.sqrt(f.pos().distSqr(mc.player.blockPosition()));
            String line = f.type() + "  " + dist + "m  (" + f.pos().getX() + ", "
                    + f.pos().getY() + ", " + f.pos().getZ() + ")";
            e.getGuiGraphics().drawString(mc.font, line, 6, y, 0xFFFFFF);
            y += 11;
        }
    }

    private static void box(BufferBuilder bb, Matrix4f m,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float b) {
        // bottom
        line(bb, m, x0, y0, z0, x1, y0, z0, r, g, b);
        line(bb, m, x1, y0, z0, x1, y0, z1, r, g, b);
        line(bb, m, x1, y0, z1, x0, y0, z1, r, g, b);
        line(bb, m, x0, y0, z1, x0, y0, z0, r, g, b);
        // top
        line(bb, m, x0, y1, z0, x1, y1, z0, r, g, b);
        line(bb, m, x1, y1, z0, x1, y1, z1, r, g, b);
        line(bb, m, x1, y1, z1, x0, y1, z1, r, g, b);
        line(bb, m, x0, y1, z1, x0, y1, z0, r, g, b);
        // verticals
        line(bb, m, x0, y0, z0, x0, y1, z0, r, g, b);
        line(bb, m, x1, y0, z0, x1, y1, z0, r, g, b);
        line(bb, m, x1, y0, z1, x1, y1, z1, r, g, b);
        line(bb, m, x0, y0, z1, x0, y1, z1, r, g, b);
    }

    private static void line(BufferBuilder bb, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b) {
        bb.vertex(m, x1, y1, z1).color(r, g, b, 1f).endVertex();
        bb.vertex(m, x2, y2, z2).color(r, g, b, 1f).endVertex();
    }
}
