package org.simdjson;

import jdk.incubator.vector.ByteVector;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

class TestUtils {

    static String padWithSpaces(String str) {
        byte[] strBytes = toUtf8(str);
        byte[] padded = new byte[strBytes.length + 64];
        Arrays.fill(padded, (byte) ' ');
        System.arraycopy(strBytes, 0, padded, 0, strBytes.length);
        return new String(padded, UTF_8);
    }

    static ByteVector chunk(String str, int n) {
        return ByteVector.fromArray(StructuralIndexer.SPECIES, str.getBytes(UTF_8), n * StructuralIndexer.SPECIES.vectorByteSize());
    }

    static byte[] toUtf8(String str) {
        return str.getBytes(UTF_8);
    }

    static byte[] loadTestFile(String name) throws IOException {
        try (InputStream is = TestUtils.class.getResourceAsStream(name)) {
            return is.readAllBytes();
        }
    }
}
