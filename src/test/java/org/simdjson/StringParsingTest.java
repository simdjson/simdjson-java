package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.simdjson.testutils.RandomStringSource;
import org.simdjson.testutils.StringTestData;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static org.apache.commons.text.StringEscapeUtils.unescapeJava;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.simdjson.testutils.SimdJsonAssertions.assertThat;
import static org.simdjson.testutils.TestUtils.loadTestFile;
import static org.simdjson.testutils.TestUtils.toUtf8;

public class StringParsingTest {

    @ParameterizedTest
    @RandomStringSource
    public void stringAtRoot(String jsonStr, String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + jsonStr + "\"");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"abc\",", "\"abc\"def"})
    public void moreValuesThanOneStringAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @Test
    public void usableUnicodeCharacters() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.usableEscapedUnicodeCharacters();

        for (String character : characters) {
            try {
                byte[] json = toUtf8("\"" + character + "\"");

                // when
                JsonValue value = parser.parse(json, json.length);

                // then
                assertThat(value).isEqualTo(unescapeJava(character));
            } catch (Throwable e) {
                fail("Failed for character: " + character, e);
            }
        }
    }

    @Test
    public void unicodeCharactersReservedForLowSurrogate() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> unicodeCharacters = StringTestData.escapedLowSurrogates();

        for (String character : unicodeCharacters) {
            try {
                byte[] json = toUtf8("\"" + character + "\"");

                // when
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

                // then
                assertThat(ex)
                        .hasMessage("Invalid code point. The range U+DC00–U+DFFF is reserved for low surrogate.");
            } catch (Throwable e) {
                fail("Failed for character: " + character, e);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\uD8001", "\\uD800\\1", "\\uD800u", "\\uD800\\e", "\\uD800\\DC00", "\\uD800"})
    public void invalidLowSurrogateEscape(String invalidCharacter) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + invalidCharacter + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Low surrogate should start with '\\u'");
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
        assertThat(ex)
                .hasMessage("Invalid code point. Low surrogate should be in the range U+DC00–U+DFFF.");
    }

    @Test
    public void invalidLowSurrogateRange() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> unicodeCharacters = StringTestData.escapedUnicodeCharactersWithInvalidLowSurrogate();

        for (String character : unicodeCharacters) {
            try {
                byte[] json = toUtf8("\"" + character + "\"");

                // when
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

                // then
                assertThat(ex)
                        .hasMessage("Invalid code point. Low surrogate should be in the range U+DC00–U+DFFF.");
            } catch (Throwable e) {
                fail("Failed for character: " + character, e);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\u", "\\u1", "\\u12", "\\u123"})
    public void invalidUnicode(String invalidCharacter) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + invalidCharacter + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Invalid unicode escape sequence.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\g", "\\ą"})
    public void invalidEscape(String invalidCharacter) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"" + invalidCharacter + "\"]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessageStartingWith("Escaped unexpected character: ");
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

    @ParameterizedTest
    @ValueSource(strings = {"/wide_bench.json", "/deep_bench.json"})
    public void issue26(String file) throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = loadTestFile(file);

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isObject()).isTrue();
    }

    @Test
    public void unescapedControlCharacterAsString() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.unescapedControlCharacters();

        for (String character : characters) {
            try {
                byte[] json = toUtf8("\"" + character + "\"");

                // when
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

                // then
                assertThat(ex)
                        .hasMessage("Unescaped characters. Within strings, there are characters that should be escaped.");
            } catch (Throwable e) {
                fail("Failed for character: " + character, e);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"", "\\"})
    public void unescapedSpecialStringCharacterAsString(String character) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + character + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessageStartingWith("Unclosed string. A string is opened, but never closed.");
    }

    @Test
    public void arrayOfStrings() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"abc\", \"ab\\\\c\"]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isTrue();
        assertThat(it.next()).isEqualTo("abc");
        assertThat(it.next()).isEqualTo("ab\\c");
        assertThat(it.hasNext()).isFalse();
    }

    @Test
    public void passedLengthSmallerThanStringLength() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"aaaaa\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 6));

        // then
        assertThat(ex)
                .hasMessage("Unclosed string. A string is opened, but never closed.");
    }
}
