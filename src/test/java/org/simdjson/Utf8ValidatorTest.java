package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

class Utf8ValidatorTest {
    private static final VectorSpecies<Byte> VECTOR_SPECIES = StructuralIndexer.SPECIES;


    /* ASCII / 1 BYTE TESTS */

    @Test
    void validate_allEightBitValues_invalidAscii() {
        byte[] invalidAscii = new byte[128];

        int index = 0;
        for (int eightBitVal = 255; eightBitVal >= 128; eightBitVal--) {
            invalidAscii[index++] = (byte) eightBitVal;
        }

        SimdJsonParser parser = new SimdJsonParser();
        for (int i = 0; i < 128; i += VECTOR_SPECIES.vectorByteSize()) {
            byte[] vectorChunk = Arrays.copyOfRange(invalidAscii, i, i + VECTOR_SPECIES.vectorByteSize());

            assertThatExceptionOfType(JsonParsingException.class)
                    .isThrownBy(() -> parser.parse(vectorChunk, vectorChunk.length))
                    .withMessage("Invalid UTF8");
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

        SimdJsonParser parser = new SimdJsonParser();
        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.length()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());

            assertThatExceptionOfType(JsonParsingException.class)
                    .isThrownBy(() -> parser.parse(vectorChunk, vectorChunk.length))
                    .withMessage("Invalid UTF8");
        }
    }

    @Test
    void validate_extraContinuationByte_2Byte_invalid() {
        byte[] inputBytes = new byte[3];
        inputBytes[0] = (byte) 0b110_00010;
        inputBytes[1] = (byte) 0b10_000000;
        inputBytes[2] = (byte) 0b10_000000; // two byte lead should only have one continuation byte

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationOneByteTooShort_2Byte_invalid() {
        byte[] inputBytes = new byte[1];
        inputBytes[0] = (byte) 0b110_00010;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_extraContinuationByte_3Byte_invalid() {
        byte[] inputBytes = new byte[4];
        inputBytes[0] = (byte) 0b1110_0000;
        inputBytes[1] = (byte) 0b10_100000;
        inputBytes[2] = (byte) 0b10_000000;
        inputBytes[3] = (byte) 0b10_000000; // three byte lead should only have two continuation bytes

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationOneByteTooShort_3Byte_invalid() {
        byte[] inputBytes = new byte[2];
        inputBytes[0] = (byte) 0b1110_0000;
        inputBytes[1] = (byte) 0b10_100000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationTwoBytesTooShort_3Byte_invalid() {
        byte[] inputBytes = new byte[1];
        inputBytes[0] = (byte) 0b1110_0000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_extraContinuationByte_4Byte_invalid() {
        byte[] inputBytes = new byte[5];
        inputBytes[0] = (byte) 0b11110_000;
        inputBytes[1] = (byte) 0b10_010000;
        inputBytes[2] = (byte) 0b10_000000;
        inputBytes[3] = (byte) 0b10_000000;
        inputBytes[4] = (byte) 0b10_000000; // four byte lead should only have three continuation bytes

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationOneByteTooShort_4Byte_invalid() {
        byte[] inputBytes = new byte[3];
        inputBytes[0] = (byte) 0b11110_000;
        inputBytes[1] = (byte) 0b10_010000;
        inputBytes[2] = (byte) 0b10_000000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationTwoBytesTooShort_4Byte_invalid() {
        byte[] inputBytes = new byte[2];
        inputBytes[0] = (byte) 0b11110_000;
        inputBytes[1] = (byte) 0b10_010000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationThreeBytesTooShort_4Byte_invalid() {
        byte[] inputBytes = new byte[1];
        inputBytes[0] = (byte) 0b11110_000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }


    /* 2 BYTE / LATIN TESTS */

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

        SimdJsonParser parser = new SimdJsonParser();
        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.length()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());

            assertThatExceptionOfType(JsonParsingException.class)
                    .isThrownBy(() -> parser.parse(vectorChunk, vectorChunk.length))
                    .withMessage("Invalid UTF8");
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

        SimdJsonParser parser = new SimdJsonParser();
        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.length()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());

            assertThatExceptionOfType(JsonParsingException.class)
                    .isThrownBy(() -> parser.parse(vectorChunk, vectorChunk.length))
                    .withMessage("Invalid UTF8");
        }
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

        SimdJsonParser parser = new SimdJsonParser();
        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.vectorByteSize()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());

            assertThatExceptionOfType(JsonParsingException.class)
                    .isThrownBy(() -> parser.parse(vectorChunk, vectorChunk.length))
                    .withMessage("Invalid UTF8");
        }
    }


    /* 4 BYTE / Supplementary TESTS */

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

        SimdJsonParser parser = new SimdJsonParser();
        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.vectorByteSize()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());

            assertThatExceptionOfType(JsonParsingException.class)
                    .isThrownBy(() -> parser.parse(vectorChunk, vectorChunk.length))
                    .withMessage("Invalid UTF8");
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

        SimdJsonParser parser = new SimdJsonParser();
        for (int i = 0; i < inputBytes.length; i += VECTOR_SPECIES.vectorByteSize()) {
            byte[] vectorChunk = Arrays.copyOfRange(inputBytes, i, i + VECTOR_SPECIES.vectorByteSize());

            assertThatExceptionOfType(JsonParsingException.class)
                    .isThrownBy(() -> parser.parse(vectorChunk, vectorChunk.length))
                    .withMessage("Invalid UTF8");
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

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationOneByteTooShort_3Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 2] = (byte) 0b1110_0000;
        inputBytes[vectorBytes - 1] = (byte) 0b10_100000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationTwoBytesTooShort_3Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 1] = (byte) 0b1110_0000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationOneByteTooShort_4Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 3] = (byte) 0b11110_000;
        inputBytes[vectorBytes - 2] = (byte) 0b10_010000;
        inputBytes[vectorBytes - 1] = (byte) 0b10_000000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationTwoBytesTooShort_4Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 2] = (byte) 0b11110_000;
        inputBytes[vectorBytes - 1] = (byte) 0b10_010000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }

    @Test
    void validate_continuationThreeBytesTooShort_4Byte_eof_invalid() {
        int vectorBytes = VECTOR_SPECIES.vectorByteSize();
        byte[] inputBytes = new byte[vectorBytes];
        inputBytes[vectorBytes - 1] = (byte) 0b11110_000;

        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }


    /* file tests */

    @ParameterizedTest
    @ValueSource(strings = {"/twitter.json", "/nhkworld.json"})
    void validate_utf8InputFiles_valid(String inputFilePath) throws IOException {
        byte[] inputBytes = TestUtils.loadTestFile(inputFilePath);
        SimdJsonParser parser = new SimdJsonParser();
        assertThatCode(() -> parser.parse(inputBytes, inputBytes.length)).doesNotThrowAnyException();
    }

    @Test
    void validate_utf8InputFile_invalid() throws IOException {
        byte[] inputBytes = TestUtils.loadTestFile("/malformed.txt");
        SimdJsonParser parser = new SimdJsonParser();
        assertThatExceptionOfType(JsonParsingException.class)
                .isThrownBy(() -> parser.parse(inputBytes, inputBytes.length))
                .withMessage("Invalid UTF8");
    }
}