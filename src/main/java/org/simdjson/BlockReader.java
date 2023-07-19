package org.simdjson;

import java.util.Arrays;

class BlockReader {

    private static final byte SPACE = 0x20;

    private final int stepSize;
    private final byte[] lastBlock;
    private final byte[] spaces;

    private byte[] buffer;
    private int len;
    private int idx = 0;
    private int lenMinusStep;

    BlockReader(int stepSize) {
        this.stepSize = stepSize;
        this.lastBlock = new byte[stepSize];
        this.spaces = new byte[stepSize];
        Arrays.fill(spaces, SPACE);
    }

    void reset(byte[] buffer, int len) {
        this.idx = 0;
        this.len = len;
        this.buffer = buffer;
        this.lenMinusStep = len < stepSize ? 0 : len - stepSize;
    }

    boolean hasFullBlock() {
        return idx < lenMinusStep;
    }

    byte[] remainder() {
        System.arraycopy(spaces, 0, lastBlock, 0, lastBlock.length);
        System.arraycopy(buffer, idx, lastBlock, 0, len - idx);
        return lastBlock;
    }

    void advance() {
        idx += stepSize;
    }

    int getBlockIndex() {
        return idx;
    }
}
