package org.simdjson;

import java.util.Arrays;

public class BitIndexes {

    private final int[] indexes;

    private int writeIdx;
    private int readIdx;

    BitIndexes(int capacity) {
        indexes = new int[capacity];
    }

    void write(int blockIndex, long bits) {
        if (bits == 0) {
            return;
        }

        int idx = blockIndex - 64;
        int cnt = Long.bitCount(bits);
        for (int i = 0; i < 8; i++) {
            indexes[i + writeIdx] = idx + Long.numberOfTrailingZeros(bits);
            bits = clearLowestBit(bits);
        }

        if (cnt > 8) {
            for (int i = 8; i < 16; i++) {
                indexes[i + writeIdx] = idx + Long.numberOfTrailingZeros(bits);
                bits = clearLowestBit(bits);
            }
            if (cnt > 16) {
                int i = 16;
                do {
                    indexes[i + writeIdx] = idx + Long.numberOfTrailingZeros(bits);
                    bits = clearLowestBit(bits);
                    i++;
                } while (i < cnt);
            }
        }
        writeIdx += cnt;
    }

    private long clearLowestBit(long bits) {
        return bits & (bits - 1);
    }

    int advance() {
        return indexes[readIdx++];
    }

    int getAndAdvance() {
        assert readIdx <= writeIdx;
        return indexes[readIdx++];
    }

    int getLast() {
        return indexes[writeIdx - 1];
    }

    int advanceAndGet() {
        assert readIdx + 1 <= writeIdx;
        return indexes[++readIdx];
    }

    int peek() {
        assert readIdx <= writeIdx;
        return indexes[readIdx];
    }

    boolean hasNext() {
        return writeIdx > readIdx;
    }

    boolean isEnd() {
        return writeIdx == readIdx;
    }

    boolean isPastEnd() {
        return readIdx > writeIdx;
    }

    void finish() {
        // If we go past the end of the detected structural indexes, it means we are dealing with an invalid JSON.
        // Thus, we need to stop processing immediately and throw an exception. To avoid checking after every increment
        // of readIdx whether this has happened, we jump to the first structural element. This should produce the
        // desired outcome, i.e., an iterator should detect invalid JSON. To understand how this works, let's first
        // exclude primitive values (numbers, strings, booleans, nulls) from the scope of possible JSON documents. We
        // can do this because, when these values are parsed, the length of the input buffer is verified, ensuring we
        // never go past its end. Therefore, we can focus solely on objects and arrays. Since we always check that if
        // the first character is '{', the last one must be '}', and if the first character is '[', the last one must
        // be ']', we know that if we've reached beyond the buffer without crashing, the input is either '{...}' or '[...]'.
        // Thus, if we jump to the first structural element, we will generate either '{...}{' or '[...]['. Both of these
        // are invalid sequences and will be detected by the iterator, which will then stop processing and throw an
        // exception informing about the invalid JSON.
        indexes[writeIdx] = 0;
    }

    void reset() {
        writeIdx = 0;
        readIdx = 0;
    }
}
