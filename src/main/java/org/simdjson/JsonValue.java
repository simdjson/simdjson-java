package org.simdjson;

import java.util.Iterator;
import java.util.Map;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

    public Iterator<Map.Entry<CharSequence, JsonValue>> objectIterator() {
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

    public CharSequence asCharSequence() {
        return asCharSequence(tapeIdx);
    }

    private CharSequence asCharSequence(int idx) {
        int stringBufferIdx = (int) tape.getValue(idx);
        int len = IntegerUtils.toInt(stringBuffer, stringBufferIdx);
        return new StringView(stringBufferIdx + Integer.BYTES, len);
    }

    public JsonValue get(String name) {
        Iterator<Map.Entry<CharSequence, JsonValue>> it = objectIterator();
        while (it.hasNext()) {
            Map.Entry<CharSequence, JsonValue> entry = it.next();
            CharSequence key = entry.getKey();
            if (CharSequence.compare(key, name) == 0) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Stream<JsonValue> stream(String name) {
        var jsonValue = get(name);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(jsonValue.arrayIterator(), 0), false);
    }

    public Stream<JsonValue> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this.arrayIterator(), 0), false);
    }

    public String asString() {
        return asCharSequence().toString();
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
                return asCharSequence().toString();
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

    private class ObjectIterator implements Iterator<Map.Entry<CharSequence, JsonValue>> {

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
        public Map.Entry<CharSequence, JsonValue> next() {
            CharSequence key = asCharSequence(idx);
            idx = tape.computeNextIndex(idx);
            JsonValue value = new JsonValue(tape, idx, stringBuffer, buffer);
            idx = tape.computeNextIndex(idx);
            return new ObjectField(key, value);
        }
    }

    private static class ObjectField implements Map.Entry<CharSequence, JsonValue> {

        private final CharSequence key;
        private final JsonValue value;

        ObjectField(CharSequence key, JsonValue value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public CharSequence getKey() {
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

    private class StringView implements CharSequence {

        private final int startIdx;
        private final int len;

        StringView(int startIdx, int len) {
            this.startIdx = startIdx;
            this.len = len;
        }

        @Override
        public int length() {
            return len;
        }

        @Override
        public char charAt(int index) {
            return (char) stringBuffer[startIdx + index];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new StringView(startIdx + start, startIdx + end);
        }

        @Override
        public String toString() {
            return new String(stringBuffer, startIdx, len, UTF_8);
        }
    }
}
