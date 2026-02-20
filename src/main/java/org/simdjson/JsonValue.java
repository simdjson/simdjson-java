package org.simdjson;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.simdjson.Tape.DOUBLE;
import static org.simdjson.Tape.FALSE_VALUE;
import static org.simdjson.Tape.INT64;
import static org.simdjson.Tape.NULL_VALUE;
import static org.simdjson.Tape.START_ARRAY;
import static org.simdjson.Tape.START_OBJECT;
import static org.simdjson.Tape.STRING;
import static org.simdjson.Tape.TRUE_VALUE;

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

    /**
     * Reads a segment of bytes described by the internal state of the current object
     * and passes it to the provided {@link JsonValueByteConsumer} for processing.
     *
     * @param consumer the {@link JsonValueByteConsumer} that processes the extracted bytes;
     *                 it will receive a segment of the internal {@code stringBuffer} array
     *                 defined by the current tape index and length
     */
    public void acceptBytes(JsonValueByteConsumer consumer) {
        int stringBufferIdx = (int) tape.getValue(tapeIdx);
        int len = IntegerUtils.toInt(stringBuffer, stringBufferIdx);
        consumer.acceptBytes(stringBuffer, stringBufferIdx + Integer.BYTES, len);
    }

    private String getString(int tapeIdx) {
        int stringBufferIdx = (int) tape.getValue(tapeIdx);
        int len = IntegerUtils.toInt(stringBuffer, stringBufferIdx);
        return new String(stringBuffer, stringBufferIdx + Integer.BYTES, len, UTF_8);
    }

    public JsonValue get(String name) {
        byte[] bytes = name.getBytes(UTF_8);
        return get(bytes);
    }

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

    public interface JsonValueByteConsumer {

        /**
         * Consumes a segment of bytes from the provided array for processing.
         *
         * @param bytes  the array of bytes to be processed
         * @param offset the starting position in the array from which bytes should be read
         * @param length the number of bytes to be read from the array, starting at the offset
         */
        void acceptBytes(byte[] bytes, int offset, int length);
    }
}
