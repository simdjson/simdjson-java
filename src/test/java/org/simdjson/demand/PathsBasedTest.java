package org.simdjson.demand;

import static org.simdjson.testutils.SimdJsonAssertions.assertThat;
import static org.simdjson.testutils.TestUtils.toUtf8;

import org.junit.jupiter.api.Test;
import org.simdjson.JsonParsingException;
import org.simdjson.PathsBasedJsonParser;

public class PathsBasedTest {
    @Test
    public void testParseObjectWithDefaultTypeString() {
        byte[] bytes = toUtf8("{\"first\": 1, \"field\": 2, \"second\": 3}");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("first", "second", "third");
        Object[] result = parser.parse(bytes, bytes.length);
        assertThat(result[0]).isEqualTo("1");
        assertThat(result[1]).isEqualTo("3");
        assertThat(result[2]).isEqualTo(null);
    }

    @Test
    public void testParseObjectWithType() {
        byte[] bytes = toUtf8("{\"first\": 1, \"field\": 2, \"second\": 3}");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("first:INT", "second:INT", "third:INT");
        Object[] result = parser.parse(bytes, bytes.length);
        assertThat(result[0]).isEqualTo(1);
        assertThat(result[1]).isEqualTo(3);
        assertThat(result[2]).isEqualTo(null);
    }

    @Test
    public void testParseArrayWithDefaultTypeString() {
        byte[] bytes = toUtf8("[1, \"a\", {\"[first]\": 1, \"field\": 2, \"second\": 3}]");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("[0]", "[2].[[first]]", "[2].second", "[2]");
        Object[] result = parser.parse(bytes, bytes.length);
        assertThat(result[0]).isEqualTo("1");
        assertThat(result[1]).isEqualTo("1");
        assertThat(result[2]).isEqualTo("3");
        assertThat(result[3]).isEqualTo("{\"[first]\": 1, \"field\": 2, \"second\": 3}");
    }

    @Test
    public void testParseArrayWithType() {
        byte[] bytes = toUtf8("[1, \"a\", {\"[first]\": 1, \"field\": 2, \"second\": 3}]");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("[0]:INT", "[2].[[first]]:INT", "[2].second:INT", "[2]:STRING");
        Object[] result = parser.parse(bytes, bytes.length);
        assertThat(result[0]).isEqualTo(1);
        assertThat(result[1]).isEqualTo(1);
        assertThat(result[2]).isEqualTo(3);
        assertThat(result[3]).isEqualTo("{\"[first]\": 1, \"field\": 2, \"second\": 3}");
    }

    @Test
    public void testFieldNamesWithNonAsciiCharacters() {
        byte[] json = toUtf8("{\"ąćśńźż\": 1, \"\\u20A9\\u0E3F\": 2, \"αβγ\": 3, \"😀abc😀\": 4}");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("ąćśńźż:INT", "\\u20A9\\u0E3F:INT", "αβγ:INT", "😀abc😀:INT");
        Object[] result = parser.parse(json, json.length);
        assertThat(result[0]).isEqualTo(1);
        assertThat(result[1]).isEqualTo(2);
        assertThat(result[2]).isEqualTo(3);
        assertThat(result[3]).isEqualTo(4);
    }

    @Test
    public void testComplexJson() {
        byte[] json =
                toUtf8("{\"object1\":{\"field1\":123,\"field2\":\"xyz\",\"field3\":3.14,\"field4\":true,\"field5\":null,\"field6\":{\"field7\":{\"field8\":\"abc\"},\"field9\":[1,2,3,4],\"field10\":[1,\"xyz\",1.1,[1,2,3]]}},\"object2\":[\"xyz\",{},123,null,[[1,2,3],[4,5,6]]]}");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("object1.field1:INT", "object1.field3:DOUBLE", "object1.field6.field9",
                "object1.field6.field9.[0]:INT", "object1.field6.field9.[0].[0]", "object2.[4]", "object2.[4].[1]", "object2.[4].[1].[1]:INT");
        Object[] result = parser.parse(json, json.length);
        assertThat(result[0]).isEqualTo(123);
        assertThat(result[1]).isEqualTo(3.14);
        assertThat(result[2]).isEqualTo("[1,2,3,4]");
        assertThat(result[3]).isEqualTo(1);
        assertThat(result[4]).isEqualTo(null);
        assertThat(result[5]).isEqualTo("[[1,2,3],[4,5,6]]");
        assertThat(result[6]).isEqualTo("[4,5,6]");
        assertThat(result[7]).isEqualTo(5);
    }

    @Test
    public void testResultArrayIsIndependentAcrossParseCalls() {
        // Bug fix verification: a previous implementation reused the same result array, so the
        // reference returned from the first parse() would be silently overwritten by the second one.
        PathsBasedJsonParser parser = new PathsBasedJsonParser("a:INT", "b:INT");

        byte[] firstJson = toUtf8("{\"a\":1,\"b\":2}");
        Object[] first = parser.parse(firstJson, firstJson.length);

        byte[] secondJson = toUtf8("{\"a\":10,\"b\":20}");
        Object[] second = parser.parse(secondJson, secondJson.length);

        assertThat(first[0]).isEqualTo(1);
        assertThat(first[1]).isEqualTo(2);
        assertThat(second[0]).isEqualTo(10);
        assertThat(second[1]).isEqualTo(20);
    }

    @Test
    public void testObjectLeafDoesNotIncludeTrailingWhitespace() {
        // Bug fix verification: when extracting an object/array leaf as a raw string, the slice must
        // end at its closing bracket (not at the next structural element).
        byte[] json = toUtf8("{\"a\": {\"x\": 1}   , \"b\": 2}");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("a", "b:INT");
        Object[] result = parser.parse(json, json.length);
        assertThat(result[0]).isEqualTo("{\"x\": 1}");
        assertThat(result[1]).isEqualTo(2);
    }

    @Test
    public void testArrayLeafDoesNotIncludeTrailingWhitespace() {
        byte[] json = toUtf8("{\"a\": [1, 2, 3]   , \"b\": 2}");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("a", "b:INT");
        Object[] result = parser.parse(json, json.length);
        assertThat(result[0]).isEqualTo("[1, 2, 3]");
        assertThat(result[1]).isEqualTo(2);
    }

    @Test
    public void testDuplicateKeysDoNotCauseEarlyExit() {
        // Bug fix verification: a duplicate key used to make `collected` advance twice, causing the
        // parser to terminate the loop before reaching later requested fields.
        byte[] json = toUtf8("{\"a\":1,\"a\":2,\"b\":3}");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("a:INT", "b:INT");
        Object[] result = parser.parse(json, json.length);
        // Last occurrence wins, matching the behavior of most JSON parsers.
        assertThat(result[0]).isEqualTo(2);
        assertThat(result[1]).isEqualTo(3);
    }

    @Test
    public void testInvalidRootThrowsJsonParsingException() {
        byte[] json = toUtf8("\"just a string\"");
        PathsBasedJsonParser parser = new PathsBasedJsonParser("a");
        try {
            parser.parse(json, json.length);
        } catch (JsonParsingException expected) {
            return;
        }
        throw new AssertionError("expected JsonParsingException for non-object/array root");
    }
}
