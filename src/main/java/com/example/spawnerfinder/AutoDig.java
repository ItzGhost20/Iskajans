package com.example.spawnerfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AutoDig: walks/tunnels to the nearest spawner and mines it with a silk touch pickaxe.
 * Every other block on the way is mined with a regular pickaxe.
 *
 * Safety: stops on low health, on any damage, when you press a movement key, when a block will not break
 * (e.g. claim protection - it never tries to get around that) and never touches containers or any block with
 * block-entity data (chests, furnaces, signs, ...).
 */
@Mod.EventBusSubscriber(modid = "spawnerfinder", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AutoDig {
    private AutoDig() {}

    private enum Phase { IDLE, PLANNING, DIGGING, SPAWNER, PICKUP }

    public static boolean active = false;
    public static String status = "idle";
    public static List<BlockPos> path = List.of();
    public static int idx = 0;

    private static Phase phase = Phase.IDLE;
    private static BlockPos target;
    private static Set<Long> goals = Set.of();
    private static final Set<Long> blocked = new HashSet<>();
    private static final Set<Long> skipTargets = new HashSet<>();
    private static BlockPos breakingPos;
    private static int breakTicks, replans, stuck, pickupTicks, spawnerTicks, protectedFails, mined;
    private static Vec3 lastPos = Vec3.ZERO;
    private static float lastHealth = 20f;
    private static boolean wantForward, wantJump;

    // ---------------------------------------------------------------- control

    public static void toggle(Minecraft mc) {
        if (active) stop(mc, "stopped by you");
        else start(mc);
    }

    public static void start(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!Settings.master) {
            msg(mc, "Master switch is OFF (K).");
            return;
        }
        if (!Settings.dig.enabled) {
            msg(mc, "AutoDig is switched OFF. Turn it ON in the panel (AutoDig tab) first.");
            return;
        }
        active = true;
        phase = Phase.PLANNING;
        blocked.clear();
        skipTargets.clear();
        replans = 0;
        protectedFails = 0;
        mined = 0;
        stuck = 0;
        lastHealth = mc.player.getHealth();
        lastPos = mc.player.position();
        status = "planning...";
        msg(mc, "started");
    }

    public static void stop(Minecraft mc, String reason) {
        boolean was = active;
        active = false;
        phase = Phase.IDLE;
        wantForward = false;
        wantJump = false;
        path = List.of();
        breakingPos = null;
        status = reason;
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        if (was) msg(mc, "stopped: " + reason);
    }

    private static void msg(Minecraft mc, String s) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("[AutoDig] " + s), false);
    }

    private static void replan(Minecraft mc, String why) {
        replans++;
        if (replans > 8) {
            stop(mc, "too many re-plans (" + why + ")");
            return;
        }
        phase = Phase.PLANNING;
        breakingPos = null;
        breakTicks = 0;
        status = "re-planning: " + why;
    }

    // ---------------------------------------------------------------- tick

    public static void tick(Minecraft mc) {
        wantForward = false;
        wantJump = false;
        if (!active) return;

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            active = false;
            phase = Phase.IDLE;
            return;
        }
        if (!Settings.master || !Settings.dig.enabled) {
            stop(mc, "switched off");
            return;
        }
        if (mc.screen != null) return; // paused while a screen is open

        LocalPlayer p = mc.player;
        if (p.getHealth() < Settings.dig.minHealth) {
            stop(mc, "low health");
            return;
        }
        if (p.getHealth() < lastHealth - 0.5f) {
            stop(mc, "you took damage");
            return;
        }
        lastHealth = p.getHealth();

        switch (phase) {
            case PLANNING -> plan(mc);
            case DIGGING -> dig(mc);
            case SPAWNER -> spawner(mc);
            case PICKUP -> pickup(mc);
            default -> { }
        }
    }

    /** Applies the walking commands. Any real movement key pressed by the player cancels AutoDig. */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent e) {
        if (!active) return;
        Input in = e.getInput();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && (in.up || in.down || in.left || in.right || in.jumping || in.shiftKeyDown)) {
            stop(mc, "manual override");
            return;
        }
        in.up = wantForward;
        in.forwardImpulse = wantForward ? 1f : 0f;
        in.leftImpulse = 0f;
        in.jumping = wantJump;
    }

    // ---------------------------------------------------------------- planning (the brain)

    private static void plan(Minecraft mc) {
        LocalPlayer p = mc.player;
        BlockPos start = p.blockPosition();
        double r2 = (double) Settings.dig.maxRadius * Settings.dig.maxRadius;

        List<ClientEvents.Found> cands = new ArrayList<>();
        for (ClientEvents.Found f : ClientEvents.foundList()) {
            if (skipTargets.contains(f.pos().asLong())) continue;
            if (!Settings.dig.filter.equals("ALL") && !f.type().equals(Settings.dig.filter)) continue;
            double dx = f.pos().getX() - start.getX();
            double dz = f.pos().getZ() - start.getZ();
            if (dx * dx + dz * dz > r2) continue;
            cands.add(f);
        }
        cands.sort(Comparator.comparingDouble(f -> f.pos().distSqr(start)));

        if (cands.isEmpty()) {
            stop(mc, mined > 0 ? "done, mined " + mined + " spawner(s)" : "no reachable spawner in range");
            return;
        }

        // nearest first: evaluate the 3 nearest and take the cheapest route
        Pathfinder.Plan best = null;
        BlockPos bestTarget = null;
        int n = Math.min(3, cands.size());
        for (int i = 0; i < n; i++) {
            Pathfinder.Plan pl = Pathfinder.find(mc, start, cands.get(i).pos(), blocked);
            if (pl != null && (best == null || pl.cost() < best.cost())) {
                best = pl;
                bestTarget = cands.get(i).pos();
            }
        }
        if (best == null) {
            for (int i = 0; i < n; i++) skipTargets.add(cands.get(i).pos().asLong());
            status = "no safe route to the nearest ones, trying next";
            return;
        }

        path = best.path();
        idx = 0;
        target = bestTarget;
        goals = best.goals();
        phase = Phase.DIGGING;
        breakingPos = null;
        breakTicks = 0;
        stuck = 0;
        status = "digging to " + target.getX() + " " + target.getY() + " " + target.getZ()
                + " (" + (path.size() - 1) + " steps)";
    }

    // ---------------------------------------------------------------- digging / walking

    private static void dig(Minecraft mc) {
        LocalPlayer p = mc.player;
        BlockPos feet = p.blockPosition();

        if (goals.contains(feet.asLong())) {
            phase = Phase.SPAWNER;
            spawnerTicks = 0;
            breakingPos = null;
            status = "at the spawner";
            return;
        }

        boolean onPath = false;
        for (int i = idx; i <= Math.min(idx + 2, path.size() - 1); i++) {
            if (path.get(i).equals(feet)) {
                onPath = true;
                if (i > idx) {
                    Vec3 c = Vec3.atBottomCenterOf(path.get(i));
                    if (Math.hypot(c.x - p.getX(), c.z - p.getZ()) < 0.3) idx = i;
                }
            }
        }
        if (!onPath) {
            replan(mc, "left the path");
            return;
        }
        if (idx >= path.size() - 1) {
            replan(mc, "path ended");
            return;
        }

        BlockPos cur = path.get(idx);
        BlockPos nxt = path.get(idx + 1);

        for (BlockPos c : Pathfinder.clearCells(cur, nxt)) {
            BlockState s = mc.level.getBlockState(c);
            if (!s.getFluidState().isEmpty()) {
                replan(mc, "liquid ahead");
                return;
            }
            if (!s.getCollisionShape(mc.level, c).isEmpty()) {
                mine(mc, c, s);
                return;
            }
        }

        // way is clear: walk to the next cell
        breakingPos = null;
        Vec3 ctr = Vec3.atBottomCenterOf(nxt);
        aim(p, new Vec3(ctr.x, p.getEyeY(), ctr.z), Settings.dig.rotSpeed);
        float targetYaw = (float) (Mth.atan2(ctr.z - p.getZ(), ctr.x - p.getX()) * 180.0 / Math.PI) - 90f;
        float yawErr = Math.abs(Mth.wrapDegrees(targetYaw - p.getYRot()));
        double hd = Math.hypot(ctr.x - p.getX(), ctr.z - p.getZ());
        wantForward = yawErr < 30f && hd > 0.12;
        wantJump = nxt.getY() > feet.getY() && p.onGround();

        // stuck detection
        if (wantForward && p.position().distanceToSqr(lastPos) < 0.0004) stuck++;
        else stuck = 0;
        lastPos = p.position();
        if (stuck > 40) {
            stuck = 0;
            blocked.add(nxt.asLong());
            replan(mc, "stuck");
        }
    }

    private static void mine(Minecraft mc, BlockPos c, BlockState s) {
        LocalPlayer p = mc.player;
        DigTools.Ctx ctx = new DigTools.Ctx(mc);
        if (!Pathfinder.canMine(ctx, mc.level, s, c)) {
            blocked.add(c.asLong());
            replan(mc, "cannot mine " + s.getBlock().getName().getString());
            return;
        }
        int slot = DigTools.pick(mc, s);
        if (slot < 0) {
            stop(mc, "no usable pickaxe in the hotbar");
            return;
        }
        p.getInventory().selected = slot;

        Vec3 center = Vec3.atCenterOf(c);
        if (!aim(p, center, Settings.dig.rotSpeed)) return;
        if (p.getEyePosition().distanceTo(center) > 4.6) {
            replan(mc, "out of reach");
            return;
        }

        if (c.equals(breakingPos)) breakTicks++;
        else {
            breakingPos = c;
            breakTicks = 0;
        }
        if (breakTicks > 400) {
            protectedFails++;
            blocked.add(c.asLong());
            if (protectedFails >= 3) {
                stop(mc, "blocks will not break (claim/protection?), not trying to get around it");
                return;
            }
            replan(mc, "block will not break");
            return;
        }

        Vec3 eye = p.getEyePosition();
        Direction d = Direction.getNearest(eye.x - center.x, eye.y - center.y, eye.z - center.z);
        if (mc.gameMode.continueDestroyBlock(c, d)) p.swing(InteractionHand.MAIN_HAND);
        status = "mining " + s.getBlock().getName().getString();
    }

    // ---------------------------------------------------------------- the spawner itself

    private static void spawner(Minecraft mc) {
        LocalPlayer p = mc.player;
        BlockState s = mc.level.getBlockState(target);

        if (!s.is(Blocks.SPAWNER)) {
            mined++;
            skipTargets.add(target.asLong());
            phase = Phase.PICKUP;
            pickupTicks = 0;
            breakingPos = null;
            mc.gameMode.stopDestroyBlock();
            status = "collecting the spawner";
            return;
        }
        if (!goals.contains(p.blockPosition().asLong())) {
            replan(mc, "moved away from the spawner");
            return;
        }
        int slot = DigTools.silkPick(mc);
        if (slot < 0) {
            stop(mc, "no silk touch pickaxe in the hotbar - spawner left untouched");
            return;
        }
        p.getInventory().selected = slot;

        Vec3 center = Vec3.atCenterOf(target);
        if (!aim(p, center, Settings.dig.rotSpeed)) return;
        if (p.getEyePosition().distanceTo(center) > 4.6) {
            replan(mc, "spawner out of reach");
            return;
        }
        if (++spawnerTicks > 1200) {
            protectedFails++;
            skipTargets.add(target.asLong());
            if (protectedFails >= 3) {
                stop(mc, "spawner will not break (protected?)");
                return;
            }
            replan(mc, "spawner will not break");
            return;
        }
        Vec3 eye = p.getEyePosition();
        Direction d = Direction.getNearest(eye.x - center.x, eye.y - center.y, eye.z - center.z);
        if (mc.gameMode.continueDestroyBlock(target, d)) p.swing(InteractionHand.MAIN_HAND);
        status = "mining the spawner (silk touch)";
    }

    private static void pickup(Minecraft mc) {
        LocalPlayer p = mc.player;
        pickupTicks++;
        BlockPos feet = p.blockPosition();
        if (target.getY() == feet.getY() && pickupTicks < 25) {
            Vec3 c = Vec3.atBottomCenterOf(target);
            aim(p, new Vec3(c.x, p.getEyeY(), c.z), Settings.dig.rotSpeed);
            wantForward = Math.hypot(c.x - p.getX(), c.z - p.getZ()) > 0.3;
        }
        if (pickupTicks >= 25) {
            if (Settings.dig.continuous) {
                phase = Phase.PLANNING;
                status = "next spawner...";
            } else {
                stop(mc, "done, mined " + mined + " spawner(s)");
            }
        }
    }

    // ---------------------------------------------------------------- aiming

    /** Turns the camera toward the point, limited to maxStep degrees per tick. True when (almost) there. */
    private static boolean aim(LocalPlayer p, Vec3 point, float maxStep) {
        Vec3 eye = p.getEyePosition();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float ty = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float tp = (float) -(Mth.atan2(dy, h) * 180.0 / Math.PI);
        float ny = approach(p.getYRot(), ty, maxStep);
        float np = Mth.clamp(approach(p.getXRot(), tp, maxStep), -90f, 90f);
        p.setYRot(ny);
        p.setXRot(np);
        return Math.abs(Mth.wrapDegrees(ty - ny)) < 6f && Math.abs(tp - np) < 6f;
    }

    private static float approach(float cur, float target, float step) {
        float d = Mth.wrapDegrees(target - cur);
        if (Math.abs(d) <= step) return cur + d;
        return cur + Math.signum(d) * step;
    }
}
