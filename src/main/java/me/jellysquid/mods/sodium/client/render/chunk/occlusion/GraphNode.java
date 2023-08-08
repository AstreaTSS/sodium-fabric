package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

public class GraphNode {
    private static final long MASK_CONNECTIONS = 0xFFFFFFFFFFFFL; // 48 bits
    private static final long MASK_FLAGS = 0xFFL; // 8 bits

    private static final int OFFSET_CONNECTIONS = 0;
    private static final int OFFSET_FLAGS = 48;

    public static long pack(long connections, int flags) {
        return ((connections & MASK_CONNECTIONS) << OFFSET_CONNECTIONS) |
                ((flags & MASK_FLAGS) << OFFSET_FLAGS);
    }

    public static long unpackConnections(long packed) {
        return (packed >>> OFFSET_CONNECTIONS) & MASK_CONNECTIONS;
    }

    public static int unpackFlags(long packed) {
        return (int) ((packed >>> OFFSET_FLAGS) & MASK_FLAGS);
    }

    public static long empty() {
        return 0L;
    }

    public static boolean isEmpty(long packed) {
        return packed == empty();
    }
}
