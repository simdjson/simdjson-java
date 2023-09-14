package org.simdjson;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.lang.Double.longBitsToDouble;
import static java.lang.Long.compareUnsigned;
import static java.lang.Long.divideUnsigned;
import static java.lang.Long.numberOfLeadingZeros;
import static java.lang.Long.remainderUnsigned;
import static java.lang.Math.abs;
import static java.lang.Math.unsignedMultiplyHigh;
import static org.simdjson.CharacterUtils.isStructuralOrWhitespace;
import static org.simdjson.NumberParserTables.NUMBER_OF_ADDITIONAL_DIGITS_AFTER_LEFT_SHIFT;
import static org.simdjson.NumberParserTables.POWERS_OF_FIVE;
import static org.simdjson.NumberParserTables.POWER_OF_FIVE_DIGITS;

class NumberParser {

    // When parsing doubles, we assume that a long used to store digits is unsigned. Thus, it can safely accommodate
    // up to 19 digits (9999999999999999999 < 2^64).
    private static final int FAST_PATH_MAX_DIGIT_COUNT = 19;
    // The smallest non-zero number representable in binary64 is 2^-1074, which is about 4.941 * 10^-324.
    // If we consider a number in the form of w * 10^q where 1 <= w <= 9999999999999999999, then
    // 1 * 10^q <= w * 10^q <= 9.999999999999999999 * 10^18 * 10^q. To ensure w * 10^q < 2^-1074, q must satisfy the
    // following inequality: 9.999999999999999999 * 10^(18 + q) < 2^-1074. This condition holds true whenever
    // 18 + q < -324. Thus, for q < -342, we can reliably conclude that the number w * 10^q is smaller than 2^-1074,
    // and this, in turn means the number is equal to zero.
    private static final int FAST_PATH_MIN_POWER_OF_TEN = -342;
    // We know that (1 - 2^-53) * 2^1024, which is about 1.798 * 10^308, is the largest number representable in binary64.
    // When the parsed number is expressed as w * 10^q, where w >= 1, we are sure that for any q > 308, the number is
    // infinite.
    private static final int FAST_PATH_MAX_POWER_OF_TEN = 308;
    private static final double[] POWERS_OF_TEN = {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
    };
    private static final long MAX_LONG_REPRESENTED_AS_DOUBLE_EXACTLY = 9007199254740991L; // 2^53 - 1
    private static final int IEEE64_EXPONENT_BIAS = 1023;
    private static final int IEEE64_SIGN_BIT_INDEX = 63;
    private static final int IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT = 52;
    private static final int IEEE64_SIGNIFICAND_SIZE_IN_BITS = IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT + 1;
    private static final int IEEE64_MAX_FINITE_NUMBER_EXPONENT = 1023;
    private static final int IEEE64_MIN_FINITE_NUMBER_EXPONENT = -1022;
    private static final int IEEE64_SUBNORMAL_EXPONENT = -1023;
    private static final int LONG_MAX_DIGIT_COUNT = 19;
    // This is the upper limit for the count of decimal digits taken into account in the slow path. All digits exceeding
    // this threshold are excluded.
    private static final int SLOW_PATH_MAX_DIGIT_COUNT = 800;
    private static final int SLOW_PATH_MAX_SHIFT = 60;
    private static final byte[] SLOW_PATH_SHIFTS = {
            0, 3, 6, 9, 13, 16, 19, 23, 26, 29,
            33, 36, 39, 43, 46, 49, 53, 56, 59,
    };

    private final Tape tape;
    private final SlowPathDecimal slowPathDecimal = new SlowPathDecimal();

    private int currentIdx;

    NumberParser(Tape tape) {
        this.tape = tape;
    }

