package org.simdjson;

import jdk.incubator.vector.ByteVector;

import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class StringUtils {

    static String padWithSpaces(String str) {
        int targetLength = 64;
        byte[] strBytes = str.getBytes(UTF_8);
        assertThat(strBytes.length).isLessThanOrEqualTo(targetLength);
        byte[] padded = new byte[targetLength];
        Arrays.fill(padded, (byte) ' ');
        System.arraycopy(strBytes, 0, padded, 0, strBytes.length);
        return new String(padded, UTF_8);
    }


    static ByteVector chunk(String str) {
        return ByteVector.fromArray(ByteVector.SPECIES_512, str.getBytes(UTF_8), 0);
    }

    static ByteVector chunk0(String str) {
        return ByteVector.fromArray(ByteVector.SPECIES_256, str.getBytes(UTF_8), 0);
    }

    static ByteVector chunk1(String str) {
        return ByteVector.fromArray(ByteVector.SPECIES_256, str.getBytes(UTF_8), 32);
    }

    static ByteVector chunk(String str, int n) {
        return ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, str.getBytes(UTF_8), n * 32);
    }

    static ByteVector[] chunks(String str) {
        int bytesPerChunk = ByteVector.SPECIES_PREFERRED.vectorByteSize();
        int nChunks = 64 / bytesPerChunk;
        ByteVector[] chunks = switch (nChunks) {
            case 1 -> new ByteVector[] {chunk(str, 0)};
            case 2 -> new ByteVector[] {chunk(str, 0), chunk(str, 1)};
            case 4 -> new ByteVector[] {chunk(str, 0), chunk(str, 1), chunk(str, 2), chunk(str, 3)};
            default -> throw new RuntimeException("Unsupported chunk count: " + nChunks);
        };
        return chunks;
    }
}
