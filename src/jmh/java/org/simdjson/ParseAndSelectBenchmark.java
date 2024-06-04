package org.simdjson;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.simdjson.SimdJsonPaddingUtil.padded;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ParseAndSelectBenchmark {

    private final SimdJsonParser simdJsonParser = new SimdJsonParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private byte[] buffer;
    private byte[] bufferPadded;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        try (InputStream is = ParseBenchmark.class.getResourceAsStream("/twitter.json")) {
            buffer = is.readAllBytes();
            bufferPadded = padded(buffer);
        }
        System.out.println("VectorSpecies = " + VectorUtils.BYTE_SPECIES);
    }

    @Benchmark
    public int countUniqueUsersWithDefaultProfile_jackson() throws IOException {
        JsonNode jacksonJsonNode = objectMapper.readTree(buffer);
        Set<String> defaultUsers = new HashSet<>();
        Iterator<JsonNode> tweets = jacksonJsonNode.get("statuses").elements();
        while (tweets.hasNext()) {
            JsonNode tweet = tweets.next();
            JsonNode user = tweet.get("user");
            if (user.get("default_profile").asBoolean()) {
                defaultUsers.add(user.get("screen_name").textValue());
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    public int countUniqueUsersWithDefaultProfile_fastjson() {
        JSONObject jsonObject = (JSONObject) JSON.parse(buffer);
        Set<String> defaultUsers = new HashSet<>();
        Iterator<Object> tweets = jsonObject.getJSONArray("statuses").iterator();
        while (tweets.hasNext()) {
            JSONObject tweet = (JSONObject) tweets.next();
            JSONObject user = (JSONObject) tweet.get("user");
            if (user.getBoolean("default_profile")) {
                defaultUsers.add(user.getString("screen_name"));
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    public int countUniqueUsersWithDefaultProfile_simdjson() {
        JsonValue simdJsonValue = simdJsonParser.parse(buffer, buffer.length);
        Set<String> defaultUsers = new HashSet<>();
        Iterator<JsonValue> tweets = simdJsonValue.get("statuses").arrayIterator();
        while (tweets.hasNext()) {
            JsonValue tweet = tweets.next();
            JsonValue user = tweet.get("user");
            if (user.get("default_profile").asBoolean()) {
                defaultUsers.add(user.get("screen_name").asString());
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    public int countUniqueUsersWithDefaultProfile_simdjsonPadded() {
        JsonValue simdJsonValue = simdJsonParser.parse(bufferPadded, buffer.length);
        Set<String> defaultUsers = new HashSet<>();
        Iterator<JsonValue> tweets = simdJsonValue.get("statuses").arrayIterator();
        while (tweets.hasNext()) {
            JsonValue tweet = tweets.next();
            JsonValue user = tweet.get("user");
            if (user.get("default_profile").asBoolean()) {
                defaultUsers.add(user.get("screen_name").asString());
            }
        }
        return defaultUsers.size();
    }
}
