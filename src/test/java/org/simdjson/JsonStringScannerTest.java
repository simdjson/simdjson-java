package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simdjson.TestUtils.chunk;
import static org.simdjson.TestUtils.padWithSpaces;

public class JsonStringScannerTest {

    @Test
    public void testUnquotedString() {
        // given
        JsonStringScanner stringScanner = new JsonStringScanner();
        String str = padWithSpaces("abc 123");

        // when
        JsonStringBlock block = next(stringScanner, str);

        // then
        assertThat(block.quote()).isEqualTo(0);
    }

    @Test
    public void testQuotedString() {
        // given
        JsonStringScanner stringScanner = new JsonStringScanner();
        String str = padWithSpaces("\"abc 123\"");

        // when
        JsonStringBlock block = next(stringScanner, str);

        // then
        assertThat(block.quote()).isEqualTo(0x101);
    }

    @Test
    public void testStartingQuotes() {
        // given
        JsonStringScanner stringScanner = new JsonStringScanner();
        String str = padWithSpaces("\"abc 123");

        // when
        JsonStringBlock block = next(stringScanner, str);

        // then
        assertThat(block.quote()).isEqualTo(0x1);
    }

    @Test
    public void testQuotedStringSpanningMultipleBlocks() {
        // given
        JsonStringScanner stringScanner = new JsonStringScanner();
        String str0 = "abc \"a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 b0 b1 b2 b3 b4 b5 b6 b7 b8 b9";
        String str1 = " c0 c1 c2 c3 c4 c5 c6 c7 c8 c9 d0 d1 d2 d3 d4 d5 d6 d7 d8 d\" def";

        // when
        JsonStringBlock firstBlock = next(stringScanner, str0);
        JsonStringBlock secondBlock = next(stringScanner, str1);

        // then
        assertThat(firstBlock.quote()).isEqualTo(0x10);
        assertThat(secondBlock.quote()).isEqualTo(0x800000000000000L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc \\\"123",    // abc \"123
            "abc \\\\\\\"123" // abc \\\"123
    })
    public void testEscapedQuote(String str) {
        // given
        JsonStringScanner stringScanner = new JsonStringScanner();
        String padded = padWithSpaces(str);

        // when
        JsonStringBlock block = next(stringScanner, padded);

        // then
        assertThat(block.quote()).isEqualTo(0);
    }

    @Test
    public void testEscapedQuoteSpanningMultipleBlocks() {
        // given
        JsonStringScanner stringScanner = new JsonStringScanner();
        String str0 = "a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 b0 b1 b2 b3 b4 b5 b6 b7 b8 b9 c0 \\";
        String str1 = padWithSpaces("\"def");

        // when
        JsonStringBlock firstBlock = next(stringScanner, str0);
        JsonStringBlock secondBlock = next(stringScanner, str1);

        // then
        assertThat(firstBlock.quote()).isEqualTo(0);
        assertThat(secondBlock.quote()).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc \\\\\"123",    // abc \\"123
            "abc \\\\\\\\\"123" // abc \\\\"123
    })
    public void testUnescapedQuote(String str) {
        // given
        JsonStringScanner stringScanner = new JsonStringScanner();
        String padded = padWithSpaces(str);

        // when
        JsonStringBlock block = next(stringScanner, padded);

        // then
        assertThat(block.quote()).isEqualTo(0x1L << str.indexOf('"'));
    }

    @Test
    public void testUnescapedQuoteSpanningMultipleBlocks() {
        // given
        JsonStringScanner stringScanner = new JsonStringScanner();
        String str0 = padWithSpaces("a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 b0 b1 b2 b3 b4 b5 b6 b7 b8 b9 c0 \\");
        String str1 = padWithSpaces("\\\"abc");

        // when
        JsonStringBlock firstBlock = next(stringScanner, str0);
        JsonStringBlock secondBlock = next(stringScanner, str1);

        // then
        assertThat(firstBlock.quote()).isEqualTo(0);
        assertThat(secondBlock.quote()).isEqualTo(0x2);
    }

    private JsonStringBlock next(JsonStringScanner scanner, String str) {
        return switch (StructuralIndexer.N_CHUNKS) {
            case 1 -> scanner.next(chunk(str, 0));
            case 2 -> scanner.next(chunk(str, 0), chunk(str, 1));
            case 4 -> scanner.next(chunk(str, 0), chunk(str, 1), chunk(str, 2), chunk(str, 3));
            default -> throw new RuntimeException("Unsupported chunk count: " + StructuralIndexer.N_CHUNKS);
        };
    }
}
