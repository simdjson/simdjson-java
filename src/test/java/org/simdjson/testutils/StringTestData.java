package org.simdjson.testutils;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.translate.AggregateTranslator;
import org.apache.commons.text.translate.CharSequenceTranslator;
import org.apache.commons.text.translate.JavaUnicodeEscaper;
import org.apache.commons.text.translate.LookupTranslator;

import java.util.List;
import java.util.Map;

import static java.lang.Character.MAX_CODE_POINT;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;
import static java.util.stream.IntStream.rangeClosed;

public class StringTestData {

    public static final CharSequenceTranslator ESCAPE_JSON = new AggregateTranslator(
            new LookupTranslator(Map.of("\"", "\\\"", "\\", "\\\\")),
            JavaUnicodeEscaper.below(0x20)
    );

    public static String randomString(int minChars, int maxChars) {
        int stringLen = RandomUtils.nextInt(minChars, maxChars + 1);
        var rawString = RandomStringUtils.random(stringLen);
        var jsonString = ESCAPE_JSON.translate(rawString);
        System.out.println("Generated string: " + jsonString + " [" + StringEscapeUtils.escapeJava(jsonString) + "]");
        return jsonString;
    }

    /**
     * Returns all usable characters that don't need to be escaped.
     * It means that all control characters, '"', and '\' are not returned.
     */
    public static List<String> usableSingleCodeUnitCharacters() {
        return rangeClosed(0, MAX_CODE_POINT)
                .filter(Character::isBmpCodePoint)
                .filter(codePoint -> !isReservedCodePoint(codePoint))
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .filter(codePoint -> (char) codePoint != '"')
                .filter(codePoint -> (char) codePoint != '\\')
                .mapToObj(codePoint -> (char) codePoint)
                .map(String::valueOf)
                .toList();
    }

    public static List<String> usableEscapedSingleCodeUnitCharacters() {
        return rangeClosed(0, MAX_CODE_POINT)
                .filter(Character::isBmpCodePoint)
                .filter(codePoint -> !isReservedCodePoint(codePoint))
                .mapToObj(StringTestData::toUnicodeEscape)
                .toList();
    }

    public static List<String> reservedEscapedSingleCodeUnitCharacters() {
        return rangeClosed(0, MAX_CODE_POINT)
                .filter(Character::isBmpCodePoint)
                .filter(StringTestData::isReservedCodePoint)
                .mapToObj(StringTestData::toUnicodeEscape)
                .toList();
    }

    public static List<String> escapedLowSurrogates() {
        return rangeClosed(0xDC00, 0xDFFF)
                .mapToObj(StringTestData::toUnicodeEscape)
                .toList();
    }

    public static List<String> usableTwoCodeUnitsCharacters() {
        return rangeClosed(0, MAX_CODE_POINT)
                .filter(codePoint -> !Character.isBmpCodePoint(codePoint))
                .mapToObj(Character::toString)
                .toList();
    }

    public static List<String> usableEscapedUnicodeCharacters() {
        return rangeClosed(0, MAX_CODE_POINT)
                .filter(codePoint -> !isReservedCodePoint(codePoint))
                .mapToObj(StringTestData::toUnicodeEscape)
                .toList();
    }

    public static List<String> escapedUnicodeCharactersWithInvalidLowSurrogate() {
        return rangeClosed(0x0000, 0xFFFF)
                .filter(lowSurrogate -> lowSurrogate < 0xDC00 || lowSurrogate > 0xDFFF)
                .mapToObj(lowSurrogate -> String.format("\\uD800\\u%04X", lowSurrogate))
                .toList();
    }

    public static List<String> unescapedControlCharacters() {
        return rangeClosed(0, 0x001F)
                .mapToObj(codePoint -> (char) codePoint)
                .map(String::valueOf)
                .toList();
    }

    private static String toUnicodeEscape(int codePoint) {
        if (isBmpCodePoint(codePoint)) {
            return String.format("\\u%04X", codePoint);
        } else {
            return String.format("\\u%04X\\u%04X",
                    (int) Character.highSurrogate(codePoint), (int) lowSurrogate(codePoint));
        }
    }

    private static boolean isReservedCodePoint(int codePoint) {
        return codePoint >= 0xD800 && codePoint <= 0xDFFF;
    }
}
