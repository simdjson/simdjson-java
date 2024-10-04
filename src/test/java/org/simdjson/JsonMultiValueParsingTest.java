package org.simdjson;

import static org.simdjson.testutils.SimdJsonAssertions.assertThat;
import static org.simdjson.testutils.TestUtils.toUtf8;

import org.junit.jupiter.api.Test;

public class JsonMultiValueParsingTest {
    @Test
    public void testParseMultiValue() {
        byte[] json = toUtf8("{\"field1\":{\"field2\":\"value2\",\"field3\":3},\"field4\":[\"value4\",\"value5\"],\"field5\":null}");
        SimdJsonParserWithFixPath parser = new SimdJsonParserWithFixPath("field1.field2", "field1.field3", "field4", "field4.0", "field5");
        String[] result = parser.parse(json, json.length);
        assertThat(result[0]).isEqualTo("value2");
        assertThat(result[1]).isEqualTo("3");
        assertThat(result[2]).isEqualTo("[\"value4\",\"value5\"]");
        assertThat(result[3]).isEqualTo("value4");
        assertThat(result[4]).isEqualTo(null);
    }

    @Test
    public void testNonAsciiCharacters() {
        byte[] json = toUtf8("{\"ąćśńźż\": 1, \"\\u20A9\\u0E3F\": 2, \"αβγ\": 3, \"😀abc😀\": 4}");
        SimdJsonParserWithFixPath parser = new SimdJsonParserWithFixPath("ąćśńźż", "\\u20A9\\u0E3F", "αβγ", "😀abc😀");
        // when
        String[] result = parser.parse(json, json.length);
        // then
        assertThat(result[0]).isEqualTo("1");
        assertThat(result[1]).isEqualTo("2");
        assertThat(result[2]).isEqualTo("3");
        assertThat(result[3]).isEqualTo("4");
    }
}
