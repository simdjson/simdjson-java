package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.simdjson.annotations.JsonFieldName;
import org.simdjson.schemas.ClassWithIntegerField;
import org.simdjson.schemas.ClassWithPrimitiveBooleanField;
import org.simdjson.schemas.ClassWithPrimitiveByteField;
import org.simdjson.schemas.ClassWithPrimitiveCharacterField;
import org.simdjson.schemas.ClassWithPrimitiveDoubleField;
import org.simdjson.schemas.ClassWithPrimitiveFloatField;
import org.simdjson.schemas.ClassWithPrimitiveIntegerField;
import org.simdjson.schemas.ClassWithPrimitiveLongField;
import org.simdjson.schemas.ClassWithPrimitiveShortField;
import org.simdjson.schemas.ClassWithStringField;
import org.simdjson.schemas.RecordWithIntegerField;
import org.simdjson.schemas.RecordWithPrimitiveBooleanField;
import org.simdjson.schemas.RecordWithPrimitiveByteField;
import org.simdjson.schemas.RecordWithPrimitiveCharacterField;
import org.simdjson.schemas.RecordWithPrimitiveDoubleField;
import org.simdjson.schemas.RecordWithPrimitiveFloatField;
import org.simdjson.schemas.RecordWithPrimitiveIntegerField;
import org.simdjson.schemas.RecordWithPrimitiveLongField;
import org.simdjson.schemas.RecordWithPrimitiveShortField;
import org.simdjson.schemas.RecordWithStringField;
import org.simdjson.testutils.MapEntry;
import org.simdjson.testutils.MapSource;
import org.simdjson.testutils.SchemaBasedRandomValueSource;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.TestUtils.padWithSpaces;
import static org.simdjson.TestUtils.toUtf8;
import static org.simdjson.testutils.SimdJsonAssertions.assertThat;

public class ObjectSchemaBasedParsingTest {

    @ParameterizedTest
    @ValueSource(classes = {
            RecordWithIntegerField.class,
            ClassWithIntegerField.class,
            ClassWithoutExplicitConstructor.class
    })
    public void emptyObject(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{}");

        // when
        Object object = parser.parse(json, json.length, expectedType);

        // then
        assertThat(object).isNotNull();
        assertThat(object).hasAllNullFieldsOrProperties();
    }

    @ParameterizedTest
    @ValueSource(classes = {
            RecordWithPrimitiveByteField.class,
            RecordWithPrimitiveShortField.class,
            RecordWithPrimitiveIntegerField.class,
            RecordWithPrimitiveLongField.class,
            RecordWithPrimitiveBooleanField.class,
            RecordWithPrimitiveFloatField.class,
            RecordWithPrimitiveDoubleField.class,
            RecordWithPrimitiveCharacterField.class,
            ClassWithPrimitiveByteField.class,
            ClassWithPrimitiveShortField.class,
            ClassWithPrimitiveIntegerField.class,
            ClassWithPrimitiveLongField.class,
            ClassWithPrimitiveBooleanField.class,
            ClassWithPrimitiveFloatField.class,
            ClassWithPrimitiveDoubleField.class,
            ClassWithPrimitiveCharacterField.class
    })
    public void emptyObjectWhenPrimitiveFieldsAreExpected(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{}");

        // when
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasCauseExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    public void nullAtRootWhenObjectIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        RecordWithPrimitiveByteField object = parser.parse(json, json.length, RecordWithPrimitiveByteField.class);

        // then
        assertThat(object).isNull();
    }

