package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.StringUtils.toUtf8;

public class SimdJsonParserTest {

    @Test
    public void testEmptyArray() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        while (it.hasNext()) {
            fail("Unexpected value");
            it.next();
        }
    }

    @Test
    public void testEmptyObject() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{}");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isObject()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        while (it.hasNext()) {
            fail("Unexpected field");
            it.next();
        }
    }

    @Test
    public void testArrayIterator() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1, 2, 3]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        int[] expectedValues = new int[]{1, 2, 3};
        int counter = 0;
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        while (it.hasNext()) {
            JsonValue element = it.next();
            assertThat(element.isLong()).isTrue();
            assertThat(element.asLong()).isEqualTo(expectedValues[counter]);
            counter++;
        }
        assertThat(counter).isEqualTo(expectedValues.length);
    }

    @Test
    public void testObjectIterator() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": 1, \"b\": 2, \"c\": 3}");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isObject()).isTrue();
        String[] expectedKeys = new String[]{"a", "b", "c"};
        int[] expectedValue = new int[]{1, 2, 3};
        int counter = 0;
        Iterator<Map.Entry<CharSequence, JsonValue>> it = jsonValue.objectIterator();
        while (it.hasNext()) {
            Map.Entry<CharSequence, JsonValue> field = it.next();
            CharSequence key = field.getKey();
            assertString(key, expectedKeys[counter]);
            assertLong(field.getValue(), expectedValue[counter]);
            counter++;
        }
        assertThat(counter).isEqualTo(expectedKeys.length);
    }

    @Test
    public void testBooleanValues() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[true, false]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isTrue();
        assertBoolean(it.next(), true);
        assertBoolean(it.next(), false);
        assertThat(it.hasNext()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testBooleanValuesAsRoot(boolean booleanVal) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(Boolean.toString(booleanVal));

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertBoolean(jsonValue, booleanVal);
    }

    @Test
    public void testNullValue() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[null]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isTrue();
        JsonValue element = it.next();
        assertThat(element.isNull()).isTrue();
        assertThat(it.hasNext()).isFalse();
    }

    @Test
    public void testNullValueAsRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isNull()).isTrue();
    }

    @Test
    public void testStringValues() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"abc\", \"ab\\\\c\"]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isTrue();
        assertString(it.next(), "abc");
        assertString(it.next(), "ab\\c");
        assertThat(it.hasNext()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "ą"})
    public void testStringValuesAsRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"" + jsonStr + "\"");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertString(jsonValue, jsonStr);
    }

    @Test
    public void testNumericValues() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[0, 1, -1, 1.1]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isTrue();
        assertLong(it.next(), 0);
        assertLong(it.next(), 1);
        assertLong(it.next(), -1);
        assertDouble(it.next(), "1.1");
        assertThat(it.hasNext()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "-1"})
    public void testLongValuesAsRoot(String longStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(longStr);

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertLong(jsonValue, Long.parseLong(longStr));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1", "-1.1", "1e1", "1E1", "-1e1", "-1E1", "1e-1", "1E-1", "1.1e1", "1.1E1"})
    public void testDoubleValuesAsRoot(String doubleStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(doubleStr);

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertDouble(jsonValue, doubleStr);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true,", "false,", "null,", "1,", "\"abc\",", "1.1,"})
    public void testInvalidPrimitivesAsRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage())
                .isEqualTo("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[n]", "{\"a\":n}"})
    public void testInvalidNull(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid value starting at " + jsonStr.indexOf('n') + ". Expected 'null'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[f]", "{\"a\":f}"})
    public void testInvalidFalse(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid value starting at " + jsonStr.indexOf('f') + ". Expected 'false'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[t]", "{\"a\":t}"})
    public void testInvalidTrue(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid value starting at " + jsonStr.indexOf('t') + ". Expected 'true'.");
    }

    @Test
    public void testUnicodeString() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"\\u005C\"]");

        // when
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Support for unicode characters is not implemented yet.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\g", "\\ą"})
    public void testInvalidEscape(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"" + jsonStr + "\"]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).startsWith("Escaped unexpected character: ");
    }

    @Test
    public void testLongString() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isTrue();
        assertString(it.next(), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(it.hasNext()).isFalse();
    }

    @Test
    public void testArraySize() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1, 2, 3]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        assertThat(jsonValue.getSize()).isEqualTo(3);
    }

    @Test
    public void testObjectSize() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"1\":1,\"2\":1,\"3\":1}");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isObject()).isTrue();
        assertThat(jsonValue.getSize()).isEqualTo(3);
    }

    @Test
    public void testLargeArraySize() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        int realArraySize = 0xFFFFFF + 1;
        byte[] json = new byte[realArraySize * 2 - 1 + 2];
        json[0] = '[';
        int i = 0;
        while (i < realArraySize) {
            json[i * 2 + 1] = (byte) '0';
            json[i * 2 + 2] = (byte) ',';
            i++;
        }
        json[json.length - 1] = ']';

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        assertThat(jsonValue.getSize()).isEqualTo(0xFFFFFF);
    }

    private static void assertString(JsonValue actual, String expected) {
        assertThat(actual.isString()).isTrue();
        assertThat(actual.asString()).isEqualTo(expected);
        assertString(actual.asCharSequence(), expected);
    }

    private static void assertString(CharSequence actual, String expected) {
        byte[] bytesExpected = expected.getBytes(UTF_8);
        assertThat(actual.length()).isEqualTo(bytesExpected.length);
        for (int i = 0; i < actual.length(); i++) {
            assertThat((byte) actual.charAt(i)).isEqualTo(bytesExpected[i]);
        }
    }

    private static void assertBoolean(JsonValue actual, boolean expected) {
        assertThat(actual.isBoolean()).isTrue();
        assertThat(actual.asBoolean()).isEqualTo(expected);
    }

    private static void assertLong(JsonValue actual, long expected) {
        assertThat(actual.isLong()).isTrue();
        assertThat(actual.asLong()).isEqualTo(expected);
    }

    private static void assertDouble(JsonValue actual, String str) {
        assertThat(actual.isDouble()).isTrue();
        assertThat(actual.asDouble()).isEqualTo(Double.valueOf(str));
    }
}
