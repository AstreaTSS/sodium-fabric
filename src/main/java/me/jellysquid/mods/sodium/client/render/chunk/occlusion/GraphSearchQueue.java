package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;

public class GraphSearchQueue {
    private final long key;

    private int size;

    private final byte[] incoming = new byte[RenderRegion.REGION_SIZE];
    private final byte[] queue = new byte[RenderRegion.REGION_SIZE + 1];

    public GraphSearchQueue(long key) {
        this.key = key;
    }

    public void add(int sectionIndex, int directions) {
        byte incoming = MemoryIntrinsics.getByte(this.incoming, sectionIndex);
        MemoryIntrinsics.putByte(this.incoming, sectionIndex, (byte) (incoming | directions));
        MemoryIntrinsics.putByte(this.queue, this.size, (byte) sectionIndex);

        this.size += (incoming == (byte) 0) ? 1 : 0;
    }

    public int getNodeIndex(int queueIndex) {
        return Byte.toUnsignedInt(this.queue[queueIndex]);
    }

    public int getIncomingDirections(int sectionIndex) {
        return Byte.toUnsignedInt(this.incoming[sectionIndex]);
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public long getKey() {
        return this.key;
    }
}
