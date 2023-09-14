package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.JsonValueAssert.assertThat;
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
        assertThat(it.next()).isEqualTo(true);
        assertThat(it.next()).isEqualTo(false);
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
        assertThat(jsonValue).isEqualTo(booleanVal);
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
        assertThat(it.next()).isEqualTo("abc");
        assertThat(it.next()).isEqualTo("ab\\c");
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
        assertThat(jsonValue).isEqualTo(jsonStr);
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
        assertThat(it.next()).isEqualTo(0);
        assertThat(it.next()).isEqualTo(1);
        assertThat(it.next()).isEqualTo(-1);
        assertThat(it.next()).isEqualTo(1.1);
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
        assertThat(jsonValue).isEqualTo(Long.parseLong(longStr));
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
        assertThat(jsonValue).isEqualTo(Double.parseDouble(doubleStr));
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
}
