package org.simdjson;

import org.simdjson.OnDemandJsonIterator.IteratorResult;
import org.simdjson.ResolvedClass.ResolvedClassCategory;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

class SchemaBasedJsonIterator {

    private static final int INITIAL_ARRAY_SIZE = 16;

    private final ClassResolver classResolver;
    private final OnDemandJsonIterator jsonIterator;
    private final byte[] stringBuffer;

    SchemaBasedJsonIterator(BitIndexes bitIndexes, byte[] stringBuffer, int padding) {
        this.jsonIterator = new OnDemandJsonIterator(bitIndexes, padding);
        this.classResolver = new ClassResolver();
        this.stringBuffer = stringBuffer;
    }

    @SuppressWarnings("unchecked")
    <T> T walkDocument(byte[] padded, int len, Class<T> expectedType) {
        jsonIterator.init(padded, len);
        classResolver.reset();

        ResolvedClass resolvedExpectedClass = classResolver.resolveClass(expectedType);
        return switch (resolvedExpectedClass.getClassCategory()) {
            case BOOLEAN_PRIMITIVE -> (T) jsonIterator.getRootNonNullBoolean();
            case BOOLEAN -> (T) jsonIterator.getRootBoolean();
            case BYTE_PRIMITIVE -> (T) Byte.valueOf(jsonIterator.getRootNonNullByte());
            case BYTE -> (T) jsonIterator.getRootByte();
            case SHORT_PRIMITIVE -> (T) Short.valueOf(jsonIterator.getRootNonNullShort());
            case SHORT -> (T) jsonIterator.getRootShort();
            case INT_PRIMITIVE -> (T) Integer.valueOf(jsonIterator.getRootNonNullInt());
            case INT -> (T) jsonIterator.getRootInt();
            case LONG_PRIMITIVE -> (T) Long.valueOf(jsonIterator.getRootNonNullLong());
            case LONG -> (T) jsonIterator.getRootLong();
            case FLOAT_PRIMITIVE -> (T) Float.valueOf(jsonIterator.getRootNonNullFloat());
            case FLOAT -> (T) jsonIterator.getRootFloat();
            case DOUBLE_PRIMITIVE -> (T) Double.valueOf(jsonIterator.getRootNonNullDouble());
            case DOUBLE -> (T) jsonIterator.getRootDouble();
            case CHAR_PRIMITIVE -> (T) Character.valueOf(jsonIterator.getRootNonNullChar());
            case CHAR -> (T) jsonIterator.getRootChar();
            case STRING -> (T) getRootString();
            case ARRAY -> (T) getRootArray(resolvedExpectedClass.getElementClass());
            case CUSTOM -> (T) getRootObject(resolvedExpectedClass);
            case LIST -> throw new JsonParsingException("Lists at the root are not supported. Consider using an array instead.");
        };
    }

    private Object getRootObject(ResolvedClass expectedClass) {
        IteratorResult result = jsonIterator.startIteratingRootObject();
        Object object = getObject(expectedClass, result);
        jsonIterator.assertNoMoreJsonValues();
        return object;
    }

    private Object getObject(ResolvedClass expectedClass) {
        IteratorResult result = jsonIterator.startIteratingObject();
        return getObject(expectedClass, result);
    }

    private Object getObject(ResolvedClass expectedClass, IteratorResult result) {
        if (result == IteratorResult.NOT_EMPTY) {
            ConstructorArgumentsMap argumentsMap = expectedClass.getArgumentsMap();
            Object[] args = new Object[argumentsMap.getArgumentCount()];
            int parentDepth = jsonIterator.getDepth() - 1;
            collectArguments(argumentsMap, args);
            jsonIterator.skipChild(parentDepth);
            return createObject(expectedClass, args);
        } else if (result == IteratorResult.EMPTY) {
            ConstructorArgumentsMap argumentsMap = expectedClass.getArgumentsMap();
            Object[] args = new Object[argumentsMap.getArgumentCount()];
            return createObject(expectedClass, args);
        }
        return null;
    }

    private Object createObject(ResolvedClass expectedClass, Object[] args) {
        try {
            return expectedClass.getConstructor().newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new JsonParsingException("Failed to construct an instance of " + expectedClass.getRawClass().getName(), e);
        }
    }

    private void collectArguments(ConstructorArgumentsMap argumentsMap, Object[] args) {
        int collected = 0;
        int argLen = args.length;
        boolean hasFields = true;
        while (collected < argLen && hasFields) {
            int fieldNameLen = jsonIterator.getFieldName(stringBuffer);
            jsonIterator.moveToFieldValue();
            ConstructorArgument argument = argumentsMap.get(stringBuffer, fieldNameLen);
            if (argument != null) {
                ResolvedClass argumentClass = argument.resolvedClass();
                collectArgument(argumentClass, args, argument);
                collected++;
            } else {
                jsonIterator.skipChild();
            }
            hasFields = jsonIterator.nextObjectField();
        }
    }

