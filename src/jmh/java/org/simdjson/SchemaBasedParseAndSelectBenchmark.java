package org.simdjson;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig$;
import com.github.plokhotnyuk.jsoniter_scala.core.package$;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.simdjson.SimdJsonPaddingUtil.padded;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class SchemaBasedParseAndSelectBenchmark {

    private final SimdJsonParser simdJsonParser = new SimdJsonParser();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private byte[] buffer;
    private byte[] bufferPadded;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        try (InputStream is = ParseBenchmark.class.getResourceAsStream("/twitter.json")) {
            buffer = is.readAllBytes();
            bufferPadded = padded(buffer);
        }
        System.out.println("VectorSpecies = " + StructuralIndexer.BYTE_SPECIES);
    }

    @Benchmark
    public int countUniqueUsersWithDefaultProfile_simdjson() {
        Set<String> defaultUsers = new HashSet<>();
        SimdJsonTwitter twitter = simdJsonParser.parse(bufferPadded, buffer.length, SimdJsonTwitter.class);
        for (SimdJsonStatus status : twitter.statuses()) {
            SimdJsonUser user = status.user();
            if (user.default_profile()) {
                defaultUsers.add(user.screen_name());
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    public int countUniqueUsersWithDefaultProfile_jackson() throws IOException {
        Set<String> defaultUsers = new HashSet<>();
        SimdJsonTwitter twitter = objectMapper.readValue(buffer, SimdJsonTwitter.class);
        for (SimdJsonStatus status : twitter.statuses()) {
            SimdJsonUser user = status.user();
            if (user.default_profile()) {
                defaultUsers.add(user.screen_name());
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    public int countUniqueUsersWithDefaultProfile_jsoniter_scala() {
        Twitter twitter = package$.MODULE$.readFromArray(buffer, ReaderConfig$.MODULE$, Twitter$.MODULE$.codec());
        Set<String> defaultUsers = new HashSet<>();
        for (Status tweet: twitter.statuses()) {
            User user = tweet.user();
            if (user.default_profile()) {
                defaultUsers.add(user.screen_name());
            }
        }
        return defaultUsers.size();
    }

    @Benchmark
    public int countUniqueUsersWithDefaultProfile_fastjson() {
        Set<String> defaultUsers = new HashSet<>();
        SimdJsonTwitter twitter = JSON.parseObject(buffer, SimdJsonTwitter.class);
        for (SimdJsonStatus status : twitter.statuses()) {
            SimdJsonUser user = status.user();
            if (user.default_profile()) {
                defaultUsers.add(user.screen_name());
            }
        }
        return defaultUsers.size();
    }

    record SimdJsonUser(boolean default_profile, String screen_name) {

    }

    record SimdJsonStatus(SimdJsonUser user) {

    }

    record SimdJsonTwitter(List<SimdJsonStatus> statuses) {

    }
}
