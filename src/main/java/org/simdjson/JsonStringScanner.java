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
        VectorSpecies<Byte> species = ByteVector.SPECIES_512;
        this.backslashMask = ByteVector.broadcast(species, (byte) '\\');
        this.quoteMask = ByteVector.broadcast(species, (byte) '"');
    }

    JsonStringBlock next(ByteVector chunk) {
        long backslash = eq(chunk, backslashMask);
        long escaped = findEscaped(backslash);
        long quote = eq(chunk, quoteMask) & ~escaped;
        long inString = prefixXor(quote) ^ prevInString;
        prevInString = inString >> 63;
        return new JsonStringBlock(quote, inString);
    }

    private long eq(ByteVector chunk, ByteVector mask) {
        return chunk.eq(mask).toLong();
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
