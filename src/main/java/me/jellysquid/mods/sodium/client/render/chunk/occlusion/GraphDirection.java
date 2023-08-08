package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

import net.minecraft.util.math.Direction;

public class GraphDirection {
    public static final int NEG_X = 0;
    public static final int POS_X = 1;
    public static final int NEG_Y = 2;
    public static final int POS_Y = 3;
    public static final int NEG_Z = 4;
    public static final int POS_Z = 5;


    public static final int COUNT   = 6;

    public static final int NONE    = 0b000000;
    public static final int ALL     = 0b111111;

    private static final Direction[] ENUMS;
    private static final int[] OPPOSITE;
    private static final int[] X, Y, Z;

    static {
        OPPOSITE = new int[COUNT];
        OPPOSITE[NEG_X] = POS_X;
        OPPOSITE[POS_X] = NEG_X;
        OPPOSITE[NEG_Y] = POS_Y;
        OPPOSITE[POS_Y] = NEG_Y;
        OPPOSITE[NEG_Z] = POS_Z;
        OPPOSITE[POS_Z] = NEG_Z;

        X = new int[COUNT];
        X[NEG_X] = -1;
        X[POS_X] = 1;

        Y = new int[COUNT];
        Y[NEG_Y] = -1;
        Y[POS_Y] = 1;

        Z = new int[COUNT];
        Z[NEG_Z] = -1;
        Z[POS_Z] = 1;

        ENUMS = new Direction[COUNT];
        ENUMS[NEG_X] = Direction.WEST;
        ENUMS[POS_X] = Direction.EAST;
        ENUMS[NEG_Y] = Direction.DOWN;
        ENUMS[POS_Y] = Direction.UP;
        ENUMS[NEG_Z] = Direction.NORTH;
        ENUMS[POS_Z] = Direction.SOUTH;
    }

    public static int opposite(int direction) {
        return OPPOSITE[direction];
    }

    public static int x(int direction) {
        return X[direction];
    }

    public static int y(int direction) {
        return Y[direction];
    }

    public static int z(int direction) {
        return Z[direction];
    }

    public static Direction toEnum(int direction) {
        return ENUMS[direction];
    }

    public static boolean contains(int bitfield, int direction) {
        return (bitfield & (1 << direction)) != 0;
    }
}