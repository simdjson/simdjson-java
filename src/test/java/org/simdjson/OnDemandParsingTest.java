package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.simdjson.OnDemandJsonIterator.IteratorResult;
import org.simdjson.OnDemandJsonIterator.ValueType;

import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.OnDemandJsonIterator.STRING_BUFFER_PADDING;
import static org.simdjson.testutils.TestUtils.toUtf8;

public class OnDemandParsingTest {

    private final byte[] stringBuffer = new byte[256 + STRING_BUFFER_PADDING];

    @Test
    public void rootObject() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": 1, \"b\": \"x\", \"c\": true}");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(it)).isEqualTo("a");
        it.moveToFieldValue();
        assertThat(it.getNonNullLong()).isEqualTo(1);
        assertThat(it.nextObjectField()).isTrue();
        assertThat(fieldName(it)).isEqualTo("b");
        it.moveToFieldValue();
        assertThat(string(it)).isEqualTo("x");
        assertThat(it.nextObjectField()).isTrue();
        assertThat(fieldName(it)).isEqualTo("c");
        it.moveToFieldValue();
        assertThat(it.getNonNullBoolean()).isTrue();
        assertThat(it.nextObjectField()).isFalse();
        assertThat(it.getDepth()).isEqualTo(0);
    }

    @Test
    public void rootArray() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1, 2, 3]");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        List<Long> values = new ArrayList<>();
        do {
            values.add(it.getNonNullLong());
        } while (it.nextArrayElement());
        assertThat(values).containsExactly(1L, 2L, 3L);
        assertThat(it.getDepth()).isEqualTo(0);
    }

    @Test
    public void nestedObjectsAndArrays() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": {\"b\": [1, {\"c\": [true, null]}]}, \"d\": [[2], [3, 4]]}");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(it)).isEqualTo("a");
        it.moveToFieldValue();
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(it)).isEqualTo("b");
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getNonNullLong()).isEqualTo(1);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(it)).isEqualTo("c");
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getBoolean()).isTrue();
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getBoolean()).isNull();
        assertThat(it.nextArrayElement()).isFalse();
        assertThat(it.nextObjectField()).isFalse();
        assertThat(it.nextArrayElement()).isFalse();
        assertThat(it.nextObjectField()).isFalse();
        assertThat(it.nextObjectField()).isTrue();
        assertThat(fieldName(it)).isEqualTo("d");
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        List<List<Long>> rows = new ArrayList<>();
        do {
            assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
            List<Long> row = new ArrayList<>();
            do {
                row.add(it.getNonNullLong());
            } while (it.nextArrayElement());
            rows.add(row);
        } while (it.nextArrayElement());
        assertThat(rows).containsExactly(List.of(2L), List.of(3L, 4L));
        assertThat(it.nextObjectField()).isFalse();
        assertThat(it.getDepth()).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{ }", " {}"})
    public void emptyRootObject(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.EMPTY);
        assertThat(it.getDepth()).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[ ]", " []"})
    public void emptyRootArray(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.EMPTY);
        assertThat(it.getDepth()).isEqualTo(0);
    }

    @Test
    public void nullAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        IteratorResult objectResult = parser.parseOnDemand(json, json.length).startIteratingRootObject();
        IteratorResult arrayResult = parser.parseOnDemand(json, json.length).startIteratingRootArray();

        // then
        assertThat(objectResult).isEqualTo(IteratorResult.NULL);
        assertThat(arrayResult).isEqualTo(IteratorResult.NULL);
    }

    @Test
    public void emptyAndNullNestedContainers() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": {}, \"b\": [], \"c\": null, \"d\": null, \"e\": 5}");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.EMPTY);
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(it.nextObjectField()).isTrue();
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.EMPTY);
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(it.nextObjectField()).isTrue();
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.NULL);
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(it.nextObjectField()).isTrue();
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NULL);
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(it.nextObjectField()).isTrue();
        assertThat(fieldName(it)).isEqualTo("e");
        it.moveToFieldValue();
        assertThat(it.getNonNullInt()).isEqualTo(5);
        assertThat(it.nextObjectField()).isFalse();
    }

    @Test
    public void numbersAndBooleans() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[-9223372036854775808, 9223372036854775807, null, -2147483648, 2147483647, null, "
                + "1.5, 7, -2.5e-3, null, 0.25, 3, null, true, false, true, null]");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getNonNullLong()).isEqualTo(Long.MIN_VALUE);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getLong()).isEqualTo(Long.MAX_VALUE);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getLong()).isNull();
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getNonNullInt()).isEqualTo(Integer.MIN_VALUE);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getInt()).isEqualTo(Integer.MAX_VALUE);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getInt()).isNull();
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getNonNullDouble()).isEqualTo(1.5);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getNonNullDouble()).isEqualTo(7.0);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getDouble()).isEqualTo(-2.5e-3);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getDouble()).isNull();
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getNonNullFloat()).isEqualTo(0.25f);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getFloat()).isEqualTo(3.0f);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getFloat()).isNull();
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getNonNullBoolean()).isTrue();
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getNonNullBoolean()).isFalse();
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getBoolean()).isTrue();
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getBoolean()).isNull();
        assertThat(it.nextArrayElement()).isFalse();
    }

    @Test
    public void stringsAndNull() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"\", \"abc\", null]");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getString(stringBuffer)).isEqualTo(0);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(string(it)).isEqualTo("abc");
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getString(stringBuffer)).isEqualTo(-1);
        assertThat(it.nextArrayElement()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "\\u0105\\u0107\\u015B, \u0105\u0107\u015B",
            "\\u03B1\\u03B2\\u03B3, \u03B1\u03B2\u03B3",
            "\\uD83D\\uDE00abc\\uD83D\\uDE00, \uD83D\uDE00abc\uD83D\uDE00",
            "\u0105\u20AC\uD83D\uDE00, \u0105\u20AC\uD83D\uDE00",
            "a\\\"b\\\\c\\/d, a\"b\\c/d"
    })
    public void escapedAndNonAsciiStringsAndFieldNames(String jsonStr, String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"" + jsonStr + "\": \"" + jsonStr + "\"}");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(it)).isEqualTo(expected);
        it.moveToFieldValue();
        assertThat(string(it)).isEqualTo(expected);
        assertThat(it.nextObjectField()).isFalse();
    }

    @Test
    public void controlCharacterEscapesInStringsAndFieldNames() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"\\b\\f\\n\\r\\t\": \"\\t\\r\\n\\f\\b\\u0000\"}");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(it)).isEqualTo("\b\f\n\r\t");
        it.moveToFieldValue();
        assertThat(string(it)).isEqualTo("\t\r\n\f\b\u0000");
        assertThat(it.nextObjectField()).isFalse();
    }

    @Test
    public void skipChildOnEveryKindOfValue() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"s\": \"x:y\", \"n\": -1.5e3, \"t\": true, \"f\": false, \"z\": null, "
                + "\"o\": {\"a\": [1, {\"b\": \"}\"}], \"c\": {}}, \"e\": {}, \"a\": [[1], {\"c\": [3]}, []], "
                + "\"last\": 7}");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        List<String> skipped = new ArrayList<>();
        long last = 0;
        do {
            String name = fieldName(it);
            it.moveToFieldValue();
            if (name.equals("last")) {
                last = it.getNonNullLong();
            } else {
                it.skipChild();
                assertThat(it.getDepth()).isEqualTo(1);
                skipped.add(name);
            }
        } while (it.nextObjectField());
        assertThat(skipped).containsExactly("s", "n", "t", "f", "z", "o", "e", "a");
        assertThat(last).isEqualTo(7);
    }

    @Test
    public void skipChildOnArrayElements() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[\"x\", 1, {\"a\": [2]}, [3, [4]], null, true, 5]");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        int skipped = 0;
        for (int i = 0; i < 6; i++) {
            it.skipChild();
            assertThat(it.getDepth()).isEqualTo(1);
            assertThat(it.nextArrayElement()).isTrue();
            skipped++;
        }
        assertThat(skipped).isEqualTo(6);
        assertThat(it.getNonNullLong()).isEqualTo(5);
        assertThat(it.nextArrayElement()).isFalse();
    }

    @Test
    public void depthOfObjects() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": {\"x\": 1, \"y\": {\"z\": [1, 2]}, \"w\": 3}, \"b\": 2}");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(fieldName(it)).isEqualTo("a");
        assertThat(it.getDepth()).isEqualTo(1);
        it.moveToFieldValue();
        assertThat(it.getDepth()).isEqualTo(2);
        int parentDepth = it.getDepth() - 1;
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getDepth()).isEqualTo(2);
        assertThat(it.getDepth() - 1).isEqualTo(parentDepth);
        assertThat(fieldName(it)).isEqualTo("x");
        it.moveToFieldValue();
        assertThat(it.getDepth()).isEqualTo(3);
        assertThat(it.getNonNullLong()).isEqualTo(1);
        assertThat(it.getDepth()).isEqualTo(2);
        assertThat(it.nextObjectField()).isTrue();
        assertThat(it.getDepth()).isEqualTo(2);
        assertThat(fieldName(it)).isEqualTo("y");
        it.moveToFieldValue();
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(it)).isEqualTo("z");
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getDepth()).isEqualTo(5);

        // when: skipping the rest of "a" from two levels below it
        it.skipChild(parentDepth);

        // then
        assertThat(it.getDepth()).isEqualTo(parentDepth);
        assertThat(it.nextObjectField()).isTrue();
        assertThat(fieldName(it)).isEqualTo("b");
        it.moveToFieldValue();
        assertThat(it.getNonNullLong()).isEqualTo(2);
        assertThat(it.nextObjectField()).isFalse();
        assertThat(it.getDepth()).isEqualTo(0);
    }

    @Test
    public void depthOfArrays() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": [1, [2, [3, 4], 5], 6], \"b\": 2}");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(it)).isEqualTo("a");
        it.moveToFieldValue();
        assertThat(it.getDepth()).isEqualTo(2);
        int parentDepth = it.getDepth() - 1;
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getDepth()).isEqualTo(3);
        assertThat(it.getDepth() - 2).isEqualTo(parentDepth);
        assertThat(it.getNonNullLong()).isEqualTo(1);
        assertThat(it.getDepth()).isEqualTo(2);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getDepth()).isEqualTo(3);
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getDepth()).isEqualTo(4);
        assertThat(it.getNonNullLong()).isEqualTo(2);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getNonNullLong()).isEqualTo(3);
        assertThat(it.getDepth()).isEqualTo(4);

        // when: skipping the rest of "a" from two levels below it
        it.skipChild(parentDepth);

        // then
        assertThat(it.getDepth()).isEqualTo(parentDepth);
        assertThat(it.nextObjectField()).isTrue();
        assertThat(fieldName(it)).isEqualTo("b");
        it.moveToFieldValue();
        assertThat(it.getNonNullLong()).isEqualTo(2);
        assertThat(it.nextObjectField()).isFalse();
    }

    @Test
    public void skipRestOfObjectBetweenFields() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[{\"a\": 1, \"b\": {\"c\": 2}, \"d\": [3]}, 4]");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(it)).isEqualTo("a");
        it.moveToFieldValue();
        assertThat(it.getNonNullLong()).isEqualTo(1);
        it.skipChild();
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getNonNullLong()).isEqualTo(4);
        assertThat(it.nextArrayElement()).isFalse();
    }

    @Test
    public void skipRestOfObjectAtFieldName() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[{\"a\": 1, \"b\": {\"c\": 2}}, 4]");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.skipChild();
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(it.nextArrayElement()).isTrue();
        assertThat(it.getNonNullLong()).isEqualTo(4);
        assertThat(it.nextArrayElement()).isFalse();
    }

    @Test
    public void skipRestOfArrayBetweenElements() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": [1, [2], {\"b\": 3}], \"c\": 4}");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(it.getNonNullLong()).isEqualTo(1);
        it.skipChild();
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(it.nextObjectField()).isTrue();
        assertThat(fieldName(it)).isEqualTo("c");
        it.moveToFieldValue();
        assertThat(it.getNonNullLong()).isEqualTo(4);
        assertThat(it.nextObjectField()).isFalse();
    }

    @Test
    public void skipChildWithDepthAlreadyReachedDoesNothing() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": 1, \"b\": 2}");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        it.getNonNullLong();
        int offset = it.peekOffset();

        // when
        it.skipChild(1);

        // then
        assertThat(it.getDepth()).isEqualTo(1);
        assertThat(it.peekOffset()).isEqualTo(offset);
    }

    @ParameterizedTest
    @CsvSource({
            "'{\"a\": 1}', OBJECT",
            "{}, OBJECT",
            "'[1, 2]', ARRAY",
            "[], ARRAY",
            "\"abc\", STRING",
            "\"\", STRING",
            "0, NUMBER",
            "-1, NUMBER",
            "1.5e3, NUMBER",
            "true, BOOLEAN",
            "false, BOOLEAN",
            "null, NULL"
    })
    public void peekType(String value, ValueType expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"v\": " + value + "}");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        int offset = it.peekOffset();

        // when
        ValueType type = it.peekType();

        // then
        assertThat(type).isEqualTo(expected);
        assertThat(it.peekOffset()).isEqualTo(offset);
        assertThat(it.getDepth()).isEqualTo(2);
        it.skipChild();
        assertThat(it.nextObjectField()).isFalse();
    }

    @Test
    public void peekTypeOfArrayElements() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[{}, [], \"a\", 1, true, null]");

        // when
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // then
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        List<ValueType> types = new ArrayList<>();
        do {
            types.add(it.peekType());
            it.skipChild();
        } while (it.nextArrayElement());
        assertThat(types).containsExactly(
                ValueType.OBJECT, ValueType.ARRAY, ValueType.STRING, ValueType.NUMBER, ValueType.BOOLEAN, ValueType.NULL
        );
    }

    @Test
    public void peekTypeOfUnrecognizedValue() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[x]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::peekType);

        // then
        assertThat(ex)
                .hasMessage("Unrecognized value starting at 1. Expected: object, array, string, number, 'true', 'false' or 'null'.");
    }

    @Test
    public void peekTypeAtEndOfDocument() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getNonNullLong();
        assertThat(it.nextArrayElement()).isFalse();

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::peekType);

        // then
        assertThat(ex).hasMessage("Expected a value but reached end of buffer.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"b\": [1, {\"c\": \"]}\"}], \"d\": {}}", "[[1, 2], {\"e\": null}, []]", "{}", "[]"})
    public void byteSpanOfSkippedValue(String value) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\":  " + value + " , \"z\": 1}");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();

        // when
        int start = it.peekOffset();
        it.skipChild();
        int end = it.lastTokenOffset() + 1;

        // then
        assertThat(new String(json, start, end - start, UTF_8)).isEqualTo(value);
    }

    @Test
    public void byteSpanOfIteratedValues() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        String object = "{\"b\": [1, 2]}";
        String array = "[3, {\"c\": 4}]";
        byte[] json = toUtf8("[ " + object + ", " + array + " ]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        int objectStart = it.peekOffset();
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        it.skipChild();
        assertThat(it.nextObjectField()).isFalse();
        int objectEnd = it.lastTokenOffset() + 1;
        assertThat(it.nextArrayElement()).isTrue();
        int arrayStart = it.peekOffset();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getNonNullLong();
        assertThat(it.nextArrayElement()).isTrue();
        it.skipChild();
        assertThat(it.nextArrayElement()).isFalse();
        int arrayEnd = it.lastTokenOffset() + 1;

        // then
        assertThat(new String(json, objectStart, objectEnd - objectStart, UTF_8)).isEqualTo(object);
        assertThat(new String(json, arrayStart, arrayEnd - arrayStart, UTF_8)).isEqualTo(array);
    }

    @Test
    public void offsetsOfScalars() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[ \"ab\" ,  12 ]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // when
        int rootOffset = it.peekOffset();
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        int rootLastToken = it.lastTokenOffset();
        int stringOffset = it.peekOffset();
        it.getString(stringBuffer);
        int stringLastToken = it.lastTokenOffset();
        assertThat(it.nextArrayElement()).isTrue();
        int numberOffset = it.peekOffset();
        it.getNonNullLong();
        int numberLastToken = it.lastTokenOffset();

        // then
        assertThat(rootOffset).isEqualTo(0);
        assertThat(rootLastToken).isEqualTo(0);
        assertThat(stringOffset).isEqualTo(2);
        assertThat(stringLastToken).isEqualTo(2);
        assertThat(numberOffset).isEqualTo(10);
        assertThat(numberLastToken).isEqualTo(10);
    }

    @Test
    public void peekOffsetAtEndOfDocument() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": 1}  ");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        it.skipChild();

        // then
        assertThat(it.getDepth()).isEqualTo(0);
        assertThat(it.peekOffset()).isEqualTo(json.length);
        assertThat(it.lastTokenOffset()).isEqualTo(7);
    }

    @Test
    public void lastTokenOffsetBeforeAnyTokenWasConsumed() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": 1}");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // when
        IllegalStateException ex = assertThrows(IllegalStateException.class, it::lastTokenOffset);

        // then
        assertThat(ex).hasMessage("No token has been consumed yet.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0", "-0", "7", "-12", "9223372036854775807", "123456789012345678901234567890",
            "3.14", "-0.5", "1.50", "0.000001",
            "1e10", "1E10", "1.5e-3", "-2E+5", "6.02214076e23"
    })
    public void rawNumber(String literal) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": " + literal + ", \"b\": [" + literal + "]}");
        byte[] destination = new byte[literal.length()];
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();

        // when
        int fieldLength = it.getRawNumber(destination);
        String fieldLiteral = new String(destination, 0, fieldLength, UTF_8);
        assertThat(it.nextObjectField()).isTrue();
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        int elementLength = it.getRawNumber(destination);
        String elementLiteral = new String(destination, 0, elementLength, UTF_8);

        // then
        assertThat(fieldLiteral).isEqualTo(literal);
        assertThat(elementLiteral).isEqualTo(literal);
        assertThat(it.nextArrayElement()).isFalse();
        assertThat(it.nextObjectField()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"1\"", "null", "true", "false", "{}", "[]"})
    public void rawNumberOfNonNumber(String value) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[" + value + "]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> it.getRawNumber(new byte[16]));

        // then
        assertThat(ex).hasMessage("Invalid value starting at 1. Expected number.");
    }

    @Test
    public void rawNumberIntoTooSmallDestination() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[12.345]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> it.getRawNumber(new byte[5]));

        // then
        assertThat(ex).hasMessage("The number starting at 1 has 6 bytes, but the destination array has only 5.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1     | getString             | Invalid value starting at 1. Expected either string or 'null'.",
            "true  | getString             | Invalid value starting at 1. Expected either string or 'null'.",
            "1     | getNonNullBoolean     | Unrecognized boolean value. Expected: 'true' or 'false'.",
            "\"a\" | getNonNullLong        | Invalid number. Minus has to be followed by a digit.",
            "1.5   | getNonNullLong        | Number has to be followed by a structural character or whitespace.",
            "\"a\" | getNonNullDouble      | Invalid number. Minus has to be followed by a digit.",
            "[]    | startIteratingObject  | Expected '{' but got: '['.",
            "{}    | startIteratingArray   | Expected '[' but got: '{'."
    })
    public void wrongTypeAccess(String value, String accessor, String expectedMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[" + value + "]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> {
            switch (accessor) {
                case "getString" -> it.getString(stringBuffer);
                case "getNonNullBoolean" -> it.getNonNullBoolean();
                case "getNonNullLong" -> it.getNonNullLong();
                case "getNonNullDouble" -> it.getNonNullDouble();
                case "startIteratingObject" -> it.startIteratingObject();
                case "startIteratingArray" -> it.startIteratingArray();
                default -> throw new IllegalArgumentException(accessor);
            }
        });

        // then
        assertThat(ex).hasMessage(expectedMessage);
    }

    @Test
    public void fieldNameWhereValueIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> it.getFieldName(stringBuffer));

        // then
        assertThat(ex).hasMessage("Expected '\"' but got: '1'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"a\": 1", "{\"a\": 1 2", "{\"a\": 1] "})
    public void unclosedRootObject(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::startIteratingRootObject);

        // then
        assertThat(ex).hasMessage("Unclosed object. Missing '}' for starting '{'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1, 2", "[1, 2 3", "[1, 2} "})
    public void unclosedRootArray(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::startIteratingRootArray);

        // then
        assertThat(ex).hasMessage("Unclosed array. Missing ']' for starting '['.");
    }

    @Test
    public void unclosedNestedObject() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": {\"b\": 1}");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        assertThat(it.startIteratingObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        it.getNonNullLong();
        assertThat(it.nextObjectField()).isFalse();

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::nextObjectField);

        // then
        assertThat(ex).hasMessage("Expected ',' but reached end of buffer.");
    }

    @Test
    public void unclosedNestedObjectWhileSkipping() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[[{\"a\": [1]]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::skipChild);

        // then
        assertThat(ex).hasMessage("Not enough close braces.");
    }

    @Test
    public void missingColon() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\" 1}");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::moveToFieldValue);

        // then
        assertThat(ex).hasMessage("Expected ':' but got: '1'.");
    }

    @Test
    public void missingCommaBetweenFields() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": 1 \"b\": 2}");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        it.getNonNullLong();

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::nextObjectField);

        // then
        assertThat(ex).hasMessage("Expected ',' but got: '\"'.");
    }

    @Test
    public void missingCommaBetweenElements() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1 2]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getNonNullLong();

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::nextArrayElement);

        // then
        assertThat(ex).hasMessage("Missing comma between array values");
    }

    @Test
    public void trailingContentAfterRootObject() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": 1}{\"b\": 2}");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        it.getNonNullLong();

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::nextObjectField);

        // then
        assertThat(ex).hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @Test
    public void trailingContentAfterRootArray() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1] [2]");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getNonNullLong();

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::nextArrayElement);

        // then
        assertThat(ex).hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @Test
    public void trailingContentAfterSkippedRootObject() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": 1} \"b\" }");
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::skipChild);

        // then
        assertThat(ex).hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{} {}", "null {}"})
    public void trailingContentAfterEmptyOrNullRootObject(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, it::startIteratingRootObject);

        // then
        assertThat(ex).hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t "})
    public void noStructuralElement(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parseOnDemand(json, json.length));

        // then
        assertThat(ex).hasMessage("No structural element found.");
    }

    @Test
    public void stringBufferPaddingIsOneVector() {
        // expect
        assertThat(STRING_BUFFER_PADDING).isEqualTo(VectorUtils.BYTE_SPECIES.vectorByteSize());
    }

    @Test
    public void stringBufferWithPaddingIsLargeEnough() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        for (int length = 0; length <= 3 * STRING_BUFFER_PADDING; length++) {
            String plain = "a".repeat(length);
            String escaped = "\\n".repeat(length);
            byte[] json = toUtf8("{\"" + plain + "\": \"" + escaped + "\"}");
            byte[] destination = new byte[length + STRING_BUFFER_PADDING];

            // when
            OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
            assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
            int nameLength = it.getFieldName(destination);
            String name = new String(destination, 0, nameLength, UTF_8);
            it.moveToFieldValue();
            int valueLength = it.getString(destination);
            String value = new String(destination, 0, valueLength, UTF_8);

            // then
            assertThat(name).isEqualTo(plain);
            assertThat(value).isEqualTo("\n".repeat(length));
            assertThat(it.nextObjectField()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    public void stringBufferWithoutPaddingIsTooSmall(int vectors) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        int length = vectors * STRING_BUFFER_PADDING;
        byte[] json = toUtf8("{\"" + "k".repeat(length) + "\": \"" + "v".repeat(length) + "\"}");
        byte[] destination = new byte[length + STRING_BUFFER_PADDING - 1];
        OnDemandJsonIterator nameIt = parser.parseOnDemand(json, json.length);
        assertThat(nameIt.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);

        // when
        assertThrows(IndexOutOfBoundsException.class, () -> nameIt.getFieldName(destination));
        OnDemandJsonIterator valueIt = parser.parseOnDemand(json, json.length);
        assertThat(valueIt.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        valueIt.getFieldName(new byte[length + STRING_BUFFER_PADDING]);
        valueIt.moveToFieldValue();

        // then
        assertThrows(IndexOutOfBoundsException.class, () -> valueIt.getString(destination));
    }

    @Test
    public void parserReuse() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] first = toUtf8("{\"a\": 123456789, \"b\": \"long string value\"}");
        byte[] second = toUtf8("[7]");

        // when
        OnDemandJsonIterator firstIt = parser.parseOnDemand(first, first.length);
        assertThat(firstIt.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(fieldName(firstIt)).isEqualTo("a");
        firstIt.moveToFieldValue();
        assertThat(firstIt.getNonNullLong()).isEqualTo(123456789);
        OnDemandJsonIterator secondIt = parser.parseOnDemand(second, second.length);

        // then
        assertThat(secondIt).isSameAs(firstIt);
        assertThat(secondIt.getDepth()).isEqualTo(1);
        assertThat(secondIt.startIteratingRootArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        assertThat(secondIt.getNonNullLong()).isEqualTo(7);
        assertThat(secondIt.nextArrayElement()).isFalse();
        assertThat(secondIt.peekOffset()).isEqualTo(second.length);
    }

    @Test
    public void parserReuseAcrossEntryPoints() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"a\": [1, 2]}");

        // when
        JsonValue dom = parser.parse(json, json.length);
        long domFirst = dom.get("a").arrayIterator().next().asLong();
        OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(stringBuffer);
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        long onDemandFirst = it.getNonNullLong();

        // then
        assertThat(onDemandFirst).isEqualTo(domFirst);
    }

    private String fieldName(OnDemandJsonIterator it) {
        int length = it.getFieldName(stringBuffer);
        return new String(stringBuffer, 0, length, UTF_8);
    }

    private String string(OnDemandJsonIterator it) {
        int length = it.getString(stringBuffer);
        return new String(stringBuffer, 0, length, UTF_8);
    }
}
