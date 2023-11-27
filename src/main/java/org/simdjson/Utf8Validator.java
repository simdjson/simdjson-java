package org.simdjson;

import jdk.incubator.vector.*;

import java.util.Arrays;

class Utf8Validator {

    private static final VectorSpecies<Byte> VECTOR_SPECIES = StructuralIndexer.BYTE_SPECIES;
    private static final ByteVector INCOMPLETE_CHECK = getIncompleteCheck();
    private static final VectorShuffle<Integer> SHIFT_FOUR_BYTES_FORWARD = VectorShuffle.iota(StructuralIndexer.INT_SPECIES,
            StructuralIndexer.INT_SPECIES.elementSize() - 1, 1, true);
    private static final ByteVector LOW_NIBBLE_MASK = ByteVector.broadcast(VECTOR_SPECIES, 0b0000_1111);
    private static final ByteVector ALL_ASCII_MASK = ByteVector.broadcast(VECTOR_SPECIES, (byte) 0b1000_0000);

    /**
     * Validate the input bytes are valid UTF8
     *
     * @param inputBytes the input bytes to validate
     * @throws JsonParsingException if the input is not valid UTF8
     */
    static void validate(byte[] inputBytes) {
        long previousIncomplete = 0;
        long errors = 0;
        int previousFourUtf8Bytes = 0;

        int idx = 0;
        for (; idx < VECTOR_SPECIES.loopBound(inputBytes.length); idx += VECTOR_SPECIES.vectorByteSize()) {
            ByteVector utf8Vector = ByteVector.fromArray(VECTOR_SPECIES, inputBytes, idx);
            // ASCII fast path can bypass the checks that are only required for multibyte code points
            if (isAscii(utf8Vector)) {
                errors |= previousIncomplete;
            } else {
                previousIncomplete = isIncomplete(utf8Vector);

                var fourBytesPrevious = fourBytesPreviousSlice(utf8Vector, previousFourUtf8Bytes);

                ByteVector firstCheck = firstTwoByteSequenceCheck(utf8Vector.reinterpretAsInts(), fourBytesPrevious);
                ByteVector secondCheck = lastTwoByteSequenceCheck(utf8Vector.reinterpretAsInts(), fourBytesPrevious, firstCheck);

                errors |= secondCheck.compare(VectorOperators.NE, 0).toLong();
            }
            previousFourUtf8Bytes = utf8Vector.reinterpretAsInts().lane(StructuralIndexer.INT_SPECIES.length() - 1);
        }

        // if the input file doesn't align with the vector width, pad the missing bytes with zero
        VectorMask<Byte> remainingBytes = VECTOR_SPECIES.indexInRange(idx, inputBytes.length);
        ByteVector lastVectorChunk = ByteVector.fromArray(VECTOR_SPECIES, inputBytes, idx, remainingBytes);
        if (!isAscii(lastVectorChunk)) {
            previousIncomplete = isIncomplete(lastVectorChunk);

            var fourBytesPrevious = fourBytesPreviousSlice(lastVectorChunk, previousFourUtf8Bytes);

            ByteVector firstCheck = firstTwoByteSequenceCheck(lastVectorChunk.reinterpretAsInts(), fourBytesPrevious);
            ByteVector secondCheck = lastTwoByteSequenceCheck(lastVectorChunk.reinterpretAsInts(), fourBytesPrevious, firstCheck);

            errors |= secondCheck.compare(VectorOperators.NE, 0).toLong();
        }

        if ((errors | previousIncomplete) != 0) {
            throw new JsonParsingException("Invalid UTF8");
        }
    }

    /* Shuffles the input forward by four bytes to make space for the previous four bytes.
    The previous three bytes are required for validation, pulling in the last integer will give the previous four bytes.
    The switch to integer vectors is to allow for integer shifting instead of the more expensive shuffle / slice operations */
    private static IntVector fourBytesPreviousSlice(ByteVector vectorChunk, int previousFourUtf8Bytes) {
        return vectorChunk.reinterpretAsInts()
                .rearrange(SHIFT_FOUR_BYTES_FORWARD)
                .withLane(0, previousFourUtf8Bytes);
    }

    // works similar to previousUtf8Vector.slice(VECTOR_SPECIES.length() - numOfBytesToInclude, utf8Vector) but without the performance cost
    private static ByteVector previousVectorSlice(IntVector utf8Vector, IntVector fourBytesPrevious, int numOfPreviousBytes) {
        return utf8Vector
                .lanewise(VectorOperators.LSHL, Byte.SIZE * numOfPreviousBytes)
                .or(fourBytesPrevious.lanewise(VectorOperators.LSHR, Byte.SIZE * (4 - numOfPreviousBytes)))
                .reinterpretAsBytes();
    }

