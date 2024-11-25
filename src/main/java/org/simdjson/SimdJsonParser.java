package org.simdjson;

public class SimdJsonParser {

    private static final int PADDING = 64;
    private static final int DEFAULT_CAPACITY = 34 * 1024 * 1024; // we should be able to handle jsons <= 34MiB
    private static final int DEFAULT_MAX_DEPTH = 1024;

    private final StructuralIndexer indexer;
    private final BitIndexes bitIndexes;
    private final JsonIterator jsonIterator;
    private final SchemaBasedJsonIterator schemaBasedJsonIterator;
    private final byte[] paddedBuffer;

    public SimdJsonParser() {
        this(DEFAULT_CAPACITY, DEFAULT_MAX_DEPTH);
    }

    public SimdJsonParser(int capacity, int maxDepth) {
        bitIndexes = new BitIndexes(capacity);
        byte[] stringBuffer = new byte[capacity];
        jsonIterator = new JsonIterator(bitIndexes, stringBuffer, capacity, maxDepth, PADDING);
        schemaBasedJsonIterator = new SchemaBasedJsonIterator(bitIndexes, stringBuffer, PADDING);
        paddedBuffer = new byte[capacity];
        indexer = new StructuralIndexer(bitIndexes);
    }

    public <T> T parse(byte[] buffer, int len, Class<T> expectedType) {
        byte[] padded = padIfNeeded(buffer, len);
        reset();
        stage1(padded, len);
        return schemaBasedJsonIterator.walkDocument(padded, len, expectedType);
    }

    public JsonValue parse(byte[] buffer, int len) {
        byte[] padded = padIfNeeded(buffer, len);
        reset();
        stage1(padded, len);
        return jsonIterator.walkDocument(padded, len);
    }

    private byte[] padIfNeeded(byte[] buffer, int len) {
        if (buffer.length - len < PADDING) {
            System.arraycopy(buffer, 0, paddedBuffer, 0, len);
            return paddedBuffer;
        }
        return buffer;
    }

    private void reset() {
        jsonIterator.reset();
    }

    private void stage1(byte[] buffer, int length) {
        Utf8Validator.validate(buffer, length);
        indexer.index(buffer, length);
    }
}
