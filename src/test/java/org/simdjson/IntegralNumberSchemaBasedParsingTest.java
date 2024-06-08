package org.simdjson;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.cartesian.CartesianTest;
import org.junitpioneer.jupiter.cartesian.CartesianTest.Values;
import org.simdjson.schemas.RecordWithBooleanField;
import org.simdjson.schemas.RecordWithByteArrayField;
import org.simdjson.schemas.RecordWithByteField;
import org.simdjson.schemas.RecordWithByteListField;
import org.simdjson.schemas.RecordWithIntegerArrayField;
import org.simdjson.schemas.RecordWithIntegerField;
import org.simdjson.schemas.RecordWithIntegerListField;
import org.simdjson.schemas.RecordWithLongArrayField;
import org.simdjson.schemas.RecordWithLongField;
import org.simdjson.schemas.RecordWithLongListField;
import org.simdjson.schemas.RecordWithPrimitiveBooleanField;
import org.simdjson.schemas.RecordWithPrimitiveByteArrayField;
import org.simdjson.schemas.RecordWithPrimitiveByteField;
import org.simdjson.schemas.RecordWithPrimitiveIntegerArrayField;
import org.simdjson.schemas.RecordWithPrimitiveIntegerField;
import org.simdjson.schemas.RecordWithPrimitiveLongArrayField;
import org.simdjson.schemas.RecordWithPrimitiveLongField;
import org.simdjson.schemas.RecordWithPrimitiveShortArrayField;
import org.simdjson.schemas.RecordWithPrimitiveShortField;
import org.simdjson.schemas.RecordWithShortArrayField;
import org.simdjson.schemas.RecordWithShortField;
import org.simdjson.schemas.RecordWithShortListField;
import org.simdjson.schemas.RecordWithStringField;
import org.simdjson.testutils.MapEntry;
import org.simdjson.testutils.MapSource;
import org.simdjson.testutils.RandomIntegralNumberSource;
import org.simdjson.testutils.SchemaBasedRandomValueSource;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.testutils.TestUtils.toUtf8;

public class IntegralNumberSchemaBasedParsingTest {

    @ParameterizedTest
    @RandomIntegralNumberSource(
            classes = {
                    Byte.class,
                    byte.class,
                    Short.class,
                    short.class,
                    Integer.class,
                    int.class,
                    Long.class,
                    long.class
            },
            includeMinMax = true
    )
    public void integralNumberAtRoot(Class<?> schema, String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, schema);

        // then
        assertThat(value).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(classes = {Byte.class, Short.class, Integer.class, Long.class})
    public void nullAtRootWhenIntegralNumberIsExpected(Class<?> schema) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        Object value = parser.parse(json, json.length, schema);

