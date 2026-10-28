package com.example.spawnerfinder;

import net.minecraftforge.fml.common.Mod;

@Mod("spawnerfinder")
public class SpawnerFinder {
    public SpawnerFinder() {
        Settings.load();
    }
}