    void parseNumber(byte[] buffer, int offset) {
        boolean negative = buffer[offset] == '-';

        currentIdx = negative ? offset + 1 : offset;

        int digitsStartIdx = currentIdx;
        long digits = parseDigits(buffer, 0);
        int digitCount = currentIdx - digitsStartIdx;
        if (digitCount == 0) {
            throw new JsonParsingException("Invalid number. Minus has to be followed by a digit.");
        }
        if ('0' == buffer[digitsStartIdx] && digitCount > 1) {
            throw new JsonParsingException("Invalid number. Leading zeroes are not allowed.");
        }

        long exponent = 0;
        boolean isDouble = false;
        if ('.' == buffer[currentIdx]) {
            isDouble = true;
            currentIdx++;
            int firstIdxAfterPeriod = currentIdx;
            digits = parseDigits(buffer, digits);
            exponent = firstIdxAfterPeriod - currentIdx;
            if (exponent == 0) {
                throw new JsonParsingException("Invalid number. Decimal point has to be followed by a digit.");
            }
            digitCount = currentIdx - digitsStartIdx;
        }
        if (isExponentIndicator(buffer[currentIdx])) {
            isDouble = true;
            currentIdx++;
            exponent = parseExponent(buffer, exponent);
        }
        if (!isStructuralOrWhitespace(buffer[currentIdx])) {
            throw new JsonParsingException("Number has to be followed by a structural character or whitespace.");
        }
        if (isDouble) {
            double d;
            if (shouldBeHandledBySlowPath(buffer, digitsStartIdx, digitCount)) {
                d = slowlyParseDouble(buffer, offset);
            } else {
                d = computeDouble(negative, digits, exponent);
            }
            tape.appendDouble(d);
        } else {
            if (isOutOfLongRange(negative, digits, digitCount)) {
                throw new JsonParsingException("Number value is out of long range ([" + Long.MIN_VALUE + ", " + Long.MAX_VALUE + "]).");
            }
            tape.appendInt64(negative ? (~digits + 1) : digits);
        }
    }

    private static boolean isOutOfLongRange(boolean negative, long digits, int digitCount) {
        if (digitCount < LONG_MAX_DIGIT_COUNT) {
            return false;
        }
        if (digitCount > LONG_MAX_DIGIT_COUNT) {
            return true;
        }
        if (negative && digits == Long.MIN_VALUE) {
            // The maximum value we can store in a long is 9223372036854775807. When we try to store 9223372036854775808,
            // a long wraps around, resulting in -9223372036854775808 (Long.MIN_VALUE). If the number we are parsing is
            // negative, and we've attempted to store 9223372036854775808 in "digits", we can be sure that we are
            // dealing with Long.MIN_VALUE, which obviously does not fall outside the acceptable range.
            return false;
        }
        return digits < 0;
    }

