package org.simdjson;

import jdk.incubator.vector.ByteVector;

class JsonStringScanner {
    private static final long EVEN_BITS_MASK = 0x5555555555555555L;
    private static final long ODD_BITS_MASK = ~EVEN_BITS_MASK;

    private static final ByteVector BACKSLASH_MASK_128 = ByteVector.broadcast(ByteVector.SPECIES_128, (byte) '\\');
    private static final ByteVector QUOTE_MASK_128 = ByteVector.broadcast(ByteVector.SPECIES_128, (byte) '"');

    private static final ByteVector BACKSLASH_MASK_256 = ByteVector.broadcast(ByteVector.SPECIES_256, (byte) '\\');
    private static final ByteVector QUOTE_MASK_256 = ByteVector.broadcast(ByteVector.SPECIES_256, (byte) '"');

    private static final ByteVector BACKSLASH_MASK_512 = ByteVector.broadcast(ByteVector.SPECIES_512, (byte) '\\');
    private static final ByteVector QUOTE_MASK_512 = ByteVector.broadcast(ByteVector.SPECIES_512, (byte) '"');


    private long prevInString = 0;
    private long prevEscaped = 0;

    JsonStringScanner() {
    }

    JsonStringBlock next(ByteVector chunk0) {
        assert chunk0.species() == ByteVector.SPECIES_512;
        var backslash = eq(chunk0, BACKSLASH_MASK_512);
        var escaped = findEscaped(backslash);
        var quote = eq(chunk0, QUOTE_MASK_512) & ~escaped;
        var inString = prefixXor(quote) ^ prevInString;
        prevInString = inString >> 63;
        return new JsonStringBlock(quote, inString);
    }

    JsonStringBlock next(ByteVector chunk0, ByteVector chunk1) {
        assert chunk0.species() == ByteVector.SPECIES_256;
        assert chunk1.species() == ByteVector.SPECIES_256;
        var backslash = eq(chunk0, chunk1, BACKSLASH_MASK_256);
        var escaped = findEscaped(backslash);
        var quote = eq(chunk0, chunk1, QUOTE_MASK_256) & ~escaped;
        var inString = prefixXor(quote) ^ prevInString;
        prevInString = inString >> 63;
        return new JsonStringBlock(quote, inString);
    }

    JsonStringBlock next(ByteVector chunk0, ByteVector chunk1, ByteVector chunk2, ByteVector chunk3) {
        assert chunk0.species() == ByteVector.SPECIES_128;
        assert chunk1.species() == ByteVector.SPECIES_128;
        assert chunk2.species() == ByteVector.SPECIES_128;
        assert chunk3.species() == ByteVector.SPECIES_128;
        var backslash = eq(chunk0, chunk1, chunk2, chunk3, BACKSLASH_MASK_128);
        var escaped = findEscaped(backslash);
        var quote = eq(chunk0, chunk1, chunk2, chunk3, QUOTE_MASK_128) & ~escaped;
        var inString = prefixXor(quote) ^ prevInString;
        prevInString = inString >> 63;
        return new JsonStringBlock(quote, inString);
    }

    private static long eq(ByteVector chunk0, ByteVector mask) {
        long r = chunk0.eq(mask).toLong();
        return r;
    }

    private static long eq(ByteVector chunk0, ByteVector chunk1, ByteVector mask) {
        long r0 = chunk0.eq(mask).toLong();
        long r1 = chunk1.eq(mask).toLong();
        return r0 | (r1 << 32);
    }

    private static long eq(ByteVector chunk0, ByteVector chunk1, ByteVector chunk2, ByteVector chunk3, ByteVector mask) {
        long r0 = chunk0.eq(mask).toLong();
        long r1 = chunk1.eq(mask).toLong();
        long r2 = chunk2.eq(mask).toLong();
        long r3 = chunk3.eq(mask).toLong();
        return (r0 & 0xFFFFL) | ((r1 & 0xFFFFL) << 16) | ((r2 & 0xFFFFL) << 32) | ((r3 & 0xFFFFL) << 48);
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

