package org.simdjson;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.simdjson.SimdJsonPaddingUtil.padded;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ParseAndSelectBenchmark {

    private static final String STATUSES = "statuses";
    private static final byte[] STATUSES_BYTES = STATUSES.getBytes(StandardCharsets.UTF_8);
    private static final String USER = "user";
    private static final byte[] USER_BYTES = USER.getBytes(StandardCharsets.UTF_8);
    private static final String DEFAULT_PROFILE = "default_profile";
    private static final byte[] DEFAULT_PROFILE_BYTES = DEFAULT_PROFILE.getBytes(StandardCharsets.UTF_8);
    private static final String SCREEN_NAME = "screen_name";
    private static final byte[] SCREEN_NAME_BYTES = SCREEN_NAME.getBytes(StandardCharsets.UTF_8);
    private final SimdJsonParser simdJsonParser = new SimdJsonParser();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> defaultUsers = new HashSet<>();
    private final CapturedJsonValueConsumer capturedNameConsumer = new CapturedJsonValueConsumer();

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

    @TearDown(Level.Trial)
    public void tearDownPerTrial() {
        System.out.println("defaultUsers.size() = " + defaultUsers.size());
        defaultUsers.clear();
        capturedNameConsumer.reset();
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Xmx512m", "-Xms512m", "-XX:MaxDirectMemorySize=1g"})
    public int countUniqueUsersWithDefaultProfile_jackson() throws IOException {
        JsonNode jacksonJsonNode = objectMapper.readTree(buffer);
        Iterator<JsonNode> tweets = jacksonJsonNode.get(STATUSES).elements();
        while (tweets.hasNext()) {
            JsonNode tweet = tweets.next();
            JsonNode user = tweet.get(USER);
            if (user.get(DEFAULT_PROFILE).asBoolean()) {
                defaultUsers.add(user.get(SCREEN_NAME).textValue());
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Xmx512m", "-Xms512m", "-XX:MaxDirectMemorySize=1g"})
    public int countUniqueUsersWithDefaultProfile_fastjson() {
        JSONObject jsonObject = (JSONObject) JSON.parse(buffer);
        Iterator<Object> tweets = jsonObject.getJSONArray(STATUSES).iterator();
        while (tweets.hasNext()) {
            JSONObject tweet = (JSONObject) tweets.next();
            JSONObject user = (JSONObject) tweet.get(USER);
            if (user.getBoolean(DEFAULT_PROFILE)) {
                defaultUsers.add(user.getString(SCREEN_NAME));
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Xmx512m", "-Xms512m", "-XX:MaxDirectMemorySize=1g"})
    public int countUniqueUsersWithDefaultProfile_simdjson() {
        JsonValue simdJsonValue = simdJsonParser.parse(buffer, buffer.length);
        Iterator<JsonValue> tweets = simdJsonValue.get(STATUSES).arrayIterator();
        while (tweets.hasNext()) {
            JsonValue tweet = tweets.next();
            JsonValue user = tweet.get(USER);
            if (user.get(DEFAULT_PROFILE).asBoolean()) {
                defaultUsers.add(user.get(SCREEN_NAME).asString());
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Xmx512m", "-Xms512m", "-XX:MaxDirectMemorySize=1g"})
    public int countUniqueUsersWithDefaultProfile_simdjson_gc_free() {
        JsonValue simdJsonValue = simdJsonParser.parse(buffer, buffer.length);
        Iterator<JsonValue> tweets = simdJsonValue.get(STATUSES_BYTES).arrayIterator();
        while (tweets.hasNext()) {
            JsonValue tweet = tweets.next();
            JsonValue user = tweet.get(USER_BYTES);
            if (user.get(DEFAULT_PROFILE_BYTES).asBoolean()) {
                JsonValue jsonValue = user.get(SCREEN_NAME_BYTES);
                jsonValue.acceptBytes(capturedNameConsumer);
                String capturedName = capturedNameConsumer.getCapturedName();
                if (capturedName != null) {
                    defaultUsers.add(capturedName);
                }
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Xmx512m", "-Xms512m", "-XX:MaxDirectMemorySize=1g"})
    public int countUniqueUsersWithDefaultProfile_simdjsonPadded() {
        JsonValue simdJsonValue = simdJsonParser.parse(bufferPadded, buffer.length);
        Iterator<JsonValue> tweets = simdJsonValue.get(STATUSES).arrayIterator();
        while (tweets.hasNext()) {
            JsonValue tweet = tweets.next();
            JsonValue user = tweet.get(USER);
            if (user.get(DEFAULT_PROFILE).asBoolean()) {
                defaultUsers.add(user.get(SCREEN_NAME).asString());
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Xmx512m", "-Xms512m", "-XX:MaxDirectMemorySize=1g"})
    public int countUniqueUsersWithDefaultProfile_simdjsonPadded_gc_free() {
        JsonValue simdJsonValue = simdJsonParser.parse(bufferPadded, buffer.length);
        Iterator<JsonValue> tweets = simdJsonValue.get(STATUSES_BYTES).arrayIterator();
        while (tweets.hasNext()) {
            JsonValue tweet = tweets.next();
            JsonValue user = tweet.get(USER_BYTES);
            if (user.get(DEFAULT_PROFILE_BYTES).asBoolean()) {
                JsonValue jsonValue = user.get(SCREEN_NAME_BYTES);
                jsonValue.acceptBytes(capturedNameConsumer);
                String capturedName = capturedNameConsumer.getCapturedName();
                if (capturedName != null) {
                    defaultUsers.add(capturedName);
                }
            }
        }
        return defaultUsers.size();
    }

    private static class CapturedJsonValueConsumer implements JsonValue.JsonValueByteConsumer {
        private final HashMap<BytesKey, String> byteSequenceHashMap = new HashMap<>();
        // reused lookup key to avoid allocation on hits
        private final BytesKey lookupKey = new BytesKey();
        private String capturedName;

        @Override
        public void acceptBytes(byte[] bytes, int offset, int length) {
            this.capturedName = null;
            // 1) lookup
            lookupKey.wrap(bytes, offset, length);
            String cached = byteSequenceHashMap.get(lookupKey);
            if (cached != null) {
                capturedName = cached;
                return;
            }
            String value = new String(bytes, offset, length, StandardCharsets.UTF_8);
            byteSequenceHashMap
                    .put(new BytesKey(bytes, offset, length), value);
            this.capturedName = value;
        }

        public String getCapturedName() {
            return capturedName;
        }

        public void reset() {
            this.capturedName = null;
            this.byteSequenceHashMap.clear();
        }
    }

    /**
     * Immutable-ish bytes-slice key with content-based hash/equals.
     * IMPORTANT: only safe if the underlying bytes do not change for the lifetime of the map entries.
     * (In your simdjson case: stringBuffer is stable, so this is fine.)
     */
    private static final class BytesKey {
        private byte[] bytes;
        private int offset;
        private int length;
        private int hash; // cached

        BytesKey() {
        }

        BytesKey(byte[] bytes, int offset, int length) {
            wrap(bytes, offset, length);
        }

        void wrap(byte[] bytes, int offset, int length) {
            this.bytes = bytes;
            this.offset = offset;
            this.length = length;
            this.hash = fnv1a32(bytes, offset, length);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BytesKey other)) return false;
            if (this.length != other.length) return false;
            return bytesEqual(this.bytes, this.offset, other.bytes, other.offset, this.length);
        }

        private static boolean bytesEqual(byte[] a, int aOff, byte[] b, int bOff, int len) {
            for (int i = 0; i < len; i++) {
                if (a[aOff + i] != b[bOff + i]) return false;
            }
            return true;
        }

        // fast stable hash for byte slices
        private static int fnv1a32(byte[] a, int off, int len) {
            int h = 0x811C9DC5;
            int end = off + len;
            for (int i = off; i < end; i++) {
                h ^= (a[i] & 0xFF);
                h *= 0x01000193;
            }
            return h;
        }
    }

    //Benchmark                                                                                                 Mode  Cnt        Score   Error   Units
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson                                      thrpt    2      424.752           ops/s
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson:gc.alloc.rate                        thrpt    2      739.232          MB/sec
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson:gc.alloc.rate.norm                   thrpt    2  1825142.125            B/op
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson:gc.count                             thrpt    2     4244.000          counts
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson:gc.time                              thrpt    2     4788.000              ms
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson                                       thrpt    2      409.952           ops/s
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson:gc.alloc.rate                         thrpt    2      570.240          MB/sec
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson:gc.alloc.rate.norm                    thrpt    2  1458734.186            B/op
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson:gc.count                              thrpt    2     2651.000          counts
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson:gc.time                               thrpt    2     3014.000              ms
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson                                      thrpt    2     1526.368           ops/s
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson:gc.alloc.rate                        thrpt    2       19.179          MB/sec
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson:gc.alloc.rate.norm                   thrpt    2    13177.708            B/op
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson:gc.count                             thrpt    2      232.000          counts
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson:gc.time                              thrpt    2      327.000              ms
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded                                thrpt    2     1472.190           ops/s
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded:gc.alloc.rate                  thrpt    2       18.500          MB/sec
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded:gc.alloc.rate.norm             thrpt    2    13177.770            B/op
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded:gc.count                       thrpt    2      253.000          counts
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded:gc.time                        thrpt    2      371.000              ms
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded_gc_free                        thrpt    2     1529.952           ops/s
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded_gc_free:gc.alloc.rate          thrpt    2        0.049          MB/sec
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded_gc_free:gc.alloc.rate.norm     thrpt    2       33.686            B/op
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded_gc_free:gc.count               thrpt    2        2.000          counts
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded_gc_free:gc.time                thrpt    2        8.000              ms
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson_gc_free                              thrpt    2     1524.408           ops/s
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson_gc_free:gc.alloc.rate                thrpt    2        0.049          MB/sec
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson_gc_free:gc.alloc.rate.norm           thrpt    2       33.607            B/op
    //ParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson_gc_free:gc.count                     thrpt    2          ≈ 0          counts
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson                           thrpt    2     1169.618           ops/s
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson:gc.alloc.rate             thrpt    2      227.498          MB/sec
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson:gc.alloc.rate.norm        thrpt    2   203960.598            B/op
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson:gc.count                  thrpt    2       23.000          counts
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_fastjson:gc.time                   thrpt    2       20.000              ms
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson                            thrpt    2      776.049           ops/s
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson:gc.alloc.rate              thrpt    2      332.906          MB/sec
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson:gc.alloc.rate.norm         thrpt    2   449824.902            B/op
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson:gc.count                   thrpt    2       11.000          counts
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_jackson:gc.time                    thrpt    2       10.000              ms
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson                           thrpt    2     2536.616           ops/s
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson:gc.alloc.rate             thrpt    2       59.315          MB/sec
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson:gc.alloc.rate.norm        thrpt    2    24520.276            B/op
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson:gc.count                  thrpt    2        4.000          counts
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjson:gc.time                   thrpt    2        6.000              ms
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded                     thrpt    2     2748.359           ops/s
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded:gc.alloc.rate       thrpt    2       64.267          MB/sec
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded:gc.alloc.rate.norm  thrpt    2    24520.257            B/op
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded:gc.count            thrpt    2        7.000          counts
    //SchemaBasedParseAndSelectBenchmark.countUniqueUsersWithDefaultProfile_simdjsonPadded:gc.time             thrpt    2        9.000              ms

    /**
     * to run this from ide, i.e intellij please add "--add-modules jdk.incubator.vector" in VM options.
     */
    static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder().include(ParseAndSelectBenchmark.class.getSimpleName()).
                forks(1).
                warmupIterations(2).
                measurementIterations(2)
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(options).run();
    }
}