    private static double computeDouble(boolean negative, long significand10, long exp10) {
        if (abs(exp10) < POWERS_OF_TEN.length && compareUnsigned(significand10, MAX_LONG_REPRESENTED_AS_DOUBLE_EXACTLY) <= 0) {
            // This path has been described in https://www.exploringbinary.com/fast-path-decimal-to-floating-point-conversion/.
            double d = significand10;
            if (exp10 < 0) {
                d = d / POWERS_OF_TEN[(int) -exp10];
            } else {
                d = d * POWERS_OF_TEN[(int) exp10];
            }
            return negative ? -d : d;
        }

        // The following path is an implementation of the Eisel-Lemire algorithm described by Daniel Lemire in
        // "Number Parsing at a Gigabyte per Second" (https://arxiv.org/abs/2101.11408).

        if (exp10 < FAST_PATH_MIN_POWER_OF_TEN || significand10 == 0) {
            return zero(negative);
        } else if (exp10 > FAST_PATH_MAX_POWER_OF_TEN) {
            return infinity(negative);
        }

        // We start by normalizing the decimal significand so that it is within the range of [2^63, 2^64).
        int lz = numberOfLeadingZeros(significand10);
        significand10 <<= lz;

        // Initially, the number we are parsing is in the form of w * 10^q = w * 5^q * 2^q, and our objective is to
        // convert it to m * 2^p. We can represent w * 10^q as w * 5^q * 2^r * 2^p, where w * 5^q * 2^r = m.
        // Therefore, in the next step we compute w * 5^q. The implementation of this multiplication is optimized
        // to minimize necessary operations while ensuring precise results. For further insight, refer to the
        // aforementioned paper.
        int powersOfFiveTableIndex = 2 * (int) (exp10 - FAST_PATH_MIN_POWER_OF_TEN);
        long upper = unsignedMultiplyHigh(significand10, POWERS_OF_FIVE[powersOfFiveTableIndex]);
        long lower = significand10 * POWERS_OF_FIVE[powersOfFiveTableIndex];
        if ((upper & 0x1FF) == 0x1FF) {
            long secondUpper = unsignedMultiplyHigh(significand10, POWERS_OF_FIVE[powersOfFiveTableIndex + 1]);
            lower += secondUpper;
            if (compareUnsigned(secondUpper, lower) > 0) {
                upper++;
            }
            // As it has been proven by Noble Mushtak and Daniel Lemire in "Fast Number Parsing Without Fallback"
            // (https://arxiv.org/abs/2212.06644), at this point we are sure that the product is sufficiently accurate,
            // and more computation is not needed.
        }

        // Here, we extract the binary significand from the product. Although in binary64 the significand has 53 bits,
        // we extract 54 bits to use the least significant bit for rounding. Since both the decimal significand and the
        // values stored in POWERS_OF_FIVE are normalized, ensuring that their most significant bits are set, the product
        // has either 0 or 1 leading zeros. As a result, we need to perform a right shift of either 9 or 10 bits.
        long upperBit = upper >>> 63;
        long upperShift = upperBit + 9;
        long significand2 = upper >>> upperShift;

        // Now, we have to determine the value of the binary exponent. Let's begin by calculating the contribution of
        // 10^q. Our goal is to compute f0 and f1 such that:
        // - when q >= 0: 10^q = (5^q / 2^(f0 - q)) * 2^f0
        // - when q < 0: 10^q = (2^(f1 - q) / 5^-q) * 2^f1
        // Both (5^q / 2^(f0 - q)) and (2^(f1 - q) / 5^-q) must fall within the range of [1, 2).
        // It turns out that these conditions are met when:
        // - 0 <= q <= FAST_PATH_MAX_POWER_OF_TEN, and f0 = floor(log2(5^q)) + q = floor(q * log(5) / log(2)) + q = (217706 * q) / 2^16.
        // - FAST_PATH_MIN_POWER_OF_TEN <= q < 0, and f1 = -ceil(log2(5^-q)) + q = -ceil(-q * log(5) / log(2)) + q = (217706 * q) / 2^16.
        // Thus, we can express the contribution of 10^q to the exponent as (217706 * exp10) >> 16.
        //
        // Furthermore, we need to factor in the following normalizations we've performed:
        // - shifting the decimal significand left bitwise
        // - shifting the binary significand right bitwise if the most significant bit of the product was 1
        // Therefore, we add (63 - lz + upperBit) to the exponent.
        long exp2 = ((217706 * exp10) >> 16) + 63 - lz + upperBit;
        if (exp2 < IEEE64_MIN_FINITE_NUMBER_EXPONENT) {
            // In the next step, we right-shift the binary significand by the difference between the minimum exponent
            // and the binary exponent. In Java, the shift distance is limited to the range of 0 to 63, inclusive.
            // Thus, we need to handle the case when the distance is >= 64 separately and always return zero.
            if (exp2 <= IEEE64_MIN_FINITE_NUMBER_EXPONENT - 64) {
                return zero(negative);
            }

            // In this branch, it is likely that we are handling a subnormal number. Therefore, we adjust the significand
            // to conform to the formula representing subnormal numbers: (significand2 * 2^(1 - IEEE64_EXPONENT_BIAS)) / 2^52.
            significand2 >>= 1 - IEEE64_EXPONENT_BIAS - exp2;
            // Round up if the significand is odd and remove the least significant bit that we've left for rounding.
            significand2 += significand2 & 1;
            significand2 >>= 1;

            // Here, we are addressing a scenario in which the original number was subnormal, but it became normal after
            // rounding up. For example, when we are parsing 2.2250738585072013e-308 before rounding and removing the
            // least significant bit significand2 = 0x3fffffffffffff and exp2 = -1023. After rounding, we get
            // significand2 = 0x10000000000000, which is the significand of the smallest normal number.
            exp2 = (significand2 < (1L << 52)) ? IEEE64_SUBNORMAL_EXPONENT : IEEE64_MIN_FINITE_NUMBER_EXPONENT;
            return toDouble(negative, significand2, exp2);
        }

        // Here, we are addressing a scenario of rounding the binary significand when it falls precisely halfway
        // between two integers. To understand the rationale behind the condition used to identify this case, refer to
        // sections 6, 8.1, and 9.1 of "Number Parsing at a Gigabyte per Second".
        if ((compareUnsigned(lower, 1) <= 0) && (exp10 >= -4) && (exp10 <= 23) && ((significand2 & 3) == 1)) {
            if (significand2 << upperShift == upper) {
                significand2 &= ~1;
            }
        }

        // Round up if the significand is odd and remove the least significant bit that we've left for rounding.
        significand2 += significand2 & 1;
        significand2 >>= 1;

        if (significand2 == (1L << IEEE64_SIGNIFICAND_SIZE_IN_BITS)) {
            // If we've reached here, it means that rounding has caused an overflow. We need to divide the significand
            // by 2 and update the exponent accordingly.
            significand2 >>= 1;
            exp2++;
        }

        if (exp2 > IEEE64_MAX_FINITE_NUMBER_EXPONENT) {
            return infinity(negative);
        }
        return toDouble(negative, significand2, exp2);
    }

