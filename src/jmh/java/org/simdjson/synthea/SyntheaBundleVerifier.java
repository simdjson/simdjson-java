package org.simdjson.synthea;

import org.simdjson.JsonValue;
import org.simdjson.SimdJsonParser;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs every {@link SyntheaExtractor.Mode} over the corpus and checks that they all compute the same
 * checksum and resource counts. Each mode is run twice with the same parser, to also catch state that
 * leaks from one parse into the next. Any failure, or any disagreement, makes the verification fail.
 * <p>
 * Usage (no JMH involved):
 * <pre>
 *   java --add-modules jdk.incubator.vector -Dsynthea.dir=DIR [-Dsynthea.maxFiles=N] [-Dsynthea.validateUtf8=false] \
 *        -cp build/libs/*-jmh.jar org.simdjson.synthea.SyntheaBundleVerifier
 * </pre>
 * Exit status 0 when all modes agree, 1 otherwise.
 */
public final class SyntheaBundleVerifier {

    private SyntheaBundleVerifier() {
    }

    public static void main(String[] args) {
        SyntheaCorpus corpus = SyntheaCorpus.fromSystemProperties();
        boolean validateUtf8 = SyntheaExtractor.validateUtf8FromSystemProperties();
        corpus.printSummary(System.out);
        System.out.println("java.vm.version=" + System.getProperty("java.vm.version")
                + " " + SyntheaExtractor.VALIDATE_UTF8_PROPERTY + "=" + validateUtf8);
        Report report = verify(corpus, corpus.size(), validateUtf8);
        report.print(System.out);
        printResourceTypes(corpus, validateUtf8, System.out);
        System.out.println(report.agree() ? "VERIFY PASS" : "VERIFY FAIL");
        System.exit(report.agree() ? 0 : 1);
    }

    /** Runs all modes over the first {@code files} documents of the corpus, with parsers that validate UTF-8 or not. */
    public static Report verify(SyntheaCorpus corpus, int files, boolean validateUtf8) {
        Map<SyntheaExtractor.Mode, SyntheaChecksum.Result> results = new EnumMap<>(SyntheaExtractor.Mode.class);
        Map<SyntheaExtractor.Mode, String> failures = new EnumMap<>(SyntheaExtractor.Mode.class);
        for (SyntheaExtractor.Mode mode : SyntheaExtractor.Mode.values()) {
            SyntheaExtractor extractor = new SyntheaExtractor(corpus.maxLength(), validateUtf8);
            SyntheaChecksum.Result first = null;
            for (int pass = 0; pass < 2; pass++) {
                extractor.sum().reset();
                int i = 0;
                try {
                    for (; i < files; i++) {
                        extractor.run(mode, corpus.document(i), corpus.length(i));
                    }
                } catch (RuntimeException e) {
                    failures.put(mode, "pass " + pass + ", file " + corpus.name(i) + ": " + e);
                    break;
                }
                SyntheaChecksum.Result result = SyntheaChecksum.Result.of(extractor.sum());
                if (first == null) {
                    first = result;
                } else if (!first.sameAs(result)) {
                    failures.put(mode, "second pass with the same parser differs: " + first + " vs " + result);
                }
            }
            if (!failures.containsKey(mode)) {
                results.put(mode, first);
            }
        }
        return new Report(files, results, failures);
    }

    public record Report(int files,
                         Map<SyntheaExtractor.Mode, SyntheaChecksum.Result> results,
                         Map<SyntheaExtractor.Mode, String> failures) {

        public boolean agree() {
            if (!failures.isEmpty()) {
                return false;
            }
            SyntheaChecksum.Result reference = results.get(SyntheaExtractor.Mode.DOM_GET_STRING);
            if (reference == null) {
                return false;
            }
            for (SyntheaChecksum.Result result : results.values()) {
                if (!reference.sameAs(result)) {
                    return false;
                }
            }
            return true;
        }

        public void print(PrintStream out) {
            out.println("Verified " + files + " file(s):");
            SyntheaChecksum.Result reference = results.get(SyntheaExtractor.Mode.DOM_GET_STRING);
            for (SyntheaExtractor.Mode mode : SyntheaExtractor.Mode.values()) {
                String line;
                if (failures.containsKey(mode)) {
                    line = "FAILED      " + failures.get(mode);
                } else {
                    SyntheaChecksum.Result result = results.get(mode);
                    boolean same = reference != null && reference.sameAs(result);
                    line = (same ? "OK          " : "MISMATCH    ") + result;
                }
                out.printf("  %-14s %s%n", mode.label(), line);
            }
        }
    }

    /** Informational: resource counts for every resourceType, not only the four the extraction reads. */
    private static void printResourceTypes(SyntheaCorpus corpus, boolean validateUtf8, PrintStream out) {
        SimdJsonParser parser = new SimdJsonParser(corpus.maxLength() + SyntheaCorpus.PADDING, 1024, validateUtf8);
        Map<String, Long> counts = new TreeMap<>();
        for (int i = 0; i < corpus.size(); i++) {
            JsonValue bundle = parser.parse(corpus.document(i), corpus.length(i));
            JsonValue entries = bundle.isObject() ? bundle.get("entry") : null;
            if (entries == null || !entries.isArray()) {
                continue;
            }
            Iterator<JsonValue> it = entries.arrayIterator();
            while (it.hasNext()) {
                JsonValue entry = it.next();
                JsonValue resource = entry.isObject() ? entry.get("resource") : null;
                if (resource != null && resource.isObject()) {
                    JsonValue type = resource.get("resourceType");
                    String name = type != null && type.isString() ? type.asString() : "<none>";
                    counts.merge(name, 1L, Long::sum);
                }
            }
        }
        out.println("All resource types: " + counts);
    }
}
