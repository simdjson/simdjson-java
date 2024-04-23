package org.simdjson;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.simdjson.schemas.RecordWithBooleanField;
import org.simdjson.schemas.RecordWithCharacterArrayField;
import org.simdjson.schemas.RecordWithCharacterField;
import org.simdjson.schemas.RecordWithCharacterListField;
import org.simdjson.schemas.RecordWithIntegerField;
import org.simdjson.schemas.RecordWithPrimitiveBooleanField;
import org.simdjson.schemas.RecordWithPrimitiveCharacterArrayField;
import org.simdjson.schemas.RecordWithPrimitiveCharacterField;
import org.simdjson.schemas.RecordWithPrimitiveIntegerField;
import org.simdjson.schemas.RecordWithStringArrayField;
import org.simdjson.schemas.RecordWithStringField;
import org.simdjson.schemas.RecordWithStringListField;
import org.simdjson.testutils.MapEntry;
import org.simdjson.testutils.MapSource;
import org.simdjson.testutils.RandomStringSource;
import org.simdjson.testutils.SchemaBasedRandomValueSource;
import org.simdjson.testutils.StringTestData;

import java.util.List;

import static org.apache.commons.text.StringEscapeUtils.unescapeJava;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.simdjson.TestUtils.padWithSpaces;
import static org.simdjson.TestUtils.toUtf8;

public class StringSchemaBasedParsingTest {

