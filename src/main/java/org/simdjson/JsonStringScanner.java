package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;

class JsonStringScanner {

    private static final long EVEN_BITS_MASK = 0x5555555555555555L;
    private static final long ODD_BITS_MASK = ~EVEN_BITS_MASK;

    private final ByteVector backslashMask;
    private final ByteVector quoteMask;

    private long prevInString = 0;
    private long prevEscaped = 0;

    JsonStringScanner() {
        this.backslashMask = ByteVector.broadcast(StructuralIndexer.SPECIES, (byte) '\\');
        this.quoteMask = ByteVector.broadcast(StructuralIndexer.SPECIES, (byte) '"');
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

    JsonStringBlock next(ByteVector chunk0, ByteVector chunk1, ByteVector chunk2, ByteVector chunk3) {
        long backslash = eq(chunk0, chunk1, chunk2, chunk3, backslashMask);
        long escaped = findEscaped(backslash);
        long quote = eq(chunk0, chunk1, chunk2, chunk3, quoteMask) & ~escaped;
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

    private long eq(ByteVector chunk0, ByteVector chunk1, ByteVector chunk2, ByteVector chunk3, ByteVector mask) {
        long r0 = chunk0.eq(mask).toLong();
        long r1 = chunk1.eq(mask).toLong();
        long r2 = chunk2.eq(mask).toLong();
        long r3 = chunk3.eq(mask).toLong();
        return r0 | (r1 << 16) | (r2 << 32) | (r3 << 48);
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
        prevEscaped = ((oddSequenceStarts ^ sequencesStartingOnEvenBits) & (backslash ^ sequencesStartingOnEvenBits)) >>> 63;

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
