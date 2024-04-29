package org.simdjson;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.simdjson.schemas.ClassWithIntegerField;
import org.simdjson.schemas.RecordWithBooleanListField;
import org.simdjson.schemas.RecordWithIntegerListField;
import org.simdjson.schemas.RecordWithPrimitiveIntegerArrayField;
import org.simdjson.schemas.RecordWithStringArrayField;
import org.simdjson.testutils.MapEntry;
import org.simdjson.testutils.MapSource;
import org.simdjson.testutils.SchemaBasedRandomValueSource;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.TestUtils.padWithSpaces;
import static org.simdjson.TestUtils.toUtf8;
import static org.simdjson.testutils.SimdJsonAssertions.assertThat;

public class ArraySchemaBasedParsingTest {

    @ParameterizedTest
    @ValueSource(classes = {
            Object[].class,
            String[].class,
            char[].class,
            Character[].class,
            byte[].class,
            Byte[].class,
            short[].class,
            Short[].class,
            int[].class,
            Integer[].class,
            long[].class,
            Long[].class,
            boolean[].class,
            Boolean[].class,
            float[].class,
            Float[].class,
            double[].class,
            Double[].class,
            ClassWithIntegerField[].class
    })
    public void emptyArrayAtRoot(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[]");

        // when
        Object array = parser.parse(json, json.length, expectedType);

        // then
        assertThat(array).isInstanceOf(expectedType);
        assertThat(array.getClass().isArray()).isTrue();
        Assertions.assertThat(Array.getLength(array)).isEqualTo(0);
    }