    private void collectArgument(ResolvedClass argumentClass, Object[] args, ConstructorArgument argument) {
        args[argument.idx()] = switch (argumentClass.getClassCategory()) {
            case BOOLEAN_PRIMITIVE -> jsonIterator.getNonNullBoolean();
            case BOOLEAN -> jsonIterator.getBoolean();
            case BYTE_PRIMITIVE -> jsonIterator.getNonNullByte();
            case BYTE -> jsonIterator.getByte();
            case SHORT_PRIMITIVE -> jsonIterator.getNonNullShort();
            case SHORT -> jsonIterator.getShort();
            case INT_PRIMITIVE -> jsonIterator.getNonNullInt();
            case INT -> jsonIterator.getInt();
            case LONG_PRIMITIVE -> jsonIterator.getNonNullLong();
            case LONG -> jsonIterator.getLong();
            case FLOAT_PRIMITIVE -> jsonIterator.getNonNullFloat();
            case FLOAT -> jsonIterator.getFloat();
            case DOUBLE_PRIMITIVE -> jsonIterator.getNonNullDouble();
            case DOUBLE -> jsonIterator.getDouble();
            case CHAR_PRIMITIVE -> jsonIterator.getNonNullChar();
            case CHAR -> jsonIterator.getChar();
            case STRING -> getString();
            case ARRAY -> getArray(argumentClass.getElementClass());
            case LIST -> getList(argumentClass.getElementClass());
            case CUSTOM -> getObject(argument.resolvedClass());
        };
    }

