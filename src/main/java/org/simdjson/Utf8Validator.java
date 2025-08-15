package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorShuffle;

import java.util.Arrays;

import static jdk.incubator.vector.VectorOperators.EQ;
import static jdk.incubator.vector.VectorOperators.LSHL;
import static jdk.incubator.vector.VectorOperators.LSHR;
import static jdk.incubator.vector.VectorOperators.NE;
import static jdk.incubator.vector.VectorOperators.UGE;
import static jdk.incubator.vector.VectorOperators.UGT;
import static jdk.incubator.vector.VectorShuffle.iota;
import static org.simdjson.VectorUtils.BYTE_SPECIES;
import static org.simdjson.VectorUtils.INT_SPECIES;

class Utf8Validator {

    // Leading byte not followed by a continuation byte but by another leading or ASCII byte, e.g. 11______ 0_______, 11______ 11______
    private static final byte TOO_SHORT = 1;
    // ASCII followed by continuation byte e.g. 01111111 10_000000.
    private static final byte TOO_LONG = 1 << 1;
    // Any 3-byte sequence that could be represented by a shorter sequence (any sequence smaller than 1110_0000 10_100000 10_000000).
    private static final byte OVERLONG_3BYTE = 1 << 2;
    // Any decoded code point greater than U+10FFFF. e.g. 11110_100 10_010000 10_000000 10_000000.
    private static final byte TOO_LARGE = 1 << 3;
    // Code points in the range of U+D800 - U+DFFF (inclusive) are the surrogates for UTF-16.
    // These 2048 code points that are reserved for UTF-16 are disallowed in UTF-8, e.g. 1110_1101 10_100000 10_000000.
    private static final byte SURROGATE = 1 << 4;
    // First valid 2-byte sequence: 110_00010 10_000000. Anything smaller is considered overlong as it fits into a 1-byte sequence.
    private static final byte OVERLONG_2BYTE = 1 << 5;
    // Similar to TOO_LARGE, but for cases where the continuation byte's high nibble is 1000, e.g. 11110_101 10_000000 10_000000.
    private static final byte TOO_LARGE_1000 = 1 << 6;
    // Any decoded code point below above U+FFFF, e.g. 11110_000 10_000000 10_000000 10_000000.
    private static final byte OVERLONG_4BYTE = 1 << 6;
    // An example: 10_000000 10_000000.
    private static final byte TWO_CONTINUATIONS = (byte) (1 << 7);
    private static final byte MAX_2_LEADING_BYTE = (byte) 0b110_11111;
    private static final byte MAX_3_LEADING_BYTE = (byte) 0b1110_1111;
    private static final int TWO_BYTES_SIZE = Byte.SIZE * 2;
    private static final int THREE_BYTES_SIZE = Byte.SIZE * 3;
    private static final ByteVector BYTE_1_HIGH_LOOKUP = createByte1HighLookup();
    private static final ByteVector BYTE_1_LOW_LOOKUP = createByte1LowLookup();
    private static final ByteVector BYTE_2_HIGH_LOOKUP = createByte2HighLookup();
    private static final ByteVector INCOMPLETE_CHECK = createIncompleteCheck();
    private static final byte LOW_NIBBLE_MASK = 0b0000_1111;
    private static final byte ALL_ASCII_MASK = (byte) 0b1000_0000;
    private static final VectorShuffle<Integer> FOUR_BYTES_FORWARD_SHIFT = iota(INT_SPECIES, INT_SPECIES.elementSize() - 1, 1, true);
    private static final int STEP_SIZE = BYTE_SPECIES.vectorByteSize();