    private static ByteVector firstTwoByteSequenceCheck(IntVector utf8Vector, IntVector fourBytesPrevious) {
        // shift the current input forward by 1 byte to include 1 byte from the previous input
        var oneBytePrevious = previousVectorSlice(utf8Vector, fourBytesPrevious, 1);

        // high nibbles of the current input (e.g. 0xC3 >> 4 = 0xC)
        ByteVector byte2HighNibbles = utf8Vector.lanewise(VectorOperators.LSHR, 4)
                .reinterpretAsBytes().and(LOW_NIBBLE_MASK);

        // high nibbles of the shifted input
        ByteVector byte1HighNibbles = oneBytePrevious.reinterpretAsInts().lanewise(VectorOperators.LSHR, 4)
                .reinterpretAsBytes().and(LOW_NIBBLE_MASK);

        // low nibbles of the shifted input (e.g. 0xC3 & 0xF = 0x3)
        ByteVector byte1LowNibbles = oneBytePrevious.and(LOW_NIBBLE_MASK);

        ByteVector byte1HighState = byte1HighNibbles.selectFrom(LookupTable.byte1High);
        ByteVector byte1LowState = byte1LowNibbles.selectFrom(LookupTable.byte1Low);
        ByteVector byte2HighState = byte2HighNibbles.selectFrom(LookupTable.byte2High);

        return byte1HighState.and(byte1LowState).and(byte2HighState);
    }

    // All remaining checks are invalid 3–4 byte sequences, which either have too many continuations bytes or not enough
    private static ByteVector lastTwoByteSequenceCheck(IntVector utf8Vector, IntVector fourBytesPrevious, ByteVector firstCheck) {
        // the minimum 3byte lead - 1110_0000 is always greater than the max 2byte lead - 110_11111
        ByteVector twoBytesPrevious = previousVectorSlice(utf8Vector, fourBytesPrevious, 2);
        VectorMask<Byte> is3ByteLead = twoBytesPrevious.compare(VectorOperators.UNSIGNED_GT, (byte) 0b110_11111);

        // the minimum 4byte lead - 1111_0000 is always greater than the max 3byte lead - 1110_1111
        ByteVector threeBytesPrevious = previousVectorSlice(utf8Vector, fourBytesPrevious, 3);
        VectorMask<Byte> is4ByteLead = threeBytesPrevious.compare(VectorOperators.UNSIGNED_GT, (byte) 0b1110_1111);

        // the firstCheck vector contains 0x80 values on continuation byte indexes
        // the 3/4 byte lead bytes should match up with these indexes and zero them out
        return firstCheck.add((byte) 0x80, is3ByteLead.or(is4ByteLead));
    }