    private List<Object> getList(ResolvedClass elementType) {
        IteratorResult result = jsonIterator.startIteratingArray();
        if (result == IteratorResult.EMPTY) {
            return Collections.emptyList();
        }
        if (result == IteratorResult.NULL) {
            return null;
        }

        LinkedList<Object> list = new LinkedList<>();
        boolean hasElements = true;

        switch (elementType.getClassCategory()) {
            case BOOLEAN -> {
                while (hasElements) {
                    list.add(jsonIterator.getBoolean());
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case BYTE -> {
                while (hasElements) {
                    list.add(jsonIterator.getByte());
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case CHAR -> {
                while (hasElements) {
                    list.add(jsonIterator.getChar());
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case SHORT -> {
                while (hasElements) {
                    list.add(jsonIterator.getShort());
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case INT -> {
                while (hasElements) {
                    list.add(jsonIterator.getInt());
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case LONG -> {
                while (hasElements) {
                    list.add(jsonIterator.getLong());
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case DOUBLE -> {
                while (hasElements) {
                    list.add(jsonIterator.getDouble());
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case FLOAT -> {
                while (hasElements) {
                    list.add(jsonIterator.getFloat());
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case STRING -> {
                while (hasElements) {
                    list.add(getString());
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case CUSTOM -> {
                while (hasElements) {
                    list.add(getObject(elementType));
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case ARRAY -> {
                while (hasElements) {
                    list.add(getArray(elementType.getElementClass()));
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            case LIST -> {
                while (hasElements) {
                    list.add(getList(elementType.getElementClass()));
                    hasElements = jsonIterator.nextArrayElement();
                }
            }
            default -> throw new JsonParsingException("Unsupported array element type: " + elementType.getRawClass().getName());
        }

        return list;
    }

    private Object getRootArray(ResolvedClass elementType) {
        IteratorResult result = jsonIterator.startIteratingRootArray();
        Object array = getArray(elementType, result);
        jsonIterator.assertNoMoreJsonValues();
        return array;
    }

    private Object getArray(ResolvedClass elementType) {
        IteratorResult result = jsonIterator.startIteratingArray();
        return getArray(elementType, result);
    }

    private Object getArray(ResolvedClass elementType, IteratorResult result) {
        if (result == IteratorResult.EMPTY) {
            ResolvedClassCategory classCategory = elementType.getClassCategory();
            return classCategory.getEmptyArray() != null ? classCategory.getEmptyArray() : Array.newInstance(elementType.getRawClass(), 0);
        }
        if (result == IteratorResult.NULL) {
            return null;
        }

        return switch (elementType.getClassCategory()) {
            case BOOLEAN_PRIMITIVE -> getPrimitiveBooleanArray();
            case BOOLEAN -> getBooleanArray();
            case BYTE_PRIMITIVE -> getBytePrimitiveArray();
            case BYTE -> getByteArray();
            case CHAR_PRIMITIVE -> getCharPrimitiveArray();
            case CHAR -> getCharArray();
            case SHORT_PRIMITIVE -> getShortPrimitiveArray();
            case SHORT -> getShortArray();
            case INT_PRIMITIVE -> getIntPrimitiveArray();
            case INT -> getIntArray();
            case LONG_PRIMITIVE -> getLongPrimitiveArray();
            case LONG -> getLongArray();
            case DOUBLE_PRIMITIVE -> getDoublePrimitiveArray();
            case DOUBLE -> getDoubleArray();
            case FLOAT_PRIMITIVE -> getFloatPrimitiveArray();
            case FLOAT -> getFloatArray();
            case STRING -> getStringArray();
            case CUSTOM -> getCustomObjectArray(elementType);
            case ARRAY -> getArrayOfArrays(elementType);
            case LIST -> throw new JsonParsingException("Arrays of lists are not supported.");
        };
    }

    private Object getFloatArray() {
        Float[] array = new Float[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Float[] copy = new Float[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getFloat();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Float[] copy = new Float[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object getFloatPrimitiveArray() {
        float[] array = new float[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                float[] copy = new float[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getNonNullFloat();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            float[] copy = new float[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object getDoubleArray() {
        Double[] array = new Double[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Double[] copy = new Double[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getDouble();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Double[] copy = new Double[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object getDoublePrimitiveArray() {
        double[] array = new double[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                double[] copy = new double[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getNonNullDouble();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            double[] copy = new double[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object getLongPrimitiveArray() {
        long[] array = new long[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                long[] copy = new long[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getNonNullLong();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            long[] copy = new long[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object getLongArray() {
        Long[] array = new Long[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Long[] copy = new Long[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getLong();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Long[] copy = new Long[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object getShortPrimitiveArray() {
        short[] array = new short[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                short[] copy = new short[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getNonNullShort();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            short[] copy = new short[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object getShortArray() {
        Short[] array = new Short[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Short[] copy = new Short[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getShort();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Short[] copy = new Short[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object[] getCustomObjectArray(ResolvedClass elementType) {
        Object[] array = (Object[]) Array.newInstance(elementType.getRawClass(), INITIAL_ARRAY_SIZE);
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Object[] copy = (Object[]) Array.newInstance(elementType.getRawClass(), newCapacity);
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = getObject(elementType);
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Object[] copy = (Object[]) Array.newInstance(elementType.getRawClass(), size);
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object[] getArrayOfArrays(ResolvedClass elementType) {
        Object[] array = (Object[]) Array.newInstance(elementType.getRawClass(), INITIAL_ARRAY_SIZE);
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Object[] copy = (Object[]) Array.newInstance(elementType.getRawClass(), newCapacity);
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = getArray(elementType.getElementClass());
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Object[] copy = (Object[]) Array.newInstance(elementType.getRawClass(), size);
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Integer[] getIntArray() {
        Integer[] array = new Integer[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Integer[] copy = new Integer[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getInt();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Integer[] copy = new Integer[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private int[] getIntPrimitiveArray() {
        int[] array = new int[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                int[] copy = new int[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getNonNullInt();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            int[] copy = new int[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object getCharArray() {
        Character[] array = new Character[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Character[] copy = new Character[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getChar();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Character[] copy = new Character[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private char[] getCharPrimitiveArray() {
        char[] array = new char[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                char[] copy = new char[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getNonNullChar();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            char[] copy = new char[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Object getByteArray() {
        Byte[] array = new Byte[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Byte[] copy = new Byte[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getByte();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Byte[] copy = new Byte[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private byte[] getBytePrimitiveArray() {
        byte[] array = new byte[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                byte[] copy = new byte[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getNonNullByte();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            byte[] copy = new byte[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private Boolean[] getBooleanArray() {
        Boolean[] array = new Boolean[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                Boolean[] copy = new Boolean[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getBoolean();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            Boolean[] copy = new Boolean[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private boolean[] getPrimitiveBooleanArray() {
        boolean[] array = new boolean[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                boolean[] copy = new boolean[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = jsonIterator.getNonNullBoolean();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            boolean[] copy = new boolean[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private String[] getStringArray() {
        String[] array = new String[INITIAL_ARRAY_SIZE];
        int size = 0;
        boolean hasElements = true;
        while (hasElements) {
            int oldCapacity = array.length;
            if (size == oldCapacity) {
                int newCapacity = calculateNewCapacity(oldCapacity);
                String[] copy = new String[newCapacity];
                System.arraycopy(array, 0, copy, 0, oldCapacity);
                array = copy;
            }
            array[size++] = getString();
            hasElements = jsonIterator.nextArrayElement();
        }
        if (size != array.length) {
            String[] copy = new String[size];
            System.arraycopy(array, 0, copy, 0, size);
            array = copy;
        }
        return array;
    }

    private static int calculateNewCapacity(int oldCapacity) {
        int minCapacity = oldCapacity + 1;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }
        return newCapacity;
    }

    private String getString() {
        int len = jsonIterator.getString(stringBuffer);
        if (len == -1) {
            return null;
        }
        return new String(stringBuffer, 0, len, UTF_8);
    }

    private String getRootString() {
        int len = jsonIterator.getRootString(stringBuffer);
        if (len == -1) {
            return null;
        }
        return new String(stringBuffer, 0, len, UTF_8);
    }
}
