package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simdjson.testutils.TestUtils.loadTestFile;
import static org.simdjson.testutils.TestUtils.toUtf8PaddedWithSpaces;

public class BenchmarkCorrectnessTest {

    @Test
    public void countUniqueTwitterUsersWithDefaultProfile() throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = loadTestFile("/twitter.json");

        for (int i = 0; i < 10; i++) {
            Set<String> defaultUsers = new HashSet<>();

            // when
            JsonValue simdJsonValue = parser.parse(json, json.length);
            Iterator<JsonValue> tweets = simdJsonValue.get("statuses").arrayIterator();
            while (tweets.hasNext()) {
                JsonValue tweet = tweets.next();
                JsonValue user = tweet.get("user");
                if (user.get("default_profile").asBoolean()) {
                    defaultUsers.add(user.get("screen_name").asString());
                }
            }

            // then
            assertThat(defaultUsers.size()).isEqualTo(86);
        }
    }

    @Test
    public void schemaBasedCountUniqueTwitterUsersWithDefaultProfile() throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        byte[] json = loadTestFile("/twitter.json");

        for (int i = 0; i < 10; i++) {
            Set<String> defaultUsers = new HashSet<>();

            // when
            Statuses statuses = parser.parse(json, json.length, Statuses.class);
            for (var status : statuses.statuses()) {
                User user = status.user();
                if (user.default_profile()) {
                    defaultUsers.add(user.screen_name());
                }
            }

            // then
            assertThat(defaultUsers.size()).isEqualTo(86);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2.2250738585072013e-308, 0x1p-1022", // fast path
            "1.00000000000000188558920870223463870174566020691753515394643550663070558368373221972569761144603605635692374830246134201063722058e-309, 1e-309" // slow path
    })
    public void numberParserTest(String input, Double expected) {
        // given
        Tape tape = new Tape(100);
        NumberParser numberParser = new NumberParser();
        byte[] numberUtf8Bytes = toUtf8PaddedWithSpaces(input);

        // when
        numberParser.parseNumber(numberUtf8Bytes, 0, tape);

        // then
        assertThat(tape.getDouble(0)).isEqualTo(expected);
    }

    record User(boolean default_profile, String screen_name) {

    }

    record Status(User user) {

    }

    record Statuses(List<Status> statuses) {

    }
}
