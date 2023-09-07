package org.simdjson;

class Tape {

    static final char ROOT = 'r';
    static final char START_ARRAY = '[';
    static final char START_OBJECT = '{';
    static final char END_ARRAY = ']';
    static final char END_OBJECT = '}';
    static final char STRING = '"';
    static final char INT64 = 'l';
    static final char DOUBLE = 'd';
    static final char TRUE_VALUE = 't';
    static final char FALSE_VALUE = 'f';
    static final char NULL_VALUE = 'n';

    private static final long JSON_VALUE_MASK = 0x00FFFFFFFFFFFFFFL;
    private static final int JSON_COUNT_MASK = 0xFFFFFF;

    private final long[] tape;

    private int tapeIdx;

    Tape(int capacity) {
        tape = new long[capacity];
    }

    void append(long val, char type) {
        tape[tapeIdx] = val | (((long) type) << 56);
        tapeIdx++;
    }

    void appendInt64(long val) {
        append(0, INT64);
        tape[tapeIdx] = val;
        tapeIdx++;
    }

    void appendDouble(double val) {
        append(0, DOUBLE);
        tape[tapeIdx] = Double.doubleToRawLongBits(val);
        tapeIdx++;
    }

    void write(int idx, long val, char type) {
        tape[idx] = val | (((long) type) << 56);
    }

    void skip() {
        tapeIdx++;
    }

    void reset() {
        tapeIdx = 0;
    }

    int getCurrentIdx() {
        return tapeIdx;
    }

    char getType(int idx) {
        return (char) (tape[idx] >> 56);
    }

    long getValue(int idx) {
        return tape[idx] & JSON_VALUE_MASK;
    }

    long getInt64Value(int idx) {
        return tape[idx + 1];
    }

    double getDouble(int idx) {
        long bits = getInt64Value(idx);
        return Double.longBitsToDouble(bits);
    }

    int getMatchingBraceIndex(int idx) {
        return (int) tape[idx];
    }

    int getScopeCount(int idx) {
        return (int) ((tape[idx] >> 32) & JSON_COUNT_MASK);
    }

    int computeNextIndex(int idx) {
        switch (getType(idx)) {
            case START_ARRAY, START_OBJECT -> {
                return getMatchingBraceIndex(idx);
            }
            case INT64, DOUBLE -> {
                return idx + 2;
            }
            default -> {
                return idx + 1;
            }
        }
    }
}
