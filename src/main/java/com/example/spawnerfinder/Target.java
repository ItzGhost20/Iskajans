package com.example.spawnerfinder;

import net.minecraft.world.phys.AABB;

/** Something to highlight: a box in the world with a type, label and distance from the player. */
public record Target(AABB box, String type, String label, double dist) {}
