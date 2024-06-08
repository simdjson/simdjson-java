package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.simdjson.schemas.RecordWithBooleanArrayField;
import org.simdjson.schemas.RecordWithBooleanField;
import org.simdjson.schemas.RecordWithBooleanListField;
import org.simdjson.schemas.RecordWithIntegerField;
import org.simdjson.schemas.RecordWithPrimitiveBooleanArrayField;
import org.simdjson.schemas.RecordWithPrimitiveBooleanField;
import org.simdjson.schemas.RecordWithPrimitiveIntegerField;
import org.simdjson.schemas.RecordWithStringField;
import org.simdjson.testutils.MapEntry;
import org.simdjson.testutils.MapSource;
import org.simdjson.testutils.SchemaBasedRandomValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.testutils.TestUtils.toUtf8;

public class BooleanSchemaBasedParsingTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void booleanValueAtRoot(boolean booleanVal) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(Boolean.toString(booleanVal));

        // when
        Boolean booleanValue = parser.parse(json, json.length, Boolean.class);

        // then
        assertThat(booleanValue).isEqualTo(booleanVal);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void primitiveBooleanValueAtRoot(boolean booleanVal) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(Boolean.toString(booleanVal));

        // when
        boolean booleanValue = parser.parse(json, json.length, boolean.class);

        // then
        assertThat(booleanValue).isEqualTo(booleanVal);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void booleanValueAtObjectField(boolean booleanVal) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + booleanVal + "}");

        // when
        RecordWithBooleanField object = parser.parse(json, json.length, RecordWithBooleanField.class);

        // then
        assertThat(object.field()).isEqualTo(booleanVal);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void primitiveBooleanValueAtObjectField(boolean booleanVal) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + booleanVal + "}");

        // when
        RecordWithPrimitiveBooleanField object = parser.parse(json, json.length, RecordWithPrimitiveBooleanField.class);

        // then
        assertThat(object.field()).isEqualTo(booleanVal);
    }

    @Test
    public void nullAtRootWhenBooleanIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        Boolean booleanValue = parser.parse(json, json.length, Boolean.class);

        // then
        assertThat(booleanValue).isNull();
    }

    @Test
    public void nullAtRootWhenPrimitiveBooleanIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, boolean.class));

        // then
        assertThat(ex)
                .hasMessage("Unrecognized boolean value. Expected: 'true' or 'false'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"abc\"", "1"})
    public void invalidTypeForBoolean(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Boolean.class));

        // then
        assertThat(ex)
                .hasMessage("Unrecognized boolean value. Expected: 'true', 'false' or 'null'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"abc\"", "1"})
    public void invalidTypeForPrimitiveBoolean(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, boolean.class));

        // then
        assertThat(ex)
                .hasMessage("Unrecognized boolean value. Expected: 'true' or 'false'.");
    }

    @Test
    public void nullAtObjectFieldWhenBooleanIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        RecordWithBooleanField object = parser.parse(json, json.length, RecordWithBooleanField.class);

        // then
        assertThat(object.field()).isNull();
    }

    @Test
    public void nullAtObjectFieldWhenPrimitiveBooleanIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveBooleanField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Unrecognized boolean value. Expected: 'true' or 'false'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true,", "false,"})
    public void moreValuesThanOnePrimitiveBooleanAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, boolean.class));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true,", "false,", "null,"})
    public void moreValuesThanOneBooleanAtRoot(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Boolean.class));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "truee", value = "true"),
            @MapEntry(stringKey = "falsee", value = "false"),
            @MapEntry(stringKey = "nul", value = "null"),
            @MapEntry(stringKey = "nulll", value = "null"),
            @MapEntry(stringKey = "nuul", value = "null")
    })
    public void invalidBooleanAtRoot(String actual, String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(actual);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, Boolean.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected '" + expected + "'.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "truee", value = "true"),
            @MapEntry(stringKey = "falsee", value = "false")
    })
    public void invalidPrimitiveBooleanAtRoot(String actual, String expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(actual);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, boolean.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected '" + expected + "'.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = Integer.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = int.class, value = "Invalid number. Minus has to be followed by a digit.")
    })
    public void mismatchedTypeForTrueAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("true");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = Integer.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = int.class, value = "Invalid number. Minus has to be followed by a digit.")
    })
    public void mismatchedTypeForFalseAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("false");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = RecordWithStringField.class, value = "Invalid value starting at 10. Expected either string or 'null'."),
            @MapEntry(classKey = RecordWithPrimitiveIntegerField.class, value = "Invalid number. Minus has to be followed by a digit.")
    })
    public void mismatchedTypeForTrue(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": true}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = RecordWithStringField.class, value = "Invalid value starting at 10. Expected either string or 'null'."),
            @MapEntry(classKey = RecordWithPrimitiveIntegerField.class, value = "Invalid number. Minus has to be followed by a digit.")
    })
    public void mismatchedTypeForFalse(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": false}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Boolean[].class, nulls = false)
    public void arrayOfBooleansAtRoot(String jsonStr, Boolean[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Boolean[] array = parser.parse(json, json.length, Boolean[].class);

        // then
        assertThat(array).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Boolean[].class, nulls = true)
    public void arrayOfBooleansAndNullsAtRoot(String jsonStr, Boolean[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Boolean[] array = parser.parse(json, json.length, Boolean[].class);

        // then
        assertThat(array).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = boolean[].class, nulls = false)
    public void arrayOfPrimitiveBooleansAtRoot(String jsonStr, boolean[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        boolean[] array = parser.parse(json, json.length, boolean[].class);

        // then
        assertThat(array).containsExactly(expected);
    }

    @Test
    public void arrayOfPrimitiveBooleansAndNullsAtRoot() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[true, false, null]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, boolean[].class));

        // then
        assertThat(ex)
                .hasMessage("Unrecognized boolean value. Expected: 'true' or 'false'.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = boolean[].class, value = "Unrecognized boolean value. Expected: 'true' or 'false'."),
            @MapEntry(classKey = Boolean[].class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'.")
    })
    public void arrayOfBooleansMixedWithOtherTypesAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[true, false, 1]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = RecordWithPrimitiveBooleanArrayField.class, value = "Unrecognized boolean value. Expected: 'true' or 'false'."),
            @MapEntry(classKey = RecordWithBooleanArrayField.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'."),
            @MapEntry(classKey = RecordWithBooleanListField.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'.")
    })
    public void arrayOfBooleansMixedWithOtherTypesAtObjectField(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [true, false, 1]}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = int[].class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = int.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = boolean.class, value = "Unrecognized boolean value. Expected: 'true' or 'false'."),
            @MapEntry(classKey = Boolean.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'."),
            @MapEntry(classKey = boolean[][].class, value = "Expected '[' but got: 't'."),
            @MapEntry(classKey = Boolean[][].class, value = "Expected '[' but got: 't'.")
    })
    public void mismatchedTypeForArrayOfBooleansAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[true, false]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = boolean[].class, value = "Expected '[' but got: '{'."),
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = RecordWithIntegerField.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = RecordWithPrimitiveBooleanField.class, value = "Unrecognized boolean value. Expected: 'true' or 'false'."),
            @MapEntry(classKey = RecordWithStringField.class, value = "Invalid value starting at 10. Expected either string or 'null'."),
            @MapEntry(classKey = boolean.class, value = "Unrecognized boolean value. Expected: 'true' or 'false'."),
            @MapEntry(classKey = Boolean.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'.")
    })
    public void mismatchedTypeForArrayOfBooleansAtObjectField(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [true, false]}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Boolean[].class, nulls = false)
    public void objectWithArrayOfBooleans(String jsonStr, Boolean[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithBooleanArrayField object = parser.parse(json, json.length, RecordWithBooleanArrayField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = boolean[].class, nulls = false)
    public void objectWithArrayOfPrimitiveBooleans(String jsonStr, boolean[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithPrimitiveBooleanArrayField object = parser.parse(json, json.length, RecordWithPrimitiveBooleanArrayField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Boolean[].class, nulls = false)
    public void objectWithListOfBooleans(String jsonStr, Boolean[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithBooleanListField object = parser.parse(json, json.length, RecordWithBooleanListField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = Boolean[].class, nulls = true)
    public void objectWithListOfBooleansAndNulls(String jsonStr, Boolean[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        RecordWithBooleanListField object = parser.parse(json, json.length, RecordWithBooleanListField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @Test
    public void missingBooleanField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"intField\": 1}");

        // when
        RecordWithBooleanField object = parser.parse(json, json.length, RecordWithBooleanField.class);

        // then
        assertThat(object.field()).isNull();
    }

    @Test
    public void missingPrimitiveBooleanField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"intField\": 1}");

        // when
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveBooleanField.class)
        );

        // then
        assertThat(ex.getCause()).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {boolean.class, Boolean.class})
    public void emptyJson(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("No structural element found.");
    }

    @ParameterizedTest
    @ValueSource(classes = {boolean.class, Boolean.class})
    public void passedLengthSmallerThanTrueLength(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("true");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'true'.");
    }

    @ParameterizedTest
    @ValueSource(classes = {boolean.class, Boolean.class})
    public void passedLengthSmallerThanFalseLength(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("false");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 4, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'false'.");
    }

    @Test
    public void passedLengthSmallerThanNullLength() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3, Boolean.class));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'null'.");
    }
}
