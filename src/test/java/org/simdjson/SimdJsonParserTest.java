package org.simdjson;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SimdJsonParserTest {

    @Test
    public void testEmptyArray() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes("[]");

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
        byte[] json = toBytes("{}");

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
        byte[] json = toBytes("[1, 2, 3]");

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
        byte[] json = toBytes("{\"a\": 1, \"b\": 2, \"c\": 3}");

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
        byte[] json = toBytes("[true, false]");

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
        byte[] json = toBytes(Boolean.toString(booleanVal));

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertBoolean(jsonValue, booleanVal);
    }

    @Test
    public void testNullValue() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes("[null]");

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
        byte[] json = toBytes("null");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isNull()).isTrue();
    }

    @Test
    public void testStringValues() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes("[\"abc\", \"ab\\\\c\"]");

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
        byte[] json = toBytes("\"" + jsonStr + "\"");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertString(jsonValue, jsonStr);
    }

    @Test
    public void testLongValues() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes("[0, 1, -1]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isTrue();
        assertLong(it.next(), 0);
        assertLong(it.next(), 1);
        assertLong(it.next(), -1);
        assertThat(it.hasNext()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1})
    public void testLongValuesAsRoot(int intVal) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes(String.valueOf(intVal));

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertLong(jsonValue, intVal);
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MAX_VALUE, Long.MIN_VALUE})
    public void testMinMaxLongValue(long longVal) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes(String.valueOf(longVal));

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertLong(jsonValue, longVal);
    }

    @Test
    public void testOutOfRangeLongValues() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes("[9223372036854775808, 99223372036854775808, -9223372036854775809, -99223372036854775809]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isTrue();
        // Not sure if this is how out-of-ranges should be handled.
        // The JSON specification doesn't say much about it: https://datatracker.ietf.org/doc/html/rfc8259#section-6.
        // Jackson handles them in the same way.
        assertLong(it.next(), -9223372036854775808L);
        assertLong(it.next(), 6989651668307017728L);
        assertLong(it.next(), 9223372036854775807L);
        assertLong(it.next(), -6989651668307017729L);
        assertThat(it.hasNext()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true,", "false,", "null,", "1,", "\"abc\",", "1.1,"})
    public void testInvalidPrimitivesAsRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage())
                .isEqualTo("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.1",
            "9355950000000000000.00000000000000000000000000000000001844674407370955161600000184467440737095516161844674407370955161407370955161618446744073709551616000184467440737095516166000001844674407370955161618446744073709551614073709551616184467440737095516160001844674407370955161601844674407370955674451616184467440737095516140737095516161844674407370955161600018446744073709551616018446744073709551611616000184467440737095001844674407370955161600184467440737095516160018446744073709551168164467440737095516160001844073709551616018446744073709551616184467440737095516160001844674407536910751601611616000184467440737095001844674407370955161600184467440737095516160018446744073709551616184467440737095516160001844955161618446744073709551616000184467440753691075160018446744073709",
            "2.2250738585072013e-308",
            "-92666518056446206563E3",
            "-42823146028335318693e-128",
            "90054602635948575728E72",
            "1.00000000000000188558920870223463870174566020691753515394643550663070558368373221972569761144603605635692374830246134201063722058e-309",
            "0e9999999999999999999999999999",
            "-2402844368454405395.2"
    })
    @Disabled("https://github.com/simdjson/simdjson-java/issues/5")
    public void testFloatValues(String floatStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes(floatStr);

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isDouble()).isTrue();
        assertThat(jsonValue.asDouble()).isEqualTo(Double.valueOf(floatStr));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[n]", "{\"a\":n}"})
    public void testInvalidNull(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes(jsonStr);

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
        byte[] json = toBytes(jsonStr);

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
        byte[] json = toBytes(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid value starting at " + jsonStr.indexOf('t') + ". Expected 'true'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[-]", "{\"a\":-}"})
    public void testInvalidNegativeNumber(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid number starting at " + jsonStr.indexOf('-'));
    }

    @Test
    public void testUnicodeString() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes("[\"\\u005C\"]");

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
        byte[] json = toBytes("[\"" + jsonStr + "\"]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).startsWith("Escaped unexpected character: ");
    }

    @Test
    public void testLongString() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toBytes("[\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"]");

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
        byte[] json = toBytes("[1, 2, 3]");

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
        byte[] json = toBytes("{\"1\":1,\"2\":1,\"3\":1}");

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

    private static byte[] toBytes(String str) {
        return str.getBytes(UTF_8);
    }
}