        // then
        assertThat(value).isNull();
    }

    @ParameterizedTest
    @ValueSource(classes = {byte.class, short.class, int.class, long.class})
    public void nullAtRootWhenPrimitiveIntegralNumberIsExpected(Class<?> schema) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, schema));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @RandomIntegralNumberSource(
            classes = {
                    RecordWithByteField.class,
                    RecordWithPrimitiveByteField.class,
                    RecordWithShortField.class,
                    RecordWithPrimitiveShortField.class,
                    RecordWithIntegerField.class,
                    RecordWithPrimitiveIntegerField.class,
                    RecordWithLongField.class,
                    RecordWithPrimitiveLongField.class
            },
            includeMinMax = true
    )
    public void integralNumberAtObjectField(Class<?> schema, String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object object = parser.parse(json, json.length, schema);

        // then
        assertThat(object).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            RecordWithByteField.class,
            RecordWithShortField.class,
            RecordWithIntegerField.class,
            RecordWithLongField.class
    })
    public void nullAtObjectFieldWhenIntegralNumberIsExpected(Class<?> schema) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        Object object = parser.parse(json, json.length, schema);

        // then
        assertThat(object).extracting("field").isNull();
    }

    @ParameterizedTest
    @ValueSource(classes = {
            RecordWithPrimitiveByteField.class,
            RecordWithPrimitiveShortField.class,
            RecordWithPrimitiveIntegerField.class,
            RecordWithPrimitiveLongField.class
    })
    public void nullAtObjectFieldWhenPrimitiveIntegralNumberIsExpected(Class<?> schema) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, schema));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(
            schemas = {
                    Byte[].class,
                    byte[].class,
                    Short[].class,
                    short[].class,
                    Integer[].class,
                    int[].class,
                    Long[].class,
                    long[].class
            },
            nulls = false
    )
    public void arrayOfIntegralNumbersAtRoot(Class<?> schema, String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object array = parser.parse(json, json.length, schema);

        // then
        assertThat(array.getClass().isArray()).isTrue();
        assertThat(array).isEqualTo(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(
            schemas = {
                    Byte[].class,
                    Short[].class,
                    Integer[].class,
                    Long[].class
            },
            nulls = true
    )
    public void arrayOfIntegralNumbersAndNullsAtRoot(Class<?> schema, String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object array = parser.parse(json, json.length, schema);

        // then
        assertThat(array.getClass().isArray()).isTrue();
        assertThat(array).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(classes = {byte.class, short.class, int.class, long.class})
    public void arrayOfPrimitiveIntegralNumbersAndNullsAtRoot(Class<?> schema) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[-128, 1, 127, null]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, schema));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(
            schemas = {
                    RecordWithByteArrayField.class,
                    RecordWithPrimitiveByteArrayField.class,
                    RecordWithShortArrayField.class,
                    RecordWithPrimitiveShortArrayField.class,
                    RecordWithIntegerArrayField.class,
                    RecordWithPrimitiveIntegerArrayField.class,
                    RecordWithLongArrayField.class,
                    RecordWithPrimitiveLongArrayField.class
            },
            nulls = false
    )
    public void objectWithArrayOfIntegralNumbers(Class<?> schema, String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object object = parser.parse(json, json.length, schema);

        // then
        assertThat(object).usingRecursiveComparison().isEqualTo(expected);
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(
            schemas = {
                    RecordWithByteArrayField.class,
                    RecordWithByteListField.class,
                    RecordWithShortArrayField.class,
                    RecordWithShortListField.class,
                    RecordWithIntegerArrayField.class,
                    RecordWithIntegerListField.class,
                    RecordWithLongArrayField.class,
                    RecordWithLongListField.class
            },
            nulls = true
    )
    public void objectWithArrayOfIntegralNumbersWithNulls(Class<?> schema, String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object object = parser.parse(json, json.length, schema);

        // then
        assertThat(object).usingRecursiveComparison().isEqualTo(expected);
    }

    @CartesianTest
    public void outOfPrimitiveByteRange(
            @Values(classes = {byte.class, Byte.class}) Class<?> expectedType,
            @Values(strings = {
                    "-9223372036854775809",
                    "-129",
                    "128",
                    "9223372036854775808"
            }) String numStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Number value is out of byte range ([-128, 127]).");
    }

    @CartesianTest
    public void outOfPrimitiveShortRange(
            @Values(classes = {short.class, Short.class}) Class<?> expectedType,
            @Values(strings = {
                    "-9223372036854775809",
                    "-32769",
                    "32768",
                    "9223372036854775808"
            }) String numStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Number value is out of short range ([-32768, 32767]).");
    }

    @CartesianTest
    public void outOfPrimitiveIntegerRange(
            @Values(classes = {int.class, Integer.class}) Class<?> expectedType,
            @Values(strings = {
                    "-9223372036854775809",
                    "-2147483649",
                    "2147483648",
                    "9223372036854775808"
            }) String numStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Number value is out of int range ([-2147483648, 2147483647]).");
    }

    @CartesianTest
    public void outOfPrimitiveLongRange(
            @Values(classes = {long.class, Long.class}) Class<?> expectedType,
            @Values(strings = {
                    "9223372036854775808",
                    "9999999999999999999",
                    "10000000000000000000",
                    "-9223372036854775809",
                    "-9999999999999999999",
                    "-10000000000000000000"
            }) String numStr) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Number value is out of long range ([-9223372036854775808, 9223372036854775807]).");
    }

    @CartesianTest
    public void leadingZerosAreNotAllowed(
            @Values(strings = {"01", "-01", "000", "-000"}) String jsonStr,
            @Values(classes = {
                    byte.class,
                    Byte.class,
                    short.class,
                    Short.class,
                    int.class,
                    Integer.class,
                    long.class,
                    Long.class
            }) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Leading zeroes are not allowed.");
    }

    @CartesianTest
    public void minusHasToBeFollowedByAtLeastOneDigit(
            @Values(strings = {"-a123", "--123", "-+123"}) String jsonStr,
            @Values(classes = {
                    byte.class,
                    Byte.class,
                    short.class,
                    Short.class,
                    int.class,
                    Integer.class,
                    long.class,
                    Long.class
            }) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @CartesianTest
    public void numberHasToBeFollowedByStructuralCharacterOrWhitespace(
            @Values(strings = {"-1-2", "1a"}) String jsonStr,
            @Values(classes = {
                    byte.class,
                    Byte.class,
                    short.class,
                    Short.class,
                    int.class,
                    Integer.class,
                    long.class,
                    Long.class
            }) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Number has to be followed by a structural character or whitespace.");
    }

    @CartesianTest
    public void moreValuesThanOneIntegralNumberAtRoot(
            @Values(strings = {"123,", "123{}", "1:"}) String jsonStr,
            @Values(classes = {
                    byte.class,
                    Byte.class,
                    short.class,
                    Short.class,
                    int.class,
                    Integer.class,
                    long.class,
                    Long.class
            }) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @CartesianTest
    public void floatingPointNumberAsIntegralNumber(
            @Values(strings = {"1.0", "-1.0", "1e1", "1.9e1"}) String jsonStr,
            @Values(classes = {
                    byte.class,
                    Byte.class,
                    short.class,
                    Short.class,
                    int.class,
                    Integer.class,
                    long.class,
                    Long.class
            }) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Number has to be followed by a structural character or whitespace.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = BigInteger.class, value = "Class: java.math.BigInteger has more than one constructor."),
            @MapEntry(classKey = Number.class, value = "Unsupported class: java.lang.Number. Interfaces and abstract classes are not supported."),
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = Boolean.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'."),
            @MapEntry(classKey = byte[].class, value = "Expected '[' but got: '1'.")
    })
    public void mismatchedTypeForIntegralNumberAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("123");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = RecordWithStringField.class, value = "Invalid value starting at 10. Expected either string or 'null'."),
            @MapEntry(classKey = RecordWithBooleanField.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'."),
            @MapEntry(classKey = RecordWithByteArrayField.class, value = "Expected '[' but got: '1'.")
    })
    public void mismatchedTypeForIntegralNumberAtObjectField(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": 123}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            byte[].class,
            Byte[].class,
            short[].class,
            Short[].class,
            int[].class,
            Integer[].class,
            long[].class,
            Long[].class
    })
    public void arrayOfIntegralNumbersMixedWithOtherTypesAtRoot(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1, -1, true]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @ValueSource(classes = {
            RecordWithByteArrayField.class,
            RecordWithPrimitiveByteArrayField.class,
            RecordWithByteListField.class,
            RecordWithShortArrayField.class,
            RecordWithPrimitiveShortArrayField.class,
            RecordWithShortListField.class,
            RecordWithIntegerArrayField.class,
            RecordWithPrimitiveIntegerArrayField.class,
            RecordWithIntegerListField.class,
            RecordWithLongArrayField.class,
            RecordWithPrimitiveLongArrayField.class,
            RecordWithLongListField.class
    })
    public void arrayOfIntegralNumbersMixedWithOtherTypesAtObjectField(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [1, -1, true]}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = BigInteger[].class, value = "Class: java.math.BigInteger has more than one constructor."),
            @MapEntry(classKey = Number[].class, value = "Unsupported class: java.lang.Number. Interfaces and abstract classes are not supported."),
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = int.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = byte.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = Byte.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = byte[][].class, value = "Expected '[' but got: '1'."),
            @MapEntry(classKey = Byte[][].class, value = "Expected '[' but got: '1'.")
    })
    public void mismatchedTypeForArrayOfIntegralNumbersAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1, -1, 0]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = byte[].class, value = "Expected '[' but got: '{'."),
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = RecordWithBooleanField.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'."),
            @MapEntry(classKey = RecordWithPrimitiveBooleanField.class, value = "Unrecognized boolean value. Expected: 'true' or 'false'."),
            @MapEntry(classKey = RecordWithStringField.class, value = "Invalid value starting at 10. Expected either string or 'null'."),
            @MapEntry(classKey = byte.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = Byte.class, value = "Invalid number. Minus has to be followed by a digit.")
    })
    public void mismatchedTypeForArrayOfIntegralNumbersAtObjectField(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [1, -1, 0]}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            byte.class,
            Byte.class,
            short.class,
            Short.class,
            int.class,
            Integer.class,
            long.class,
            Long.class
    })
    public void startingWithPlusIsNotAllowed(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("+1");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @CartesianTest
    public void numberHasToStartWithMinusOrDigit(
            @Values(strings = {"a123", "a-123"}) String jsonStr,
            @Values(classes = {
                    byte.class,
                    Byte.class,
                    short.class,
                    Short.class,
                    int.class,
                    Integer.class,
                    long.class,
                    Long.class
            }) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @ValueSource(classes = {byte.class, Byte.class})
    public void minusZeroIsTreatedAsByteZero(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("-0");

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo((byte) 0);
    }

    @ParameterizedTest
    @ValueSource(classes = {short.class, Short.class})
    public void minusZeroIsTreatedAsShortZero(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("-0");

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo((short) 0);
    }

    @ParameterizedTest
    @ValueSource(classes = {int.class, Integer.class})
    public void minusZeroIsTreatedAsIntegerZero(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("-0");

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(classes = {long.class, Long.class})
    public void minusZeroIsTreatedAsLongZero(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("-0");

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0L);
    }

    @ParameterizedTest
    @ValueSource(classes = {Byte.class, byte.class, Short.class, short.class, Integer.class, int.class, Long.class, long.class})
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
    @ValueSource(classes = {Byte.class, Short.class, Integer.class, Long.class})
    public void passedLengthSmallerThanNullLength(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'null'.");
    }

    @ParameterizedTest
    @ValueSource(classes = {byte.class, Byte.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class})
    public void passedLengthSmallerThanNumberLength(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("1234");

        // when
        Object value = parser.parse(json, 2, expectedType);

        // then
        assertThat(value.toString()).isEqualTo("12");
    }
}
