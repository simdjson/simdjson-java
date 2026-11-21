package org.simdjson;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.simdjson.Tape.DOUBLE;
import static org.simdjson.Tape.FALSE_VALUE;
import static org.simdjson.Tape.INT64;
import static org.simdjson.Tape.NULL_VALUE;
import static org.simdjson.Tape.START_ARRAY;
import static org.simdjson.Tape.START_OBJECT;
import static org.simdjson.Tape.STRING;
import static org.simdjson.Tape.TRUE_VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;

public class JsonValue {

    private final Tape tape;
    private final byte[] buffer;
    private final int tapeIdx;
    private final byte[] stringBuffer;

    JsonValue(Tape tape, int tapeIdx, byte[] stringBuffer, byte[] buffer) {
        this.tape = tape;
        this.tapeIdx = tapeIdx;
        this.stringBuffer = stringBuffer;
        this.buffer = buffer;
    }

    public boolean isArray() {
        return tape.getType(tapeIdx) == START_ARRAY;
    }

    public boolean isObject() {
        return tape.getType(tapeIdx) == START_OBJECT;
    }

    public boolean isLong() {
        return tape.getType(tapeIdx) == INT64;
    }

    public boolean isDouble() {
        return tape.getType(tapeIdx) == DOUBLE;
    }

    public boolean isBoolean() {
        char type = tape.getType(tapeIdx);
        return type == TRUE_VALUE || type == FALSE_VALUE;
    }

    public boolean isNull() {
        return tape.getType(tapeIdx) == NULL_VALUE;
    }

    public boolean isString() {
        return tape.getType(tapeIdx) == STRING;
    }

    public Iterator<JsonValue> arrayIterator() {
        return new ArrayIterator(tapeIdx);
    }

    public Iterator<Map.Entry<String, JsonValue>> objectIterator() {
        return new ObjectIterator(tapeIdx);
    }

    public long asLong() {
        return tape.getInt64Value(tapeIdx);
    }

    public double asDouble() {
        return tape.getDouble(tapeIdx);
    }

    public boolean asBoolean() {
        return tape.getType(tapeIdx) == TRUE_VALUE;
    }

    public String asString() {
        return getString(tapeIdx);
    }

    private String getString(int tapeIdx) {
        int stringBufferIdx = (int) tape.getValue(tapeIdx);
        int len = IntegerUtils.toInt(stringBuffer, stringBufferIdx);
        return new String(stringBuffer, stringBufferIdx + Integer.BYTES, len, UTF_8);
    }

    public JsonValue get(String name) {
        return get(name.getBytes(UTF_8));
    }

    /**
     * Same as {@link #get(String)}, with the field name already encoded as UTF-8. Passing a constant array avoids
     * encoding the name on every lookup.
     *
     * @param name the field name, encoded as UTF-8
     * @return the value of the first field with this name, or {@code null} if this object has no such field
     */
    public JsonValue get(byte[] name) {
        int idx = tapeIdx + 1;
        int endIdx = tape.getMatchingBraceIndex(tapeIdx) - 1;
        while (idx < endIdx) {
            int stringBufferIdx = (int) tape.getValue(idx);
            int len = IntegerUtils.toInt(stringBuffer, stringBufferIdx);
            int valIdx = tape.computeNextIndex(idx);
            idx = tape.computeNextIndex(valIdx);
            int stringBufferFromIdx = stringBufferIdx + Integer.BYTES;
            int stringBufferToIdx = stringBufferFromIdx + len;
            if (Arrays.compare(name, 0, name.length, stringBuffer, stringBufferFromIdx, stringBufferToIdx) == 0) {
                return new JsonValue(tape, valIdx, stringBuffer, buffer);
            }
        }
        return null;
    }

    public int getSize() {
        return tape.getScopeCount(tapeIdx);
    }

    /**
     * Returns the index of this value in the parsed document, the starting point for the index-based methods.
     * <p>
     * The index-based methods navigate the whole document that this value belongs to with plain {@code int} indices,
     * so a caller can find and read the values it needs without allocating a {@code JsonValue}, an iterator or a
     * {@code String} per value. Every index passed to them must come from {@link #index()}, {@link #field},
     * {@link #firstElement} or {@link #advance} on a value of the same document, and stays valid until the parser's
     * next parse call. For example, to read {@code doc.items[*].id}:
     * <pre>{@code
     * int items = doc.field(doc.index(), ITEMS);
     * if (items >= 0 && doc.isArrayAt(items)) {
     *     int end = doc.scopeEnd(items);
     *     for (int item = doc.firstElement(items); item >= 0 && item < end; item = doc.advance(item)) {
     *         int id = doc.isObjectAt(item) ? doc.field(item, ID) : -1;
     *         if (id >= 0 && doc.isLongAt(id)) {
     *             consume(doc.longAt(id));
     *         }
     *     }
     * }
     * }</pre>
     * The methods that require a value of a particular type throw an {@link IllegalArgumentException} when the value
     * at the index has another type; the {@code is...At} methods test the type first.
     *
     * @return the index of this value
     */
    public int index() {
        return tapeIdx;
    }