    static void validate(byte[] buffer, int length) {
        long previousIncomplete = 0;
        long errors = 0;
        int previousFourUtf8Bytes = 0;

        int loopBound = BYTE_SPECIES.loopBound(length);
        int offset = 0;
        for (; offset < loopBound; offset += STEP_SIZE) {
            ByteVector chunk = ByteVector.fromArray(BYTE_SPECIES, buffer, offset);
            IntVector chunkAsInts = chunk.reinterpretAsInts();
            // ASCII fast path can bypass the checks that are only required for multibyte code points.
            if (chunk.and(ALL_ASCII_MASK).compare(EQ, 0).allTrue()) {
                errors |= previousIncomplete;
            } else {
                previousIncomplete = chunk.compare(UGE, INCOMPLETE_CHECK).toLong();
                // Shift the input forward by four bytes to make space for the previous four bytes.
                // The previous three bytes are required for validation, pulling in the last integer
                // will give the previous four bytes. The switch to integer vectors is to allow for
                // integer shifting instead of the more expensive shuffle / slice operations.
                IntVector chunkWithPreviousFourBytes = chunkAsInts
                        .rearrange(FOUR_BYTES_FORWARD_SHIFT)
                        .withLane(0, previousFourUtf8Bytes);
                // Shift the current input forward by one byte to include one byte from the previous chunk.
                ByteVector previousOneByte = chunkAsInts
                        .lanewise(LSHL, Byte.SIZE)
                        .or(chunkWithPreviousFourBytes.lanewise(LSHR, THREE_BYTES_SIZE))
                        .reinterpretAsBytes();
                ByteVector byte2HighNibbles = chunkAsInts.lanewise(LSHR, 4)
                        .reinterpretAsBytes()
                        .and(LOW_NIBBLE_MASK);
                ByteVector byte1HighNibbles = previousOneByte.reinterpretAsInts()
                        .lanewise(LSHR, 4)
                        .reinterpretAsBytes()
                        .and(LOW_NIBBLE_MASK);
                ByteVector byte1LowNibbles = previousOneByte.and(LOW_NIBBLE_MASK);
                ByteVector byte1HighState = byte1HighNibbles.selectFrom(BYTE_1_HIGH_LOOKUP);
                ByteVector byte1LowState = byte1LowNibbles.selectFrom(BYTE_1_LOW_LOOKUP);
                ByteVector byte2HighState = byte2HighNibbles.selectFrom(BYTE_2_HIGH_LOOKUP);
                ByteVector firstCheck = byte1HighState.and(byte1LowState).and(byte2HighState);
                // All remaining checks are for invalid 3 and 4-byte sequences, which either have too many
                // continuation bytes or not enough.
                ByteVector previousTwoBytes = chunkAsInts
                        .lanewise(LSHL, TWO_BYTES_SIZE)
                        .or(chunkWithPreviousFourBytes.lanewise(LSHR, TWO_BYTES_SIZE))
                        .reinterpretAsBytes();
                // The minimum leading byte of 3-byte sequences is always greater than the maximum leading byte of 2-byte sequences.
                VectorMask<Byte> is3ByteLead = previousTwoBytes.compare(UGT, MAX_2_LEADING_BYTE);
                ByteVector previousThreeBytes = chunkAsInts
                        .lanewise(LSHL, THREE_BYTES_SIZE)
                        .or(chunkWithPreviousFourBytes.lanewise(LSHR, Byte.SIZE))
                        .reinterpretAsBytes();
                // The minimum leading byte of 4-byte sequences is always greater than the maximum leading byte of 3-byte sequences.
                VectorMask<Byte> is4ByteLead = previousThreeBytes.compare(UGT, MAX_3_LEADING_BYTE);
                // The firstCheck vector contains 0x80 values on continuation byte indexes.
                // The leading bytes of 3 and 4-byte sequences should match up with these indexes and zero them out.
                ByteVector secondCheck = firstCheck.add((byte) 0x80, is3ByteLead.or(is4ByteLead));
                errors |= secondCheck.compare(NE, 0).toLong();
            }
            previousFourUtf8Bytes = chunkAsInts.lane(INT_SPECIES.length() - 1);
        }

        // If the input file doesn't align with the vector width, pad the missing bytes with zeros.
        VectorMask<Byte> remainingBytes = BYTE_SPECIES.indexInRange(offset, length);
        ByteVector chunk = ByteVector.fromArray(BYTE_SPECIES, buffer, offset, remainingBytes);
        if (!chunk.and(ALL_ASCII_MASK).compare(EQ, 0).allTrue()) {
            IntVector chunkAsInts = chunk.reinterpretAsInts();
            previousIncomplete = chunk.compare(UGE, INCOMPLETE_CHECK).toLong();
            // Shift the input forward by four bytes to make space for the previous four bytes.
            // The previous three bytes are required for validation, pulling in the last integer
            // will give the previous four bytes. The switch to integer vectors is to allow for
            // integer shifting instead of the more expensive shuffle / slice operations.
            IntVector chunkWithPreviousFourBytes = chunkAsInts
                    .rearrange(FOUR_BYTES_FORWARD_SHIFT)
                    .withLane(0, previousFourUtf8Bytes);
            // Shift the current input forward by one byte to include one byte from the previous chunk.
            ByteVector previousOneByte = chunkAsInts
                    .lanewise(LSHL, Byte.SIZE)
                    .or(chunkWithPreviousFourBytes.lanewise(LSHR, THREE_BYTES_SIZE))
                    .reinterpretAsBytes();
            ByteVector byte2HighNibbles = chunkAsInts.lanewise(LSHR, 4)
                    .reinterpretAsBytes()
                    .and(LOW_NIBBLE_MASK);
            ByteVector byte1HighNibbles = previousOneByte.reinterpretAsInts()
                    .lanewise(LSHR, 4)
                    .reinterpretAsBytes()
                    .and(LOW_NIBBLE_MASK);
            ByteVector byte1LowNibbles = previousOneByte.and(LOW_NIBBLE_MASK);
            ByteVector byte1HighState = byte1HighNibbles.selectFrom(BYTE_1_HIGH_LOOKUP);
            ByteVector byte1LowState = byte1LowNibbles.selectFrom(BYTE_1_LOW_LOOKUP);
            ByteVector byte2HighState = byte2HighNibbles.selectFrom(BYTE_2_HIGH_LOOKUP);
            ByteVector firstCheck = byte1HighState.and(byte1LowState).and(byte2HighState);
            // All remaining checks are for invalid 3 and 4-byte sequences, which either have too many
            // continuation bytes or not enough.
            ByteVector previousTwoBytes = chunkAsInts
                    .lanewise(LSHL, TWO_BYTES_SIZE)
                    .or(chunkWithPreviousFourBytes.lanewise(LSHR, TWO_BYTES_SIZE))
                    .reinterpretAsBytes();
            // The minimum leading byte of 3-byte sequences is always greater than the maximum leading byte of 2-byte sequences.
            VectorMask<Byte> is3ByteLead = previousTwoBytes.compare(UGT, MAX_2_LEADING_BYTE);
            ByteVector previousThreeBytes = chunkAsInts
                    .lanewise(LSHL, THREE_BYTES_SIZE)
                    .or(chunkWithPreviousFourBytes.lanewise(LSHR, Byte.SIZE))
                    .reinterpretAsBytes();
            // The minimum leading byte of 4-byte sequences is always greater than the maximum leading byte of 3-byte sequences.
            VectorMask<Byte> is4ByteLead = previousThreeBytes.compare(UGT, MAX_3_LEADING_BYTE);
            // The firstCheck vector contains 0x80 values on continuation byte indexes.
            // The leading bytes of 3 and 4-byte sequences should match up with these indexes and zero them out.
            ByteVector secondCheck = firstCheck.add((byte) 0x80, is3ByteLead.or(is4ByteLead));
            errors |= secondCheck.compare(NE, 0).toLong();
        }

        if ((errors | previousIncomplete) != 0) {
            throw new JsonParsingException("The input is not valid UTF-8");
        }
    }