    @Test
    public void emptyStringAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"\"");

        // when
        String string = parser.parse(json, json.length, String.class);

        // then
        assertThat(string).isEqualTo("");
    }

    @ParameterizedTest
    @RandomStringSource
    public void stringAtRoot(String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + expected + "\"");

        // when
        String string = parser.parse(json, json.length, String.class);

        // then
        assertThat(string).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "1"})
    public void typeOtherThanStringAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected either string or 'null'.");
    }

    @Test
    public void nullAtRootWhenStringIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        String string = parser.parse(json, json.length, String.class);

        // then
        assertThat(string).isNull();
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = Integer.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = char.class, value = "String cannot be deserialized to a char. Expected a single-character string."),
            @MapEntry(classKey = Character.class, value = "String cannot be deserialized to a char. Expected a single-character string."),
            @MapEntry(classKey = int.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = Boolean.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'.")
    })
    public void mismatchedTypeForStringAsRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"abc\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"abc\",", "\"abc\"def"})
    public void moreValuesThanOneStringAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String.class));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @Test
    public void emptyStringAtObjectField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": \"\"}");

        // when
        RecordWithStringField object = parser.parse(json, json.length, RecordWithStringField.class);

        // then
        assertThat(object.field()).isEqualTo("");
    }

    @ParameterizedTest
    @RandomStringSource
    public void stringAtObjectField(String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": \"" + expected + "\"}");

        // when
        RecordWithStringField object = parser.parse(json, json.length, RecordWithStringField.class);

        // then
        assertThat(object.field()).isEqualTo(expected);
    }

    @Test
    public void nullAtObjectFieldWhenStringIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        RecordWithStringField object = parser.parse(json, json.length, RecordWithStringField.class);

        // then
        assertThat(object.field()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "1"})
    public void typeOtherThanStringAtObjectField(String value) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + value + "}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithStringField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 10. Expected either string or 'null'.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = RecordWithPrimitiveCharacterField.class, value = "String cannot be deserialized to a char. Expected a single-character string."),
            @MapEntry(classKey = RecordWithCharacterField.class, value = "String cannot be deserialized to a char. Expected a single-character string."),
            @MapEntry(classKey = RecordWithPrimitiveIntegerField.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = RecordWithBooleanField.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'.")
    })
    public void mismatchedTypeForStringAtObjectField(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": \"abc\"}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @Test
    public void usableUnicodeCharactersAsString() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.usableEscapedUnicodeCharacters();

        for (String character : characters) {
            try {
                byte[] json = toUtf8("\"" + character + "\"");

                // when
                String value = parser.parse(json, json.length, String.class);

                // then
                assertThat(value).isEqualTo(unescapeJava(character));
            } catch (Throwable e) {
                fail("Failed for character: " + character, e);
            }
        }
    }

    @Test
    public void unicodeCharactersReservedForLowSurrogateAsString() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> codePoints = StringTestData.escapedLowSurrogates();

        for (String codePoint : codePoints) {
            try {
                byte[] json = toUtf8("\"" + codePoint + "\"");

                // when
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

                // then
                assertThat(ex)
                        .hasMessage("Invalid code point. The range U+DC00–U+DFFF is reserved for low surrogate.");
            } catch (Throwable e) {
                fail("Failed for code point: " + codePoint, e);
            }
        }
    }

    @ParameterizedTest
    @RandomStringSource(maxChars = 1)
    public void characterAtRoot(String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + expected + "\"");

        // when
        Character character = parser.parse(json, json.length, Character.class);

        // then
        assertThat(character)
                .isEqualTo(StringEscapeUtils.unescapeJava(expected).charAt(0));
    }

    @ParameterizedTest
    @RandomStringSource(maxChars = 1)
    public void primitiveCharAtRoot(String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + expected + "\"");

        // when
        char character = parser.parse(json, json.length, char.class);

        // then
        assertThat(character)
                .isEqualTo(StringEscapeUtils.unescapeJava(expected).charAt(0));
    }

    @Test
    public void nullAtRootWhenCharacterIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        Character character = parser.parse(json, json.length, Character.class);

        // then
        assertThat(character).isNull();
    }

    @Test
    public void nullAtRootWhenPrimitiveCharacterIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected string.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "1"})
    public void typeOtherThanCharacterAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Character.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected either string or 'null'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "1"})
    public void typeOtherThanPrimitiveCharacterAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected string.");
    }

    @ParameterizedTest
    @RandomStringSource(maxChars = 1)
    public void characterAtObjectField(String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": \"" + expected + "\"}");

        // when
        RecordWithCharacterField object = parser.parse(json, json.length, RecordWithCharacterField.class);

        // then
        assertThat(object.field())
                .isEqualTo(StringEscapeUtils.unescapeJava(expected).charAt(0));
    }

    @Test
    public void nullAtObjectFieldWhenCharacterIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        RecordWithCharacterField object = parser.parse(json, json.length, RecordWithCharacterField.class);

        // then
        assertThat(object.field()).isNull();
    }

    @ParameterizedTest
    @RandomStringSource(maxChars = 1)
    public void primitiveCharAtObjectField(String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": \"" + expected + "\"}");

        // when
        RecordWithPrimitiveCharacterField object = parser.parse(json, json.length, RecordWithPrimitiveCharacterField.class);

        // then
        assertThat(object.field())
                .isEqualTo(StringEscapeUtils.unescapeJava(expected).charAt(0));
    }

    @Test
    public void nullAtObjectFieldWhenPrimitiveCharacterIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveCharacterField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 10. Expected string.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "1"})
    public void typeOtherThanCharacterAtObjectField(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithCharacterField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 10. Expected either string or 'null'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "1"})
    public void typeOtherThanPrimitiveCharacterAtObjectField(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveCharacterField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 10. Expected string.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\"", "\"a"})
    public void missingQuotationMarkForCharacter(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Character.class));

        // then
        assertThat(ex)
                .hasMessage("Unclosed string. A string is opened, but never closed.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\"", "\"a"})
    public void missingQuotationMarkForPrimitiveCharacter(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessage("Unclosed string. A string is opened, but never closed.");
    }

    @Test
    public void missingQuotationMarksForCharacterAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("a");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Character.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected either string or 'null'.");
    }

    @Test
    public void missingQuotationMarksForPrimitiveCharacterAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("a");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected string.");
    }

    @Test
    public void missingQuotationMarksForCharacterAtObjectField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": a}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithCharacterField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 10. Expected either string or 'null'.");
    }

    @Test
    public void missingQuotationMarksForPrimitiveCharacterAtObjectField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": a}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveCharacterField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 10. Expected string.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"ab\"", "\"\\u0024\\u0023\""})
    public void stringLongerThanOneCharacterWhenCharacterIsExpected(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Character.class));

        // then
        assertThat(ex)
                .hasMessage("String cannot be deserialized to a char. Expected a single-character string.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"ab\"", "\"\\u0024\\u0023\""})
    public void stringLongerThanOneCharacterWhenPrimitiveCharacterIsExpected(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessage("String cannot be deserialized to a char. Expected a single-character string.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\\"", "\\\\", "\\/", "\\b", "\\f", "\\n", "\\r", "\\t"})
    public void twoCharacterEscapeSequenceAsPrimitiveCharacter(String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + expected + "\"");

        // when
        char character = parser.parse(json, json.length, char.class);

        // then
        assertThat(character).isEqualTo(unescapeJava(expected).charAt(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\\"", "\\\\", "\\/", "\\b", "\\f", "\\n", "\\r", "\\t"})
    public void twoCharacterEscapeSequenceAsCharacter(String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + expected + "\"");

        // when
        Character character = parser.parse(json, json.length, Character.class);

        // then
        assertThat(character).isEqualTo(unescapeJava(expected).charAt(0));
    }

    @ParameterizedTest
    @ValueSource(classes = {Character.class, char.class})
    public void restrictedEscapedSingleCodeUnit(Class<?> expectedClass) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.reservedEscapedSingleCodeUnitCharacters();

        for (String expected : characters) {
            try {
                byte[] json = toUtf8("\"" + expected + "\"");

                // when
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedClass));

                // then
                assertThat(ex)
                        .hasMessage("Invalid code point. Should be within the range U+0000–U+D777 or U+E000–U+FFFF.");
            } catch (Throwable e) {
                fail("Failed for character: " + expected, e);
            }
        }
    }

    @Test
    public void usableEscapedSingleCodeUnitAsPrimitiveCharacter() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.usableEscapedSingleCodeUnitCharacters();

        for (String expected : characters) {
            try {
                byte[] json = toUtf8("\"" + expected + "\"");

                // when
                char character = parser.parse(json, json.length, char.class);

                // then
                assertThat(character).isEqualTo(unescapeJava(expected).charAt(0));
            } catch (Throwable e) {
                fail("Failed for character: " + expected, e);
            }
        }
    }

    @Test
    public void usableEscapedSingleCodeUnitAsCharacter() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.usableEscapedSingleCodeUnitCharacters();

        for (String expected : characters) {
            try {
                byte[] json = toUtf8("\"" + expected + "\"");

                // when
                Character character = parser.parse(json, json.length, Character.class);

                // then
                assertThat(character).isEqualTo(unescapeJava(expected).charAt(0));
            } catch (Throwable e) {
                fail("Failed for character: " + expected, e);
            }
        }
    }

    @Test
    public void usableSingleCodeUnitAsCharacter() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.usableSingleCodeUnitCharacters();

        for (String expected : characters) {
            try {
                byte[] json = toUtf8("\"" + expected + "\"");

                // when
                Character character = parser.parse(json, json.length, Character.class);

                // then
                assertThat(character).isEqualTo(expected.charAt(0));
            } catch (Throwable e) {
                fail("Failed for character: " + expected, e);
            }
        }
    }

    @Test
    public void usableSingleCodeUnitAsPrimitiveCharacter() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.usableSingleCodeUnitCharacters();

        for (String expected : characters) {
            try {
                byte[] json = toUtf8("\"" + expected + "\"");

                // when
                char character = parser.parse(json, json.length, char.class);

                // then
                assertThat(character).isEqualTo(expected.charAt(0));
            } catch (Throwable e) {
                fail("Failed for character: " + expected, e);
            }
        }
    }

    @Test
    public void usableTwoCodeUnitsAsPrimitiveCharacter() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.usableTwoCodeUnitsCharacters();

        for (String expected : characters) {
            try {
                byte[] json = toUtf8("\"" + expected + "\"");

                // when
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

                // then
                assertThat(ex)
                        .hasMessage("String cannot be deserialized to a char. Expected a single 16-bit code unit character.");
            } catch (Throwable e) {
                fail("Failed for character: " + expected, e);
            }
        }
    }

    @Test
    public void usableTwoCodeUnitsAsCharacter() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.usableTwoCodeUnitsCharacters();

        for (String expected : characters) {
            try {
                byte[] json = toUtf8("\"" + expected + "\"");

                // when
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Character.class));

                // then
                assertThat(ex)
                        .hasMessage("String cannot be deserialized to a char. Expected a single 16-bit code unit character.");
            } catch (Throwable e) {
                fail("Failed for character: " + expected, e);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"a\",", "\"a\"b"})
    public void moreValuesThanOnePrimitiveCharacterAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"a\",", "\"a\"b"})
    public void moreValuesThanOneCharacterAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Character.class));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\uD8001", "\\uD800\\1", "\\uD800u", "\\uD800\\e", "\\uD800\\DC00", "\\uD800"})
    public void invalidLowSurrogateEscape(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + input + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String.class));

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
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String.class));

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
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String.class));

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
    public void invalidUnicodeAsString(String invalidCharacter) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + invalidCharacter + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid unicode escape sequence.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\u", "\\u1", "\\u12", "\\u123"})
    public void invalidUnicodeAsPrimitiveCharacter(String invalidCharacter) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + invalidCharacter + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid unicode escape sequence.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\u", "\\u1", "\\u12", "\\u123"})
    public void invalidUnicodeAsCharacter(String invalidCharacter) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + invalidCharacter + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Character.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid unicode escape sequence.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\g", "\\ą"})
    public void invalidEscapeAsString(String escapedCharacter) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + escapedCharacter + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String.class));

        // then
        assertThat(ex).hasMessageStartingWith("Escaped unexpected character: ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\g", "\\ą"})
    public void invalidEscapeAsPrimitiveCharacter(String escapedCharacter) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + escapedCharacter + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessageStartingWith("Escaped unexpected character: ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\g", "\\ą"})
    public void invalidEscapeAsCharacter(String escapedCharacter) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + escapedCharacter + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessageStartingWith("Escaped unexpected character: ");
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
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String.class));

                // then
                assertThat(ex)
                        .hasMessage("Unescaped characters. Within strings, there are characters that should be escaped.");
            } catch (Throwable e) {
                fail("Failed for character: " + character, e);
            }
        }
    }

    @Test
    public void unescapedControlCharacterAsPrimitiveCharacter() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.unescapedControlCharacters();

        for (String character : characters) {
            try {
                byte[] json = toUtf8("\"" + character + "\"");

                // when
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

                // then
                assertThat(ex)
                        .hasMessage("Unescaped characters. Within strings, there are characters that should be escaped.");
            } catch (Throwable e) {
                fail("Failed for character: " + character, e);
            }
        }
    }

    @Test
    public void unescapedControlCharacterAsCharacter() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        List<String> characters = StringTestData.unescapedControlCharacters();

        for (String character : characters) {
            try {
                byte[] json = toUtf8("\"" + character + "\"");

                // when
                JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Character.class));

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
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String.class));

        // then
        assertThat(ex)
                .hasMessageStartingWith("Unclosed string. A string is opened, but never closed.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"", "\\"})
    public void unescapedSpecialStringCharacterAsPrimitiveCharacter(String character) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + character + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char.class));

        // then
        assertThat(ex)
                .hasMessageStartingWith("Unclosed string. A string is opened, but never closed.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"", "\\"})
    public void unescapedSpecialStringCharacterAsCharacter(String character) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + character + "\"");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Character.class));

        // then
        assertThat(ex)
                .hasMessageStartingWith("Unclosed string. A string is opened, but never closed.");
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = String[].class, nulls = false)
    public void arrayOfStringsAtRoot(String jsonStr, String[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        String[] array = parser.parse(json, json.length, String[].class);

        // then
        assertThat(array).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = String[].class, nulls = true)
    public void arrayOfStringsAndNullsAtRoot(String jsonStr, String[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        String[] array = parser.parse(json, json.length, String[].class);

        // then
        assertThat(array).containsExactly(expected);
    }

    @Test
    public void arrayOfStringsMixedWithOtherTypesAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"abc\", \"ab\\\\c\", 1]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, String[].class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 17. Expected either string or 'null'.");
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = String[].class, nulls = false)
    public void objectWithArrayOfStrings(String jsonStr, String[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithStringArrayField object = parser.parse(json, json.length, RecordWithStringArrayField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = String[].class, nulls = false)
    public void objectWithListOfStrings(String jsonStr, String[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithStringListField object = parser.parse(json, json.length, RecordWithStringListField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = String[].class, nulls = true)
    public void objectWithListOfStringsAndNulls(String jsonStr, String[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithStringListField object = parser.parse(json, json.length, RecordWithStringListField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Character[].class, nulls = false)
    public void arrayOfCharactersAtRoot(String jsonStr, Character[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Character[] array = parser.parse(json, json.length, Character[].class);

        // then
        assertThat(array).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = char[].class, nulls = false)
    public void arrayOfPrimitiveCharactersAtRoot(String jsonStr, char[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        char[] array = parser.parse(json, json.length, char[].class);

        // then
        assertThat(array).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Character[].class, nulls = true)
    public void arrayOfCharsAndNullsAtRoot(String jsonStr, Character[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Character[] array = parser.parse(json, json.length, Character[].class);

        // then
        assertThat(array).containsExactly(expected);
    }

    @Test
    public void arrayOfPrimitiveCharactersAndNullsAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"a\", \"b\", null]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, char[].class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 11. Expected string.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = char[].class, value = "Invalid value starting at 11. Expected string."),
            @MapEntry(classKey = Character[].class, value = "Invalid value starting at 11. Expected either string or 'null'.")
    })
    public void arrayOfCharactersMixedWithOtherTypesAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"a\", \"b\", 1]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Character[].class, nulls = false)
    public void objectWithArrayOfCharacters(String jsonStr, Character[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithCharacterArrayField object = parser.parse(json, json.length, RecordWithCharacterArrayField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = char[].class, nulls = false)
    public void objectWithArrayOfPrimitiveCharacters(String jsonStr, char[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithPrimitiveCharacterArrayField object = parser.parse(json, json.length, RecordWithPrimitiveCharacterArrayField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Character[].class, nulls = false)
    public void objectWithListOfCharacters(String jsonStr, Character[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithCharacterListField object = parser.parse(json, json.length, RecordWithCharacterListField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Character[].class, nulls = true)
    public void objectWithListOfCharactersAndNulls(String jsonStr, Character[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithCharacterListField object = parser.parse(json, json.length, RecordWithCharacterListField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = int[].class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = int.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = boolean.class, value = "Unrecognized boolean value. Expected: 'true' or 'false'."),
            @MapEntry(classKey = Boolean.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'."),
            @MapEntry(classKey = String[][].class, value = "Expected '[' but got: '\"'.")
    })
    public void mismatchedTypeForArrayOfStringsAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"abc\", \"ab\\\\c\"]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = int[].class, value = "Expected '[' but got: '{'."),
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = RecordWithIntegerField.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = RecordWithPrimitiveBooleanField.class, value = "Unrecognized boolean value. Expected: 'true' or 'false'."),
            @MapEntry(classKey = RecordWithStringField.class, value = "Invalid value starting at 10. Expected either string or 'null'."),
            @MapEntry(classKey = String[].class, value = "Expected '[' but got: '{'."),
            @MapEntry(classKey = String[][].class, value = "Expected '[' but got: '{'.")
    })
    public void mismatchedTypeForArrayOfStringsAtObjectField(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [\"abc\", \"ab\\\\c\"]}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @Test
    public void missingStringField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"intField\": 1}");

        // when
        RecordWithStringField object = parser.parse(json, json.length, RecordWithStringField.class);

        // then
        assertThat(object.field()).isNull();
    }

    @Test
    public void missingCharacterField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"intField\": 1}");

        // when
        RecordWithCharacterField object = parser.parse(json, json.length, RecordWithCharacterField.class);

        // then
        assertThat(object.field()).isNull();
    }

    @Test
    public void missingPrimitiveCharacterField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"intField\": 1}");

        // when
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveCharacterField.class)
        );

        // then
        assertThat(ex.getCause()).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {char.class, Character.class, String.class})
    public void emptyJson(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("No structural element found.");
    }

    @ParameterizedTest
    @ValueSource(classes = {Character.class, String.class})
    public void passedLengthSmallerThanNullLength(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(padWithSpaces("null"));

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'null'.");
    }

    @Test
    public void passedLengthSmallerThanStringLength() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(padWithSpaces("\"aaaaa\""));

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3, String.class));

        // then
        assertThat(ex)
                .hasMessage("Unclosed string. A string is opened, but never closed.");
    }
}
