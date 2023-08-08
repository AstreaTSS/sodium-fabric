package me.jellysquid.mods.sodium.client.util.collections;

import net.minecraft.util.math.MathHelper;

import java.util.Arrays;

public class BitArray {
    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
    private static final int BIT_INDEX_MASK = BITS_PER_WORD - 1;

    private final long[] words;
    private final int count;

    public BitArray(int count) {
        this.words = new long[(MathHelper.roundUpToMultiple(count, BITS_PER_WORD) >> ADDRESS_BITS_PER_WORD)];
        this.count = count;
    }

    public boolean get(int index) {
        return (this.words[wordIndex(index)] & 1L << bitIndex(index)) != 0;
    }

    public void set(int index) {
        this.words[wordIndex(index)] |= 1L << bitIndex(index);
    }
    
    public void unset(int index) {
        this.words[wordIndex(index)] &= ~(1L << bitIndex(index));
    }

    private static int wordIndex(int index) {
        return index >> ADDRESS_BITS_PER_WORD;
    }

    private static int bitIndex(int index) {
        return index & BIT_INDEX_MASK;
    }

    public void fill(boolean value) {
        Arrays.fill(this.words, value ? 0xFFFFFFFFFFFFFFFFL : 0x0000000000000000L);
    }

    public void clear() {
        this.fill(false);
    }

    public int count() {
        int sum = 0;

        for (long word : this.words) {
            sum += Long.bitCount(word);
        }

        return sum;
    }

    public int capacity() {
        return this.count;
    }
}