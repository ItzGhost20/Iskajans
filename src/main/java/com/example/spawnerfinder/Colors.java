package com.example.spawnerfinder;

public final class Colors {
    private Colors() {}

    public static float[] of(String t) {
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

    public static int rgb(String t) {
        float[] c = of(t);
        return ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
    }
}