    /* checks that the previous vector isn't in an incomplete state.
    Previous vector is in an incomplete state if the last byte is smaller than 0xC0,
    or the second last byte is smaller than 0xE0, or the third last byte is smaller than 0xF0.*/
    private static ByteVector getIncompleteCheck() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] eofArray = new byte[vectorBytes];
        Arrays.fill(eofArray, (byte) 255);
        eofArray[vectorBytes - 3] = (byte) 0xF0;
        eofArray[vectorBytes - 2] = (byte) 0xE0;
        eofArray[vectorBytes - 1] = (byte) 0xC0;
        return ByteVector.fromArray(VECTOR_SPECIES, eofArray, 0);
    }

    private static long isIncomplete(ByteVector utf8Vector) {
        return utf8Vector.compare(VectorOperators.UNSIGNED_GE, INCOMPLETE_CHECK).toLong();
    }

    // ASCII will never exceed 01111_1111
    private static boolean isAscii(ByteVector utf8Vector) {
        return utf8Vector.and(ALL_ASCII_MASK).compare(VectorOperators.EQ, 0).allTrue();
    }

    private static class LookupTable {
        /* Bit 0 = Too Short (lead byte not followed by a continuation byte but by a lead/ASCII byte)
         e.g.   11______ 0_______
                11______ 11______ */
        static final byte TOO_SHORT = 1;

        /* Bit 1 = Too Long (ASCII followed by continuation byte)
         e.g.   01111111 10_000000 */
        static final byte TOO_LONG = 1 << 1;

        /* Bit 2 = Overlong 3-byte
        Any 3-byte sequence that could be represented by a shorter sequence
        Which is any sequence smaller than 1110_0000 10_100000 10_000000 */
        static final byte OVERLONG_3BYTE = 1 << 2;

        /* Bit 3 = Too Large
        Any decoded codepoint greater than U+10FFFF
        e.g.    11110_100 10_010000 10_000000 10_000000 */
        static final byte TOO_LARGE = 1 << 3;

        /* Bit 4 = Surrogate
        code points in the range of U+D800 - U+DFFF (inclusive) are the surrogates for UTF-16.
        These 2048 code points that are reserved for UTF-16 are disallowed in UTF-8
        e.g.    1110_1101 10_100000 10_000000 */
        static final byte SURROGATE = 1 << 4;

        /* Bit 5 = Overlong 2-byte
        first valid two byte sequence: 110_00010 10_000000
        anything smaller is considered overlong as it would fit into a one byte sequence / ASCII */
        static final byte OVERLONG_2BYTE = 1 << 5;

        /* Bit 6 = Too Large 1000
         Similar to TOO_LARGE, but for cases where the continuation byte's high nibble is 1000
         e.g. 11110_101 10_000000 10_000000 */
        static final byte TOO_LARGE_1000 = 1 << 6;

        /* Bit 6 = Overlong 4-byte
         Any decoded code point below above U+FFFF / 11110_000 10_001111 10_111111 10_111111
         e.g. 11110_000 10_000000 10_000000 10_000000 */
        static final byte OVERLONG_4BYTE = 1 << 6;

        /* Bit 7 = Two Continuations
         e.g. 10_000000 10_000000 */
        static final byte TWO_CONTINUATIONS = (byte) (1 << 7);

        private final static ByteVector byte1High = getByte1HighLookup();
        private final static ByteVector byte1Low = getByte1LowLookup();
        private final static ByteVector byte2High = getByte2HighLookup();

        private static ByteVector getByte1HighLookup() {
            byte[] byte1HighArray = new byte[]{
                    /* ASCII high nibble = 0000 -> 0111, ie 0 -> 7 index in lookup table */
                    TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG,
                    /* Continuation high nibble = 1000 -> 1011 */
                    TWO_CONTINUATIONS, TWO_CONTINUATIONS, TWO_CONTINUATIONS, TWO_CONTINUATIONS,
                    /* Two byte lead high nibble = 1100 -> 1101 */
                    TOO_SHORT | OVERLONG_2BYTE, TOO_SHORT,
                    /* Three byte lead high nibble = 1110 */
                    TOO_SHORT | OVERLONG_3BYTE | SURROGATE,
                    /* Four byte lead high nibble = 1111 */
                    TOO_SHORT | TOO_LARGE | TOO_LARGE_1000 | OVERLONG_4BYTE
            };

            return alignArrayToVector(byte1HighArray);
        }

        private static ByteVector alignArrayToVector(byte[] arrayValues) {
            // pad array with zeroes to align up with vector size
            byte[] alignedArray = new byte[VECTOR_SPECIES.vectorByteSize()];
            System.arraycopy(arrayValues, 0, alignedArray, 0, arrayValues.length);
            return ByteVector.fromArray(VECTOR_SPECIES, alignedArray, 0);
        }

        private static ByteVector getByte1LowLookup() {
            final byte CARRY = TOO_SHORT | TOO_LONG | TWO_CONTINUATIONS;
            byte[] byte1LowArray = new byte[]{
                    /* ASCII, two Byte lead and three byte lead low nibble = 0000 -> 1111,
                    *  Four byte lead low nibble = 0000 -> 0111
                    * Continuation byte low nibble is inconsequential
                    * Low nibble does not affect the states TOO_SHORT, TOO_LONG, TWO_CONTINUATIONS, so they will be carried over regardless */
                    CARRY | OVERLONG_2BYTE | OVERLONG_3BYTE | OVERLONG_4BYTE,
                    // 0001
                    CARRY | OVERLONG_2BYTE,
                    CARRY,
                    CARRY,
                    // 1111_0100 -> 1111 = TOO_LARGE range
                    CARRY | TOO_LARGE,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    // 1110_1101
                    CARRY | TOO_LARGE | TOO_LARGE_1000 | SURROGATE,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000
            };

            return alignArrayToVector(byte1LowArray);
        }

        private static ByteVector getByte2HighLookup() {
            byte[] byte2HighArray = new byte[]{
                    // ASCII high nibble = 0000 -> 0111, ie 0 -> 7 index in lookup table
                    TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,
                    // Continuation high nibble - 1000 -> 1011
                    TOO_LONG | TWO_CONTINUATIONS | OVERLONG_2BYTE | OVERLONG_3BYTE | OVERLONG_4BYTE | TOO_LARGE_1000,
                    TOO_LONG | TWO_CONTINUATIONS | OVERLONG_2BYTE | OVERLONG_3BYTE | TOO_LARGE,
                    TOO_LONG | TWO_CONTINUATIONS | OVERLONG_2BYTE | SURROGATE | TOO_LARGE,
                    TOO_LONG | TWO_CONTINUATIONS | OVERLONG_2BYTE | SURROGATE | TOO_LARGE,
                    // 1100 -> 1111 = unexpected lead byte
                    TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT
            };

            return alignArrayToVector(byte2HighArray);
        }
    }
}