    /**
     * Returns the end of the object or array at {@code idx}: the index just past its last field or element. The
     * fields or elements of the container are the values from {@link #firstElement} (for an array) onwards, followed
     * with {@link #advance}, up to this index.
     *
     * @param idx the index of an object or an array
     * @return the end index of the container, exclusive
     * @throws IllegalArgumentException if the value at {@code idx} is neither an object nor an array
     */
    public int scopeEnd(int idx) {
        checkContainer(idx);
        return tape.getMatchingBraceIndex(idx) - 1;
    }

    /**
     * Returns the index of the value that follows the value at {@code idx}, skipping its contents if it is an object
     * or an array. Within an array this is the next element. Within an object, field names and field values
     * alternate: from a field name it moves to its value, and from a value to the next field name. The result equals
     * {@link #scopeEnd} of the container when {@code idx} is its last element or field value.
     *
     * @param idx the index of any value
     * @return the index of the following value
     */
    public int advance(int idx) {
        return tape.computeNextIndex(idx);
    }

    /**
     * Returns the index of the first element of the array at {@code arrIdx}.
     *
     * @param arrIdx the index of an array
     * @return the index of the first element, or {@code -1} if the array is empty
     * @throws IllegalArgumentException if the value at {@code arrIdx} is not an array
     */
    public int firstElement(int arrIdx) {
        check(arrIdx, START_ARRAY);
        int first = arrIdx + 1;
        return first < tape.getMatchingBraceIndex(arrIdx) - 1 ? first : -1;
    }

    /**
     * Looks up a field of the object at {@code objIdx}, like {@link #get(byte[])}, without allocating.
     *
     * @param objIdx the index of an object
     * @param name   the field name, encoded as UTF-8
     * @return the index of the value of the first field with this name, or {@code -1} if there is none
     * @throws IllegalArgumentException if the value at {@code objIdx} is not an object
     */
    public int field(int objIdx, byte[] name) {
        check(objIdx, START_OBJECT);
        int idx = objIdx + 1;
        int endIdx = tape.getMatchingBraceIndex(objIdx) - 1;
        while (idx < endIdx) {
            int stringBufferIdx = (int) tape.getValue(idx);
            int len = IntegerUtils.toInt(stringBuffer, stringBufferIdx);
            int valIdx = tape.computeNextIndex(idx);
            idx = tape.computeNextIndex(valIdx);
            int stringBufferFromIdx = stringBufferIdx + Integer.BYTES;
            int stringBufferToIdx = stringBufferFromIdx + len;
            if (Arrays.compare(name, 0, name.length, stringBuffer, stringBufferFromIdx, stringBufferToIdx) == 0) {
                return valIdx;
            }
        }
        return -1;
    }

    /**
     * Tests whether the value at {@code idx} is an object.
     *
     * @param idx the index of any value
     * @return {@code true} if the value at {@code idx} is an object
     */
    public boolean isObjectAt(int idx) {
        return tape.getType(idx) == START_OBJECT;
    }

    /**
     * Tests whether the value at {@code idx} is an array.
     *
     * @param idx the index of any value
     * @return {@code true} if the value at {@code idx} is an array
     */
    public boolean isArrayAt(int idx) {
        return tape.getType(idx) == START_ARRAY;
    }

    /**
     * Tests whether the value at {@code idx} is a string.
     *
     * @param idx the index of any value
     * @return {@code true} if the value at {@code idx} is a string
     */
    public boolean isStringAt(int idx) {
        return tape.getType(idx) == STRING;
    }

    /**
     * Tests whether the value at {@code idx} is a number stored as a {@code long}.
     *
     * @param idx the index of any value
     * @return {@code true} if the value at {@code idx} is a number stored as a {@code long}, which is the case for
     * every integer literal; see {@link #isLong()}
     */
    public boolean isLongAt(int idx) {
        return tape.getType(idx) == INT64;
    }