    // The following parser is based on the idea described in
    // https://nigeltao.github.io/blog/2020/parse-number-f64-simple.html and implemented in
    // https://github.com/simdjson/simdjson/blob/caff09cafceb0f5f6fc9109236d6dd09ac4bc0d8/src/from_chars.cpp
    private double slowlyParseDouble(byte[] buffer, int offset) {
        SlowPathDecimal decimal = slowPathDecimal;
        decimal.reset();

        decimal.negative = buffer[offset] == '-';
        currentIdx = decimal.negative ? offset + 1 : offset;
        long exp10 = 0;

        skipZeros(buffer);
        parseDigits(buffer, decimal);
        if (buffer[currentIdx] == '.') {
            currentIdx++;
            int firstIdxAfterPeriod = currentIdx;
            if (decimal.digitCount == 0) {
                skipZeros(buffer);
            }
            parseDigits(buffer, decimal);
            exp10 = firstIdxAfterPeriod - currentIdx;
        }

        int currentIdxMovingBackwards = currentIdx - 1;
        int trailingZeros = 0;
        // Here, we also skip the period to handle cases like 100000000000000000000.000000
        while (buffer[currentIdxMovingBackwards] == '0' || buffer[currentIdxMovingBackwards] == '.') {
            if (buffer[currentIdxMovingBackwards] == '0') {
                trailingZeros++;
            }
            currentIdxMovingBackwards--;
        }
        exp10 += decimal.digitCount;
        decimal.digitCount -= trailingZeros;

        if (decimal.digitCount > SLOW_PATH_MAX_DIGIT_COUNT) {
            decimal.digitCount = SLOW_PATH_MAX_DIGIT_COUNT;
            decimal.truncated = true;
        }

        if (isExponentIndicator(buffer[currentIdx])) {
            currentIdx++;
            exp10 = parseExponent(buffer, exp10);
        }

        // At this point, the number we are parsing is represented in the following way: w * 10^exp10, where -1 < w < 1.
        if (exp10 <= -324) {
            // We know that -1e-324 < w * 10^exp10 < 1e-324. In binary64 -1e-324 = -0.0 and 1e-324 = +0.0, so we can
            // safely return +/-0.0.
            return zero(decimal.negative);
        } else if (exp10 >= 310) {
            // We know that either w * 10^exp10 <= -0.1e310 or w * 10^exp10 >= 0.1e310.
            // In binary64 -0.1e310 = -inf and 0.1e310 = +inf, so we can safely return +/-inf.
            return infinity(decimal.negative);
        }

        decimal.exp10 = (int) exp10;
        int exp2 = 0;

        // We start the following loop with the decimal in the form of w * 10^exp10. After a series of
        // right-shifts (dividing by a power of 2), we transform the decimal into w' * 2^exp2 * 10^exp10,
        // where exp10 is <= 0. Resultantly, w' * 10^exp10 is in the range of [0, 1).
        while (decimal.exp10 > 0) {
            int shift = resolveShiftDistanceBasedOnExponent10(decimal.exp10);
            decimal.shiftRight(shift);
            exp2 += shift;
        }

        // Now, we are left-shifting to get to the point where w'' * 10^exp10 is within the range of [1/2, 1).
        while (decimal.exp10 <= 0) {
            int shift;
            if (decimal.exp10 == 0) {
                if (decimal.digits[0] >= 5) {
                    break;
                }
                shift = (decimal.digits[0] < 2) ? 2 : 1;
            } else {
                shift = resolveShiftDistanceBasedOnExponent10(-decimal.exp10);
            }
            decimal.shiftLeft(shift);
            exp2 -= shift;
        }

        // Here, w'' * 10^exp10 falls within the range of [1/2, 1). In binary64, the significand must be within the
        // range of [1, 2). We can get to the target range by decreasing the binary exponent. Resultantly, the decimal
        // is represented as w'' * 10^exp10 * 2^exp2, where w'' * 10^exp10 is in the range of [1, 2).
        exp2--;

        while (IEEE64_MIN_FINITE_NUMBER_EXPONENT > exp2) {
            int n = IEEE64_MIN_FINITE_NUMBER_EXPONENT - exp2;
            if (n > SLOW_PATH_MAX_SHIFT) {
                n = SLOW_PATH_MAX_SHIFT;
            }
            decimal.shiftRight(n);
            exp2 += n;
        }

        // To conform to the IEEE 754 standard, the binary significand must fall within the range of [2^52, 2^53). Hence,
        // we perform the following multiplication. If, after this step, the significand is less than 2^52, we have a
        // subnormal number, which we will address later.
        decimal.shiftLeft(IEEE64_SIGNIFICAND_SIZE_IN_BITS);

        long significand2 = decimal.computeSignificand();
        if (significand2 >= (1L << IEEE64_SIGNIFICAND_SIZE_IN_BITS)) {
            // If we've reached here, it means that rounding has caused an overflow. We need to divide the significand
            // by 2 and update the exponent accordingly.
            significand2 >>= 1;
            exp2++;
        }

        if (significand2 < (1L << IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT)) {
            exp2 = IEEE64_SUBNORMAL_EXPONENT;
        }
        if (exp2 > IEEE64_MAX_FINITE_NUMBER_EXPONENT) {
            return infinity(decimal.negative);
        }
        return toDouble(decimal.negative, significand2, exp2);
    }

