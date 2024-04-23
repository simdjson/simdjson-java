package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.simdjson.testutils.MapEntry;
import org.simdjson.testutils.MapSource;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.TestUtils.toUtf8;
import static org.simdjson.testutils.SimdJsonAssertions.assertThat;

public class ArrayParsingTest {

    @Test
    public void emptyArrayAtRoot() {
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
    public void arrayIterator() {
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
    public void arraySize() {
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
    public void largeArraySize() {
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

    @Test
    public void missingCommaInArrayAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1 1]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Missing comma between array values");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,,1]", "[,]", "[,,]"})
    public void tooManyCommas(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[,", "[1 ", "[,,", "[1,", "[1", "["})
    public void unclosedArray(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Unclosed array. Missing ']' for starting '['.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "[[]]", value = "Missing comma between array values"),
            @MapEntry(stringKey = "[]", value = "Unclosed array. Missing ']' for starting '['.")
    })
    public void unclosedArrayDueToPassedLength(String jsonStr, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length - 1));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @Test
    public void missingCommaInArrayAtObjectField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [1 1]}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage("Missing comma between array values");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "[,", value = "Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'."),
            @MapEntry(stringKey = "[1 ", value = "Missing comma between array values"),
            @MapEntry(stringKey = "[,,", value = "Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'."),
            @MapEntry(stringKey = "[1,", value = "Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'."),
            @MapEntry(stringKey = "[1", value = "Missing comma between array values"),
            @MapEntry(stringKey = "[", value = "Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'.")
    })
    public void unclosedArrayAtObjectField(String jsonStr, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @Test
    public void noMoreElements() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1, 2, 3]");
        JsonValue jsonValue = parser.parse(json, json.length);
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        it.next();
        it.next();
        it.next();

        // when
        NoSuchElementException ex = assertThrows(NoSuchElementException.class, it::next);

        // then
        assertThat(ex)
                .hasMessage("No more elements");
    }

    @Test
    public void unclosedArrayPaddedWithOpenBraces() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[[[[");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 2));

        // then
        assertThat(ex)
                .hasMessage("Unclosed array. Missing ']' for starting '['.");
    }

    @Test
    public void validArrayPaddedWithOpenBraces() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[][[[[");

        // when
        JsonValue jsonValue = parser.parse(json, 2);

        // then
        assertThat(jsonValue.isArray()).isTrue();
        Iterator<JsonValue> it = jsonValue.arrayIterator();
        while (it.hasNext()) {
            fail("Unexpected value");
            it.next();
        }
    }
}
