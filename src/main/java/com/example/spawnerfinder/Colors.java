package com.example.spawnerfinder;

import java.util.List;

public final class Colors {
    private Colors() {}

    /** Types shown in the color picker even before they are seen in the world. */
    public static final List<String> DEFAULT_TYPES = List.of(
            "zombie", "skeleton", "spider", "cave_spider", "blaze", "silverfish",
            "magma_cube", "creeper", "unknown",
            "librarian", "cleric", "farmer", "armorer", "weaponsmith", "toolsmith",
            "wandering_trader");

    public static float[] of(String t) {
        Integer c = Settings.colors.get(t);
        if (c != null) {
            return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
        }
        return defaultOf(t);
    }

    public static int rgb(String t) {
        Integer c = Settings.colors.get(t);
        if (c != null) return c & 0xFFFFFF;
        float[] f = defaultOf(t);
        return ((int) (f[0] * 255) << 16) | ((int) (f[1] * 255) << 8) | (int) (f[2] * 255);
    }

    private static float[] defaultOf(String t) {
        return switch (t) {
            case "zombie" -> new float[]{0.3f, 1f, 0.3f};
            case "skeleton" -> new float[]{0.9f, 0.9f, 0.9f};
            case "spider" -> new float[]{1f, 0.2f, 0.2f};
            case "cave_spider" -> new float[]{0.2f, 0.9f, 0.9f};
            case "blaze" -> new float[]{1f, 0.6f, 0.1f};
            case "silverfish" -> new float[]{0.65f, 0.65f, 0.8f};
            case "magma_cube" -> new float[]{1f, 0.4f, 0f};
            case "creeper" -> new float[]{0.4f, 1f, 0.6f};
            case "librarian" -> new float[]{0.7f, 0.5f, 1f};
            case "cleric" -> new float[]{1f, 0.4f, 0.8f};
            case "farmer" -> new float[]{0.6f, 1f, 0.3f};
            case "toolsmith", "weaponsmith", "armorer" -> new float[]{0.7f, 0.7f, 0.75f};
            case "wandering_trader" -> new float[]{0.3f, 0.6f, 1f};
            default -> {
                int h = t.hashCode();
                yield new float[]{
                        Math.max(0.35f, ((h >> 16) & 0xFF) / 255f),
                        Math.max(0.35f, ((h >> 8) & 0xFF) / 255f),
                        Math.max(0.35f, (h & 0xFF) / 255f)};
            }
        };
    }
}
