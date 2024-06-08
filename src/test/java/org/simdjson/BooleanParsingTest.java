package org.simdjson;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.testutils.SimdJsonAssertions.assertThat;
import static org.simdjson.testutils.TestUtils.toUtf8;

public class BooleanParsingTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void booleanValuesAtRoot(boolean booleanVal) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(Boolean.toString(booleanVal));

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue).isEqualTo(booleanVal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true,", "false,"})
    public void moreThanBooleanAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fals", "falsee", "[f]", "{\"a\":f}"})
    public void invalidFalse(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at " + jsonStr.indexOf('f') + ". Expected 'false'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tru", "truee", "[t]", "{\"a\":t}"})
    public void invalidTrue(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at " + jsonStr.indexOf('t') + ". Expected 'true'.");
    }

    @Test
    public void arrayOfBooleans() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[true, false]");

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        Assertions.assertThat(it.hasNext()).isTrue();
        assertThat(it.next()).isEqualTo(true);
        assertThat(it.next()).isEqualTo(false);
        Assertions.assertThat(it.hasNext()).isFalse();
    }

    @Test
    public void passedLengthSmallerThanTrueLength() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("true");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'true'.");
    }

    @Test
    public void passedLengthSmallerThanFalseLength() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("false");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 4));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'false'.");
    }
}
