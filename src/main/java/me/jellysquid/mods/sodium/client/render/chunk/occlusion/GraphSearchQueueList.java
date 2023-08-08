package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

import org.jetbrains.annotations.NotNull;

class GraphSearchQueueList {
    @NotNull final GraphSearchQueue origin;

    @NotNull final GraphSearchQueue negX;
    @NotNull final GraphSearchQueue posX;
    @NotNull final GraphSearchQueue negY;
    @NotNull final GraphSearchQueue posY;
    @NotNull final GraphSearchQueue negZ;
    @NotNull final GraphSearchQueue posZ;

    GraphSearchQueueList(@NotNull GraphSearchQueue origin,
                         @NotNull GraphSearchQueue negX,
                         @NotNull GraphSearchQueue posX,
                         @NotNull GraphSearchQueue negY,
                         @NotNull GraphSearchQueue posY,
                         @NotNull GraphSearchQueue negZ,
                         @NotNull GraphSearchQueue posZ)
    {
        this.origin = origin;
        this.negX = negX;
        this.posX = posX;
        this.negY = negY;
        this.posY = posY;
        this.negZ = negZ;
        this.posZ = posZ;
    }
}
