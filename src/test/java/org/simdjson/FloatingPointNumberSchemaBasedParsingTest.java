package org.simdjson;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.cartesian.CartesianTest;
import org.junitpioneer.jupiter.cartesian.CartesianTest.Values;
import org.simdjson.schemas.RecordWithBooleanField;
import org.simdjson.schemas.RecordWithByteArrayField;
import org.simdjson.schemas.RecordWithDoubleArrayField;
import org.simdjson.schemas.RecordWithDoubleField;
import org.simdjson.schemas.RecordWithDoubleListField;
import org.simdjson.schemas.RecordWithFloatArrayField;
import org.simdjson.schemas.RecordWithFloatField;
import org.simdjson.schemas.RecordWithFloatListField;
import org.simdjson.schemas.RecordWithPrimitiveBooleanField;
import org.simdjson.schemas.RecordWithPrimitiveDoubleArrayField;
import org.simdjson.schemas.RecordWithPrimitiveDoubleField;
import org.simdjson.schemas.RecordWithPrimitiveFloatArrayField;
import org.simdjson.schemas.RecordWithPrimitiveFloatField;
import org.simdjson.schemas.RecordWithStringField;
import org.simdjson.testutils.CartesianTestCsv;
import org.simdjson.testutils.CartesianTestCsvRow;
import org.simdjson.testutils.FloatingPointNumberTestFile;
import org.simdjson.testutils.FloatingPointNumberTestFile.FloatingPointNumberTestCase;
import org.simdjson.testutils.FloatingPointNumberTestFilesSource;
import org.simdjson.testutils.MapEntry;
import org.simdjson.testutils.MapSource;
import org.simdjson.testutils.SchemaBasedRandomValueSource;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.TestUtils.padWithSpaces;
import static org.simdjson.TestUtils.toUtf8;

public class FloatingPointNumberSchemaBasedParsingTest {

    @ParameterizedTest
    @SchemaBasedRandomValueSource(schemas = {Float.class, float.class, Double.class, double.class}, nulls = false)
    public void floatingPointNumberAtRoot(String numberStr, Class<?> schema, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numberStr);

        // when
        Object number = parser.parse(json, json.length, schema);

        // then
        assertThat(number).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(classes = {Float.class, Double.class})
    public void nullAtRootWhenFloatingPointNumberIsExpected(Class<?> schema) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("null");

        // when
        Object value = parser.parse(json, json.length, schema);

