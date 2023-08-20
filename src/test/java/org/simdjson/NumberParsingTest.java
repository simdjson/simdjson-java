package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.StringUtils.toUtf8;

public class NumberParsingTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "123.",
            "1..1",
            "1.e1",
            "1.E1"
    })
    public void decimalPointHasToBeFollowedByAtLeastOneDigit(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid number. Decimal point has to be followed by a digit.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1e+-2",
            "1E+-2",
            "1e--23",
            "1E--23",
            "1ea",
            "1Ea",
            "1e",
            "1E",
            "1e+",
            "1E+"
    })
    public void exponentIndicatorHasToBeFollowedByAtLeastOneDigit(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid number. Exponent indicator has to be followed by a digit.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "012",
            "-012",
            "012e34",
            "01.2",
            "000",
            "-000"
    })
    public void leadingZerosAreNotAllowed(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid number. Leading zeroes are not allowed.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-a123",
            "--123",
            "-+123",
            "-.123",
            "-e123",
            "[-]",
            "{\"a\":-}"
    })
    public void minusHasToBeFollowedByAtLeastOneDigit(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Invalid number. Minus has to be followed by a digit.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-1-2",
            "1.0.1",
            "12E12.12",
            "1e2e3",
            "1a"
    })
    public void numberHasToBeFollowedByStructuralCharacterOrWhitespace(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Number has to be followed by a structural character or whitespace.");
    }

    @Test
    public void minusZeroIsTreatedAsIntegerZero() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("-0");

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertLong(value, 0);
    }

    @Test
    public void startingWithPlusIsNotAllowed() {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8("+1");

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a123",
            "{\"num\": a123}",
            "a-123",
            "{\"num\": a-123}"
    })
    public void numberHasToStartWithMinusOrDigit(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'.");
    }

    @ParameterizedTest
    @ValueSource(longs = {
            Long.MAX_VALUE,
            Long.MIN_VALUE
    })
    public void minMaxLongValue(long input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(String.valueOf(input));

        // when
        JsonValue jsonValue = parser.parse(json, json.length);

        // then
        assertLong(jsonValue, input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "9223372036854775808",
            "9999999999999999999",
            "10000000000000000000",
            "-9223372036854775809",
            "-9999999999999999999",
            "-10000000000000000000"
    })
    public void outOfRangeLongIsNotAllowed(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonParsingException ex = assertThrows(JsonParsingException.class, () -> parser.parse(json, json.length));

        // then
        assertThat(ex.getMessage()).isEqualTo("Number value is out of long range ([-9223372036854775808, 9223372036854775807]).");
    }

    @ParameterizedTest
    @CsvSource({
            "1e000000000000000000001, 10.0",
            "1e-000000000000000000001, 0.1"
    })
    public void exponentWithMoreDigitsThanLongCanAccommodateAndLeadingZeros(String input, double expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, expected);
    }

    @ParameterizedTest
    @CsvSource({
            "0e999999999999999999999, 0.0",
            "0e-999999999999999999999, 0.0",
            "1e999999999999999999999, Infinity",
            "1e-999999999999999999999, 0.0",
            "9999999999999999999999999999999999999999e-999999999999999999999, 0.0",
            "0.9999999999999999999999999999999999999999e999999999999999999999, Infinity"
    })
    public void exponentWithMoreDigitsThanLongCanAccommodate(String input, double expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
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
    })
    public void positiveInfinity(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, Double.POSITIVE_INFINITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
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
    })
    public void negativeInfinity(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, Double.NEGATIVE_INFINITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
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
    })
    public void positiveZero(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, 0.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
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
    })
    public void negativeZero(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, -0.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // In this case the binary significand after rounding up is equal to 9007199254740992 (2^53),
            // which is more than we can store (2^53 - 1).
            "7.2057594037927933e16",
            "72057594037927933.0000000000000000",
    })
    public void roundingOverflow(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, 7.2057594037927933e16);
        assertDouble(value, 7.2057594037927936e16);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2.2250738585072016e-308",
            "2.2250738585072015e-308",
            "2.2250738585072014e-308",
            "2.2250738585072013e-308",
            "2.2250738585072012e-308"
    })
    public void minNormalDouble(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, 0x1p-1022);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2.2250738585072011e-308",
            "2.2250738585072010e-308",
            "2.2250738585072009e-308",
            "2.2250738585072008e-308",
            "2.2250738585072007e-308",
            "0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022250738585072008890245868760858598876504231122409594654935248025624400092282356951787758888037591552642309780950434312085877387158357291821993020294379224223559819827501242041788969571311791082261043971979604000454897391938079198936081525613113376149842043271751033627391549782731594143828136275113838604094249464942286316695429105080201815926642134996606517803095075913058719846423906068637102005108723282784678843631944515866135041223479014792369585208321597621066375401613736583044193603714778355306682834535634005074073040135602968046375918583163124224521599262546494300836851861719422417646455137135420132217031370496583210154654068035397417906022589503023501937519773030945763173210852507299305089761582519159720757232455434770912461317493580281734466552734375",
    })
    public void maxSubnormalDouble(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, 0x0.fffffffffffffp-1022);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3e-324",
            "4.9e-324",
            "4.9406564584124654e-324",
            "4.94065645841246544176568792868e-324",
    })
    public void minSubnormalDouble(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, 0x0.0000000000001p-1022);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.7976931348623157e308",
            "1.7976931348623158e308",
    })
    public void maxDouble(String input) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, 0x1.fffffffffffffp+1023);
    }

    @ParameterizedTest
    @CsvSource({
            "2251799813685803.75, 2251799813685804",
            "4503599627370497.5, 4503599627370498",
            "4503599627475353.5, 4503599627475354",
            "9007199254740993.0, 9007199254740992.0",
            "4503599627370496.5, 4503599627370496",
            "4503599627475352.5, 4503599627475352",
            "2251799813685248.25, 2251799813685248",
            "2.22507385850720212418870147920222032907240528279439037814303133837435107319244194686754406432563881851382188218502438069999947733013005649884107791928741341929297200970481951993067993290969042784064731682041565926728632933630474670123316852983422152744517260835859654566319282835244787787799894310779783833699159288594555213714181128458251145584319223079897504395086859412457230891738946169368372321191373658977977723286698840356390251044443035457396733706583981055420456693824658413747607155981176573877626747665912387199931904006317334709003012790188175203447190250028061277777916798391090578584006464715943810511489154282775041174682194133952466682503431306181587829379004205392375072083366693241580002758391118854188641513168478436313080237596295773983001708984375e-308, 2.2250738585072024e-308",
            "1125899906842624.125, 1125899906842624",
            "1125899906842901.875, 1125899906842902",
            "9007199254740993.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000, 9007199254740992",
    })
    public void roundTiesToEven(String input, double expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, expected);
    }

    @ParameterizedTest
    @CsvSource({
            "12045e23, 12045e23",
            "2251799813685803.85, 2251799813685804",
            "4503599627370497.6, 4503599627370498",
            "45035996.273704995, 45035996.273705",
            "4503599627475353.6, 4503599627475354",
            "1.797693134862315700000000000000001e308, 1.7976931348623157e308",
            "860228122.6654514319E+90, 8.602281226654515e98",
            "-42823146028335318693e-128, -4.282314602833532e-109",
            "-2402844368454405395.2, -2402844368454405600",
            "2402844368454405395.2, 2402844368454405600",
            "-2240084132271013504.131248280843119943687942846658579428, -2240084132271013600",
    })
    public void roundUpToNearest(String input, double expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, expected);
    }

    @ParameterizedTest
    @CsvSource({
            "2251799813685803.15, 2251799813685803",
            "4503599627370497.2, 4503599627370497",
            "45035996.273704985, 45035996.27370498",
            "4503599627475353.2, 4503599627475353",
            "9355950000000000000.00000000000000000000000000000000001844674407370955161600000184467440737095516161844674407370955161407370955161618446744073709551616000184467440737095516166000001844674407370955161618446744073709551614073709551616184467440737095516160001844674407370955161601844674407370955674451616184467440737095516140737095516161844674407370955161600018446744073709551616018446744073709551611616000184467440737095001844674407370955161600184467440737095516160018446744073709551168164467440737095516160001844073709551616018446744073709551616184467440737095516160001844674407536910751601611616000184467440737095001844674407370955161600184467440737095516160018446744073709551616184467440737095516160001844955161618446744073709551616000184467440753691075160018446744073709, 9355950000000000000",
            "1.0000000000000006661338147750939242541790008544921875, 1.0000000000000007",
            "-92666518056446206563e3, -9.26665180564462e22",
            "90054602635948575728e72, 9.005460263594858e91",
            "7.0420557077594588669468784357561207962098443483187940792729600000e59, 7.042055707759459e59",
    })
    public void roundDownToNearest(String input, double expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, expected);
    }

    @ParameterizedTest
    @CsvSource({
            "9007199254740991.0, 9007199254740991"
    })
    public void exactDouble(String input, double expected) {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = toUtf8(input);

        // when
        JsonValue value = parser.parse(json, json.length);

        // then
        assertDouble(value, expected);
    }

    @ParameterizedTest
    @MethodSource("listTestFiles")
    // This test assumes that input files are formatted as described in: https://github.com/nigeltao/parse-number-fxx-test-data
    public void testFiles(File file) throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] cells = line.split(" ");
                Double expected = Double.longBitsToDouble(Long.decode("0x" + cells[2]));
                String input = readInputNumber(cells[3]);
                byte[] json = toUtf8(input);

                // when
                JsonValue value = parser.parse(json, json.length);

                // then
                assertDouble(value, expected);
            }
        }
    }

    private static String readInputNumber(String input) {
        boolean isDouble = input.indexOf('e') >= 0 || input.indexOf('E') >= 0 || input.indexOf('.') >= 0;
        if (isDouble) {
            if (input.startsWith(".")) {
                input = "0" + input;
            }
            return input.replaceFirst("\\.[eE]", ".0e");
        }
        return input + ".0";
    }

    private static List<File> listTestFiles() throws IOException {
        String testDataDir = System.getProperty("org.simdjson.testdata.dir", System.getProperty("user.dir") + "/testdata");
        File[] testFiles = Path.of(testDataDir, "parse-number-fxx-test-data", "data").toFile().listFiles();
        if (testFiles == null) {
            File emptyFile = new File(testDataDir, "empty.txt");
            emptyFile.createNewFile();
            return List.of(emptyFile);
        }
        return Stream.of(testFiles)
                .filter(File::isFile)
                .toList();
    }

    private static void assertLong(JsonValue actual, long expected) {
        assertThat(actual.isLong()).isTrue();
        assertThat(actual.asLong()).isEqualTo(expected);
    }

    private static void assertDouble(JsonValue actual, Double expected) {
        assertThat(actual.isDouble()).isTrue();
        assertThat(actual.asDouble()).isEqualTo(expected);
    }
}
