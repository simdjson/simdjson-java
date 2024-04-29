package org.simdjson.testutils;

import java.util.Random;

class NumberTestData {

    private static final Random RANDOM = new Random();

    static byte randomByte() {
        return (byte) RANDOM.nextInt();
    }

    static short randomShort() {
        return (short) RANDOM.nextInt();
    }

    static int randomInt() {
        return RANDOM.nextInt();
    }

    static long randomLong() {
        return RANDOM.nextLong();
    }

    static double randomDouble() {
        while (true) {
            double randomVal = Double.longBitsToDouble(RANDOM.nextLong());
            if (randomVal < Double.POSITIVE_INFINITY && randomVal > Double.NEGATIVE_INFINITY) {
                return randomVal;
            }
        }
    }

    static float randomFloat() {
        while (true) {
            float randomVal = Float.intBitsToFloat(RANDOM.nextInt());
            if (randomVal < Float.POSITIVE_INFINITY && randomVal > Float.NEGATIVE_INFINITY) {
                return randomVal;
            }
        }
    }
}