        // then
        assertThat(value).isNull();
    }

    @ParameterizedTest
    @ValueSource(classes = {float.class, double.class})
    public void nullAtRootWhenPrimitiveFloatingPointNumberIsExpected(Class<?> schema) {
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
    @SchemaBasedRandomValueSource(
            schemas = {
                    RecordWithFloatField.class,
                    RecordWithPrimitiveFloatField.class,
                    RecordWithDoubleField.class,
                    RecordWithPrimitiveDoubleField.class
            },
            nulls = false
    )
    public void floatingPointNumberAtObjectField(Class<?> schema, String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object object = parser.parse(json, json.length, schema);

        // then
        assertThat(object).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(classes = {RecordWithFloatField.class, RecordWithDoubleField.class})
    public void nullAtObjectFieldWhenFloatingPointNumberIsExpected(Class<?> schema) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": null}");

        // when
        Object object = parser.parse(json, json.length, schema);

        // then
        assertThat(object).extracting("field").isNull();
    }

    @ParameterizedTest
    @ValueSource(classes = {RecordWithPrimitiveFloatField.class, RecordWithPrimitiveDoubleField.class})
    public void nullAtObjectFieldWhenPrimitiveFloatingPointNumberIsExpected(Class<?> schema) {
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
    @SchemaBasedRandomValueSource(schemas = {Float[].class, float[].class, Double[].class, double[].class}, nulls = false)
    public void arrayOfFloatingPointNumbersAtRoot(Class<?> schema, String jsonStr, Object expected) {
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
    @SchemaBasedRandomValueSource(schemas = {Float[].class, Double[].class}, nulls = true)
    public void arrayOfFloatingPointNumbersAndNullsAtRoot(Class<?> schema, String jsonStr, Object expected) {
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
    @ValueSource(classes = {float.class, double.class})
    public void arrayOfPrimitiveFloatingPointNumbersAndNullsAtRoot(Class<?> schema) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[-1.1, 1.0, 0.0, null]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, schema));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @SchemaBasedRandomValueSource(
            schemas = {
                    RecordWithFloatArrayField.class,
                    RecordWithPrimitiveFloatArrayField.class,
                    RecordWithDoubleArrayField.class,
                    RecordWithPrimitiveDoubleArrayField.class
            },
            nulls = false
    )
    public void objectWithArrayOfFloatingPointNumbers(Class<?> schema, String jsonStr, Object expected) {
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
                    RecordWithFloatArrayField.class,
                    RecordWithFloatListField.class,
                    RecordWithDoubleArrayField.class,
                    RecordWithDoubleListField.class
            },
            nulls = true
    )
    public void objectWithArrayOfFloatingPointNumbersWithNulls(Class<?> schema, String jsonStr, Object expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object object = parser.parse(json, json.length, schema);

        // then
        assertThat(object).usingRecursiveComparison().isEqualTo(expected);
    }

    @CartesianTest
    public void leadingZerosAreNotAllowed(
            @Values(strings = {"01.0", "-01.0", "000.0", "-000.0", "012e34"}) String jsonStr,
            @Values(classes = {float.class, Float.class, Double.class, double.class}) Class<?> expectedType) {
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
            @Values(strings = {"-a123.0", "--123.0", "-+123.0", "-.123", "-e123",}) String jsonStr,
            @Values(classes = {float.class, Float.class, Double.class, double.class}) Class<?> expectedType) {
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
            @Values(strings = {"-1.0-2", "1.0a", "12E12.12", "1e2e3"}) String jsonStr,
            @Values(classes = {float.class, Float.class, Double.class, double.class}) Class<?> expectedType) {
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
    public void decimalPointHasToBeFollowedByAtLeastOneDigit(
            @Values(strings = {"123.", "1..1", "1.e1", "1.E1"}) String jsonStr,
            @Values(classes = {float.class, Float.class, Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Decimal point has to be followed by a digit.");
    }

    @CartesianTest
    public void exponentIndicatorHasToBeFollowedByAtLeastOneDigit(
            @Values(strings = {"1e+-2", "1E+-2", "1e--23", "1E--23", "1ea", "1Ea", "1e", "1E", "1e+", "1E+"}) String jsonStr,
            @Values(classes = {float.class, Float.class, Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Exponent indicator has to be followed by a digit.");
    }

    @ParameterizedTest
    @ValueSource(classes = {float.class, Float.class, Double.class, double.class})
    public void startingWithPlusIsNotAllowed(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("+1.0");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @CartesianTest
    public void numberHasToStartWithMinusOrDigit(
            @Values(strings = {"a123", "a-123"}) String jsonStr,
            @Values(classes = {float.class, Float.class, Double.class, double.class}) Class<?> expectedType) {
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
    public void positiveDoubleZero(
            @Values(strings = {
                    "0.0",
                    "2251799813685248e-342",
                    "9999999999999999999e-343",
                    "1.23e-341",
                    "123e-343",
                    "0.0e-999",
                    "0e9999999999999999999999999999",
                    "18446744073709551615e-343",
                    "0.099999999999999999999e-323",
                    "0.99999999999999999999e-324",
                    "0.9999999999999999999e-324"
            }) String jsonStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0.0d);
    }

    @CartesianTest
    public void negativeDoubleZero(
            @Values(strings = {
                    "-0.0",
                    "-2251799813685248e-342",
                    "-9999999999999999999e-343",
                    "-1.23e-341",
                    "-123e-343",
                    "-0.0e-999",
                    "-0e9999999999999999999999999999",
                    "-18446744073709551615e-343",
                    "-0.099999999999999999999e-323",
                    "-0.99999999999999999999e-324",
                    "-0.9999999999999999999e-324"
            }) String jsonStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(-0.0d);
    }

    @CartesianTest
    public void positiveFloatZero(
            @Values(strings = {
                    "0.0",
                    "1e-58",
                    "1e-64",
                    "0.0e-999",
                    "0e9999999999999999999999999999",
                    "18446744073709551615e-66",
                    "0.99999999999999999999e-46"
            }) String jsonStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0.0f);
    }

    @CartesianTest
    public void negativeFloatZero(
            @Values(strings = {
                    "-0.0",
                    "-1e-58",
                    "-1e-64",
                    "-0.0e-999",
                    "-0e9999999999999999999999999999",
                    "-18446744073709551615e-66",
                    "-0.99999999999999999999e-46"
            }) String jsonStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(-0.0f);
    }

    @CartesianTest
    public void exactDouble(
            @CartesianTestCsv({
                    "9007199254740991.0, 9007199254740991",
                    "9007199254740992.0, 9007199254740992",
                    "18014398509481988.0, 18014398509481988"
            }) CartesianTestCsvRow row,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(row.getValueAsString(0));

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(row.getValueAsDouble(1));
    }

    @CartesianTest
    public void exactFloat(
            @CartesianTestCsv({
                    "16777215.0, 16777215",
                    "16777216.0, 16777216",
                    "33554436.0, 33554436"
            }) CartesianTestCsvRow row,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(row.getValueAsString(0));

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(row.getValueAsFloat(1));
    }

    @CartesianTest
    public void minNormalDouble(
            @Values(strings = {
                    "2.2250738585072016e-308",
                    "2.2250738585072015e-308",
                    "2.2250738585072014e-308",
                    "2.2250738585072013e-308",
                    "2.2250738585072012e-308"
            }) String jsonStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x1.0p-1022d);
    }

    @CartesianTest
    public void minNormalFloat(
            @Values(strings = {
                    "1.17549433E-38",
                    "1.17549434E-38",
                    "1.17549435E-38",
                    "1.17549436E-38",
                    "1.17549437E-38"
            }) String jsonStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x1.0p-126f);
    }

    @CartesianTest
    public void maxSubnormalDouble(
            @Values(strings = {
                    "2.2250738585072011e-308",
                    "2.2250738585072010e-308",
                    "2.2250738585072009e-308",
                    "2.2250738585072008e-308",
                    "2.2250738585072007e-308",
                    "0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022250738585072008890245868760858598876504231122409594654935248025624400092282356951787758888037591552642309780950434312085877387158357291821993020294379224223559819827501242041788969571311791082261043971979604000454897391938079198936081525613113376149842043271751033627391549782731594143828136275113838604094249464942286316695429105080201815926642134996606517803095075913058719846423906068637102005108723282784678843631944515866135041223479014792369585208321597621066375401613736583044193603714778355306682834535634005074073040135602968046375918583163124224521599262546494300836851861719422417646455137135420132217031370496583210154654068035397417906022589503023501937519773030945763173210852507299305089761582519159720757232455434770912461317493580281734466552734375",
            }) String jsonStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x0.fffffffffffffp-1022d);
    }

    @CartesianTest
    public void maxSubnormalFloat(
            @Values(strings = {
                    "1.1754942e-38",
                    "0.0000000000000000000000000000000000000117549421069244107548702944485",
            }) String jsonStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x0.fffffep-126f);
    }

    @CartesianTest
    public void minSubnormalDouble(
            @Values(strings = {
                    "3e-324",
                    "4.9e-324",
                    "4.9406564584124654e-324",
                    "4.94065645841246544176568792868e-324",
            }) String jsonStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x0.0000000000001p-1022d);
    }

    @CartesianTest
    public void minSubnormalFloat(
            @Values(strings = {
                    "1e-45",
                    "1.4e-45",
                    "1.4012984643248170e-45",
                    "1.40129846432481707092372958329e-45",
            }) String jsonStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x0.000002p-126f);
    }

    @CartesianTest
    public void maxDouble(
            @Values(strings = {
                    "1.7976931348623157e308",
                    "1.7976931348623158e308",
            }) String jsonStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x1.fffffffffffffp+1023d);
    }

    @CartesianTest
    public void maxFloat(
            @Values(strings = {
                    "3.4028234664e38",
                    "3.4028234665e38",
                    "3.4028234666e38",
            }) String jsonStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x1.fffffep+127f);
    }

    @CartesianTest
    public void positiveDoubleInfinity(
            @Values(strings = {
                    "1.9e308",
                    "1.8e308",
                    "1234456789012345678901234567890e9999999999999999999999999999",
                    "1.832312213213213232132132143451234453123412321321312e308",
                    "2139879401095466344511101915470454744.9813888656856943E+272",
                    "2e30000000000000000",
                    "2e3000",
                    "1234456789012345678901234567890e999999999999999999999999999",
                    "1.7976931348623159e308",
                    "1438456663141390273526118207642235581183227845246331231162636653790368152091394196930365828634687637948157940776599182791387527135353034738357134110310609455693900824193549772792016543182680519740580354365467985440183598701312257624545562331397018329928613196125590274187720073914818062530830316533158098624984118889298281371812288789537310599037529113415438738954894752124724983067241108764488346454376699018673078404751121414804937224240805993123816932326223683090770561597570457793932985826162604255884529134126396282202126526253389383421806727954588525596114379801269094096329805054803089299736996870951258573010877404407451953846698609198213926882692078557033228265259305481198526059813164469187586693257335779522020407645498684263339921905227556616698129967412891282231685504660671277927198290009824680186319750978665734576683784255802269708917361719466043175201158849097881370477111850171579869056016061666173029059588433776015644439705050377554277696143928278093453792803846252715966016733222646442382892123940052441346822429721593884378212558701004356924243030059517489346646577724622498919752597382095222500311124181823512251071356181769376577651390028297796156208815375089159128394945710515861334486267101797497111125909272505194792870889617179758703442608016143343262159998149700606597792535574457560429226974273443630323818747730771316763398572110874959981923732463076884528677392654150010269822239401993427482376513231389212353583573566376915572650916866553612366187378959554983566712767093372906030188976220169058025354973622211666504549316958271880975697143546564469806791358707318873075708383345004090151974068325838177531266954177406661392229801349994695941509935655355652985723782153570084089560139142231.738475042362596875449154552392299548947138162081694168675340677843807613129780449323363759027012972466987370921816813162658754726545121090545507240267000456594786540949605260722461937870630634874991729398208026467698131898691830012167897399682179601734569071423681e-733"
            }) String jsonStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @CartesianTest
    public void negativeDoubleInfinity(
            @Values(strings = {
                    "-1.9e308",
                    "-1.8e308",
                    "-1234456789012345678901234567890e9999999999999999999999999999",
                    "-1.832312213213213232132132143451234453123412321321312e308",
                    "-2139879401095466344511101915470454744.9813888656856943E+272",
                    "-2e30000000000000000",
                    "-2e3000",
                    "-1234456789012345678901234567890e999999999999999999999999999",
                    "-1.7976931348623159e308",
                    "-1438456663141390273526118207642235581183227845246331231162636653790368152091394196930365828634687637948157940776599182791387527135353034738357134110310609455693900824193549772792016543182680519740580354365467985440183598701312257624545562331397018329928613196125590274187720073914818062530830316533158098624984118889298281371812288789537310599037529113415438738954894752124724983067241108764488346454376699018673078404751121414804937224240805993123816932326223683090770561597570457793932985826162604255884529134126396282202126526253389383421806727954588525596114379801269094096329805054803089299736996870951258573010877404407451953846698609198213926882692078557033228265259305481198526059813164469187586693257335779522020407645498684263339921905227556616698129967412891282231685504660671277927198290009824680186319750978665734576683784255802269708917361719466043175201158849097881370477111850171579869056016061666173029059588433776015644439705050377554277696143928278093453792803846252715966016733222646442382892123940052441346822429721593884378212558701004356924243030059517489346646577724622498919752597382095222500311124181823512251071356181769376577651390028297796156208815375089159128394945710515861334486267101797497111125909272505194792870889617179758703442608016143343262159998149700606597792535574457560429226974273443630323818747730771316763398572110874959981923732463076884528677392654150010269822239401993427482376513231389212353583573566376915572650916866553612366187378959554983566712767093372906030188976220169058025354973622211666504549316958271880975697143546564469806791358707318873075708383345004090151974068325838177531266954177406661392229801349994695941509935655355652985723782153570084089560139142231.738475042362596875449154552392299548947138162081694168675340677843807613129780449323363759027012972466987370921816813162658754726545121090545507240267000456594786540949605260722461937870630634874991729398208026467698131898691830012167897399682179601734569071423681e-733"
            }) String jsonStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @CartesianTest
    public void positiveFloatInfinity(
            @Values(strings = {
                    "1.9e39",
                    "1.8e39",
                    "1.9e40",
                    "1.8e40",
                    "1234456789012345678901234567890e9999999999999999999999999999",
                    "3.532312213213213232132132143451234453123412321321312e38",
                    "2139879401095466344511101915470454744.9813888656856943E+3",
                    "2e30000000000000000",
                    "2e3000",
                    "3.4028236e38",
                    "1438456663141390273526118207642235581183227845246331231162636653790368152091394196930365828634687637948157940776599182791387527135353034738357134110310609455693900824193549772792016543182680519740580354365467985440183598701312257624545562331397018329928613196125590274187720073914818062530830316533158098624984118889298281371812288789537310599037529113415438738954894752124724983067241108764488346454376699018673078404751121414804937224240805993123816932326223683090770561597570457793932985826162604255884529134126396282202126526253389383421806727954588525596114379801269094096329805054803089299736996870951258573010877404407451953846698609198213926882692078557033228265259305481198526059813164469187586693257335779522020407645498684263339921905227556616698129967412891282231685504660671277927198290009824680186319750978665734576683784255802269708917361719466043175201158849097881370477111850171579869056016061666173029059588433776015644439705050377554277696143928278093453792803846252715966016733222646442382892123940052441346822429721593884378212558701004356924243030059517489346646577724622498919752597382095222500311124181823512251071356181769376577651390028297796156208815375089159128394945710515861334486267101797497111125909272505194792870889617179758703442608016143343262159998149700606597792535574457560429226974273443630323818747730771316763398572110874959981923732463076884528677392654150010269822239401993427482376513231389212353583573566376915572650916866553612366187378959554983566712767093372906030188976220169058025354973622211666504549316958271880975697143546564469806791358707318873075708383345004090151974068325838177531266954177406661392229801349994695941509935655355652985723782153570084089560139142231.738475042362596875449154552392299548947138162081694168675340677843807613129780449323363759027012972466987370921816813162658754726545121090545507240267000456594786540949605260722461937870630634874991729398208026467698131898691830012167897399682179601734569071423681e-733"
            }) String jsonStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Float.POSITIVE_INFINITY);
    }

    @CartesianTest
    public void negativeFloatInfinity(
            @Values(strings = {
                    "-1.9e39",
                    "-1.8e39",
                    "-1.9e40",
                    "-1.8e40",
                    "-1234456789012345678901234567890e9999999999999999999999999999",
                    "-3.532312213213213232132132143451234453123412321321312e38",
                    "-2139879401095466344511101915470454744.9813888656856943E+3",
                    "-2e30000000000000000",
                    "-2e3000",
                    "-3.4028236e38",
                    "-1438456663141390273526118207642235581183227845246331231162636653790368152091394196930365828634687637948157940776599182791387527135353034738357134110310609455693900824193549772792016543182680519740580354365467985440183598701312257624545562331397018329928613196125590274187720073914818062530830316533158098624984118889298281371812288789537310599037529113415438738954894752124724983067241108764488346454376699018673078404751121414804937224240805993123816932326223683090770561597570457793932985826162604255884529134126396282202126526253389383421806727954588525596114379801269094096329805054803089299736996870951258573010877404407451953846698609198213926882692078557033228265259305481198526059813164469187586693257335779522020407645498684263339921905227556616698129967412891282231685504660671277927198290009824680186319750978665734576683784255802269708917361719466043175201158849097881370477111850171579869056016061666173029059588433776015644439705050377554277696143928278093453792803846252715966016733222646442382892123940052441346822429721593884378212558701004356924243030059517489346646577724622498919752597382095222500311124181823512251071356181769376577651390028297796156208815375089159128394945710515861334486267101797497111125909272505194792870889617179758703442608016143343262159998149700606597792535574457560429226974273443630323818747730771316763398572110874959981923732463076884528677392654150010269822239401993427482376513231389212353583573566376915572650916866553612366187378959554983566712767093372906030188976220169058025354973622211666504549316958271880975697143546564469806791358707318873075708383345004090151974068325838177531266954177406661392229801349994695941509935655355652985723782153570084089560139142231.738475042362596875449154552392299548947138162081694168675340677843807613129780449323363759027012972466987370921816813162658754726545121090545507240267000456594786540949605260722461937870630634874991729398208026467698131898691830012167897399682179601734569071423681e-733"
            }) String jsonStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Float.NEGATIVE_INFINITY);
    }

    @CartesianTest
    public void roundingOverflowForDouble(
            @Values(strings = {
                    // In this case the binary significand after rounding up is equal to 9007199254740992 (2^53),
                    // which is more than we can store (2^53 - 1).
                    "7.2057594037927933e16",
                    "72057594037927933.0000000000000000",
            }) String jsonStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x1.0p+56d);
    }

    @CartesianTest
    public void roundingOverflowForFloat(
            @Values(strings = {
                    // In this case the binary significand after rounding up is equal to 16777216 (2^24),
                    // which is more than we can store (2^24 - 1).
                    "7.2057594e16",
                    "72057594000000000.0000000",
            }) String jsonStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(0x1.0p+56f);
    }

    @CartesianTest
    public void exponentWithMoreDigitsThanLongCanAccommodateAndLeadingZeros(
            @CartesianTestCsv({
                    "1e000000000000000000001, 10.0",
                    "1e-000000000000000000001, 0.1"
            }) CartesianTestCsvRow row,
            @Values(classes = {Float.class, float.class, Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(row.getValueAsString(0));

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(row.getValue(1, expectedType));
    }

    @CartesianTest
    public void exponentWithMoreDigitsThanLongCanAccommodate(
            @CartesianTestCsv({
                    "0e999999999999999999999, 0.0",
                    "0e-999999999999999999999, 0.0",
                    "1e999999999999999999999, Infinity",
                    "1e-999999999999999999999, 0.0",
                    "9999999999999999999999999999999999999999e-999999999999999999999, 0.0",
                    "0.9999999999999999999999999999999999999999e999999999999999999999, Infinity"
            }) CartesianTestCsvRow row,
            @Values(classes = {Float.class, float.class, Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(row.getValueAsString(0));

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(row.getValue(1, expectedType));
    }

    @CartesianTest
    public void doubleRoundTiesToEven(
            @Values(strings = {
                    "2251799813685803.75",
                    "4503599627370497.5",
                    "4503599627475353.5",
                    "9007199254740993.0",
                    "4503599627370496.5",
                    "4503599627475352.5",
                    "2251799813685248.25",
                    "2.22507385850720212418870147920222032907240528279439037814303133837435107319244194686754406432563881851382188218502438069999947733013005649884107791928741341929297200970481951993067993290969042784064731682041565926728632933630474670123316852983422152744517260835859654566319282835244787787799894310779783833699159288594555213714181128458251145584319223079897504395086859412457230891738946169368372321191373658977977723286698840356390251044443035457396733706583981055420456693824658413747607155981176573877626747665912387199931904006317334709003012790188175203447190250028061277777916798391090578584006464715943810511489154282775041174682194133952466682503431306181587829379004205392375072083366693241580002758391118854188641513168478436313080237596295773983001708984375e-308",
                    "1125899906842624.125",
                    "1125899906842901.875",
                    "9007199254740993.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
            }) String numberStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numberStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Double.parseDouble(numberStr));
    }

    @CartesianTest
    public void doubleRoundUpToNearest(
            @Values(strings = {
                    "2251799813685803.15",
                    "4503599627370497.2",
                    "45035996.273704985",
                    "4503599627475353.2",
                    "9355950000000000000.00000000000000000000000000000000001844674407370955161600000184467440737095516161844674407370955161407370955161618446744073709551616000184467440737095516166000001844674407370955161618446744073709551614073709551616184467440737095516160001844674407370955161601844674407370955674451616184467440737095516140737095516161844674407370955161600018446744073709551616018446744073709551611616000184467440737095001844674407370955161600184467440737095516160018446744073709551168164467440737095516160001844073709551616018446744073709551616184467440737095516160001844674407536910751601611616000184467440737095001844674407370955161600184467440737095516160018446744073709551616184467440737095516160001844955161618446744073709551616000184467440753691075160018446744073709",
                    "1.0000000000000006661338147750939242541790008544921875",
                    "-92666518056446206563e3",
                    "90054602635948575728e72",
                    "7.0420557077594588669468784357561207962098443483187940792729600000e59",
            }) String numberStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numberStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Double.parseDouble(numberStr));
    }

    @CartesianTest
    public void doubleRoundDownToNearest(
            @Values(strings = {
                    "2251799813685803.15",
                    "4503599627370497.2",
                    "45035996.273704985",
                    "4503599627475353.2",
                    "9355950000000000000.00000000000000000000000000000000001844674407370955161600000184467440737095516161844674407370955161407370955161618446744073709551616000184467440737095516166000001844674407370955161618446744073709551614073709551616184467440737095516160001844674407370955161601844674407370955674451616184467440737095516140737095516161844674407370955161600018446744073709551616018446744073709551611616000184467440737095001844674407370955161600184467440737095516160018446744073709551168164467440737095516160001844073709551616018446744073709551616184467440737095516160001844674407536910751601611616000184467440737095001844674407370955161600184467440737095516160018446744073709551616184467440737095516160001844955161618446744073709551616000184467440753691075160018446744073709",
                    "1.0000000000000006661338147750939242541790008544921875",
                    "-92666518056446206563e3",
                    "90054602635948575728e72",
                    "7.0420557077594588669468784357561207962098443483187940792729600000e59",
            }) String numberStr,
            @Values(classes = {Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numberStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Double.parseDouble(numberStr));
    }

    @CartesianTest
    public void floatRoundTiesToEven(
            @Values(strings = {
                    "1.1754941406275178592461758986628081843312458647327962400313859427181746759860647699724722770042717456817626953125e-38",
                    "30219.0830078125",
                    "16252921.5",
                    "5322519.25",
                    "3900245.875",
                    "1510988.3125",
                    "782262.28125",
                    "328381.484375",
                    "156782.0703125",
                    "85003.24609375",
                    "17419.6494140625",
                    "15498.36376953125",
                    "6318.580322265625",
                    "2525.2840576171875",
                    "16407.9462890625",
                    "8388614.5"
            }) String numberStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numberStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Float.parseFloat(numberStr));
    }

    @CartesianTest
    public void floatRoundUpToNearest(
            @Values(strings = {
                    "1.1754941406275178592461758986628081843312458647327962400313859427181746759860647699724722770042717456817626953125",
                    "1.1754943508e-38",
                    "16252921.5",
                    "3900245.875",
                    "328381.484375",
                    "85003.24609375",
                    "2525.2840576171875",
                    "936.3702087402344",
                    "411.88682556152344",
                    "206.50310516357422",
                    "124.16878890991211",
                    "50.811574935913086",
                    "13.91745138168335",
                    "2.687217116355896",
                    "1.1877630352973938",
                    "0.09289376810193062",
                    "0.03706067614257336",
                    "0.028068351559340954",
                    "0.012114629615098238",
                    "0.004221370676532388",
                    "0.002153817447833717",
                    "0.0015924838953651488",
                    "0.00036393293703440577",
                    "1.1754947011469036e-38",
                    "7.0064923216240854e-46",
                    "4.7019774032891500318749461488889827112746622270883500860350068251e-38",
                    "3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679",
            }) String numberStr,
            @Values(classes = {Float.class, float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numberStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Float.parseFloat(numberStr));
    }

    @CartesianTest
    public void floatRoundDownToNearest(
            @Values(strings = {
                    "1.1754941406275178592461758986628081843312458647327962400313859427181746759860647699724722770042717456817626953125",
                    "30219.0830078125",
                    "5322519.25",
                    "1510988.3125",
                    "782262.28125",
                    "156782.0703125",
                    "17419.6494140625",
                    "15498.36376953125",
                    "6318.580322265625",
                    "1370.9265747070312",
                    "17.486443519592285",
                    "7.5464513301849365",
                    "0.7622503340244293",
                    "0.30531780421733856",
                    "0.21791061013936996",
                    "0.0008602388261351734",
                    "0.00013746770127909258",
                    "16407.9462890625",
                    "8388614.5",
                    "2.3509887016445750159374730744444913556373311135441750430175034126e-38",
                    "3.4028234664e38",
                    "3.4028234665e38",
                    "3.4028234666e38",
                    "0.000000000000000000000000000000000000011754943508222875079687365372222456778186655567720875215087517062784172594547271728515625",
                    "0.00000000000000000000000000000000000000000000140129846432481707092372958328991613128026194187651577175706828388979108268586060148663818836212158203125",
                    "0.00000000000000000000000000000000000002350988561514728583455765982071533026645717985517980855365926236850006129930346077117064851336181163787841796875",
                    "0.00000000000000000000000000000000000001175494210692441075487029444849287348827052428745893333857174530571588870475618904265502351336181163787841796875",
            }) String numberStr,
            @Values(classes = {Float.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(numberStr);

        // when
        Object value = parser.parse(json, json.length, expectedType);

        // then
        assertThat(value).isEqualTo(Float.parseFloat(numberStr));
    }

    @CartesianTest
    public void moreValuesThanOneFloatingPointNumberAtRoot(
            @Values(strings = {"123.0,", "123.0{}", "1.0:"}) String jsonStr,
            @Values(classes = {float.class, Float.class, Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = BigDecimal.class, value = "Class: java.math.BigDecimal has more than one constructor."),
            @MapEntry(classKey = Number.class, value = "Unsupported class: java.lang.Number. Interfaces and abstract classes are not supported."),
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = Boolean.class, value = "Unrecognized boolean value. Expected: 'true', 'false' or 'null'."),
            @MapEntry(classKey = byte[].class, value = "Expected '[' but got: '1'.")
    })
    public void mismatchedTypeForFloatingPointNumberAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("123.0");

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
    public void mismatchedTypeForFloatingPointNumberAtObjectField(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": 123.0}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @ValueSource(classes = {float[].class, Float[].class, double[].class, Double[].class})
    public void arrayOfFloatingPointNumbersMixedWithOtherTypesAtRoot(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1.0, -1.0, true]");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @ValueSource(classes = {
            RecordWithFloatField.class,
            RecordWithPrimitiveFloatField.class,
            RecordWithDoubleField.class,
            RecordWithPrimitiveDoubleField.class
    })
    public void arrayOfFloatingPointNumbersMixedWithOtherTypesAtObjectField(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [1.0, -1.0, true]}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @MapSource({
            @MapEntry(classKey = BigDecimal[].class, value = "Class: java.math.BigDecimal has more than one constructor."),
            @MapEntry(classKey = Number[].class, value = "Unsupported class: java.lang.Number. Interfaces and abstract classes are not supported."),
            @MapEntry(classKey = String.class, value = "Invalid value starting at 0. Expected either string or 'null'."),
            @MapEntry(classKey = int.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = byte.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = Byte.class, value = "Invalid number. Minus has to be followed by a digit."),
            @MapEntry(classKey = byte[][].class, value = "Expected '[' but got: '1'."),
            @MapEntry(classKey = Byte[][].class, value = "Expected '[' but got: '1'.")
    })
    public void mismatchedTypeForArrayOfFloatingPointNumbersAtRoot(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("[1.0, -1.0, 0]");

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
    public void mismatchedTypeForArrayOfFloatingPointNumbersAtObjectField(Class<?> expectedType, String errorMessage) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("{\"field\": [1.0, -1.0, 0.0]}");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage(errorMessage);
    }

    @ParameterizedTest
    @FloatingPointNumberTestFilesSource
    public void testFilesForPrimitiveDouble(FloatingPointNumberTestFile file) throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();

        try (FloatingPointNumberTestFile.FloatingPointNumberTestCasesIterator it = file.iterator()) {
            while (it.hasNext()) {
                FloatingPointNumberTestCase testCase = it.next();
                byte[] json = toUtf8(testCase.input());

                // when
                double value = parser.parse(json, json.length, double.class);

                // then
                assertThat(value)
                        .withFailMessage("%nline: %d%n expected: %s%n was: %s", testCase.line(), testCase.expectedDouble(), value)
                        .isEqualTo(testCase.expectedDouble());
            }
        }
    }

    @ParameterizedTest
    @FloatingPointNumberTestFilesSource
    public void testFilesForDouble(FloatingPointNumberTestFile file) throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();

        try (FloatingPointNumberTestFile.FloatingPointNumberTestCasesIterator it = file.iterator()) {
            while (it.hasNext()) {
                FloatingPointNumberTestCase testCase = it.next();
                byte[] json = toUtf8(testCase.input());

                // when
                Double value = parser.parse(json, json.length, Double.class);

                // then
                assertThat(value)
                        .withFailMessage("%nline: %d%nexpected: %s%nwas: %s", testCase.line(), testCase.expectedDouble(), value)
                        .isEqualTo(testCase.expectedDouble());
            }
        }
    }

    @ParameterizedTest
    @FloatingPointNumberTestFilesSource
    public void testFilesForPrimitiveFloat(FloatingPointNumberTestFile file) throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();

        try (FloatingPointNumberTestFile.FloatingPointNumberTestCasesIterator it = file.iterator()) {
            while (it.hasNext()) {
                FloatingPointNumberTestCase testCase = it.next();
                byte[] json = toUtf8(testCase.input());

                // when
                float value = parser.parse(json, json.length, float.class);

                // then
                assertThat(value)
                        .withFailMessage("%nline: %d%n expected: %s%n was: %s", testCase.line(), testCase.expectedFloat(), value)
                        .isEqualTo(testCase.expectedFloat());
            }
        }
    }

    @ParameterizedTest
    @FloatingPointNumberTestFilesSource
    public void testFilesForFloat(FloatingPointNumberTestFile file) throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();

        try (FloatingPointNumberTestFile.FloatingPointNumberTestCasesIterator it = file.iterator()) {
            while (it.hasNext()) {
                FloatingPointNumberTestCase testCase = it.next();
                byte[] json = toUtf8(testCase.input());

                // when
                Float value = parser.parse(json, json.length, Float.class);

                // then
                assertThat(value)
                        .withFailMessage("%nline: %d%nexpected: %s%nwas: %s", testCase.line(), testCase.expectedFloat(), value)
                        .isEqualTo(testCase.expectedFloat());
            }
        }
    }

    @CartesianTest
    public void integralNumberAsFloatingPointNumber(
            @Values(strings = {"123", "0", "-123"}) String jsonStr,
            @Values(classes = {float.class, Float.class, Double.class, double.class}) Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(jsonStr);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid floating-point number. Fraction or exponent part is missing.");
    }

    @ParameterizedTest
    @ValueSource(classes = {float.class, Float.class, double.class, Double.class})
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
    @ValueSource(classes = {Float.class, Double.class})
    public void passedLengthSmallerThanNullLength(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(padWithSpaces("null"));

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, 3, expectedType));

        // then
        assertThat(ex)
                .hasMessage("Invalid value starting at 0. Expected 'null'.");
    }

    @ParameterizedTest
    @ValueSource(classes = {float.class, Float.class, double.class, Double.class})
    public void passedLengthSmallerThanNumberLength(Class<?> expectedType) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(padWithSpaces("1.234"));

        // when
        Object value = parser.parse(json, 3, expectedType);

        // then
        assertThat(value.toString()).isEqualTo("1.2");
    }
}
