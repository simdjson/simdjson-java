package org.simdjson.synthea;

import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Order-sensitive 64-bit checksum over the values extracted from a corpus, plus resource counts per type.
 * <p>
 * Each extracted value is first reduced to a 64-bit value hash, then folded into the running checksum:
 * <ul>
 *     <li>string: FNV-1a over its UTF-8 bytes, finished with the length ({@link #hashString});</li>
 *     <li>number: the IEEE-754 bits of the value as a {@code double}, with -0.0 mapped to 0.0
 *     ({@link #hashNumber}); the JSON literals {@code 5}, {@code 5.0} and {@code 5e0} therefore hash
 *     identically;</li>
 *     <li>a field that is missing, {@code null}, or of an unexpected JSON type: the constant {@link #ABSENT}.</li>
 * </ul>
 * Resources are folded in document order. Within a resource the values are folded in a fixed order that
 * does not depend on the order of keys in the JSON (see {@link SyntheaExtractor}).
 */
public final class SyntheaChecksum {

    public static final int PATIENT = 0;
    public static final int ENCOUNTER = 1;
    public static final int OBSERVATION = 2;
    public static final int CONDITION = 3;
    public static final int OTHER = 4;
    static final String[] TYPE_NAMES = {"Patient", "Encounter", "Observation", "Condition", "other"};

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long NUMBER_SEED = 0x6a09e667f3bcc909L;
    private static final long FOLD_MULTIPLIER = 0x9e3779b97f4a7c15L;

    public static final long ABSENT = mix(0xbb67ae8584caa73bL);

    private final long[] counts = new long[TYPE_NAMES.length];
    private long checksum;

    public void reset() {
        checksum = 0;
        Arrays.fill(counts, 0);
    }

    public void fold(long valueHash) {
        checksum = (Long.rotateLeft(checksum, 23) ^ valueHash) * FOLD_MULTIPLIER;
    }

    public void count(int type) {
        counts[type]++;
    }

    public long checksum() {
        return checksum;
    }

    public long[] counts() {
        return counts.clone();
    }

    public static long hashString(byte[] bytes, int offset, int length) {
        long h = FNV_OFFSET;
        for (int i = offset; i < offset + length; i++) {
            h ^= bytes[i] & 0xFF;
            h *= FNV_PRIME;
        }
        return mix(h ^ length);
    }

    /** Same value as {@link #hashString(byte[], int, int)} over {@code s.getBytes(UTF_8)}. */
    public static long hashString(String s) {
        long h = FNV_OFFSET;
        int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (c >= 0x80) {
                // Not ASCII: UTF-16 chars are no longer UTF-8 bytes, so encode.
                byte[] bytes = s.getBytes(UTF_8);
                return hashString(bytes, 0, bytes.length);
            }
            h ^= c;
            h *= FNV_PRIME;
        }
        return mix(h ^ length);
    }

    public static long hashNumber(double value) {
        double canonical = value == 0.0 ? 0.0 : value;
        return mix(Double.doubleToLongBits(canonical) ^ NUMBER_SEED);
    }

    private static long mix(long z) {
        // SplitMix64 finalizer.
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** Immutable snapshot of a checksum and its counts. */
    public record Result(long checksum, long[] counts) {

        public static Result of(SyntheaChecksum sum) {
            return new Result(sum.checksum(), sum.counts());
        }

        public boolean sameAs(Result other) {
            return checksum == other.checksum && Arrays.equals(counts, other.counts);
        }

        public long resources() {
            long total = 0;
            for (long c : counts) {
                total += c;
            }
            return total;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(String.format("checksum=%016x resources=%d", checksum, resources()));
            for (int i = 0; i < counts.length; i++) {
                sb.append(' ').append(TYPE_NAMES[i]).append('=').append(counts[i]);
            }
            return sb.toString();
        }
    }
}
