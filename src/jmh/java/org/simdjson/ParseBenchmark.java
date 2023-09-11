package org.simdjson;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
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
public class ParseBenchmark {

    @Param({"/twitter.json", "/gsoc-2018.json", "/github_events.json"})
    String fileName;

    private final SimdJsonParser simdJsonParser = new SimdJsonParser();

    private byte[] buffer;
    private byte[] bufferPadded;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        try (InputStream is = ParseBenchmark.class.getResourceAsStream(fileName)) {
            buffer = is.readAllBytes();
            bufferPadded = padded(buffer);
        }
    }

    @Benchmark
    public JsonValue simdjson() {
        return simdJsonParser.parse(buffer, buffer.length);
    }

    @Benchmark
    public JsonValue simdjsonPadded() {
        return simdJsonParser.parse(bufferPadded, buffer.length);
    }
}
