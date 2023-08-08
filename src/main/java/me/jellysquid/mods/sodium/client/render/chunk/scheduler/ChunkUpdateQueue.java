package me.jellysquid.mods.sodium.client.render.chunk.scheduler;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.jellysquid.mods.sodium.client.util.sorting.RadixSort;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.NoSuchElementException;

public class ChunkUpdateQueue {
    private final LongSet pending = new LongOpenHashSet();

    public LongIterator getSortedList(Camera camera) {
        var size = this.pending.size();

        var positions = new long[size];
        LongIterators.unwrap(this.pending.iterator(), positions);

        var keys = new int[size];
        createSortingKeys(positions, keys, ChunkSectionPos.from(camera.getPos()));

        // maybe a Radix sort doesn't make sense here, even if it's really fast
        var indices = RadixSort.sort(keys);

        return new IndirectLongArrayIterator(positions, indices, size);
    }

    private static void createSortingKeys(long[] positions, int[] keys, ChunkSectionPos origin) {
        for (int i = 0; i < keys.length; i++) {
            var position = positions[i];
            var distance = getDistance(origin, position);

            keys[i] = RadixSort.Ints.createRadixKey(distance);
        }
    }

    private static int getDistance(ChunkSectionPos origin, long position) {
        var dx = Math.abs(origin.getX() - ChunkSectionPos.unpackX(position));
        var dy = Math.abs(origin.getY() - ChunkSectionPos.unpackY(position));
        var dz = Math.abs(origin.getZ() - ChunkSectionPos.unpackZ(position));

        return Math.max(Math.max(dx, dy), dz);
    }

    public boolean add(long key) {
        return this.pending.add(key);
    }

    public boolean remove(long key) {
        return this.pending.remove(key);
    }

    public int size() {
        return this.pending.size();
    }

    public void clear() {
        this.pending.clear();
    }

    private static class IndirectLongArrayIterator implements LongIterator {

        private final long[] values;
        private final int[] indices;

        private final int size;

        private int index;

        private IndirectLongArrayIterator(long[] values, int[] indices, int size) {
            this.values = values;
            this.indices = indices;

            this.size = size;
        }

        @Override
        public long nextLong() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            return this.values[this.indices[this.index++]];
        }

        @Override
        public boolean hasNext() {
            return this.index < this.size;
        }
    }
}
