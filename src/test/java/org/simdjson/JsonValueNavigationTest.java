package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.testutils.TestUtils.toUtf8;

public class JsonValueNavigationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"a\": 1, \"b\": -2.5, \"c\": \"x\", \"d\": true, \"e\": false, \"f\": null, \"g\": {}, \"h\": []}",
            "{\"o\": {\"p\": {\"q\": [1, [2, [3, {}]], {\"r\": \"s\"}]}}, \"t\": [[], [[]], {}]}",
            "[1, 2.0, \"three\", [4, [5]], {\"six\": 6}, null, true, false, \"\", {}, []]",
            "{\"\\u0105\\u0107\": \"\\u03B1\\u03B2\", \"\\u20AC\": [\"\\uD83D\\uDE00\", \"a\\\"b\\\\c\\n\"], \"\": \"empty key\"}",
            "{\"big\": 9223372036854775807, \"small\": -9223372036854775808, \"exp\": 1e300, \"tiny\": -4.9e-324}",
            "[]",
            "{}",
            "42",
            "-0.5",
            "\"root string\"",
            "true",
            "null"
    })
    public void indexNavigationMatchesIterators(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonValue doc = parser.parse(json, json.length);

        // then
        assertSameValue(doc, doc.index(), doc);
    }

    @Test
    public void nonAsciiKeysAndValues() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"\u0105\u0107\u015B\": \"\u03B1\u03B2\u03B3\", \"\\u20AC\": \"\\uD83D\\uDE00\"}");
        JsonValue doc = parser.parse(json, json.length);

        // when
        int first = doc.field(doc.index(), toUtf8("\u0105\u0107\u015B"));
        int second = doc.field(doc.index(), toUtf8("\u20AC"));

        // then
        assertThat(doc.stringAt(first)).isEqualTo("\u03B1\u03B2\u03B3");
        assertThat(doc.byteLength(first)).isEqualTo(6);
        assertThat(doc.stringEquals(first, toUtf8("\u03B1\u03B2\u03B3"))).isTrue();
        assertThat(doc.stringAt(second)).isEqualTo("\uD83D\uDE00");
        assertThat(doc.byteLength(second)).isEqualTo(4);
        assertThat(doc.get(toUtf8("\u20AC")).asString()).isEqualTo("\uD83D\uDE00");
    }

    @Test
    public void missingFields() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": {\"b\": 1}, \"e\": {}}");
        JsonValue doc = parser.parse(json, json.length);
        int a = doc.field(doc.index(), toUtf8("a"));
        int e = doc.field(doc.index(), toUtf8("e"));

        // expect
        assertThat(doc.field(doc.index(), toUtf8("b"))).isEqualTo(-1);
        assertThat(doc.field(doc.index(), toUtf8("A"))).isEqualTo(-1);
        assertThat(doc.field(doc.index(), toUtf8("ab"))).isEqualTo(-1);
        assertThat(doc.field(doc.index(), new byte[0])).isEqualTo(-1);
        assertThat(doc.field(a, toUtf8("c"))).isEqualTo(-1);
        assertThat(doc.field(e, toUtf8("b"))).isEqualTo(-1);
        assertThat(doc.get(toUtf8("b"))).isNull();
        assertThat(doc.get("b")).isNull();
    }

    @Test
    public void firstOccurrenceOfDuplicateField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": 1, \"a\": 2}");
        JsonValue doc = parser.parse(json, json.length);

        // when
        int a = doc.field(doc.index(), toUtf8("a"));

        // then
        assertThat(doc.longAt(a)).isEqualTo(1);
        assertThat(a).isEqualTo(doc.get("a").index());
    }

    @Test
    public void emptyContainers() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"o\": {}, \"a\": [], \"n\": [[]]}");
        JsonValue doc = parser.parse(json, json.length);
        int o = doc.field(doc.index(), toUtf8("o"));
        int a = doc.field(doc.index(), toUtf8("a"));
        int n = doc.field(doc.index(), toUtf8("n"));

        // expect
        assertThat(doc.isObjectAt(o)).isTrue();
        assertThat(doc.field(o, toUtf8("x"))).isEqualTo(-1);
        assertThat(doc.isArrayAt(a)).isTrue();
        assertThat(doc.firstElement(a)).isEqualTo(-1);
        assertThat(countElements(doc, a)).isEqualTo(0);
        assertThat(countElements(doc, n)).isEqualTo(1);
        int inner = doc.firstElement(n);
        assertThat(doc.isArrayAt(inner)).isTrue();
        assertThat(doc.firstElement(inner)).isEqualTo(-1);
        assertThat(doc.advance(inner)).isEqualTo(doc.scopeEnd(n));
    }

    @Test
    public void numberAtLosesPrecisionAboveTwoToThe53() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[9007199254740992, 9007199254740993]");
        JsonValue doc = parser.parse(json, json.length);
        int first = doc.firstElement(doc.index());
        int second = doc.advance(first);

        // expect
        assertThat(doc.longAt(first)).isEqualTo(9007199254740992L);
        assertThat(doc.numberAt(first)).isEqualTo(9007199254740992.0);
        assertThat(doc.longAt(second)).isEqualTo(9007199254740993L);
        assertThat(doc.numberAt(second)).isEqualTo(9007199254740992.0);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "field        | a | Expected an object at index 6, but found an array.",
            "field        | s | Expected an object at index 9, but found a string.",
            "firstElement | o | Expected an array at index 3, but found an object.",
            "firstElement | n | Expected an array at index 19, but found null.",
            "scopeEnd     | s | Expected an object or an array at index 9, but found a string.",
            "scopeEnd     | l | Expected an object or an array at index 11, but found an integer.",
            "stringAt     | l | Expected a string at index 11, but found an integer.",
            "byteOffset   | o | Expected a string at index 3, but found an object.",
            "byteLength   | t | Expected a string at index 17, but found a boolean.",
            "longAt       | d | Expected an integer at index 14, but found a floating-point number.",
            "longAt       | s | Expected an integer at index 9, but found a string.",
            "doubleAt     | l | Expected a floating-point number at index 11, but found an integer.",
            "numberAt     | s | Expected a number at index 9, but found a string.",
            "numberAt     | n | Expected a number at index 19, but found null.",
            "booleanAt    | n | Expected a boolean at index 19, but found null.",
            "booleanAt    | l | Expected a boolean at index 11, but found an integer."
    })
    public void wrongTypeIndex(String method, String fieldName, String expectedMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"o\": {}, \"a\": [], \"s\": \"x\", \"l\": 1, \"d\": 1.5, \"t\": true, \"n\": null}");
        JsonValue doc = parser.parse(json, json.length);
        int idx = doc.field(doc.index(), toUtf8(fieldName));

        // when
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            switch (method) {
                case "field" -> doc.field(idx, toUtf8("x"));
                case "firstElement" -> doc.firstElement(idx);
                case "scopeEnd" -> doc.scopeEnd(idx);
                case "stringAt" -> doc.stringAt(idx);
                case "byteOffset" -> doc.byteOffset(idx);
                case "byteLength" -> doc.byteLength(idx);
                case "longAt" -> doc.longAt(idx);
                case "doubleAt" -> doc.doubleAt(idx);
                case "numberAt" -> doc.numberAt(idx);
                case "booleanAt" -> doc.booleanAt(idx);
                default -> throw new IllegalStateException(method);
            }
        });

        // then
        assertThat(ex).hasMessage(expectedMessage);
    }

    @Test
    public void stringEqualsOnOtherTypesIsFalse() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"1\", 1, null, [], {}, true]");
        JsonValue doc = parser.parse(json, json.length);
        int end = doc.scopeEnd(doc.index());
        byte[] one = toUtf8("1");

        // when
        int matches = 0;
        for (int e = doc.firstElement(doc.index()); e >= 0 && e < end; e = doc.advance(e)) {
            if (doc.stringEquals(e, one)) {
                matches++;
            }
        }

        // then
        assertThat(matches).isEqualTo(1);
    }

    @Test
    public void stringBytesIsTheParserBuffer() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] first = toUtf8("[\"abc\"]");
        byte[] second = toUtf8("[\"xyz\"]");

        // when
        JsonValue firstDoc = parser.parse(first, first.length);
        byte[] firstBuffer = firstDoc.stringBytes();
        int firstIdx = firstDoc.firstElement(firstDoc.index());
        int offset = firstDoc.byteOffset(firstIdx);
        int length = firstDoc.byteLength(firstIdx);
        JsonValue secondDoc = parser.parse(second, second.length);

        // then
        assertThat(secondDoc.stringBytes()).isSameAs(firstBuffer);
        assertThat(new String(firstBuffer, offset, length, UTF_8)).isEqualTo("xyz");
    }

    private static void assertSameValue(JsonValue doc, int idx, JsonValue expected) {
        assertThat(idx).isEqualTo(expected.index());
        assertThat(doc.isObjectAt(idx)).isEqualTo(expected.isObject());
        assertThat(doc.isArrayAt(idx)).isEqualTo(expected.isArray());
        assertThat(doc.isStringAt(idx)).isEqualTo(expected.isString());
        assertThat(doc.isLongAt(idx)).isEqualTo(expected.isLong());
        assertThat(doc.isDoubleAt(idx)).isEqualTo(expected.isDouble());
        assertThat(doc.isBooleanAt(idx)).isEqualTo(expected.isBoolean());
        assertThat(doc.isNullAt(idx)).isEqualTo(expected.isNull());
        if (expected.isObject()) {
            assertSameObject(doc, idx, expected);
        } else if (expected.isArray()) {
            assertSameArray(doc, idx, expected);
        } else if (expected.isString()) {
            String value = expected.asString();
            assertThat(doc.stringAt(idx)).isEqualTo(value);
            assertThat(new String(doc.stringBytes(), doc.byteOffset(idx), doc.byteLength(idx), UTF_8)).isEqualTo(value);
            assertThat(doc.stringEquals(idx, toUtf8(value))).isTrue();
            assertThat(doc.stringEquals(idx, toUtf8(value + "x"))).isFalse();
        } else if (expected.isLong()) {
            assertThat(doc.longAt(idx)).isEqualTo(expected.asLong());
            assertThat(doc.numberAt(idx)).isEqualTo((double) expected.asLong());
        } else if (expected.isDouble()) {
            assertThat(doc.doubleAt(idx)).isEqualTo(expected.asDouble());
            assertThat(doc.numberAt(idx)).isEqualTo(expected.asDouble());
        } else if (expected.isBoolean()) {
            assertThat(doc.booleanAt(idx)).isEqualTo(expected.asBoolean());
        }
    }

    private static void assertSameObject(JsonValue doc, int idx, JsonValue expected) {
        int end = doc.scopeEnd(idx);
        int previousValue = -1;
        Iterator<Map.Entry<String, JsonValue>> it = expected.objectIterator();
        while (it.hasNext()) {
            Map.Entry<String, JsonValue> entry = it.next();
            byte[] name = toUtf8(entry.getKey());
            int value = doc.field(idx, name);
            assertThat(value).isEqualTo(entry.getValue().index());
            assertThat(expected.get(name).index()).isEqualTo(expected.get(entry.getKey()).index());
            if (previousValue >= 0) {
                assertThat(doc.advance(doc.advance(previousValue))).isEqualTo(value);
            }
            assertThat(value).isLessThan(end);
            assertSameValue(doc, value, entry.getValue());
            previousValue = value;
        }
        if (previousValue >= 0) {
            assertThat(doc.advance(previousValue)).isEqualTo(end);
        }
    }

    private static void assertSameArray(JsonValue doc, int idx, JsonValue expected) {
        int end = doc.scopeEnd(idx);
        Iterator<JsonValue> it = expected.arrayIterator();
        int element = doc.firstElement(idx);
        if (!it.hasNext()) {
            assertThat(element).isEqualTo(-1);
            return;
        }
        while (it.hasNext()) {
            assertThat(element).isLessThan(end);
            assertSameValue(doc, element, it.next());
            element = doc.advance(element);
        }
        assertThat(element).isEqualTo(end);
    }

    private static int countElements(JsonValue doc, int arrIdx) {
        int count = 0;
        int end = doc.scopeEnd(arrIdx);
        for (int e = doc.firstElement(arrIdx); e >= 0 && e < end; e = doc.advance(e)) {
            count++;
        }
        return count;
    }
}
