package org.simdjson.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TestUtils {

    public static byte[] toUtf8PaddedWithSpaces(String str) {
        byte[] strBytes = toUtf8(str);
        byte[] padded = new byte[strBytes.length + 64];
        Arrays.fill(padded, (byte) ' ');
        System.arraycopy(strBytes, 0, padded, 0, strBytes.length);
        return padded;
    }

    public static byte[] toUtf8(String str) {
        return str.getBytes(UTF_8);
    }

    public static byte[] loadTestFile(String name) throws IOException {
        try (InputStream is = TestUtils.class.getResourceAsStream(name)) {
            return is.readAllBytes();
        }
    }

    public static String toHexString(byte[] array) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(String.format("%02X", array[i]));
            if (i < array.length - 1) {
                sb.append(" ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