    private static int resolveShiftDistanceBasedOnExponent10(int exp10) {
        return (exp10 < SLOW_PATH_SHIFTS.length) ? SLOW_PATH_SHIFTS[exp10] : SLOW_PATH_MAX_SHIFT;
    }

    private long parseExponent(byte[] buffer, long exponent) {
        boolean negative = '-' == buffer[currentIdx];
        if (negative || '+' == buffer[currentIdx]) {
            currentIdx++;
        }
        int exponentStartIdx = currentIdx;
        long parsedExponent = parseDigits(buffer, 0);
        if (exponentStartIdx == currentIdx) {
            throw new JsonParsingException("Invalid number. Exponent indicator has to be followed by a digit.");
        }
        // Long.MAX_VALUE = 9223372036854775807 (19 digits). Therefore, any number with <= 18 digits can be safely
        // stored in a long without causing an overflow.
        int maxDigitCountLongCanAccommodate = 18;
        if (currentIdx > exponentStartIdx + maxDigitCountLongCanAccommodate) {
            // Potentially, we have an overflow here. We try to skip leading zeros.
            while (buffer[exponentStartIdx] == '0') {
                exponentStartIdx++;
            }
            if (currentIdx > exponentStartIdx + maxDigitCountLongCanAccommodate) {
                // We still have more digits than a long can safely accommodate.
                //
                // The largest finite number that can be represented in binary64 is (1-2^-53) * 2^1024, which is about
                // 1.798e308, and the smallest non-zero number is 2^-1074, roughly 4.941e-324. So, we might, potentially,
                // care only about numbers with explicit exponents falling within the range of [-324, 308], and return
                // either zero or infinity for everything outside of this range.However, we have to take into account
                // the fractional part of the parsed number. This part can potentially cancel out the value of the
                // explicit exponent. For example, 1000e-325 (1 * 10^3 * 10^-325 = 1 * 10^-322) is not equal to zero
                // despite the explicit exponent being less than -324.
                //
                // Let's consider a scenario where the explicit exponent is greater than 999999999999999999. As long as
                // the fractional part has <= 999999999999999690 digits, it doesn't matter whether we take
                // 999999999999999999 or its actual value as the explicit exponent. This is due to the fact that the
                // parsed number is infinite anyway (w * 10^-q * 10^999999999999999999 > (1-2^-53) * 2^1024, 0 < w < 10,
                // 0 <= q <= 999999999999999690). Similarly, in a scenario where the explicit exponent is less than
                // -999999999999999999, as long as the fractional part has <= 999999999999999674 digits, we can safely
                // take 999999999999999999 as the explicit exponent, given that the parsed number is zero anyway
                // (w * 10^q * 10^-999999999999999999 < 2^-1074, 0 < w < 10, 0 <= q <= 999999999999999674)
                //
                // Note that if the fractional part had 999999999999999674 digits, the JSON size would need to be
                // 999999999999999674 bytes, which is approximately ~888 PiB. Consequently, it's reasonable to assume
                // that the fractional part contains no more than 999999999999999674 digits.
                parsedExponent = 999999999999999999L;
            }
        }
        // Note that we don't check if 'exponent' has overflowed after the following addition. This is because we
        // know that the parsed exponent falls within the range of [-999999999999999999, 999999999999999999]. We also
        // assume that 'exponent' before the addition is within the range of [-9223372036854775808, 9223372036854775807].
        // This assumption should always be valid as the value of 'exponent' is constrained by the size of the JSON input.
        exponent += negative ? -parsedExponent : parsedExponent;
        return exponent;
    }

