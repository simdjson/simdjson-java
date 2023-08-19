package org.simdjson;

import com.google.common.base.Utf8;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class Utf8ValidatorBenchmark {
    @Param({"/twitter.json", "/gsoc-2018.json", "/github_events.json"})
    String fileName;
    byte[] bytes;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        try (InputStream is = Utf8ValidatorBenchmark.class.getResourceAsStream(fileName)) {
            bytes = is.readAllBytes();
        }
    }

    @Benchmark
    public void utf8Validator() {
        Utf8Validator.validate(bytes);
    }

    @Benchmark
    public boolean guava() {
        return Utf8.isWellFormed(bytes);
    }
}
