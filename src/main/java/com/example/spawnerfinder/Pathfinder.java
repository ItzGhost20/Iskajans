package com.example.spawnerfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The "brain": weighted A* over the player's feet cell. Moves are 4 horizontal directions, each
 * optionally one block up or down (stairs). Blocks in the way are allowed (they get mined) but cost extra.
 * Mode SHORTEST = cheapest in steps (nearest route first); mode FASTEST = estimated time including mining.
 */
public final class Pathfinder {
    private Pathfinder() {}

    public record Plan(List<BlockPos> path, double cost, Set<Long> goals) {}

    private static final class Node {
        final long key;
        final BlockPos pos;
        final Node parent;
        final double g, f;

        Node(BlockPos pos, Node parent, double g, double f) {
            this.key = pos.asLong();
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int MAX_NODES = 15000;

    /** Cells that must be free (air or passable) to go from cur to nxt. */
    public static List<BlockPos> clearCells(BlockPos cur, BlockPos nxt) {
        int dy = nxt.getY() - cur.getY();
        List<BlockPos> l = new ArrayList<>(3);
        if (dy == 0) {
            l.add(nxt);
            l.add(nxt.above());
        } else if (dy > 0) {
            l.add(cur.above(2));
            l.add(nxt);
            l.add(nxt.above());
        } else {
            l.add(nxt.above(2));
            l.add(nxt.above());
            l.add(nxt);
        }
        return l;
    }

    public static boolean canMine(DigTools.Ctx ctx, ClientLevel lv, BlockState s, BlockPos c) {
        if (s.getDestroySpeed(lv, c) < 0) return false;          // bedrock etc.
        if (lv.getBlockEntity(c) != null) return false;          // chests, furnaces, signs, spawners ...
        return ctx.correct(s);                                    // do not grind obsidian with a weak pick
    }

    private static boolean harmful(BlockState s) {
        return s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.COBWEB) || s.is(Blocks.WITHER_ROSE);
    }

    public static Plan find(Minecraft mc, BlockPos start, BlockPos spawner, Set<Long> blocked) {
        ClientLevel lv = mc.level;
        DigTools.Ctx ctx = new DigTools.Ctx(mc);
        boolean fastest = Settings.dig.mode == 1;
        double minStep = fastest ? 0.23 : 1.0;

        Set<Long> goals = new HashSet<>();
        for (int[] d : DIRS) {
            for (int y = 0; y >= -1; y--) {
                goals.add(new BlockPos(spawner.getX() + d[0], spawner.getY() + y, spawner.getZ() + d[1]).asLong());
            }
        }

        int radius = Settings.dig.maxRadius + 8;
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble((Node n) -> n.f));
        Map<Long, Node> best = new HashMap<>();
        Node s0 = new Node(start, null, 0, h(start, spawner) * minStep);
        open.add(s0);
        best.put(s0.key, s0);

        int nodes = 0;
        while (!open.isEmpty() && nodes < MAX_NODES) {
            Node n = open.poll();
            if (best.get(n.key) != n) continue; // stale entry
            nodes++;

            if (goals.contains(n.key)) {
                List<BlockPos> path = new ArrayList<>();
                for (Node c = n; c != null; c = c.parent) path.add(c.pos);
                Collections.reverse(path);
                return new Plan(path, n.g, goals);
            }

            for (int[] d : DIRS) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos t = n.pos.offset(d[0], dy, d[1]);
                    if (Math.abs(t.getX() - start.getX()) > radius || Math.abs(t.getZ() - start.getZ()) > radius) continue;
                    double c = moveCost(lv, ctx, n.pos, t, spawner, blocked, fastest);
                    if (c < 0) continue;
                    double ng = n.g + c;
                    Node ex = best.get(t.asLong());
                    if (ex != null && ex.g <= ng) continue;
                    Node nn = new Node(t, n, ng, ng + h(t, spawner) * minStep);
                    best.put(nn.key, nn);
                    open.add(nn);
                }
            }
        }
        return null;
    }

    private static int h(BlockPos pos, BlockPos sp) {
        int hx = Math.abs(pos.getX() - sp.getX()) + Math.abs(pos.getZ() - sp.getZ()) - 1;
        if (hx < 0) hx = 0;
        int y = pos.getY();
        int vs = y > sp.getY() ? y - sp.getY() : (y < sp.getY() - 1 ? sp.getY() - 1 - y : 0);
        return Math.max(hx, vs);
    }

    /** Returns the cost of the move, or -1 if the move is not allowed. */
    private static double moveCost(ClientLevel lv, DigTools.Ctx ctx, BlockPos from, BlockPos to,
                                   BlockPos spawner, Set<Long> blocked, boolean fastest) {
        double cost = fastest ? 0.23 : 1.0;
        if (to.getY() != from.getY()) cost += fastest ? 0.1 : 0.25;
        if (!lv.hasChunkAt(to)) return -1;

        for (BlockPos c : clearCells(from, to)) {
            if (c.equals(spawner)) return -1;
            if (blocked.contains(c.asLong())) return -1;
            if (!lv.hasChunkAt(c)) return -1;

            BlockState s = lv.getBlockState(c);
            if (!s.getFluidState().isEmpty()) return -1;
            if (harmful(s)) return -1;

            // never open a tunnel next to lava, discourage water
            for (Direction dir : Direction.values()) {
                BlockPos nb = c.relative(dir);
                if (!lv.hasChunkAt(nb)) continue;
                if (lv.getFluidState(nb).is(FluidTags.LAVA)) return -1;
                if (lv.getFluidState(nb).is(FluidTags.WATER)) cost += 1.5;
            }

            if (!s.getCollisionShape(lv, c).isEmpty()) {
                if (!canMine(ctx, lv, s, c)) return -1;
                float hardness = s.getDestroySpeed(lv, c);
                float speed = ctx.speed(s);
                double secs = hardness * (ctx.correct(s) ? 1.5 : 5.0) / speed;
                cost += fastest ? secs : 0.1 + 0.02 * secs;
                if (lv.getBlockState(c.above()).getBlock() instanceof FallingBlock) cost += 3;
            }
        }

        // there must be something safe to stand on
        BlockPos below = to.below();
        BlockState bs = lv.getBlockState(below);
        if (bs.getCollisionShape(lv, below).isEmpty() || !bs.getFluidState().isEmpty()) return -1;
        if (bs.is(Blocks.MAGMA_BLOCK) || bs.is(Blocks.CACTUS) || bs.is(Blocks.CAMPFIRE)
                || bs.is(Blocks.SOUL_CAMPFIRE)) return -1;
        return cost;
    }
}
