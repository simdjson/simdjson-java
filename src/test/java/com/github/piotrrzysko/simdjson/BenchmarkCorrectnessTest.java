package com.github.piotrrzysko.simdjson;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class BenchmarkCorrectnessTest {

    @Test
    public void countUniqueTwitterUsersWithDefaultProfile() throws IOException {
        // given
        SimdJsonParser parser = new SimdJsonParser();
        Set<String> defaultUsers = new HashSet<>();
        byte[] json = loadTwitterJson();

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

    private static byte[] loadTwitterJson() throws IOException {
        try (InputStream is = BenchmarkCorrectnessTest.class.getResourceAsStream("/twitter.json")) {
            return is.readAllBytes();
        }
    }
}
