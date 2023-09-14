package org.simdjson;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

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
        byte[] bytes = name.getBytes(UTF_8);
        int idx = tapeIdx + 1;
        int endIdx = tape.getMatchingBraceIndex(tapeIdx) - 1;
        while (idx < endIdx) {
            int stringBufferIdx = (int) tape.getValue(idx);
            int len = IntegerUtils.toInt(stringBuffer, stringBufferIdx);
            int valIdx = tape.computeNextIndex(idx);
            idx = tape.computeNextIndex(valIdx);
            int stringBufferFromIdx = stringBufferIdx + Integer.BYTES;
            int stringBufferToIdx = stringBufferFromIdx + len;
            if (Arrays.compare(bytes, 0, bytes.length, stringBuffer, stringBufferFromIdx, stringBufferToIdx) == 0) {
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
            JsonValue value = new JsonValue(tape, idx, stringBuffer, buffer);
            idx = tape.computeNextIndex(idx);
            return value;
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
