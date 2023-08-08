package me.jellysquid.mods.sodium.client.render.chunk;

public enum ChunkUpdateType {
    INITIAL_BUILD(false),
    REBUILD(false),
    IMPORTANT_REBUILD(true);

    public final boolean important;

    ChunkUpdateType(boolean important) {
        this.important = important;
    }


    public static boolean canPromote(ChunkUpdateType prev, ChunkUpdateType next) {
        return prev == null || (prev == REBUILD && next == IMPORTANT_REBUILD);
    }
}
