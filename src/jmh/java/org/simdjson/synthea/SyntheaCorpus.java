package org.simdjson.synthea;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * A deterministic, in-memory selection of Synthea FHIR bundles.
 * <p>
 * Configuration (system properties):
 * <ul>
 *     <li>{@code synthea.dir} (required): directory searched recursively for {@code *.json} files.</li>
 *     <li>{@code synthea.maxFiles} (default 256): take at most this many files.</li>
 *     <li>{@code synthea.maxBytes} (default unlimited): stop before the first file that would push the
 *     selection over this many bytes.</li>
 *     <li>{@code synthea.expectFingerprint} (optional): fail if the selection's fingerprint differs.</li>
 * </ul>
 * Files are sorted by their path relative to {@code synthea.dir} ('/'-separated, {@link String#compareTo}),
 * and the first ones are taken, so the same directory and limits always select the same files.
 * Every document is stored in its own array with {@link #PADDING} zero bytes after the JSON, so the parser
 * never has to copy it into its internal padded buffer.
 */
public final class SyntheaCorpus {

    public static final int PADDING = 64;

    private final Path root;
    private final String[] names;
    private final byte[][] documents;
    private final int[] lengths;
    private final long totalBytes;
    private final int maxLength;
    private final String largestName;
    private final String fingerprint;

    private SyntheaCorpus(Path root, String[] names, byte[][] documents, int[] lengths) {
        this.root = root;
        this.names = names;
        this.documents = documents;
        this.lengths = lengths;
        long total = 0;
        int max = 0;
        String largest = null;
        for (int i = 0; i < lengths.length; i++) {
            total += lengths[i];
            if (lengths[i] > max) {
                max = lengths[i];
                largest = names[i];
            }
        }
        this.totalBytes = total;
        this.maxLength = max;
        this.largestName = largest;
        this.fingerprint = computeFingerprint(names, lengths);
    }

    public static SyntheaCorpus fromSystemProperties() {
        String dir = System.getProperty("synthea.dir");
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException("System property synthea.dir is not set. "
                    + "Point it at a directory containing Synthea FHIR bundles (*.json).");
        }
        int maxFiles = Integer.parseInt(System.getProperty("synthea.maxFiles", "256"));
        long maxBytes = Long.parseLong(System.getProperty("synthea.maxBytes", String.valueOf(Long.MAX_VALUE)));
        SyntheaCorpus corpus = load(Paths.get(dir), maxFiles, maxBytes);
        String expected = System.getProperty("synthea.expectFingerprint");
        if (expected != null && !expected.isBlank() && !expected.equals(corpus.fingerprint)) {
            throw new IllegalStateException("Corpus fingerprint mismatch: expected " + expected
                    + " but the selected files have " + corpus.fingerprint);
        }
        return corpus;
    }

    public static SyntheaCorpus load(Path root, int maxFiles, long maxBytes) {
        if (maxFiles <= 0) {
            throw new IllegalArgumentException("synthea.maxFiles must be positive, got " + maxFiles);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("synthea.dir is not a directory: " + root.toAbsolutePath());
        }
        List<String> all = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .forEach(p -> all.add(relativeName(root, p)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + root, e);
        }
        if (all.isEmpty()) {
            throw new IllegalStateException("No *.json files found under " + root.toAbsolutePath());
        }
        all.sort(String::compareTo);

        List<String> names = new ArrayList<>();
        List<byte[]> documents = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        long total = 0;
        for (String name : all) {
            if (names.size() == maxFiles) {
                break;
            }
            Path path = root.resolve(name);
            long size = size(path);
            if (size > Integer.MAX_VALUE - PADDING) {
                throw new IllegalStateException("File too large for a byte array: " + path);
            }
            if (total + size > maxBytes) {
                break;
            }
            byte[] document = new byte[(int) size + PADDING];
            read(path, document, (int) size);
            names.add(name);
            documents.add(document);
            lengths.add((int) size);
            total += size;
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("synthea.maxBytes=" + maxBytes + " excludes even the first file " + all.get(0));
        }
        int[] lengthArray = new int[lengths.size()];
        for (int i = 0; i < lengthArray.length; i++) {
            lengthArray[i] = lengths.get(i);
        }
        return new SyntheaCorpus(root, names.toArray(new String[0]), documents.toArray(new byte[0][]), lengthArray);
    }

    private static String relativeName(Path root, Path file) {
        StringBuilder sb = new StringBuilder();
        for (Path part : root.relativize(file)) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot stat " + path, e);
        }
    }

    private static void read(Path path, byte[] target, int size) {
        try (InputStream in = Files.newInputStream(path)) {
            int n = in.readNBytes(target, 0, size);
            if (n != size || in.read() != -1) {
                throw new IllegalStateException("File changed while loading: " + path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    private static String computeFingerprint(String[] names, int[] lengths) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < names.length; i++) {
                digest.update(names[i].getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Integer.toString(lengths[i]).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest(), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void printSummary(PrintStream out) {
        out.println("Synthea corpus: dir=" + root.toAbsolutePath()
                + " files=" + size()
                + " totalBytes=" + totalBytes
                + " largest=" + maxLength + " (" + largestName + ")"
                + " fingerprint=" + fingerprint);
    }

    public int size() {
        return documents.length;
    }

    public String name(int i) {
        return names[i];
    }

    /** The JSON bytes of document {@code i}, followed by at least {@link #PADDING} zero bytes. */
    public byte[] document(int i) {
        return documents[i];
    }

    public int length(int i) {
        return lengths[i];
    }

    public long totalBytes() {
        return totalBytes;
    }

    public int maxLength() {
        return maxLength;
    }

    /** First 16 hex digits of SHA-256 over the selected relative names and sizes, in selection order. */
    public String fingerprint() {
        return fingerprint;
    }
}
