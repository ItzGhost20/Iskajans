package com.example.spawnerfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hotbar tool selection for AutoDig. Normal blocks: regular tool. Spawner: silk touch pickaxe only. */
public final class DigTools {
    private DigTools() {}

    static boolean hasSilk(ItemStack st) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, st) > 0;
    }

    static boolean healthy(ItemStack st) {
        if (!Settings.dig.protectTools) return true;
        return !(st.isDamageableItem() && st.getMaxDamage() - st.getDamageValue() <= 8);
    }

    static boolean isTool(ItemStack st) {
        if (st.isEmpty()) return false;
        Item it = st.getItem();
        return it instanceof PickaxeItem || (Settings.dig.bestTool && it instanceof DiggerItem);
    }

    /** Hotbar slot of a silk touch pickaxe, or -1. */
    public static int silkPick(Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof PickaxeItem && hasSilk(st) && healthy(st)) return i;
        }
        return -1;
    }

    /** Best slot for a normal block (silk touch picks are skipped unless nothing else exists), or -1. */
    public static int pick(Minecraft mc, BlockState state) {
        Inventory inv = mc.player.getInventory();
        for (int pass = 0; pass < 2; pass++) {
            int best = -1;
            float bestSpeed = -1f;
            for (int i = 0; i < 9; i++) {
                ItemStack st = inv.getItem(i);
                if (!isTool(st) || !healthy(st)) continue;
                if (pass == 0 && hasSilk(st)) continue;
                float sp = st.getDestroySpeed(state);
                if (sp > bestSpeed) {
                    bestSpeed = sp;
                    best = i;
                }
            }
            if (best >= 0) return best;
        }
        return -1;
    }

    /** Snapshot of the usable tools, used while planning a route. */
    public static final class Ctx {
        final List<ItemStack> tools = new ArrayList<>();
        private final Map<BlockState, Float> speeds = new HashMap<>();

        public Ctx(Minecraft mc) {
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < 9; i++) {
                ItemStack st = inv.getItem(i);
                if (isTool(st) && healthy(st) && !hasSilk(st)) tools.add(st);
            }
            if (tools.isEmpty()) {
                for (int i = 0; i < 9; i++) {
                    ItemStack st = inv.getItem(i);
                    if (isTool(st) && healthy(st)) tools.add(st);
                }
            }
        }

        public float speed(BlockState s) {
            return speeds.computeIfAbsent(s, k -> {
                float best = 1f;
                for (ItemStack st : tools) best = Math.max(best, st.getDestroySpeed(k));
                return best;
            });
        }

        public boolean correct(BlockState s) {
            if (!s.requiresCorrectToolForDrops()) return true;
            for (ItemStack st : tools) {
                if (st.isCorrectToolForDrops(s)) return true;
            }
            return false;
        }
    }
}