    @Test
    public void objectWithEmptyArrayField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": []}");

        // when
        RecordWithStringArrayField object = parser.parse(json, json.length, RecordWithStringArrayField.class);

        // then
        assertThat(object.field()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "false", "{}", ":", ",", "\"abc\""})
    public void invalidTypeAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, int[].class));

        // then
        assertThat(ex)
                .hasMessage("Expected '[' but got: '" + jsonStr.charAt(0) + "'.");
    }

    @Test
    public void missingCommaInArrayAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1 1]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, int[].class));

        // then
        assertThat(ex)
                .hasMessage("Missing comma between array values");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,,1]", "[,]", "[,,]"})
    public void tooManyCommasInArrayAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, int[].class));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[,", "[1 ", "[,,", "[1,", "[1", "["})
    public void unclosedArrayAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, int[].class));

        // then
        assertThat(ex)
                .hasMessage("Unclosed array. Missing ']' for starting '['.");
    }

    @Test
    public void unclosedArrayDueToPassedLength() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[[]]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3, int[][].class));

        // then
        assertThat(ex)
                .hasMessage("Missing comma between array values");
    }

    @Test
    public void unclosedArrayPaddedWithOpenBraces() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[[[[");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 2, int[].class));

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
        int[] array = parser.parse(json, 2, int[].class);

        // then
        assertThat(array).isEmpty();
    }

    @Test
    public void missingCommaInArrayAtObjectField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [1 1]}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveIntegerArrayField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Missing comma between array values");
    }

    @Test
    public void missingCommaInListAtObjectField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [1 1]}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerListField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Missing comma between array values");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,,1]", "[,]", "[,,]"})
    public void tooManyCommasInArrayAtObjectField(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveIntegerArrayField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,,1]", "[,]", "[,,]"})
    public void tooManyCommasInListAtObjectField(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerListField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "{\"field\": [,}", value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(stringKey = "{\"field\": [1 }", value = "Missing comma between array values"),
            @MapEntry(stringKey = "{\"field\": [,,}", value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(stringKey = "{\"field\": [1,}", value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(stringKey = "{\"field\": [1}", value = "Missing comma between array values"),
            @MapEntry(stringKey = "{\"field\": [}", value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(stringKey = "{\"ignore\": [1, \"field\": []}", value = "Expected ',' but reached end of buffer."),
            @MapEntry(stringKey = "{\"ignore\": [", value = "Unclosed object. Missing '}' for starting '{'.")
    })
    public void unclosedArrayAtObjectField(String jsonStr, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveIntegerArrayField.class)
        );

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "{\"field\": [,}", value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(stringKey = "{\"field\": [1 }", value = "Missing comma between array values"),
            @MapEntry(stringKey = "{\"field\": [,,}", value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(stringKey = "{\"field\": [1,}", value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(stringKey = "{\"field\": [1}", value = "Missing comma between array values"),
            @MapEntry(stringKey = "{\"field\": [}", value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(stringKey = "{\"ignore\": [1, \"field\": []}", value = "Expected ',' but reached end of buffer."),
            @MapEntry(stringKey = "{\"ignore\": [", value = "Unclosed object. Missing '}' for starting '{'.")
    })
    public void unclosedListAtObjectField(String jsonStr, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerListField.class)
        );

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @ValueSource(classes = {AbstractList.class, LinkedList.class, ArrayList.class, Set.class})
    public void unsupportedTypeForArrays(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1, 2, 3]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Unsupported class: " + expectedType.getName() +
                        ". For JSON arrays at the root, use Java arrays. For inner JSON arrays, use either Java arrays or java.util.List.");
    }

    @Test
    public void listsAtRootAreNotSupported() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1, 2, 3]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, List.class));

        // then
        assertThat(ex)
                .hasMessage("Undefined list element type.");
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = int[][].class, nulls = false)
    public void multidimensionalArrays2d(String jsonStr, int[][] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        int[][] array = parser.parse(json, json.length, int[][].class);

        // then
        assertThat(array)
                .isDeepEqualTo(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = int[][][].class, nulls = false)
    public void multidimensionalArrays3d(String jsonStr, int[][][] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        int[][][] array = parser.parse(json, json.length, int[][][].class);

        // then
        assertThat(array)
                .isDeepEqualTo(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = RecordWith2dIntegerListField.class, nulls = false)
    public void multidimensionalArrays2dAsList(String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        RecordWith2dIntegerListField object = parser.parse(json, json.length, RecordWith2dIntegerListField.class);

        // then
        assertThat(object).usingRecursiveComparison().isEqualTo(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = RecordWith3dIntegerListField.class, nulls = false)
    public void multidimensionalArrays3dAsList(String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        RecordWith3dIntegerListField object = parser.parse(json, json.length, RecordWith3dIntegerListField.class);

        // then
        assertThat(object).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    public void nullAtRootWhenArrayIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        int[] object = parser.parse(json, json.length, int[].class);

        // then
        assertThat(object).isNull();
    }

    @Test
    public void nullAtObjectFieldWhenArrayIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        RecordWithPrimitiveIntegerArrayField object = parser.parse(json, json.length, RecordWithPrimitiveIntegerArrayField.class);

        // then
        assertThat(object).isNotNull();
        assertThat(object.field()).isNull();
    }

    @Test
    public void nullAtObjectFieldWhenListIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        RecordWithBooleanListField object = parser.parse(json, json.length, RecordWithBooleanListField.class);

        // then
        assertThat(object).isNotNull();
        assertThat(object.field()).isNull();
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "[],", value = "Unclosed array. Missing ']' for starting '['."),
            @MapEntry(stringKey = "[1, 2, 3],", value = "Unclosed array. Missing ']' for starting '['."),
            @MapEntry(stringKey = "[1, 2, 3][]", value = "More than one JSON value at the root of the document, or extra characters at the end of the JSON!"),
            @MapEntry(stringKey = "[1, 2, 3]{}", value = "Unclosed array. Missing ']' for starting '['."),
            @MapEntry(stringKey = "[1, 2, 3]1", value = "Unclosed array. Missing ']' for starting '['."),
            @MapEntry(stringKey = "null,", value = "More than one JSON value at the root of the document, or extra characters at the end of the JSON!")
    })
    public void moreValuesThanOneArrayAtRoot(String jsonStr, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, int[].class));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @Test
    public void arraysOfListsAreUnsupported() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[[1, 2], [1], [12, 13]]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, List[].class));

        // then
        assertThat(ex)
                .hasMessage("Undefined list element type.");
    }

    @Test
    public void emptyJson() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, int[].class));

        // then
        assertThat(ex)
                .hasMessage("No structural element found.");
    }

    @Test
    public void passedLengthSmallerThanNullLength() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(padWithSpaces("null"));

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3, Boolean[].class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'null'.");
    }

    private record RecordWith2dIntegerListField(List<List<Integer>> field) {

    }

    private record RecordWith3dIntegerListField(List<List<List<Integer>>> field) {

    }
}
