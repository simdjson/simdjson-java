package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.List;

import static java.lang.Character.MAX_CODE_POINT;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;
import static java.util.stream.IntStream.rangeClosed;
import static org.apache.commons.text.StringEscapeUtils.unescapeJava;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.JsonValueAssert.assertThat;
import static org.simdjson.TestUtils.toUtf8;

public class StringParsingTest {

    @Test
    public void usableUnicodeCharacters() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> unicodeCharacters =  rangeClosed(0, MAX_CODE_POINT)
                .filter(Character::isDefined)
                .filter(codePoint -> !isReservedCodePoint(codePoint))
                .mapToObj(StringParsingTest::toUnicodeEscape)
                .toList();

        for (String input : unicodeCharacters) {
            byte[] json = toUtf8("\"" + input + "\"");

            // when
            JsonValue value = parser.parse(json, json.length);

            // then
            assertThat(value).isEqualTo(unescapeJava(input));
        }
    }

    @Test
    public void unicodeCharactersReservedForLowSurrogate() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> unicodeCharacters = rangeClosed(0xDC00, 0xDFFF)
                .mapToObj(StringParsingTest::toUnicodeEscape)
                .toList();

        for (String input : unicodeCharacters) {
            byte[] json = toUtf8("\"" + input + "\"");

            // when
            JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

            // then
            assertThat(ex.getMessage()).isEqualTo("Invalid code point. The range U+DC00–U+DFFF is reserved for low surrogate.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\uD8001", "\\uD800\\1", "\\uD800u", "\\uD800\\e", "\\uD800\\DC00"})
    public void invalidLowSurrogateEscape(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + input + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Low surrogate should start with '\\u'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\uD800\\u"})
    public void missingLowSurrogate(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + input + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid code point. Low surrogate should be in the range U+DC00–U+DFFF.");
    }

    @Test
    public void invalidLowSurrogateRange() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> unicodeCharacters = rangeClosed(0x0000, 0xFFFF)
                .filter(lowSurrogate -> lowSurrogate < 0xDC00 || lowSurrogate > 0xDFFF)
                .mapToObj(lowSurrogate -> String.format("\\uD800\\u%04X", lowSurrogate))
                .toList();

        for (String input : unicodeCharacters) {
            byte[] json = toUtf8("\"" + input + "\"");

            // when
            JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

            // then
            assertThat(ex.getMessage()).isEqualTo("Invalid code point. Low surrogate should be in the range U+DC00–U+DFFF.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\u", "\\u1", "\\u12", "\\u123"})
    public void invalidUnicode(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + input + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid unicode escape sequence.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\g", "\\ą"})
    public void invalidEscape(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"" + jsonStr + "\"]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).startsWith("Escaped unexpected character: ");
    }

    @Test
    public void longString() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isTrue();
        assertThat(it.next()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(it.hasNext()).isFalse();
    }

    private static String toUnicodeEscape(int codePoint) {
        if (isBmpCodePoint(codePoint)) {
            return String.format("\\u%04X", codePoint);
        } else {
            return String.format("\\u%04X\\u%04X",
                    (int) Character.highSurrogate(codePoint), (int) lowSurrogate(codePoint));
        }
    }

    private static boolean isReservedCodePoint(int codePoint) {
        return codePoint >= 0xD800 && codePoint <= 0xDFFF;
    }
}