    /**
     * Tests whether the value at {@code idx} is a number stored as a {@code double}.
     *
     * @param idx the index of any value
     * @return {@code true} if the value at {@code idx} is a number stored as a {@code double}, which is the case for
     * every literal with a fraction or an exponent; see {@link #isDouble()}
     */
    public boolean isDoubleAt(int idx) {
        return tape.getType(idx) == DOUBLE;
    }

    /**
     * Tests whether the value at {@code idx} is a boolean.
     *
     * @param idx the index of any value
     * @return {@code true} if the value at {@code idx} is {@code true} or {@code false}
     */
    public boolean isBooleanAt(int idx) {
        char type = tape.getType(idx);
        return type == TRUE_VALUE || type == FALSE_VALUE;
    }

    /**
     * Tests whether the value at {@code idx} is {@code null}.
     *
     * @param idx the index of any value
     * @return {@code true} if the value at {@code idx} is {@code null}
     */
    public boolean isNullAt(int idx) {
        return tape.getType(idx) == NULL_VALUE;
    }

    /**
     * Returns the string at {@code idx}. Only the returned {@code String} is allocated; use {@link #stringBytes},
     * {@link #byteOffset} and {@link #byteLength} to read the string without allocating.
     *
     * @param idx the index of a string
     * @return the string
     * @throws IllegalArgumentException if the value at {@code idx} is not a string
     */
    public String stringAt(int idx) {
        check(idx, STRING);
        return getString(idx);
    }

    /**
     * Returns the parser's buffer that holds the decoded strings of the document, as UTF-8. The string at an index
     * occupies {@link #byteLength} bytes starting at {@link #byteOffset}; the rest of the array holds other strings
     * and internal data.
     * <p>
     * This is the parser's own array, not a copy. Callers must not modify it, and its contents are valid only until
     * the parser's next parse call.
     *
     * @return the parser's string buffer
     */
    public byte[] stringBytes() {
        return stringBuffer;
    }

    /**
     * Returns where the string at {@code idx} starts in {@link #stringBytes()}.
     *
     * @param idx the index of a string
     * @return the offset in {@link #stringBytes()} of the first byte of the string at {@code idx}
     * @throws IllegalArgumentException if the value at {@code idx} is not a string
     */
    public int byteOffset(int idx) {
        check(idx, STRING);
        return (int) tape.getValue(idx) + Integer.BYTES;
    }

    /**
     * Returns the length of the string at {@code idx} in {@link #stringBytes()}.
     *
     * @param idx the index of a string
     * @return the length in bytes of the UTF-8 encoding of the string at {@code idx}
     * @throws IllegalArgumentException if the value at {@code idx} is not a string
     */
    public int byteLength(int idx) {
        check(idx, STRING);
        return IntegerUtils.toInt(stringBuffer, (int) tape.getValue(idx));
    }

    /**
     * Compares the string at {@code idx} with {@code expected} without allocating.
     *
     * @param idx      the index of any value
     * @param expected the expected string, encoded as UTF-8
     * @return {@code true} if the value at {@code idx} is a string with exactly these bytes; {@code false} otherwise,
     * including when it is not a string
     */
    public boolean stringEquals(int idx, byte[] expected) {
        if (tape.getType(idx) != STRING) {
            return false;
        }
        int stringBufferIdx = (int) tape.getValue(idx);
        int len = IntegerUtils.toInt(stringBuffer, stringBufferIdx);
        int from = stringBufferIdx + Integer.BYTES;
        return Arrays.equals(expected, 0, expected.length, stringBuffer, from, from + len);
    }

    /**
     * Returns the integer at {@code idx}.
     *
     * @param idx the index of a number stored as a {@code long}; see {@link #isLongAt}
     * @return the value at {@code idx}
     * @throws IllegalArgumentException if the value at {@code idx} is not stored as a {@code long}
     */
    public long longAt(int idx) {
        check(idx, INT64);
        return tape.getInt64Value(idx);
    }

    /**
     * Returns the floating-point number at {@code idx}.
     *
     * @param idx the index of a number stored as a {@code double}; see {@link #isDoubleAt}
     * @return the value at {@code idx}
     * @throws IllegalArgumentException if the value at {@code idx} is not stored as a {@code double}
     */
    public double doubleAt(int idx) {
        check(idx, DOUBLE);
        return tape.getDouble(idx);
    }

