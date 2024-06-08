package org.simdjson;

import com.google.common.base.Utf8;
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
        Utf8Validator.validate(bytes, bytes.length);
    }

    @Benchmark
    public boolean guava() {
        return Utf8.isWellFormed(bytes);
    }
}