    private long parseDigits(byte[] buffer, long digits) {
        byte digit = convertCharacterToDigit(buffer[currentIdx]);
        while (digit >= 0 && digit <= 9) {
            digits = 10 * digits + digit;
            currentIdx++;
            digit = convertCharacterToDigit(buffer[currentIdx]);
        }
        return digits;
    }

    private static boolean shouldBeHandledBySlowPath(byte[] buffer, int startDigitsIdx, int digitCount) {
        if (digitCount <= FAST_PATH_MAX_DIGIT_COUNT) {
            return false;
        }
        int start = startDigitsIdx;
        while (buffer[start] == '0' || buffer[start] == '.') {
            start++;
        }
        int significantDigitCount = digitCount - (start - startDigitsIdx);
        return significantDigitCount > FAST_PATH_MAX_DIGIT_COUNT;
    }

    private void skipZeros(byte[] buffer) {
        while (buffer[currentIdx] == '0') {
            currentIdx++;
        }
    }

    private void parseDigits(byte[] buffer, SlowPathDecimal decimal) {
        while (isDigit(buffer[currentIdx])) {
            if (decimal.digitCount < SLOW_PATH_MAX_DIGIT_COUNT) {
                decimal.digits[decimal.digitCount] = convertCharacterToDigit(buffer[currentIdx]);
            }
            decimal.digitCount++;
            currentIdx++;
        }
    }

    private static boolean isDigit(byte b) {
        return b >= '0' && b <= '9';
    }

    private static boolean isExponentIndicator(byte b) {
        return 'e' == b || 'E' == b;
    }

    private static double toDouble(boolean negative, long significand2, long exp2) {
        long bits = significand2;
        bits &= ~(1L << IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT); // clear the implicit bit
        bits |= (exp2 + IEEE64_EXPONENT_BIAS) << IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT;
        bits = negative ? (bits | (1L << IEEE64_SIGN_BIT_INDEX)) : bits;
        return longBitsToDouble(bits);
    }

    private static double infinity(boolean negative) {
        return negative ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
    }

    private static double zero(boolean negative) {
        return negative ? -0.0 : 0.0;
    }

    private static byte convertCharacterToDigit(byte b) {
        return (byte) (b - '0');
    }

    private static class SlowPathDecimal {

        final byte[] digits = new byte[SLOW_PATH_MAX_DIGIT_COUNT];
        int digitCount;
        int exp10;
        boolean truncated;
        boolean negative;

        // Before calling this method we have to make sure that the significand is within the range of [0, 2^53 - 1].
        long computeSignificand() {
            if (digitCount == 0 || exp10 < 0) {
                return 0;
            }
            long significand = 0;
            for (int i = 0; i < exp10; i++) {
                significand = (10 * significand) + ((i < digitCount) ? digits[i] : 0);
            }
            boolean roundUp = false;
            if (exp10 < digitCount) {
                roundUp = digits[exp10] >= 5;
                if ((digits[exp10] == 5) && (exp10 + 1 == digitCount)) {
                    // If the digits haven't been truncated, then we are exactly halfway between two integers. In such
                    // cases, we round to even, otherwise we round up.
                    roundUp = truncated || (significand & 1) == 1;
                }
            }
            return roundUp ? ++significand : significand;
        }

