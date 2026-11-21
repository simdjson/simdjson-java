package org.simdjson;

public class SimdJsonParser {

    private static final int PADDING = 64;
    private static final int DEFAULT_CAPACITY = 34 * 1024 * 1024; // we should be able to handle jsons <= 34MiB
    private static final int DEFAULT_MAX_DEPTH = 1024;

    private final StructuralIndexer indexer;
    private final BitIndexes bitIndexes;
    private final JsonIterator jsonIterator;
    private final SchemaBasedJsonIterator schemaBasedJsonIterator;
    private final OnDemandJsonIterator onDemandJsonIterator;
    private final byte[] paddedBuffer;
    private final boolean validateUtf8;

    public SimdJsonParser() {
        this(DEFAULT_CAPACITY, DEFAULT_MAX_DEPTH);
    }

    public SimdJsonParser(int capacity, int maxDepth) {
        this(capacity, maxDepth, true);
    }

    /**
     * Creates a parser that may skip UTF-8 validation.
     * <p>
     * By default every parse method first checks that the input is valid UTF-8 and rejects it otherwise. With
     * {@code validateUtf8} set to {@code false} that pass is skipped, which saves its cost when the input is already
     * known to be valid UTF-8, for example because it was validated or produced by a trusted component. The input is
     * then trusted: invalid UTF-8 is not detected, and strings decoded from it may be malformed UTF-8. The rest of the
     * parsing is unchanged.
     *
     * @param capacity     the size of the largest document this parser can parse, in bytes
     * @param maxDepth     the maximum nesting depth of objects and arrays
     * @param validateUtf8 {@code true} to reject input that is not valid UTF-8, {@code false} to skip the check
     */
    public SimdJsonParser(int capacity, int maxDepth, boolean validateUtf8) {
        this.validateUtf8 = validateUtf8;
        bitIndexes = new BitIndexes(capacity);
        byte[] stringBuffer = new byte[capacity];
        jsonIterator = new JsonIterator(bitIndexes, stringBuffer, capacity, maxDepth, PADDING);
        schemaBasedJsonIterator = new SchemaBasedJsonIterator(bitIndexes, stringBuffer, PADDING);
        onDemandJsonIterator = new OnDemandJsonIterator(bitIndexes, PADDING);
        paddedBuffer = new byte[capacity];
        indexer = new StructuralIndexer(bitIndexes);
    }

    /**
     * Prepares on-demand parsing of the first {@code len} bytes of {@code buffer}: validates the input as UTF-8
     * (unless the parser was created without validation), indexes its structural characters, and returns a forward-only cursor that decodes values only when asked. No
     * tape is built. See {@link OnDemandJsonIterator} for how to use the cursor.
     * <p>
     * The parser returns the same cursor instance on every call. It is valid until the next call to any parse method
     * of this parser.
     *
     * @param buffer the document; if fewer than 64 bytes follow {@code len}, the document is copied into an internal
     *               buffer, otherwise the cursor reads {@code buffer} directly, which must then not change while the
     *               cursor is in use
     * @param len    the length of the document in bytes
     * @return the cursor, positioned before the root value
     * @throws JsonParsingException if the input is not valid UTF-8 (when validated), contains no structural element, or has an
     *                              unclosed string or unescaped control characters in a string
     */
    public OnDemandJsonIterator parseOnDemand(byte[] buffer, int len) {
        byte[] padded = padIfNeeded(buffer, len);
        reset();
        stage1(padded, len);
        onDemandJsonIterator.init(padded, len);
        return onDemandJsonIterator;
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
        bitIndexes.reset();
        jsonIterator.reset();
    }

    private void stage1(byte[] buffer, int length) {
        if (validateUtf8) {
            Utf8Validator.validate(buffer, length);
        }
        indexer.index(buffer, length);
    }
}
