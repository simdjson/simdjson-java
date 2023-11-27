package org.simdjson;

import jdk.incubator.vector.ByteVector;

class JsonStringScanner {

    private static final long EVEN_BITS_MASK = 0x5555555555555555L;
    private static final long ODD_BITS_MASK = ~EVEN_BITS_MASK;

    private final ByteVector backslashMask;
    private final ByteVector quoteMask;

    private long prevInString = 0;
    private long prevEscaped = 0;

    JsonStringScanner() {
        this.backslashMask = ByteVector.broadcast(StructuralIndexer.BYTE_SPECIES, (byte) '\\');
        this.quoteMask = ByteVector.broadcast(StructuralIndexer.BYTE_SPECIES, (byte) '"');
    }

    JsonStringBlock next(ByteVector chunk0) {
        long backslash = eq(chunk0, backslashMask);
        long escaped = findEscaped(backslash);
        long quote = eq(chunk0, quoteMask) & ~escaped;
        long inString = prefixXor(quote) ^ prevInString;
        prevInString = inString >> 63;
        return new JsonStringBlock(quote, inString);
    }

    JsonStringBlock next(ByteVector chunk0, ByteVector chunk1) {
        long backslash = eq(chunk0, chunk1, backslashMask);
        long escaped = findEscaped(backslash);
        long quote = eq(chunk0, chunk1, quoteMask) & ~escaped;
        long inString = prefixXor(quote) ^ prevInString;
        prevInString = inString >> 63;
        return new JsonStringBlock(quote, inString);
    }

    private long eq(ByteVector chunk0, ByteVector mask) {
        long r = chunk0.eq(mask).toLong();
        return r;
    }

    private long eq(ByteVector chunk0, ByteVector chunk1, ByteVector mask) {
        long r0 = chunk0.eq(mask).toLong();
        long r1 = chunk1.eq(mask).toLong();
        return r0 | (r1 << 32);
    }

    private long findEscaped(long backslash) {
        if (backslash == 0) {
            long escaped = prevEscaped;
            prevEscaped = 0;
            return escaped;
        }
        backslash &= ~prevEscaped;
        long followsEscape = backslash << 1 | prevEscaped;
        long oddSequenceStarts = backslash & ODD_BITS_MASK & ~followsEscape;

        long sequencesStartingOnEvenBits = oddSequenceStarts + backslash;
        // Here, we check if the unsigned addition above caused an overflow. If that's the case, we store 1 in prevEscaped.
        // The formula used to detect overflow was taken from 'Hacker's Delight, Second Edition' by Henry S. Warren, Jr.,
        // Chapter 2-13.
        prevEscaped = ((oddSequenceStarts >>> 1) + (backslash >>> 1) + ((oddSequenceStarts & backslash) & 1)) >>> 63;

        long invertMask = sequencesStartingOnEvenBits << 1;
        return (EVEN_BITS_MASK ^ invertMask) & followsEscape;
    }

    private long prefixXor(long bitmask) {
        bitmask ^= bitmask << 1;
        bitmask ^= bitmask << 2;
        bitmask ^= bitmask << 4;
        bitmask ^= bitmask << 8;
        bitmask ^= bitmask << 16;
        bitmask ^= bitmask << 32;
        return bitmask;
    }

    void reset() {
        prevInString = 0;
        prevEscaped = 0;
    }

    void finish() {
        if (prevInString != 0) {
            throw new JsonParsingException("Unclosed string. A string is opened, but never closed.");
        }
    }
}
