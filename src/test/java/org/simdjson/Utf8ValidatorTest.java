package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class Utf8ValidatorTest {
    private static final VectorSpecies<Byte> VECTOR_SPECIES = ByteVector.SPECIES_256;

    @Test
    void isAscii_true() {
        byte[] bytes = new byte[]{'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'm', 'n', 'o', 'p', 'q'};
        VectorMask<Byte> nonZeroBytes = VECTOR_SPECIES.indexInRange(0, bytes.length);

        assertTrue(Utf8Validator.isAscii(ByteVector.fromArray(VECTOR_SPECIES, bytes, 0, nonZeroBytes)));
    }

    @Test
    void isAscii_false() {
        byte[] bytes = new byte[]{'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'm', 'n', 'o', 'p', (byte) 0b1_0000000};
        VectorMask<Byte> nonZeroBytes = VECTOR_SPECIES.indexInRange(0, bytes.length);

        assertFalse(Utf8Validator.isAscii(ByteVector.fromArray(VECTOR_SPECIES, bytes, 0, nonZeroBytes)));
    }

    /* ASCII / 1 BYTE TESTS */

    @Test
    void validate_allSevenBitValues_validAscii() {
        byte[] allValidAscii = new byte[128];
        for (int i = 0; i < 128; i++) {
            allValidAscii[i] = (byte) i;
        }

        assertDoesNotThrow(() -> Utf8Validator.validate(allValidAscii));
    }

    @Test
    void validate_allEightBitValues_invalidAscii() {
        byte[] invalidAscii = new byte[128];

        int index = 0;
        for (int eightBitVal = 255; eightBitVal >= 128; eightBitVal--) {
            invalidAscii[index++] = (byte) eightBitVal;
        }

        for (int i = 0; i < 128; i += VECTOR_SPECIES.vectorByteSize()) {
            byte[] vectorChunk = Arrays.copyOfRange(invalidAscii, i, i + VECTOR_SPECIES.vectorByteSize());
            assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(vectorChunk));
        }
    }


    /* CONTINUATION BYTE TESTS */

    // continuation byte is never valid without a preceding leader byte
    @Test
    void validate_continuationByteOutOfOrder_invalid() {
        byte minContinuationByte = (byte) 0b10_000000;
        byte maxContinuationByte = (byte) 0b10_111111;
        byte[] inputBytes = new byte[64];
        int index = 0;

        byte continuationByte = minContinuationByte;
        while (continuationByte <= maxContinuationByte) {
            inputBytes[index++] = continuationByte;
            continuationByte++;
        }


        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.length()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());
            assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(vectorChunk));
        }
    }

    @Test
    void validate_extraContinuationByte_2Byte_invalid() {
        byte[] inputBytes = new byte[3];
        inputBytes[0] = (byte) 0b110_00010;
        inputBytes[1] = (byte) 0b10_000000;
        inputBytes[2] = (byte) 0b10_000000; // two byte lead should only have one continuation byte

        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_continuationOneByteTooShort_2Byte_invalid() {
        byte[] inputBytes = new byte[1];
        inputBytes[0] = (byte) 0b110_00010;

        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_extraContinuationByte_3Byte_invalid() {
        byte[] inputBytes = new byte[4];
        inputBytes[0] = (byte) 0b1110_0000;
        inputBytes[1] = (byte) 0b10_100000;
        inputBytes[2] = (byte) 0b10_000000;
        inputBytes[3] = (byte) 0b10_000000; // three byte lead should only have two continuation bytes

        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_continuationOneByteTooShort_3Byte_invalid() {
        byte[] inputBytes = new byte[2];
        inputBytes[0] = (byte) 0b1110_0000;
        inputBytes[1] = (byte) 0b10_100000;

        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_continuationTwoBytesTooShort_3Byte_invalid() {
        byte[] inputBytes = new byte[1];
        inputBytes[0] = (byte) 0b1110_0000;

        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_extraContinuationByte_4Byte_invalid() {
        byte[] inputBytes = new byte[5];
        inputBytes[0] = (byte) 0b11110_000;
        inputBytes[1] = (byte) 0b10_010000;
        inputBytes[2] = (byte) 0b10_000000;
        inputBytes[3] = (byte) 0b10_000000;
        inputBytes[4] = (byte) 0b10_000000; // four byte lead should only have three continuation bytes

        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_continuationOneByteTooShort_4Byte_invalid() {
        byte[] inputBytes = new byte[3];
        inputBytes[0] = (byte) 0b11110_000;
        inputBytes[1] = (byte) 0b10_010000;
        inputBytes[2] = (byte) 0b10_000000;

        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_continuationTwoBytesTooShort_4Byte_invalid() {
        byte[] inputBytes = new byte[2];
        inputBytes[0] = (byte) 0b11110_000;
        inputBytes[1] = (byte) 0b10_010000;

        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_continuationThreeBytesTooShort_4Byte_invalid() {
        byte[] inputBytes = new byte[1];
        inputBytes[0] = (byte) 0b11110_000;

        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }


    /* 2 BYTE / LATIN TESTS */

    /* first valid two byte character: 110_00010 10_000000
    anything smaller is invalid as it would fit into one byte
    last valid two byte character: 110_11111 10_111111 */
    @Test
    void validate_LatinCharacters_allValid() {
        byte minLeaderByte = (byte) 0b110_00010;
        byte maxLeaderByte = (byte) 0b110_11111;
        byte minContinuationByte = (byte) 0b10_000000;
        byte maxContinuationByte = (byte) 0b10_111111;

        // headers take up 5 bits, only 11 bits remain for character code points
        // 2 to the power of 11 = 2048 - 128 (invalid code points) = 1920 valid code points
        // 1920 * 2 bytes = 3840 bytes
        byte[] inputBytes = new byte[3840];
        int index = 0;

        byte leaderByte = minLeaderByte;
        byte continuationByte = minContinuationByte;
        while (leaderByte <= maxLeaderByte) {
            inputBytes[index++] = leaderByte;
            inputBytes[index++] = continuationByte;
            if (continuationByte == maxContinuationByte) {
                leaderByte++;
                continuationByte = minContinuationByte;
            } else {
                continuationByte++;
            }
        }

        assertDoesNotThrow(() -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_overlong_2byte_invalid() {
        byte minLeaderByte = (byte) 0b110_00000;
        byte maxLeaderByte = (byte) 0b110_00001;
        byte minContinuationByte = (byte) 0b10_000000;
        byte maxContinuationByte = (byte) 0b10_111111;

        /* 7 bit code points in 2 byte utf8 is invalid
        2 to the power of 7 = 128 code points * 2 bytes = 256 bytes */
        byte[] inputBytes = new byte[256];
        int index = 0;

        byte leaderByte = minLeaderByte;
        byte continuationByte = minContinuationByte;
        while (leaderByte <= maxLeaderByte) {
            inputBytes[index++] = leaderByte;
            inputBytes[index++] = continuationByte;
            if (continuationByte == maxContinuationByte) {
                leaderByte++;
                continuationByte = minContinuationByte;
            } else {
                continuationByte++;
            }
        }

        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.length()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());
            assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(vectorChunk));
        }
    }


    /* 3 BYTE / Asiatic TESTS */

    /* first valid three byte character: 1110_0000 10_100000 10_000000
    anything smaller is invalid as it would fit into 11 bits (two byte utf8) */
    @Test
    void validate_overlong_3Byte_allInvalid() {
        byte minLeaderByte = (byte) 0b1110_0000;
        byte firstValidContinuationByte = (byte) 0b10_100000;
        byte minContinuationByte = (byte) 0b10_000000;
        byte maxContinuationByte = (byte) 0b10_111111;

        // 2 to the power of 11 = 2048 code points * 3 bytes = 6144
        byte[] inputBytes = new byte[6144];
        int index = 0;

        byte firstContinuationByte = minContinuationByte;
        byte secondContinuationByte = minContinuationByte;
        while (firstContinuationByte < firstValidContinuationByte) {
            inputBytes[index++] = minLeaderByte;
            inputBytes[index++] = firstContinuationByte;
            inputBytes[index++] = secondContinuationByte;

            if (secondContinuationByte == maxContinuationByte) {
                secondContinuationByte = minContinuationByte;
                firstContinuationByte++;
            } else {
                secondContinuationByte++;
            }
        }

        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.length()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());
            assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(vectorChunk));
        }
    }

    /* first valid three byte character: 1110_0000 10_100000 10_000000
    last valid three byte character in first sector: 1110_1101 10_011111 10_111111
    2 to the power of 16 = 65536
    2 to the power of 14 = 16384
    2 to the power of 11 = 2048

    65536 - (16384 - (2048 + 2048)) = 53248 valid code points */
    @Test
    void validate_asiaticFirstSector_allValid() {
        final byte minLeaderByte = (byte) 0b1110_0000;
        final byte maxLeaderByte = (byte) 0b1110_1101;
        final byte minContinuationByte = (byte) 0b10_000000;
        final byte maxFirstContinuationByte = (byte) 0b10_011111; // when leader byte is at max value
        final byte maxContinuationByte = (byte) 0b10_111111;

        byte leaderByte = minLeaderByte;
        byte firstContinuationByte = (byte) 0b10_100000;
        byte secondContinuationByte = minContinuationByte;

        // 53248 valid code points * 3 bytes = 159744 bytes
        byte[] inputBytes = new byte[53248 * 3];
        int index = 0;

        while (leaderByte <= maxLeaderByte) {
            inputBytes[index++] = leaderByte;
            inputBytes[index++] = firstContinuationByte;
            inputBytes[index++] = secondContinuationByte;

            if (secondContinuationByte == maxContinuationByte) {
                if ((leaderByte == maxLeaderByte && firstContinuationByte == maxFirstContinuationByte) || firstContinuationByte == maxContinuationByte) {
                    leaderByte++;
                    firstContinuationByte = minContinuationByte;
                } else {
                    firstContinuationByte++;
                }
                secondContinuationByte = minContinuationByte;
            } else {
                secondContinuationByte++;
            }
        }

        assertDoesNotThrow(() -> Utf8Validator.validate(inputBytes));
    }

    /* code points in the range of U+D800 - U+DFFF (inclusive) are the surrogates for UTF-16.
    These 2048 code points that are reserved for UTF-16 are disallowed in UTF-8
    1101 1000 0000 0000 -> 1101 1111 1111 1111 */
    @Test
    void validate_surrogateCodePoints_invalid() {
        final byte leaderByte = (byte) 0b1101_1110;
        final byte minContinuationByte = (byte) 0b10_000000;
        final byte maxContinuationByte = (byte) 0b10_111111;
        final byte minFirstContinuationByte = (byte) 0b10_100000;

        byte firstContinuationByte = minFirstContinuationByte;
        byte secondContinuationByte = minContinuationByte;

        // 2048 invalid code points * 3 bytes = 6144 bytes
        byte[] inputBytes = new byte[6144];
        int index = 0;

        while (firstContinuationByte <= maxContinuationByte) {
            inputBytes[index++] = leaderByte;
            inputBytes[index++] = firstContinuationByte;
            inputBytes[index++] = secondContinuationByte;

            if (secondContinuationByte == maxContinuationByte) {
                firstContinuationByte++;
                secondContinuationByte = minContinuationByte;
            } else {
                secondContinuationByte++;
            }
        }

        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.vectorByteSize()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());
            assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(vectorChunk));
        }
    }

    /* first valid three byte character in second sector: 1110_1110 10_000000 10_000000
   last valid three byte character: 1110_1111 10_111111 10_111111
   U+E000 - U+FFFF (inclusive) */
    @Test
    void validate_asiaticSecondSector_allValid() {
        final byte minLeaderByte = (byte) 0b1110_1110;
        final byte maxLeaderByte = (byte) 0b1110_1111;
        final byte minContinuationByte = (byte) 0b10_000000;
        final byte maxContinuationByte = (byte) 0b10_111111;

        byte leaderByte = minLeaderByte;
        byte firstContinuationByte = minContinuationByte;
        byte secondContinuationByte = minContinuationByte;

        // two to the power of 13 = 8192 valid code points * 3 bytes = 24576 bytes
        byte[] inputBytes = new byte[24576];
        int index = 0;

        while (leaderByte <= maxLeaderByte) {
            inputBytes[index++] = leaderByte;
            inputBytes[index++] = firstContinuationByte;
            inputBytes[index++] = secondContinuationByte;

            if (secondContinuationByte == maxContinuationByte) {
                if (firstContinuationByte == maxContinuationByte) {
                    leaderByte++;
                    firstContinuationByte = minContinuationByte;
                } else {
                    firstContinuationByte++;
                }
                secondContinuationByte = minContinuationByte;
            } else {
                secondContinuationByte++;
            }
        }

        assertDoesNotThrow(() -> Utf8Validator.validate(inputBytes));
    }


    /* 4 BYTE / Supplementary TESTS */

    /* first valid four byte character: 11110_000 10_010000 10_000000 10_000000
    lower values would fit into 16 bits which is only suitable for 3 byte characters (Overlong error)
    last valid four byte character: 11110_100 10_001111 10_111111 10_111111
    U+010000 - U+10FFFF (inclusive) */
    @Test
    void validate_supplementary_allValid() {
        byte minLeaderByte = (byte) 0b11110_000;
        byte maxLeaderByte = (byte) 0b11110_100;
        byte minContinuationByte = (byte) 0b10_000000;
        byte maxContinuationByte = (byte) 0b10_111111;
        byte minFirstContinuationByte = (byte) 0b10_010000;
        byte maxFirstContinuationByte = (byte) 0b10_001111; // when leader byte is at max value

        byte leaderByte = minLeaderByte;
        byte firstContinuationByte = minFirstContinuationByte;
        byte secondContinuationByte = minContinuationByte;
        byte thirdContinuationByte = minContinuationByte;

        int codePoints = 0x10FFFF - 0x010000 + 1;
        byte[] inputBytes = new byte[codePoints * 4];
        int index = 0;

        while (leaderByte <= maxLeaderByte) {
            inputBytes[index++] = leaderByte;
            inputBytes[index++] = firstContinuationByte;
            inputBytes[index++] = secondContinuationByte;
            inputBytes[index++] = thirdContinuationByte;

            if (thirdContinuationByte == maxContinuationByte) {
                if (secondContinuationByte == maxContinuationByte) {
                    if ((leaderByte == maxLeaderByte && firstContinuationByte == maxFirstContinuationByte) || (firstContinuationByte == maxContinuationByte)) {
                        leaderByte++;
                        firstContinuationByte = minContinuationByte;
                    } else {
                        firstContinuationByte++;
                    }
                    secondContinuationByte = minContinuationByte;
                } else {
                    secondContinuationByte++;
                }
                thirdContinuationByte = minContinuationByte;
            } else {
                thirdContinuationByte++;
            }
        }

        assertDoesNotThrow(() -> Utf8Validator.validate(inputBytes));
    }

    /* Overlong Test, the decoded character must be above U+FFFF / 11110_000 10_001111 10_111111 10_111111 */
    @Test
    void validate_overlong_4Byte_allInvalid() {
        byte leaderByte = (byte) 0b11110_000;
        byte minContinuationByte = (byte) 0b10_000000;
        byte maxContinuationByte = (byte) 0b10_111111;
        byte maxFirstContinuationByte = (byte) 0b10_001111;

        // 2 to the power of 16 = 65536 valid code points * 4 bytes = 262144 bytes
        byte[] inputBytes = new byte[262144];
        int index = 0;

        byte firstContinuationByte = minContinuationByte;
        byte secondContinuationByte = minContinuationByte;
        byte thirdContinuationByte = minContinuationByte;
        while (firstContinuationByte <= maxFirstContinuationByte) {
            inputBytes[index++] = leaderByte;
            inputBytes[index++] = firstContinuationByte;
            inputBytes[index++] = secondContinuationByte;
            inputBytes[index++] = thirdContinuationByte;

            if (thirdContinuationByte == maxContinuationByte) {
                if (secondContinuationByte == maxContinuationByte) {
                    firstContinuationByte++;
                    secondContinuationByte = minContinuationByte;
                } else {
                    secondContinuationByte++;
                }
                thirdContinuationByte = minContinuationByte;
            } else {
                thirdContinuationByte++;
            }
        }


        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.vectorByteSize()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());
            assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(vectorChunk));
        }
    }

    /* last valid four byte character: 11110_100 10_001111 10_111111 10_111111
     Any code point greater than U+10FFFF will result in a TOO_LARGE error */
    @Test
    void validate_tooLarge_4Byte_allInvalid() {
        byte minLeaderByte = (byte) 0b11110_100;
        byte maxLeaderByte = (byte) 0b11111_111;
        byte minContinuationByte = (byte) 0b10_000000;
        byte maxContinuationByte = (byte) 0b10_111111;
        byte minFirstContinuationByte = (byte) 0b10_010000;


        byte leaderByte = minLeaderByte;
        byte firstContinuationByte = minFirstContinuationByte;
        byte secondContinuationByte = minContinuationByte;
        byte thirdContinuationByte = minContinuationByte;

        int codePoints = 0x3FFFFF - 0x110000 + 1;
        byte[] inputBytes = new byte[codePoints * 4];
        int index = 0;

        while (leaderByte <= maxLeaderByte) {
            inputBytes[index++] = leaderByte;
            inputBytes[index++] = firstContinuationByte;
            inputBytes[index++] = secondContinuationByte;
            inputBytes[index++] = thirdContinuationByte;

            if (thirdContinuationByte == maxContinuationByte) {
                if (secondContinuationByte == maxContinuationByte) {
                    if (firstContinuationByte == maxContinuationByte) {
                        leaderByte++;
                        firstContinuationByte = minContinuationByte;
                    } else {
                        firstContinuationByte++;
                    }
                    secondContinuationByte = minContinuationByte;
                } else {
                    secondContinuationByte++;
                }
                thirdContinuationByte = minContinuationByte;
            } else {
                thirdContinuationByte++;
            }
        }


        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.vectorByteSize()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());
            assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(vectorChunk));
        }
    }

    /* check that the data stream does not terminate with an incomplete code point
     We just have to check that the last byte in the last vector is strictly smaller than 0xC0 (using an unsigned comparison)
     that the second last byte is strictly smaller than 0xE0
     the third last byte is strictly smaller than 0xF0 */
    @Test
    void validate_continuationOneByteTooShort_2Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 1] = (byte) 0b110_00010;

        ByteVector utf8Vector = ByteVector.fromArray(VECTOR_SPECIES, inputBytes, 0);
        Utf8Validator.isIncomplete(utf8Vector);
    }

    @Test
    void validate_continuationOneByteTooShort_3Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 2] = (byte) 0b1110_0000;
        inputBytes[vectorBytes - 1] = (byte) 0b10_100000;

        ByteVector utf8Vector = ByteVector.fromArray(VECTOR_SPECIES, inputBytes, 0);
        assertNotEquals(0L, Utf8Validator.isIncomplete(utf8Vector));
    }

    @Test
    void validate_continuationTwoBytesTooShort_3Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 1] = (byte) 0b1110_0000;

        ByteVector utf8Vector = ByteVector.fromArray(VECTOR_SPECIES, inputBytes, 0);
        assertNotEquals(0L, Utf8Validator.isIncomplete(utf8Vector));
    }

    @Test
    void validate_continuationOneByteTooShort_4Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 3] = (byte) 0b11110_000;
        inputBytes[vectorBytes - 2] = (byte) 0b10_010000;
        inputBytes[vectorBytes - 1] = (byte) 0b10_000000;


        ByteVector utf8Vector = ByteVector.fromArray(VECTOR_SPECIES, inputBytes, 0);
        assertNotEquals(0L, Utf8Validator.isIncomplete(utf8Vector));
    }

    @Test
    void validate_continuationTwoBytesTooShort_4Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 2] = (byte) 0b11110_000;
        inputBytes[vectorBytes - 1] = (byte) 0b10_010000;

        ByteVector utf8Vector = ByteVector.fromArray(VECTOR_SPECIES, inputBytes, 0);
        assertNotEquals(0L, Utf8Validator.isIncomplete(utf8Vector));
    }

    @Test
    void validate_continuationThreeBytesTooShort_4Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 1] = (byte) 0b11110_000;

        ByteVector utf8Vector = ByteVector.fromArray(VECTOR_SPECIES, inputBytes, 0);
        assertNotEquals(0L, Utf8Validator.isIncomplete(utf8Vector));
    }


    /* file tests */

    @ParameterizedTest
    @ValueSource(strings = {"/twitter.json", "/nhkworld.json", "/greek.txt", "/emoji-test.txt", "/amazon_cellphones.ndjson"})
    void validate_utf8InputFiles_valid(String inputFilePath) throws IOException {
        byte[] inputBytes = Objects.requireNonNull(Utf8ValidatorTest.class.getResourceAsStream(inputFilePath)).readAllBytes();
        assertDoesNotThrow(() -> Utf8Validator.validate(inputBytes));
    }

    @Test
    void validate_utf8InputFile_invalid() throws IOException {
        byte[] inputBytes = Objects.requireNonNull(Utf8ValidatorTest.class.getResourceAsStream("/malformed.txt")).readAllBytes();
        assertThrowsExactly(JsonParsingException.class, () -> Utf8Validator.validate(inputBytes));
    }
}