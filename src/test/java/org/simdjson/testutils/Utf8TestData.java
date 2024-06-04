package org.simdjson.testutils;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Utf8TestData {

    /**
     * Generates UTF-8 sequences from the provided range. Each sequence is of the given length.
     * Note that when the length is greater than necessary for a given code point, this function
     * produces sequences that are invalid UTF-8. This is a useful property when one wants to
     * generate overlong encodings for testing purposes.
     */
    public static List<byte[]> utf8Sequences(int from, int to, int length) {
        List<byte[]> result = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            byte[] bytes = new byte[length];
            int current = i;
            // continuation bytes
            for (int byteIdx = length - 1; byteIdx >= 1; byteIdx--) {
                bytes[byteIdx] = (byte) (0b1000_0000 | (current & 0b0011_1111));
                current = current >>> 6;
            }
            // leading byte
            bytes[0] = (byte) ((0x80000000 >> (24 + length - 1)) | (current & 0b0011_111));
            result.add(bytes);
        }
        return result;
    }

    public static byte[] randomUtf8ByteArray() {
        return randomUtf8ByteArray(1, 1000);
    }

    public static byte[] randomUtf8ByteArrayIncluding(byte... sequence) {
        byte[] prefix = randomUtf8ByteArray(0, 500);
        byte[] suffix = randomUtf8ByteArray(0, 500);
        byte[] result = new byte[prefix.length + sequence.length + suffix.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(sequence, 0, result, prefix.length, sequence.length);
        System.arraycopy(suffix, 0, result, prefix.length + sequence.length, suffix.length);
        return result;
    }

    public static byte[] randomUtf8ByteArrayEndedWith(byte... sequence) {
        byte[] array = randomUtf8ByteArray(0, 1000);
        byte[] result = new byte[array.length + sequence.length];
        System.arraycopy(array, 0, result, 0, array.length);
        System.arraycopy(sequence, 0, result, array.length, sequence.length);
        return result;
    }

    private static byte[] randomUtf8ByteArray(int minChars, int maxChars) {
        int stringLen = RandomUtils.nextInt(minChars, maxChars + 1);
        var string = RandomStringUtils.random(stringLen);
        return string.getBytes(StandardCharsets.UTF_8);
    }
}
