package org.simdjson;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
import java.util.concurrent.TimeUnit;

import static org.simdjson.SimdJsonPaddingUtil.padded;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class PathsBasedParseBenchmark {

    private static final String[] PATHS = {
            "statuses.[0].id:LONG",
            "statuses.[0].text",
            "statuses.[0].created_at",
            "statuses.[0].metadata.iso_language_code",
            "search_metadata.count:INT",
            "search_metadata.completed_in:DOUBLE",
            "search_metadata.query"
    };

    private final SimdJsonParser simdJsonParser = new SimdJsonParser();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PathsBasedJsonParser pathsBasedJsonParser = new PathsBasedJsonParser(PATHS);

    private byte[] buffer;
    private byte[] bufferPadded;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        try (InputStream is = ParseBenchmark.class.getResourceAsStream("/twitter.json")) {
            buffer = is.readAllBytes();
            bufferPadded = padded(buffer);
        }
        // Numeric leaves are compared strictly; string leaves only checked non-null
        // because PathsBasedJsonParser returns raw (un-unescaped) UTF-8 by design.
        Object[] expected = projectWithPathsBased();
        Object[] dom = projectWithSimdJsonDom();
        Object[] padded = projectWithSimdJsonDomPadded();
        Object[] jackson;
        try {
            jackson = projectWithJackson();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Object[] fastjson = projectWithFastjson();
        for (int i : new int[]{0, 4, 5}) {
            if (!numericEquals(expected[i], dom[i])
                    || !numericEquals(expected[i], padded[i])
                    || !numericEquals(expected[i], jackson[i])
                    || !numericEquals(expected[i], fastjson[i])) {
                throw new IllegalStateException("benchmark implementations disagree on numeric leaf #" + i
                        + ": paths=" + expected[i]
                        + ", dom=" + dom[i]
                        + ", domPadded=" + padded[i]
                        + ", jackson=" + jackson[i]
                        + ", fastjson=" + fastjson[i]);
            }
        }
        for (int i : new int[]{1, 2, 3, 6}) {
            if (expected[i] == null || dom[i] == null || padded[i] == null
                    || jackson[i] == null || fastjson[i] == null) {
                throw new IllegalStateException("benchmark implementations produced null for string leaf #" + i);
            }
        }
        System.out.println("VectorSpecies = " + VectorUtils.BYTE_SPECIES);
    }

    @Benchmark
    public Object[] projectFields_pathsBased() {
        return pathsBasedJsonParser.parse(bufferPadded, buffer.length);
    }

    @Benchmark
    public Object[] projectFields_simdjsonDom() {
        return projectWithSimdJsonDom();
    }

    @Benchmark
    public Object[] projectFields_simdjsonDomPadded() {
        return projectWithSimdJsonDomPadded();
    }

    @Benchmark
    public Object[] projectFields_jackson() throws IOException {
        return projectWithJackson();
    }

    @Benchmark
    public Object[] projectFields_fastjson() {
        return projectWithFastjson();
    }

    private Object[] projectWithSimdJsonDom() {
        JsonValue root = simdJsonParser.parse(buffer, buffer.length);
        return projectFromSimdJson(root);
    }

    private Object[] projectWithSimdJsonDomPadded() {
        JsonValue root = simdJsonParser.parse(bufferPadded, buffer.length);
        return projectFromSimdJson(root);
    }

    private static Object[] projectFromSimdJson(JsonValue root) {
        Object[] out = new Object[7];
        JsonValue statuses = root.get("statuses");
        JsonValue first = statuses.arrayIterator().next();
        out[0] = first.get("id").asLong();
        out[1] = first.get("text").asString();
        out[2] = first.get("created_at").asString();
        out[3] = first.get("metadata").get("iso_language_code").asString();
        JsonValue meta = root.get("search_metadata");
        out[4] = (int) meta.get("count").asLong();
        out[5] = meta.get("completed_in").asDouble();
        out[6] = meta.get("query").asString();
        return out;
    }

    private Object[] projectWithJackson() throws IOException {
        JsonNode root = objectMapper.readTree(buffer);
        Object[] out = new Object[7];
        JsonNode first = root.get("statuses").get(0);
        out[0] = first.get("id").asLong();
        out[1] = first.get("text").asText();
        out[2] = first.get("created_at").asText();
        out[3] = first.get("metadata").get("iso_language_code").asText();
        JsonNode meta = root.get("search_metadata");
        out[4] = meta.get("count").asInt();
        out[5] = meta.get("completed_in").asDouble();
        out[6] = meta.get("query").asText();
        return out;
    }

    private Object[] projectWithFastjson() {
        JSONObject root = (JSONObject) JSON.parse(buffer);
        Object[] out = new Object[7];
        JSONArray statuses = root.getJSONArray("statuses");
        JSONObject first = (JSONObject) statuses.get(0);
        out[0] = first.getLong("id");
        out[1] = first.getString("text");
        out[2] = first.getString("created_at");
        out[3] = first.getJSONObject("metadata").getString("iso_language_code");
        JSONObject meta = root.getJSONObject("search_metadata");
        out[4] = meta.getInteger("count");
        out[5] = meta.getDouble("completed_in");
        out[6] = meta.getString("query");
        return out;
    }

    private Object[] projectWithPathsBased() {
        return pathsBasedJsonParser.parse(bufferPadded, buffer.length);
    }

    private static boolean numericEquals(Object x, Object y) {
        if (!(x instanceof Number) || !(y instanceof Number)) {
            return false;
        }
        if (x instanceof Double || y instanceof Double || x instanceof Float || y instanceof Float) {
            return Double.compare(((Number) x).doubleValue(), ((Number) y).doubleValue()) == 0;
        }
        return ((Number) x).longValue() == ((Number) y).longValue();
    }
}
