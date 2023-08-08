package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

public class GraphNodeFlags {
    public static final int IS_LOADED               = 0;
    public static final int HAS_BLOCK_GEOMETRY      = 1;
    public static final int HAS_BLOCK_ENTITIES      = 2;
    public static final int HAS_ANIMATED_SPRITES    = 3;

    public static final int NONE = 0;

    public static boolean contains(int set, int flag) {
        return (set & (1 << flag)) != 0;
    }

    private static final int HAS_RENDER_DATA = 0b1110;

    public static boolean containsRenderData(int set) {
        return (set & HAS_RENDER_DATA) != 0;
    }
}
