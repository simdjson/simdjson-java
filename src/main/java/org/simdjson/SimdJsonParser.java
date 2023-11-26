package org.simdjson;

public class SimdJsonParser {

    private static final int STEP_SIZE = 64;
    private static final int PADDING = 64;
    private static final int DEFAULT_CAPACITY = 34 * 1024 * 1024; // we should be able to handle jsons <= 34MiB
    private static final int DEFAULT_MAX_DEPTH = 1024;

    private final BlockReader reader;
    private final StructuralIndexer indexer;
    private final BitIndexes bitIndexes;
    private final JsonIterator jsonIterator;
    private final byte[] paddedBuffer;

    public SimdJsonParser() {
        this(DEFAULT_CAPACITY, DEFAULT_MAX_DEPTH);
    }

    public SimdJsonParser(int capacity, int maxDepth) {
        bitIndexes = new BitIndexes(capacity);
        jsonIterator = new JsonIterator(bitIndexes, capacity, maxDepth, PADDING);
        paddedBuffer = new byte[capacity];
        reader = new BlockReader(STEP_SIZE);
        indexer = new StructuralIndexer(bitIndexes);
    }

    public JsonValue parse(byte[] buffer, int len) {
        stage0(buffer);
        byte[] padded = padIfNeeded(buffer, len);
        reset(padded, len);
        stage1(padded);
        return stage2(padded, len);
    }

    private byte[] padIfNeeded(byte[] buffer, int len) {
        if (buffer.length - len < PADDING) {
            System.arraycopy(buffer, 0, paddedBuffer, 0, len);
            return paddedBuffer;
        }
        return buffer;
    }

    private void reset(byte[] buffer, int len) {
        indexer.reset();
        reader.reset(buffer, len);
        bitIndexes.reset();
        jsonIterator.reset();
    }

    private void stage0(byte[] buffer) {
        Utf8Validator.validate(buffer);
    }

    private void stage1(byte[] buffer) {
        while (reader.hasFullBlock()) {
            int blockIndex = reader.getBlockIndex();
            indexer.step(buffer, blockIndex, blockIndex);
            reader.advance();
        }
        indexer.step(reader.remainder(), 0, reader.getBlockIndex());
        reader.advance();
        indexer.finish(reader.getBlockIndex());
    }

    private JsonValue stage2(byte[] buffer, int len) {
        return jsonIterator.walkDocument(buffer, len);
    }
}