    /**
     * Returns the number at {@code idx} as a {@code double}, whether it is stored as a {@code long} or as a
     * {@code double}. An integer whose magnitude exceeds 2<sup>53</sup> is rounded to the nearest {@code double}, so
     * it may not come back exactly; use {@link #longAt} for integers that must be exact.
     *
     * @param idx the index of a number
     * @return the value at {@code idx}, as a {@code double}
     * @throws IllegalArgumentException if the value at {@code idx} is not a number
     */
    public double numberAt(int idx) {
        char type = tape.getType(idx);
        if (type == DOUBLE) {
            return tape.getDouble(idx);
        }
        if (type == INT64) {
            return tape.getInt64Value(idx);
        }
        throw wrongType(idx, "a number");
    }

    /**
     * Returns the boolean at {@code idx}.
     *
     * @param idx the index of {@code true} or {@code false}
     * @return the value at {@code idx}
     * @throws IllegalArgumentException if the value at {@code idx} is not {@code true} or {@code false}
     */
    public boolean booleanAt(int idx) {
        char type = tape.getType(idx);
        if (type == TRUE_VALUE) {
            return true;
        }
        if (type == FALSE_VALUE) {
            return false;
        }
        throw wrongType(idx, "a boolean");
    }

    private void check(int idx, char expectedType) {
        if (tape.getType(idx) != expectedType) {
            throw wrongType(idx, typeName(expectedType));
        }
    }

    private void checkContainer(int idx) {
        // '[' (0x5B) and '{' (0x7B) differ only in bit 0x20, and no other tape type becomes '{' when it is set.
        if ((tape.getType(idx) | 0x20) != START_OBJECT) {
            throw wrongType(idx, "an object or an array");
        }
    }

    private IllegalArgumentException wrongType(int idx, String expected) {
        return new IllegalArgumentException("Expected " + expected + " at index " + idx + ", but found "
                + typeName(tape.getType(idx)) + ".");
    }

    private static String typeName(char type) {
        return switch (type) {
            case START_OBJECT -> "an object";
            case START_ARRAY -> "an array";
            case STRING -> "a string";
            case INT64 -> "an integer";
            case DOUBLE -> "a floating-point number";
            case TRUE_VALUE, FALSE_VALUE -> "a boolean";
            case NULL_VALUE -> "null";
            default -> "no value";
        };
    }

    @Override
    public String toString() {
        switch (tape.getType(tapeIdx)) {
            case INT64 -> {
                return String.valueOf(asLong());
            }
            case DOUBLE -> {
                return String.valueOf(asDouble());
            }
            case TRUE_VALUE, FALSE_VALUE -> {
                return String.valueOf(asBoolean());
            }
            case STRING -> {
                return asString();
            }
            case NULL_VALUE -> {
                return "null";
            }
            case START_OBJECT -> {
                return "<object>";
            }
            case START_ARRAY -> {
                return "<array>";
            }
            default -> {
                return "unknown";
            }
        }
    }

    private class ArrayIterator implements Iterator<JsonValue> {

        private final int endIdx;

        private int idx;

        ArrayIterator(int startIdx) {
            idx = startIdx + 1;
            endIdx = tape.getMatchingBraceIndex(startIdx) - 1;
        }

        @Override
        public boolean hasNext() {
            return idx < endIdx;
        }

        @Override
        public JsonValue next() {
            if (hasNext()) {
                JsonValue value = new JsonValue(tape, idx, stringBuffer, buffer);
                idx = tape.computeNextIndex(idx);
                return value;
            }
            throw new NoSuchElementException("No more elements");
        }
    }

    private class ObjectIterator implements Iterator<Map.Entry<String, JsonValue>> {

        private final int endIdx;

        private int idx;

        ObjectIterator(int startIdx) {
            idx = startIdx + 1;
            endIdx = tape.getMatchingBraceIndex(startIdx) - 1;
        }

        @Override
        public boolean hasNext() {
            return idx < endIdx;
        }

        @Override
        public Map.Entry<String, JsonValue> next() {
            String key = getString(idx);
            idx = tape.computeNextIndex(idx);
            JsonValue value = new JsonValue(tape, idx, stringBuffer, buffer);
            idx = tape.computeNextIndex(idx);
            return new ObjectField(key, value);
        }
    }

    private static class ObjectField implements Map.Entry<String, JsonValue> {

        private final String key;
        private final JsonValue value;

        ObjectField(String key, JsonValue value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public JsonValue getValue() {
            return value;
        }

        @Override
        public JsonValue setValue(JsonValue value) {
            throw new UnsupportedOperationException("Object fields are immutable");
        }
    }
}
