package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.testutils.TestUtils.toUtf8;

public class NullParsingTest {

    @Test
    public void nullValueAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[n]", "{\"a\":n}"})
    public void invalidNull(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at " + jsonStr.indexOf('n') + ". Expected 'null'.");
    }

    @Test
    public void moreThanNullAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null,");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nulll", "nul"})
    public void invalidNullAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'null'.");
    }

    @Test
    public void arrayOfNulls() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[null, null, null]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        for (int i = 0; i < 3; i++) {
            assertThat(it.hasNext()).isTrue();
            JsonValue element = it.next();
            assertThat(element.isNull()).isTrue();
        }
        assertThat(it.hasNext()).isFalse();
    }

    @Test
    public void passedLengthSmallerThanNullLength() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'null'.");
    }
}
