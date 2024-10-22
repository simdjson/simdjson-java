package org.simdjson;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ParseAndSelectFixPathBenchMark {
    @Param({"/twitter.json"})
    String fileName;
    private byte[] buffer;
    private final SimdJsonParser simdJsonParser = new SimdJsonParser();
    private final ObjectMapper jacksonObjectMapper = new ObjectMapper();
    private final SimdJsonParserWithFixPath simdJsonParserWithFixPath = new SimdJsonParserWithFixPath(
            "statuses.0.user.default_profile", "statuses.0.user.screen_name",
            "statuses.0.user.name", "statuses.0.user.id", "statuses.0.user.description",
            "statuses.1.user.default_profile", "statuses.1.user.screen_name",
            "statuses.1.user.name", "statuses.1.user.id", "statuses.1.user.description");

    @Setup(Level.Trial)
    public void setup() throws IOException {
        try (InputStream is = ParseBenchmark.class.getResourceAsStream("/twitter.json")) {
            buffer = is.readAllBytes();
        }
        System.out.println("VectorSpecies = " + VectorUtils.BYTE_SPECIES);
    }

    @Benchmark
    public JsonValue parseMultiValuesForFixPaths_SimdJson() {
        return simdJsonParser.parse(buffer, buffer.length);
    }

    @Benchmark
    public String[] parseMultiValuesForFixPaths_SimdJsonParserWithFixPath() {
        return simdJsonParserWithFixPath.parse(buffer, buffer.length);
    }

    @Benchmark
    public JsonNode parseMultiValuesForFixPaths_Jackson() throws IOException {
        return jacksonObjectMapper.readTree(buffer);
    }
}
