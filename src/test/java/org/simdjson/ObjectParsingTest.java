package org.simdjson;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simdjson.JsonValueAssert.assertThat;
import static org.simdjson.TestUtils.toUtf8;

public class ObjectParsingTest {

    @Test
    public void emptyObject() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{}");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isObject()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        assertThat(it.hasNext()).isFalse();
    }

    @Test
    public void objectIterator() {
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
        Iterator<Map.Entry<String, JsonValue>> it = jsonValue.objectIterator();
        while (it.hasNext()) {
            Map.Entry<String, JsonValue> field = it.next();
            assertThat(field.getKey()).isEqualTo(expectedKeys[counter]);
            assertThat(field.getValue()).isEqualTo(expectedValue[counter]);
            counter++;
        }
        assertThat(counter).isEqualTo(expectedKeys.length);
    }

    @Test
    public void objectSize() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"1\": 1, \"2\": 1, \"3\": 1}");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isObject()).isTrue();
        assertThat(jsonValue.getSize()).isEqualTo(3);
    }

    @Test
    public void fieldNamesWithNonAsciiCharacters() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"ąćśńźż\": 1, \"\\u20A9\\u0E3F\": 2, \"αβγ\": 3, \"😀abc😀\": 4}");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.get("ąćśńźż")).isEqualTo(1);
        assertThat(jsonValue.get("\u20A9\u0E3F")).isEqualTo(2);
        assertThat(jsonValue.get("αβγ")).isEqualTo(3);
        assertThat(jsonValue.get("😀abc😀")).isEqualTo(4);
    }

    @Test
    public void nonexistentField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"ąćśńźż\": 1, \"\\u20A9\\u0E3F\": 2, \"αβγ\": 3}");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.get("acsnz")).isNull();
        assertThat(jsonValue.get("\\u20A9\\u0E3F")).isNull();
        assertThat(jsonValue.get("αβ")).isNull();
    }
}
