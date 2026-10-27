package com.example.spawnerfinder;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "spawnerfinder", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBinds {
    public static final KeyMapping TOGGLE =
            new KeyMapping("key.spawnerfinder.toggle", GLFW.GLFW_KEY_K, "key.categories.misc");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent e) {
        e.register(TOGGLE);
    }
}
