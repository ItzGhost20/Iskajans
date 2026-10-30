package com.example.spawnerfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Mod.EventBusSubscriber(modid = "spawnerfinder", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents {

    public record Found(BlockPos pos, String type) {}

    static List<Found> found = new ArrayList<>();
    static Set<Long> known = new HashSet<>();
    static int tickCounter = 0;

    // ================= Spawner finder =================

    public static List<Found> foundList() {
        return found;
    }

    public static List<String> spawnerTypes() {
        return new ArrayList<>(new TreeSet<>(found.stream().map(Found::type).toList()));
    }

    public static List<Target> spawnerTargets(Minecraft mc) {
        List<Target> out = new ArrayList<>();
        if (mc.player == null) return out;
        Settings.Module cfg = Settings.spawner;
        double r2 = (double) cfg.range * cfg.range;
        for (Found f : found) {
            if (!cfg.filter.equals("ALL") && !f.type().equals(cfg.filter)) continue;
            double d2 = f.pos().distSqr(mc.player.blockPosition());
            if (d2 > r2) continue;
            double d = Math.sqrt(d2);
            out.add(new Target(new AABB(f.pos()), f.type(), f.type() + " " + (int) d + "m", d));
        }
        out.sort(Comparator.comparingDouble(Target::dist));
        return out;
    }

    /** Copies the nearest spawner's coordinates to the clipboard. */
    public static void copyNearest(Minecraft mc) {
        List<Target> l = spawnerTargets(mc);
        if (l.isEmpty() || mc.player == null) {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("No spawner in range"), true);
            return;
        }
        BlockPos p = BlockPos.containing(l.get(0).box().getCenter());
        mc.keyboardHandler.setClipboard(p.getX() + " " + p.getY() + " " + p.getZ());
        mc.player.displayClientMessage(Component.literal("Copied: " + p.getX() + " " + p.getY() + " " + p.getZ()), true);
    }

    private static void scanSpawners(Minecraft mc) {
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

        // sound alert for newly seen spawners
        Set<Long> now = new HashSet<>();
        boolean fresh = false;
        for (Found f : list) {
            long k = f.pos().asLong();
            now.add(k);
            if (!known.contains(k)) fresh = true;
        }
        if (fresh && Settings.alertSound && Settings.spawner.enabled) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
        }
        known = now;
        found = list;
    }

    // ================= Villager finder =================

    private static String villagerType(Entity en) {
        if (en instanceof Villager v) {
            var key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession());
            return key == null ? "villager" : key.getPath();
        }
        if (en instanceof WanderingTrader) return "wandering_trader";
        return null;
    }

    public static List<String> villagerTypes(Minecraft mc) {
        TreeSet<String> set = new TreeSet<>();
        if (mc.level == null) return new ArrayList<>();
        for (Entity en : mc.level.entitiesForRendering()) {
            String t = villagerType(en);
            if (t != null) set.add(t);
        }
        return new ArrayList<>(set);
    }

    public static List<Target> villagerTargets(Minecraft mc, float pt) {
        List<Target> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) return out;
        Settings.Module cfg = Settings.villager;

        for (Entity en : mc.level.entitiesForRendering()) {
            String type = villagerType(en);
            if (type == null) continue;
            if (!cfg.filter.equals("ALL") && !type.equals(cfg.filter)) continue;

            double x = Mth.lerp(pt, en.xOld, en.getX());
            double y = Mth.lerp(pt, en.yOld, en.getY());
            double z = Mth.lerp(pt, en.zOld, en.getZ());
            AABB b = en.getBoundingBox().move(x - en.getX(), y - en.getY(), z - en.getZ());

            double d = mc.player.position().distanceTo(b.getCenter());
            if (d > cfg.range) continue;

            String name = type;
            if (en instanceof Villager v) {
                name = type + " L" + v.getVillagerData().getLevel();
            }
            out.add(new Target(b, type, name + " " + (int) d + "m", d));
        }
        out.sort(Comparator.comparingDouble(Target::dist));
        return out;
    }

    // ================= Events =================

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        while (KeyBinds.TOGGLE.consumeClick()) {
            Settings.master = !Settings.master;
            Settings.save();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("Client: " + (Settings.master ? "ON" : "OFF")), true);
            }
        }
        while (KeyBinds.GUI.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new ControlPanel());
        }
        while (KeyBinds.DIG.consumeClick()) {
            if (mc.screen == null && mc.player != null) AutoDig.toggle(mc);
        }

        boolean scanning = Settings.master && (Settings.spawner.enabled || Settings.dig.enabled)
                && mc.level != null && mc.player != null;
        if (scanning && ++tickCounter % 20 == 0) {
            scanSpawners(mc);
        } else if (!scanning && !found.isEmpty()) {
            found = new ArrayList<>();
            known = new HashSet<>();
        }

        AutoDig.tick(mc);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (!Settings.master) return;
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (Settings.spawner.enabled) {
            Render.draw(e, spawnerTargets(mc), Settings.spawner);
        }
        if (Settings.villager.enabled) {
            Render.draw(e, villagerTargets(mc, e.getPartialTick()), Settings.villager);
        }
        if (AutoDig.active && Settings.dig.showPath) {
            Render.drawPath(e, AutoDig.path, AutoDig.idx);
        }
    }

    @SubscribeEvent
    public static void onHud(RenderGuiOverlayEvent.Post e) {
        if (!Settings.master) return;
        if (e.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        GuiGraphics g = e.getGuiGraphics();

        if (AutoDig.active) {
            g.drawCenteredString(mc.font, "AutoDig: " + AutoDig.status, g.guiWidth() / 2, 4, 0x55FF55);
            g.drawCenteredString(mc.font, "mined " + AutoDig.mined + "  |  left " + AutoDig.remaining
                    + "  |  skipped " + (AutoDig.failed + AutoDig.unreachable), g.guiWidth() / 2, 15, 0xCCCCCC);
        } else if (!AutoDig.lastStop.isEmpty() && System.currentTimeMillis() - AutoDig.lastStopTime < 15000) {
            if (AutoDig.finished) {
                g.drawCenteredString(mc.font, "AutoDig: " + AutoDig.lastStop, g.guiWidth() / 2, 4, 0x55FF55);
            } else {
                g.drawCenteredString(mc.font, "AutoDig stopped: " + AutoDig.lastStop, g.guiWidth() / 2, 4, 0xFF5555);
            }
        }
        if (Settings.spawner.enabled && Settings.spawner.hud) {
            List<Target> l = spawnerTargets(mc);
            int y = 10;
            for (int i = 0; i < Math.min(8, l.size()); i++) {
                Target t = l.get(i);
                g.drawString(mc.font, t.label(), 6, y, Colors.rgb(t.type()));
                y += 11;
            }
        }
        if (Settings.villager.enabled && Settings.villager.hud) {
            List<Target> l = villagerTargets(mc, 1f);
            int w = g.guiWidth();
            int y = 10;
            for (int i = 0; i < Math.min(8, l.size()); i++) {
                Target t = l.get(i);
                g.drawString(mc.font, t.label(), w - mc.font.width(t.label()) - 6, y, Colors.rgb(t.type()));
                y += 11;
            }
        }
    }
}