    @Test
    public void nullAtObjectFieldWhenObjectIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"nestedField\": null}");

        // when
        NestedRecordWithStringField object = parser.parse(json, json.length, NestedRecordWithStringField.class);

        // then
        assertThat(object).isNotNull();
        assertThat(object.nestedField()).isNull();
    }

    @Test
    public void recordWithExplicitFieldNames() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"ąćśńźż\": 1, \"\\u20A9\\u0E3F\": 2, \"αβγ\": 3, \"😀abc😀\": 4, \"fifth_field\": 5}");

        // when
        RecordWithExplicitFieldNames object = parser.parse(json, json.length, RecordWithExplicitFieldNames.class);

        // then
        assertThat(object.firstField()).isEqualTo(1);
        assertThat(object.secondField()).isEqualTo(2);
        assertThat(object.thirdField()).isEqualTo(3);
        assertThat(object.fourthField()).isEqualTo(4);
        assertThat(object.fifthField()).isEqualTo(5);
    }

    @Test
    public void classWithExplicitFieldNames() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"ąćśńźż\": 1, \"\\u20A9\\u0E3F\": 2, \"αβγ\": 3, \"😀abc😀\": 4, \"fifth_field\": 5}");

        // when
        StaticClassWithExplicitFieldNames object = parser.parse(json, json.length, StaticClassWithExplicitFieldNames.class);

        // then
        assertThat(object.getFirstField()).isEqualTo(1);
        assertThat(object.getSecondField()).isEqualTo(2);
        assertThat(object.getThirdField()).isEqualTo(3);
        assertThat(object.getFourthField()).isEqualTo(4);
        assertThat(object.getFifthField()).isEqualTo(5);
    }

    @Test
    public void recordWithImplicitAndExplicitFieldNames() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"implicitField\": \"abc\", \"explicit_field\": \"def\"}");

        // when
        RecordWithImplicitAndExplicitFieldNames object = parser.parse(json, json.length, RecordWithImplicitAndExplicitFieldNames.class);

        // then
        assertThat(object.implicitField()).isEqualTo("abc");
        assertThat(object.explicitField()).isEqualTo("def");
    }

    @ParameterizedTest
    @ValueSource(classes = {StaticClassWithImplicitAndExplicitFieldNames.class, StaticClassWithImplicitFieldNames.class})
    public void classWithImplicitFieldNames(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"firstField\": \"abc\", \"second_field\": \"def\"}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Some of " + expectedType.getName() + "'s constructor arguments are not annotated with @JsonFieldName.");
    }

    @Test
    public void nonStaticInnerClassesAreUnsupported() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": \"abc\"}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, NonStaticInnerClass.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Unsupported class: " + NonStaticInnerClass.class.getName() + ". Inner non-static classes are not supported.");
    }

    @Test
    public void fieldNamesWithEscapes() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"\\\"abc\\\\\": 1}");

        // when
        RecordWithEscapedFieldName jsonValue = parser.parse(json, json.length, RecordWithEscapedFieldName.class);

        // then
        assertThat(jsonValue.firstField()).isEqualTo(1);
    }

    @Test
    public void fieldExistsInJsonButDoesNotExistInRecord() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"first\": 1, \"field\": 2, \"second\": 3}");

        // when
        RecordWithIntegerField jsonValue = parser.parse(json, json.length, RecordWithIntegerField.class);

        // then
        assertThat(jsonValue.field()).isEqualTo(2);
    }

    @Test
    public void fieldDoesNotExistInJsonButExistsInRecord() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"first\": 1, \"second\": 3}");

        // when
        RecordWithIntegerField jsonValue = parser.parse(json, json.length, RecordWithIntegerField.class);

        // then
        assertThat(jsonValue.field()).isNull();
    }

    @Test
    public void primitiveFieldDoesNotExistInJsonButExistsInRecord() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"first\": 1, \"second\": 3}");

        // when
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(json, json.length, RecordWithPrimitiveIntegerField.class)
        );

        // then
        assertThat(ex)
                .hasCauseExactlyInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {NestedRecordWithStringField.class, NestedStaticClassWithStringField.class})
    public void objectWithEmptyObjectField(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"nestedField\": {}}");

        // when
        Object object = parser.parse(json, json.length, expectedType);

        // then
        assertThat(object).isNotNull();
        assertThat(object).hasNoNullFieldsOrProperties();
        assertThat(object).extracting("nestedField").hasFieldOrPropertyWithValue("field", null);
    }

    @Test
    public void objectWithObjectFieldToRecord() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"nestedField\": {\"field\": \"abc\"}}");

        // when
        NestedRecordWithStringField object = parser.parse(json, json.length, NestedRecordWithStringField.class);

        // then
        assertThat(object).isNotNull();
        assertThat(object.nestedField()).isNotNull();
        assertThat(object.nestedField().field()).isEqualTo("abc");
    }

    @Test
    public void mismatchedTypeAtRootWhenObjectIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("\"{}\"");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Expected '{' but got: '\"'.");
    }

    @Test
    public void mismatchedTypeAtObjectFieldWhenObjectIsExpected() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"nestedField\": true}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, NestedRecordWithStringField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Expected '{' but got: 't'.");
    }

    @Test
    public void invalidButParsableJson() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": 1, : 2}");

        // when
        RecordWithIntegerField object = parser.parse(json, json.length, RecordWithIntegerField.class);

        // then
        assertThat(object.field()).isEqualTo(1);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "{\"invalid\", \"field\": 1}", value = "Expected ':' but got: ','."),
            @MapEntry(stringKey = "{\"field\": 1, \"invalid\"}", value = "More than one JSON value at the root of the document, or extra characters at the end of the JSON!")
    })
    public void fieldWithoutValue(String jsonStr, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerField.class)
        );

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "{\"invalid\" 2, \"field\": 1}", value = "Expected ':' but got: '2'."),
            @MapEntry(stringKey = "{\"field\": 1, \"invalid\" 2}", value = "More than one JSON value at the root of the document, or extra characters at the end of the JSON!")
    })
    public void missingColon(String jsonStr, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerField.class)
        );

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"invalid\": 2 \"field\": 1}",
            "{\"field\": 1 \"invalid\" 2}",
    })
    public void missingComma(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Expected ',' but got: '\"'.");
    }

    @Test
    public void fieldWithoutName() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{: 2, \"field\": 1}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Expected '\"' but got: ':'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\null", "1", "true", "false", "[]", "{}"})
    public void invalidTypeOfFieldName(String fieldName) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{" + fieldName + ": 1}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Expected '\"' but got: '" + fieldName.charAt(0) + "'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"field\": 1", "{\"field\":", "{\"field\"", "{", "{\"ignore\": {\"field\": 1", "{\"field\": 1,",})
    public void unclosedObject(String jsonStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerField.class)
        );

        // then
        assertThat(ex).hasMessage("Unclosed object. Missing '}' for starting '{'.");
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = RecordWithIntegerField[].class, nulls = true)
    public void arrayOfObjectsAtRoot(String jsonStr, RecordWithIntegerField[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        RecordWithIntegerField[] array = parser.parse(json, json.length, RecordWithIntegerField[].class);

        // then
        assertThat(array).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = RecordWithIntegerField[].class, nulls = true)
    public void arrayOfObjectsAtObjectField(String jsonStr, RecordWithIntegerField[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        ArrayOfRecordsWithIntegerField object = parser.parse(json, json.length, ArrayOfRecordsWithIntegerField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = RecordWithIntegerField[].class, nulls = true)
    public void listOfObjectsAtObjectField(String jsonStr, RecordWithIntegerField[] expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": " + jsonStr + "}");

        // when
        ListOfRecordsWithIntegerField object = parser.parse(json, json.length, ListOfRecordsWithIntegerField.class);

        // then
        assertThat(object.field()).containsExactly(expected);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(stringKey = "{},", value = "Unclosed object. Missing '}' for starting '{'."),
            @MapEntry(stringKey = "{\"field\": 1},", value = "Unclosed object. Missing '}' for starting '{'."),
            @MapEntry(stringKey = "{\"field\": 1}[]", value = "Unclosed object. Missing '}' for starting '{'."),
            @MapEntry(stringKey = "{\"field\": 1}{}", value = "More than one JSON value at the root of the document, or extra characters at the end of the JSON!"),
            @MapEntry(stringKey = "{\"field\": 1}1", value = "Unclosed object. Missing '}' for starting '{'."),
            @MapEntry(stringKey = "null,", value = "More than one JSON value at the root of the document, or extra characters at the end of the JSON!")
    })
    public void moreValuesThanOneObjectAtRoot(String jsonStr, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, RecordWithIntegerField.class));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @Test
    public void classWithMultipleConstructors() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": 1, \"field2\": 2}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, ClassWithMultipleConstructors.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Class: " + ClassWithMultipleConstructors.class.getName() + " has more than one constructor.");
    }

    @Test
    public void recordWithMultipleConstructors() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": 1, \"field2\": 2}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithMultipleConstructors.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Class: " + RecordWithMultipleConstructors.class.getName() + " has more than one constructor.");
    }

    @Test
    public void missingObjectField() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"intField\": 1}");

        // when
        NestedRecordWithStringField object = parser.parse(json, json.length, NestedRecordWithStringField.class);

        // then
        assertThat(object.nestedField()).isNull();
    }

    @Test
    public void objectInstantiationFailure() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": 1}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, ClassWithFailingConstructor.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Failed to construct an instance of " + ClassWithFailingConstructor.class.getName())
                .hasCauseExactlyInstanceOf(InvocationTargetException.class);
    }

    @Test
    public void emptyJson() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithIntegerField.class)
        );

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
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, 3, RecordWithIntegerField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'null'.");
    }

    @Test
    public void genericClassesOtherThanListAreNotSupported() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": {\"field\": 123}}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithGenericField.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Parametrized types other than java.util.List are not supported.");
    }

    @Test
    public void listsWithoutElementTypeAreNotSupported() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [1, 2, 3]}");

        // when
        JsonParsingException ex = assertThrows(
                JsonParsingException.class,
                () -> parser.parse(json, json.length, RecordWithListWithoutElementType.class)
        );

        // then
        assertThat(ex)
                .hasMessage("Undefined list element type.");
    }

    private record RecordWithExplicitFieldNames(@JsonFieldName("ąćśńźż") long firstField,
                                                @JsonFieldName("\u20A9\u0E3F") long secondField,
                                                @JsonFieldName("αβγ") long thirdField,
                                                @JsonFieldName("😀abc😀") long fourthField,
                                                @JsonFieldName("fifth_field") long fifthField) {
    }

    private static class StaticClassWithExplicitFieldNames {

        private final long firstField;
        private final long secondField;
        private final long thirdField;
        private final long fourthField;
        private final long fifthField;

        private StaticClassWithExplicitFieldNames(@JsonFieldName("ąćśńźż") long firstField,
                                                  @JsonFieldName("\u20A9\u0E3F") long secondField,
                                                  @JsonFieldName("αβγ") long thirdField,
                                                  @JsonFieldName("😀abc😀") long fourthField,
                                                  @JsonFieldName("fifth_field") long fifthField) {
            this.firstField = firstField;
            this.secondField = secondField;
            this.thirdField = thirdField;
            this.fourthField = fourthField;
            this.fifthField = fifthField;
        }

        public long getFirstField() {
            return firstField;
        }

        public long getSecondField() {
            return secondField;
        }

        public long getThirdField() {
            return thirdField;
        }

        public long getFourthField() {
            return fourthField;
        }

        public long getFifthField() {
            return fifthField;
        }
    }

    private record RecordWithImplicitAndExplicitFieldNames(String implicitField,
                                                           @JsonFieldName("explicit_field") String explicitField) {
    }

    private static class StaticClassWithImplicitAndExplicitFieldNames {

        private final String firstField;
        private final String secondField;

        StaticClassWithImplicitAndExplicitFieldNames(String firstField, @JsonFieldName("second_field") String secondField) {
            this.firstField = firstField;
            this.secondField = secondField;
        }

        String getFirstField() {
            return firstField;
        }

        String getSecondField() {
            return secondField;
        }
    }

    private static class StaticClassWithImplicitFieldNames {

        private final String firstField;
        private final String secondField;

        StaticClassWithImplicitFieldNames(String firstField, String secondField) {
            this.firstField = firstField;
            this.secondField = secondField;
        }

        String getFirstField() {
            return firstField;
        }

        String getSecondField() {
            return secondField;
        }
    }

    private record RecordWithEscapedFieldName(@JsonFieldName("\"abc\\") long firstField) {
    }

    private record NestedRecordWithStringField(RecordWithStringField nestedField) {

    }

    private static class NestedStaticClassWithStringField {

        private final ClassWithStringField nestedField;

        NestedStaticClassWithStringField(@JsonFieldName("nestedField") ClassWithStringField nestedField) {
            this.nestedField = nestedField;
        }

        ClassWithStringField getNestedField() {
            return nestedField;
        }
    }

    private record ArrayOfRecordsWithIntegerField(RecordWithIntegerField[] field) {

    }

    private record ListOfRecordsWithIntegerField(List<RecordWithIntegerField> field) {

    }

    private static class ClassWithMultipleConstructors {

        private final int field;
        private final int field2;

        ClassWithMultipleConstructors(@JsonFieldName("field") int field) {
            this.field = field;
            this.field2 = 0;
        }

        ClassWithMultipleConstructors(@JsonFieldName("field") int field, @JsonFieldName("field2") int field2) {
            this.field = field;
            this.field2 = field2;
        }
    }

    private record RecordWithMultipleConstructors(int field, int field2) {

        RecordWithMultipleConstructors(int field) {
            this(field, 0);
        }
    }

    private static class ClassWithFailingConstructor {

        ClassWithFailingConstructor(@JsonFieldName("field") int field) {
            throw new RuntimeException();
        }
    }

    private class NonStaticInnerClass {

        private final String field;

        NonStaticInnerClass(@JsonFieldName("field") String field) {
            this.field = field;
        }

        String getField() {
            return field;
        }
    }

    private record RecordWithGenericField(GenericRecord<Integer> field) {

    }

    private record GenericRecord<T>(T field) {

    }

    private record RecordWithListWithoutElementType(List field) {

    }

    private static class ClassWithoutExplicitConstructor {

    }
}
