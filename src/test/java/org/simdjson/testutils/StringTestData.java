package org.simdjson.testutils;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static java.lang.Character.MAX_CODE_POINT;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;
import static java.util.stream.IntStream.rangeClosed;

public class StringTestData {

    private static final Map<String, String> CONTROL_CHARACTER_ESCAPE = new HashMap<>();

    static {
        for (int codePoint = 0; codePoint <= 0x001F; codePoint++) {
            String controlCharacter = String.valueOf((char) codePoint);
            CONTROL_CHARACTER_ESCAPE.put(controlCharacter, toUnicodeEscape(codePoint));
        }
    }

    public static String randomString(int minChars, int maxChars) {
        int stringLen = RandomUtils.nextInt(minChars, maxChars + 1);
        var string = RandomStringUtils.random(stringLen)
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\\\\", "\\\\\\\\");
        for (Map.Entry<String, String> entry : CONTROL_CHARACTER_ESCAPE.entrySet()) {
            string = string.replaceAll(entry.getKey(), Matcher.quoteReplacement(entry.getValue()));
        }
        System.out.println("Generated string: " + string + " [" + StringEscapeUtils.escapeJava(string) + "]");
        return string;
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
