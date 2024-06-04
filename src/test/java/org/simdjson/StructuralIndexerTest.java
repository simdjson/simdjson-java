package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.testutils.TestUtils.toUtf8;

public class StructuralIndexerTest {

    @Test
    public void unquotedString() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);
        String input = "abc 123";

        // when
        indexer.index(toUtf8(input), len(input));

        // then
        assertThat(bitIndexes.isEnd()).isFalse();
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(0);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(4);
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    @Test
    public void quotedString() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);
        String input = "\"abc 123\"";

        // when
        indexer.index(toUtf8(input), len(input));

        // then
        assertThat(bitIndexes.isEnd()).isFalse();
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(0);
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    @Test
    public void unclosedString() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);
        String input = "\"abc 123";

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> indexer.index(toUtf8(input), len(input))
        );

        // then
        assertThat(ex)
                .hasMessage("Unclosed string. A string is opened, but never closed.");
    }

    @Test
    public void quotedStringSpanningMultipleBlocks() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);
        String input = "abc \"a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 b0 b1 b2 b3 b4 b5 b6 b7 b8 b9 c0 c1 c2 c3 c4 c5 c6 c7 c8 c9 d0 d1 d2 d3 d4 d5 d6 d7 d8 d\" def";

        // when
        indexer.index(toUtf8(input), len(input));

        // then
        assertThat(bitIndexes.isEnd()).isFalse();
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(0);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(4);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(125);
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc \\\"123",    // abc \"123
            "abc \\\\\\\"123" // abc \\\"123
    })
    public void escapedQuote(String input) {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);

        // when
        indexer.index(toUtf8(input), len(input));

        // then
        assertThat(bitIndexes.isEnd()).isFalse();
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(0);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(4);
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    @Test
    public void escapedQuoteSpanningMultipleBlocks() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);
        String input = "a0ba1ca2ca3ca4ca5ca6ca7ca8ca9cb0cb1cb2cb3cb4cb5cb6cb7cb8cb9cc0 \\\"def";

        // when
        indexer.index(toUtf8(input), len(input));

        // then
        assertThat(bitIndexes.isEnd()).isFalse();
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(0);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(63);
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc \\\\\"123",    // abc \\"123
            "abc \\\\\\\\\"123" // abc \\\\"123
    })
    public void unescapedQuote(String input) {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> indexer.index(toUtf8(input), len(input))
        );

        // then
        assertThat(ex)
                .hasMessage("Unclosed string. A string is opened, but never closed.");
    }

    @Test
    public void unescapedQuoteSpanningMultipleBlocks() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);
        String input = "a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 b0 b1 b2 b3 b4 b5 b6 b7 b8 b9 c0 \\\\\"abc";

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> indexer.index(toUtf8(input), len(input))
        );

        // then
        assertThat(ex)
                .hasMessage("Unclosed string. A string is opened, but never closed.");
    }

    @Test
    public void operatorsClassification() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);
        String input = "a{bc}1:2,3[efg]aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // when
        indexer.index(toUtf8(input), len(input));

        // then
        assertThat(bitIndexes.isEnd()).isFalse();
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(0);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(1);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(2);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(4);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(5);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(6);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(7);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(8);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(9);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(10);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(11);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(14);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(15);
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    @Test
    public void controlCharactersClassification() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);
        byte[] input = new byte[] {
                'a', 'a', 'a', 0x1a, 'a', 0x0c, 'a', 'a', // 0x1a = <SUB>, 0x0c = <FF>
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a'
        };

        // when
        indexer.index(input, input.length);

        // then
        assertThat(bitIndexes.isEnd()).isFalse();
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(0);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(3);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(4);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(5);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(6);
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    @Test
    public void whitespacesClassification() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);
        String input = "a bc\t1\n2\r3efgaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // when
        indexer.index(toUtf8(input), len(input));

        // then
        assertThat(bitIndexes.isEnd()).isFalse();
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(0);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(2);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(5);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(7);
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(9);
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "aaaaaaaaaaaaaaa", // 120 bits
            "aaaaaaaaaaaaaaaa", // 128 bits
            "aaaaaaaaaaaaaaaaa", // 136 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 248 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 256 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 264 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 504 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 512 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 520 bits
    })
    public void inputLengthCloseToVectorWidth(String input) {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);

        // when
        indexer.index(toUtf8(input), len(input));

        // then
        assertThat(bitIndexes.isEnd()).isFalse();
        assertThat(bitIndexes.getAndAdvance()).isEqualTo(0);
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    @Test
    public void emptyInput() {
        // given
        BitIndexes bitIndexes = new BitIndexes(1024);
        StructuralIndexer indexer = new StructuralIndexer(bitIndexes);

        // when
        indexer.index(toUtf8(""), 0);

        // then
        assertThat(bitIndexes.isEnd()).isTrue();
    }

    private static int len(String input) {
        return input.getBytes(UTF_8).length;
    }
}
