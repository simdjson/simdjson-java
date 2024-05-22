package org.simdjson;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

import static java.lang.invoke.MethodHandles.byteArrayViewVarHandle;

class ConstructorArgumentsMap {

    private static final VarHandle VAR_HANDLE_LONG = byteArrayViewVarHandle(Long.TYPE.arrayType(), ByteOrder.nativeOrder());
    private static final VarHandle VAR_HANDLE_INT = byteArrayViewVarHandle(Integer.TYPE.arrayType(), ByteOrder.nativeOrder());
    // Large prime number. This one is taken from https://vanilla-java.github.io/2018/08/15/Looking-at-randomness-and-performance-for-hash-codes.html
    private static final long M2 = 0x7a646e4d;

    private final int argumentCount;
    private final int capacity;
    private final int moduloMask;
    private final byte[][] keys;
    private final ConstructorArgument[] arguments;

    ConstructorArgumentsMap(int argumentCount) {
        this.argumentCount = argumentCount;
        this.capacity = ceilingPowerOfTwo(argumentCount);
        this.moduloMask = capacity - 1;
        this.arguments = new ConstructorArgument[capacity];
        this.keys = new byte[capacity][];
    }

    private static int ceilingPowerOfTwo(int argumentCount) {
        // We don't need to check if argumentCount is greater than 2^30 because, in Java, the limit for method arguments
        // is equal to 255 (https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3.3).
        return 1 << -Integer.numberOfLeadingZeros(argumentCount - 1);
    }

    int getArgumentCount() {
        return argumentCount;
    }

    void put(byte[] fieldName, ConstructorArgument argument) {
        int place = findPlace(fieldName, fieldName.length);

        while (keys[place] != null) {
            place = (place + 1) & moduloMask;
        }
        arguments[place] = argument;
        keys[place] = fieldName;
    }

    ConstructorArgument get(byte[] buffer, int len) {
        int place = findPlace(buffer, len);
        for (int i = 0; i < capacity; i++) {
            byte[] key = keys[place];
            if (key == null) {
                return null;
            }
            if (Arrays.equals(key, 0, key.length, buffer, 0, len)) {
                return arguments[place];
            }
            place = (place + 1) & moduloMask;
        }
        return null;
    }

    private int findPlace(byte[] buffer, int len) {
        int hash = hash(buffer, len);
        return hash & moduloMask;
    }

    private static int hash(byte[] data, int len) {
        long h = 0;
        int i = 0;
        for (; i + 7 < len; i += 8) {
            h = h * M2 + getLongFromArray(data, i);
        }
        if (i + 3 < len) {
            h = h * M2 + getIntFromArray(data, i);
            i += 4;
        }
        for (; i < len; i++) {
            h = h * M2 + data[i];
        }
        h *= M2;
        return (int) (h ^ h >>> 32);
    }

    private static int getIntFromArray(byte[] value, int i) {
        return (int) VAR_HANDLE_INT.get(value, i);
    }

    private static long getLongFromArray(byte[] value, int i) {
        return (long) VAR_HANDLE_LONG.get(value, i);
    }
}
