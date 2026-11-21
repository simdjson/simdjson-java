package org.simdjson.synthea;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.simdjson.SimdJsonParser;

import java.util.concurrent.TimeUnit;

/**
 * Extracts a fixed set of fields from Synthea FHIR bundles (see {@link SyntheaExtractor}) with each
 * parsing mode.
 * <p>
 * One benchmark operation is one pass over the whole corpus, so the score (average time per operation)
 * converts to throughput as {@code totalBytes / score} and {@code files / score}; both numbers are
 * printed by the trial setup. With {@code -t N}, each of the N threads makes its own full pass per
 * operation, with its own parser, over the shared read-only corpus.
 * <p>
 * The corpus is selected by system properties of the forked JVM; see {@link SyntheaCorpus}. The property
 * {@code synthea.validateUtf8} (default {@code true}) selects whether the parsers validate UTF-8; the trial setup
 * prints the setting. Before any
 * measurement, the trial setup checks that all modes agree on the first few files and refuses to run
 * if they do not.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SyntheaBundleBenchmark {

    private static final int AGREEMENT_CHECK_FILES = 4;

    @State(Scope.Benchmark)
    public static class CorpusState {

        SyntheaCorpus corpus;
        boolean validateUtf8;

        @Setup(Level.Trial)
        public void setup() {
            corpus = SyntheaCorpus.fromSystemProperties();
            corpus.printSummary(System.out);
            validateUtf8 = SyntheaExtractor.validateUtf8FromSystemProperties();
            System.out.println(SyntheaExtractor.VALIDATE_UTF8_PROPERTY + "=" + validateUtf8);
            SyntheaBundleVerifier.Report report =
                    SyntheaBundleVerifier.verify(corpus, Math.min(AGREEMENT_CHECK_FILES, corpus.size()), validateUtf8);
            report.print(System.out);
            if (!report.agree()) {
                throw new IllegalStateException("Parsing modes disagree on the first files of the corpus; "
                        + "refusing to measure. Run SyntheaBundleVerifier for details.");
            }
        }
    }

    @State(Scope.Thread)
    public static class ExtractorState {

        SyntheaExtractor extractor;

        @Setup(Level.Trial)
        public void setup(CorpusState corpusState) {
            extractor = new SyntheaExtractor(corpusState.corpus.maxLength(), corpusState.validateUtf8);
        }
    }

    @Benchmark
    public void domParseOnly(CorpusState corpusState, ExtractorState state, Blackhole blackhole) {
        SyntheaCorpus corpus = corpusState.corpus;
        SimdJsonParser parser = state.extractor.parser();
        for (int i = 0; i < corpus.size(); i++) {
            blackhole.consume(parser.parse(corpus.document(i), corpus.length(i)));
        }
    }

    @Benchmark
    public long domGetString(CorpusState corpusState, ExtractorState state) {
        SyntheaCorpus corpus = corpusState.corpus;
        SyntheaExtractor extractor = state.extractor;
        extractor.sum().reset();
        for (int i = 0; i < corpus.size(); i++) {
            extractor.domGetString(corpus.document(i), corpus.length(i));
        }
        return extractor.sum().checksum();
    }

    @Benchmark
    public long schemaBased(CorpusState corpusState, ExtractorState state) {
        SyntheaCorpus corpus = corpusState.corpus;
        SyntheaExtractor extractor = state.extractor;
        extractor.sum().reset();
        for (int i = 0; i < corpus.size(); i++) {
            extractor.schemaBased(corpus.document(i), corpus.length(i));
        }
        return extractor.sum().checksum();
    }

    @Benchmark
    public long domGetBytes(CorpusState corpusState, ExtractorState state) {
        SyntheaCorpus corpus = corpusState.corpus;
        SyntheaExtractor extractor = state.extractor;
        extractor.sum().reset();
        for (int i = 0; i < corpus.size(); i++) {
            extractor.domGetBytes(corpus.document(i), corpus.length(i));
        }
        return extractor.sum().checksum();
    }

    @Benchmark
    public long domIndex(CorpusState corpusState, ExtractorState state) {
        SyntheaCorpus corpus = corpusState.corpus;
        SyntheaExtractor extractor = state.extractor;
        extractor.sum().reset();
        for (int i = 0; i < corpus.size(); i++) {
            extractor.domIndex(corpus.document(i), corpus.length(i));
        }
        return extractor.sum().checksum();
    }

    @Benchmark
    public long onDemand(CorpusState corpusState, ExtractorState state) {
        SyntheaCorpus corpus = corpusState.corpus;
        SyntheaExtractor extractor = state.extractor;
        extractor.sum().reset();
        for (int i = 0; i < corpus.size(); i++) {
            extractor.onDemand(corpus.document(i), corpus.length(i));
        }
        return extractor.sum().checksum();
    }
}
