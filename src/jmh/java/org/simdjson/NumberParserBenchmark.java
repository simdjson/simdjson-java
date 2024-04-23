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
import java.util.concurrent.TimeUnit;

import static org.simdjson.SimdJsonPaddingUtil.padWithSpaces;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class NumberParserBenchmark {

    private final Tape tape = new Tape(100);
    private final NumberParser numberParser = new NumberParser();

    @Param({
            "2.2250738585072013e-308", // fast path
            "1.00000000000000188558920870223463870174566020691753515394643550663070558368373221972569761144603605635692374830246134201063722058e-309" // slow path
    })
    String number;
    byte[] numberUtf8Bytes;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        numberUtf8Bytes = padWithSpaces(number);
    }

    @Benchmark
    public double baseline() {
        return Double.parseDouble(number);
    }

    @Benchmark
    public double simdjson() {
        tape.reset();
        numberParser.parseNumber(numberUtf8Bytes, 0, tape);
        return tape.getDouble(0);
    }
}
