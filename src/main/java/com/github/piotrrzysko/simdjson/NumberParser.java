package com.github.piotrrzysko.simdjson;

import static com.github.piotrrzysko.simdjson.JsonCharUtils.isStructuralOrWhitespace;

class NumberParser {

    private static final int SMALLEST_POWER = -342;
    private static final int LARGEST_POWER = 308;
    private static final double[] POWER_OF_TEN = {
            1e0,  1e1,  1e2,  1e3,  1e4,  1e5,  1e6,  1e7,  1e8,  1e9,  1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
    };

    private final Tape tape;

    private int currentIdx;

    NumberParser(Tape tape) {
        this.tape = tape;
    }

    void parseNumber(byte[] buffer, int buffIdx, int jsonIdx) {
        boolean negative = buffer[buffIdx] == '-';

        currentIdx = negative ? buffIdx + 1 : buffIdx;

        int startDigitsIdx = currentIdx;
        long i = parseDigits(buffer, 0);
        int digitCount = currentIdx - startDigitsIdx;
        if (digitCount == 0 || ('0' == buffer[startDigitsIdx] && digitCount > 1)) {
            throwException(jsonIdx);
        }

        long exponent = 0;
        boolean isDouble = false;
        if ('.' == buffer[currentIdx]) {
            isDouble = true;
            currentIdx++;
            int firstAfterPeriod = currentIdx;
            i = parseDigits(buffer, i);
            exponent = firstAfterPeriod - currentIdx;
            if (exponent == 0) {
                throwException(jsonIdx);
            }
        }
        if ('e' == buffer[currentIdx] || 'E' == buffer[currentIdx]) {
            isDouble = true;
            currentIdx++;
            exponent = parseExponent(buffer, jsonIdx, exponent);
        }
        if (!isStructuralOrWhitespace(buffer[currentIdx])) {
            throwException(jsonIdx);
        }
        if (isDouble) {
            if (digitCount > 19 && significantDigits(buffer, startDigitsIdx, digitCount) > 19) {
                throw new UnsupportedOperationException("Not implemented yet");
            } else if (exponent < SMALLEST_POWER || exponent > LARGEST_POWER) {
                if (exponent < SMALLEST_POWER || i == 0) {
                    tape.appendDouble(negative ? -0.0 : 0.0);
                } else {
                    throwException(jsonIdx);
                }
            } else {
                double computed = computeDouble(exponent, i, negative);
                tape.appendDouble(computed);
            }
        } else {
            tape.appendInt64(negative ? (~i + 1) : i);
        }
    }

    private double computeDouble(long power, long i, boolean negative) {
        if (-22 < power && power <= 22 && i <= 9007199254740991L) {
            double d = i;
            if (power < 0) {
                d = d / POWER_OF_TEN[(int) -power];
            } else {
                d = d * POWER_OF_TEN[(int) power];
            }
            if (negative) {
                d = -d;
            }
            return d;
        }
        if (i == 0) {
            return negative ? -0.0 : 0.0;
        }
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private long parseExponent(byte[] buffer, int jsonIdx, long exponent) {
        boolean negExp = '-' == buffer[currentIdx];
        if (negExp || '+' == buffer[currentIdx]) {
            currentIdx++;
        }

        int startExp = currentIdx;
        long expNumber = parseDigits(buffer, 0);

        if (startExp == currentIdx) {
            throwException(jsonIdx);
        }

        if (currentIdx > startExp + 18) {
            while (buffer[startExp] == '0') {
                startExp++;
            }
            if (currentIdx > startExp + 18) {
                expNumber = 999999999999999999L;
            }
        }
        exponent += (negExp ? -expNumber : expNumber);
        return exponent;
    }

    private long parseDigits(byte[] buffer, long acc) {
        byte digit = (byte) (buffer[currentIdx] - '0');
        while (digit >= 0 && digit <= 9) {
            acc = 10 * acc + digit;
            currentIdx++;
            digit = (byte) (buffer[currentIdx] - '0');
        }
        return acc;
    }

    private int significantDigits(byte[] buffer, int startDigitsIdx, int digitCount) {
        int start = startDigitsIdx;
        while (buffer[start] == '0' || buffer[start] == '.') {
            start++;
        }
        return digitCount - start - startDigitsIdx;
    }

    private void throwException(int idx) {
        throw new JsonParsingException("Invalid number starting at " + idx);
    }
}