        void shiftLeft(int shift) {
            if (digitCount == 0) {
                return;
            }

            int numberOfAdditionalDigits = calculateNumberOfAdditionalDigitsAfterLeftShift(shift);
            int readIndex = digitCount - 1;
            int writeIndex = digitCount - 1 + numberOfAdditionalDigits;
            long n = 0;

            while (readIndex >= 0) {
                n += (long) digits[readIndex] << shift;
                long quotient = divideUnsigned(n, 10);
                long remainder = remainderUnsigned(n, 10);
                if (writeIndex < SLOW_PATH_MAX_DIGIT_COUNT) {
                    digits[writeIndex] = (byte) remainder;
                } else if (remainder > 0) {
                    truncated = true;
                }
                n = quotient;
                writeIndex--;
                readIndex--;
            }

            while (compareUnsigned(n, 0) > 0) {
                long quotient = divideUnsigned(n, 10);
                long remainder = remainderUnsigned(n, 10);
                if (writeIndex < SLOW_PATH_MAX_DIGIT_COUNT) {
                    digits[writeIndex] = (byte) remainder;
                } else if (remainder > 0) {
                    truncated = true;
                }
                n = quotient;
                writeIndex--;
            }
            digitCount += numberOfAdditionalDigits;
            if (digitCount > SLOW_PATH_MAX_DIGIT_COUNT) {
                digitCount = SLOW_PATH_MAX_DIGIT_COUNT;
            }
            exp10 += numberOfAdditionalDigits;
            trimTrailingZeros();
        }

        // See https://nigeltao.github.io/blog/2020/parse-number-f64-simple.html#hpd-shifts
        private int calculateNumberOfAdditionalDigitsAfterLeftShift(int shift) {
            int a = NUMBER_OF_ADDITIONAL_DIGITS_AFTER_LEFT_SHIFT[shift];
            int b = NUMBER_OF_ADDITIONAL_DIGITS_AFTER_LEFT_SHIFT[shift + 1];
            int newDigitCount = a >> 11;
            int pow5OffsetA = 0x7FF & a;
            int pow5OffsetB = 0x7FF & b;

            int n = pow5OffsetB - pow5OffsetA;
            for (int i = 0; i < n; i++) {
                if (i >= digitCount) {
                    return newDigitCount - 1;
                } else if (digits[i] < POWER_OF_FIVE_DIGITS[pow5OffsetA + i]) {
                    return newDigitCount - 1;
                } else if (digits[i] > POWER_OF_FIVE_DIGITS[pow5OffsetA + i]) {
                    return newDigitCount;
                }
            }
            return newDigitCount;
        }

        void shiftRight(int shift) {
            int readIndex = 0;
            int writeIndex = 0;
            long n = 0;

            while ((n >>> shift) == 0) {
                if (readIndex < digitCount) {
                    n = (10 * n) + digits[readIndex++];
                } else if (n == 0) {
                    return;
                } else {
                    while ((n >>> shift) == 0) {
                        n = 10 * n;
                        readIndex++;
                    }
                    break;
                }
            }
            exp10 -= (readIndex - 1);
            long mask = (1L << shift) - 1;
            while (readIndex < digitCount) {
                byte newDigit = (byte) (n >>> shift);
                n = (10 * (n & mask)) + digits[readIndex++];
                digits[writeIndex++] = newDigit;
            }
            while (compareUnsigned(n, 0) > 0) {
                byte newDigit = (byte) (n >>> shift);
                n = 10 * (n & mask);
                if (writeIndex < SLOW_PATH_MAX_DIGIT_COUNT) {
                    digits[writeIndex++] = newDigit;
                } else if (newDigit > 0) {
                    truncated = true;
                }
            }
            digitCount = writeIndex;
            trimTrailingZeros();
        }

        private void trimTrailingZeros() {
            while ((digitCount > 0) && (digits[digitCount - 1] == 0)) {
                digitCount--;
            }
        }

        private void reset() {
            digitCount = 0;
            exp10 = 0;
            truncated = false;
        }
    }
}
