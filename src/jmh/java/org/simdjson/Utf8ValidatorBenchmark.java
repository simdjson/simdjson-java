package org.simdjson;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import com.google.common.base.Utf8;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class Utf8ValidatorBenchmark {
    @State(Scope.Benchmark)
    public static class MyState {
        @Param({"/twitter.json", "/gsoc-2018.json", "/github_events.json"})
        String fileName;
        byte[] bytes;

        @Setup(Level.Trial)
        public void doSetup() {
            try (InputStream is = Utf8ValidatorBenchmark.class.getResourceAsStream(fileName)){
                bytes = is.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Benchmark
    public void utf8Validator(MyState myState)  {
        Utf8Validator.validate(myState.bytes);
    }

    @Benchmark
    public boolean guava(MyState myState) {
        return Utf8.isWellFormed(myState.bytes);
    }
}
