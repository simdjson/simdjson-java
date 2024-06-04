package org.simdjson;

import org.junit.jupiter.api.Test;
import org.simdjson.testutils.TestUtils;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.testutils.TestUtils.toHexString;
import static org.simdjson.testutils.Utf8TestData.randomUtf8ByteArray;
import static org.simdjson.testutils.Utf8TestData.randomUtf8ByteArrayIncluding;
import static org.simdjson.testutils.Utf8TestData.randomUtf8ByteArrayEndedWith;
import static org.simdjson.testutils.Utf8TestData.utf8Sequences;

public class Utf8ValidationTest {

    @Test
    public void valid() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArray();

        try {
            // when
            parser.parse(input, input.length);
        } catch (JsonParsingException ex) {
            // then
            assertThat(ex)
                    .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                    .hasMessageNotContaining("The input is not valid UTF-8");
        }
    }

    @Test
    public void invalidAscii() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        for (int invalidAsciiByte = 128; invalidAsciiByte <= 255; invalidAsciiByte++) {
            byte[] input = randomUtf8ByteArrayIncluding((byte) invalidAsciiByte);

            // when
            JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

            // then
            assertThat(ex)
                    .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                    .hasMessage("The input is not valid UTF-8");
        }
    }

    @Test
    public void continuationByteWithoutPrecedingLeadingByte() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        for (int continuationByte = 0b10_000000; continuationByte <= 0b10_111111; continuationByte++) {
            byte[] input = randomUtf8ByteArrayIncluding((byte) continuationByte);

            // when
            JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

            // then
            assertThat(ex)
                    .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                    .hasMessage("The input is not valid UTF-8");
        }
    }

    @Test
    public void twoByteSequenceWithTwoContinuationBytes() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayIncluding(
                (byte) 0b110_00010,
                (byte) 0b10_000000,
                (byte) 0b10_000000
        );

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void twoByteSequenceWithoutContinuationBytes() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayIncluding((byte) 0b110_00010);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void twoByteSequenceWithoutContinuationBytesAtTheEnd() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayEndedWith((byte) 0b110_00010);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void threeByteSequenceWithThreeContinuationBytes() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayIncluding(
                (byte) 0b1110_0000,
                (byte) 0b10_100000,
                (byte) 0b10_000000,
                (byte) 0b10_000000
        );

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void threeByteSequenceWithOneContinuationByte() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayIncluding(
                (byte) 0b1110_0000,
                (byte) 0b10_100000
        );

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void threeByteSequenceWithoutContinuationBytes() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayIncluding((byte) 0b1110_0000);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void threeByteSequenceWithOneContinuationByteAtTheEnd() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayEndedWith(
                (byte) 0b1110_0000,
                (byte) 0b10_100000
        );

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void threeByteSequenceWithoutContinuationBytesAtTheEnd() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayEndedWith((byte) 0b1110_0000);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void fourByteSequenceWithFourContinuationBytes() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayIncluding(
                (byte) 0b11110_000,
                (byte) 0b10_010000,
                (byte) 0b10_000000,
                (byte) 0b10_000000,
                (byte) 0b10_000000
        );

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void fourByteSequenceWithTwoContinuationBytes() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayIncluding(
                (byte) 0b11110_000,
                (byte) 0b10_010000,
                (byte) 0b10_000000
        );

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void fourByteSequenceWithOneContinuationByte() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayIncluding(
                (byte) 0b11110_000,
                (byte) 0b10_010000
        );

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void fourByteSequenceWithoutContinuationBytes() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayIncluding((byte) 0b11110_000);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void fourByteSequenceWithTwoContinuationBytesAtTheEnd() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayEndedWith(
                (byte) 0b11110_000,
                (byte) 0b10_010000,
                (byte) 0b10_000000
        );

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void fourByteSequenceWithOneContinuationByteAtTheEnd() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayEndedWith(
                (byte) 0b11110_000,
                (byte) 0b10_010000
        );

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void fourByteSequenceWithoutContinuationBytesAtTheEnd() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = randomUtf8ByteArrayEndedWith((byte) 0b11110_000);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .overridingErrorMessage("Failed for input: %s.", toHexString(input))
                .hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void overlongTwoByteSequence() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<byte[]> sequences = utf8Sequences(0x0000, 0x007F, 2);

        for (byte[] sequence : sequences) {
            byte[] input = randomUtf8ByteArrayIncluding(sequence);

            // when
            JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

            // then
            assertThat(ex)
                    .overridingErrorMessage("Failed for sequence: %s and input: %s.", toHexString(sequence), toHexString(input))
                    .hasMessage("The input is not valid UTF-8");
        }
    }

    @Test
    public void overlongThreeByteSequence() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<byte[]> sequences = utf8Sequences(0x0000, 0x07FF, 3);

        for (byte[] sequence : sequences) {
            byte[] input = randomUtf8ByteArrayIncluding(sequence);

            // when
            JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

            // then
            assertThat(ex)
                    .overridingErrorMessage("Failed for sequence: %s and input: %s.", toHexString(sequence), toHexString(input))
                    .hasMessage("The input is not valid UTF-8");
        }
    }

    @Test
    public void surrogateCodePoints() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<byte[]> sequences = utf8Sequences(0xD800, 0xDFFF, 3);

        for (byte[] sequence : sequences) {
            byte[] input = randomUtf8ByteArrayIncluding(sequence);

            // when
            JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

            // then
            assertThat(ex)
                    .overridingErrorMessage("Failed for sequence: %s and input: %s.", toHexString(sequence), toHexString(input))
                    .hasMessage("The input is not valid UTF-8");
        }
    }

    @Test
    public void overlongFourByteSequence() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<byte[]> sequences = utf8Sequences(0x0000, 0xFFFF, 4);

        for (byte[] sequence : sequences) {
            byte[] input = randomUtf8ByteArrayIncluding(sequence);

            // when
            JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

            // then
            assertThat(ex)
                    .overridingErrorMessage("Failed for sequence: %s and input: %s.", toHexString(sequence), toHexString(input))
                    .hasMessage("The input is not valid UTF-8");
        }
    }

    @Test
    public void tooLargeFourByteSequence() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<byte[]> sequences = utf8Sequences(0x110000, 0x110400, 4);

        for (byte[] sequence : sequences) {
            byte[] input = randomUtf8ByteArrayIncluding(sequence);

            // when
            JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

            // then
            assertThat(ex)
                    .overridingErrorMessage("Failed for sequence: %s and input: %s.", toHexString(sequence), toHexString(input))
                    .hasMessage("The input is not valid UTF-8");
        }
    }

    @Test
    public void validTestFile() throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = TestUtils.loadTestFile("/nhkworld.json");

        // when / then
        assertThatCode(() -> parser.parse(input, input.length)).doesNotThrowAnyException();
    }

    @Test
    public void invalidTestFile() throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] input = TestUtils.loadTestFile("/malformed.txt");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(input, input.length));

        // then
        assertThat(ex)
                .hasMessage("The input is not valid UTF-8");
    }
}