    private static ByteVector createIncompleteCheck() {
        // Previous vector is in an incomplete state if the last byte is smaller than 0xC0,
        // or the second last byte is smaller than 0xE0, or the third last byte is smaller than 0xF0.
        int vectorByteSize = BYTE_SPECIES.vectorByteSize();
        byte[] eofArray = new byte[vectorByteSize];
        Arrays.fill(eofArray, (byte) 255);
        eofArray[vectorByteSize - 3] = (byte) 0xF0;
        eofArray[vectorByteSize - 2] = (byte) 0xE0;
        eofArray[vectorByteSize - 1] = (byte) 0xC0;
        return ByteVector.fromArray(BYTE_SPECIES, eofArray, 0);
    }

    private static ByteVector createByte1HighLookup() {
        byte[] byte1HighArray = new byte[]{
                // ASCII high nibble = 0000 -> 0111, ie 0 -> 7 index in lookup table
                TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG,
                // Continuation high nibble = 1000 -> 1011
                TWO_CONTINUATIONS, TWO_CONTINUATIONS, TWO_CONTINUATIONS, TWO_CONTINUATIONS,
                // Two byte lead high nibble = 1100 -> 1101
                TOO_SHORT | OVERLONG_2BYTE, TOO_SHORT,
                // Three byte lead high nibble = 1110
                TOO_SHORT | OVERLONG_3BYTE | SURROGATE,
                // Four byte lead high nibble = 1111
                TOO_SHORT | TOO_LARGE | TOO_LARGE_1000 | OVERLONG_4BYTE
        };
        return alignArrayToVector(byte1HighArray);
    }

    private static ByteVector createByte1LowLookup() {
        final byte CARRY = TOO_SHORT | TOO_LONG | TWO_CONTINUATIONS;
        byte[] byte1LowArray = new byte[]{
                // ASCII, two byte lead and three byte leading low nibble = 0000 -> 1111,
                // Four byte lead low nibble = 0000 -> 0111.
                // Continuation byte low nibble is inconsequential
                // Low nibble does not affect the states TOO_SHORT, TOO_LONG, TWO_CONTINUATIONS, so they will
                // be carried over regardless.
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

    private static ByteVector createByte2HighLookup() {
        byte[] byte2HighArray = new byte[]{
                // ASCII high nibble = 0000 -> 0111, ie 0 -> 7 index in lookup table
                TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,
                // Continuation high nibble - 1000 -> 1011
                TOO_LONG | TWO_CONTINUATIONS | OVERLONG_2BYTE | OVERLONG_3BYTE | OVERLONG_4BYTE | TOO_LARGE_1000,
                TOO_LONG | TWO_CONTINUATIONS | OVERLONG_2BYTE | OVERLONG_3BYTE | TOO_LARGE,
                TOO_LONG | TWO_CONTINUATIONS | OVERLONG_2BYTE | SURROGATE | TOO_LARGE,
                TOO_LONG | TWO_CONTINUATIONS | OVERLONG_2BYTE | SURROGATE | TOO_LARGE,
                // 1100 -> 1111 = unexpected leading byte
                TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT
        };
        return alignArrayToVector(byte2HighArray);
    }

    private static ByteVector alignArrayToVector(byte[] arrayValues) {
        // Pad array with zeroes to align up with vector size.
        byte[] alignedArray = new byte[BYTE_SPECIES.vectorByteSize()];
        System.arraycopy(arrayValues, 0, alignedArray, 0, arrayValues.length);
        return ByteVector.fromArray(BYTE_SPECIES, alignedArray, 0);
    }
}
