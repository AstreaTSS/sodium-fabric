package net.caffeinemc.mods.sodium.api.memory;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class MemoryIntrinsics {
    private static final Unsafe UNSAFE;

    private static final int BYTE_ARRAY_BASE_OFFSET, BYTE_ARRAY_INDEX_SCALE;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);

            UNSAFE = (Unsafe) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Couldn't obtain reference to sun.misc.Unsafe", e);
        }

        BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        BYTE_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(byte[].class);
    }

    /**
     * Copies the number of bytes specified by {@param length} between off-heap buffers {@param src} and {@param dst}.
     * <p>
     * WARNING: This function makes no attempt to verify that the parameters are correct. If you pass invalid pointers
     * or read/write memory outside a buffer, the JVM will likely crash!
     *
     * @param src The source pointer to begin copying from
     * @param dst The destination pointer to begin copying into
     * @param length The number of bytes to copy
     */
    public static void copyMemory(long src, long dst, int length) {
        // This seems to be faster than MemoryUtil.copyMemory in all cases.
        UNSAFE.copyMemory(src, dst, length);
    }

    public static byte getByte(byte[] array, int index) {
        return UNSAFE.getByte(array, BYTE_ARRAY_BASE_OFFSET + (index * BYTE_ARRAY_INDEX_SCALE));
    }

    public static void putByte(byte[] array, int index, byte value) {
        UNSAFE.putByte(array, BYTE_ARRAY_BASE_OFFSET + (index * BYTE_ARRAY_INDEX_SCALE), value);
    }
}
