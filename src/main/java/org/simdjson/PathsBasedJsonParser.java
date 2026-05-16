package org.simdjson;

import java.util.Arrays;

/**
 * A JSON parser that extracts a fixed set of paths from each input document and returns their
 * scalar/raw values directly, without ever materializing a full JSON tree.
 *
 * <p>It is intended for "JSON-tuple" workloads where the schema is known up-front and only a small
 * subset of fields needs to be projected out of potentially large documents - for example column
 * extraction in OLAP / streaming pipelines.
 *
 * <p><b>Path syntax</b>
 * <ul>
 *     <li>Object fields are separated by {@code .}, e.g. {@code "a.b.c"}.</li>
 *     <li>Array indices use {@code [N]}, e.g. {@code "items.[0].name"}.</li>
 *     <li>An optional type suffix can be appended with {@code :TYPE} where TYPE is one of
 *     {@code STRING, INT, LONG, DOUBLE, FLOAT, BOOLEAN, SHORT, BYTE, CHAR}
 *     (or their {@code *_PRIMITIVE} variants which require the value to be non-null).
 *     Default is {@code STRING}.</li>
 *     <li>Literal square brackets inside an object key are escaped by doubling them - {@code [[ }
 *     and {@code ]]} stand for {@code [} and {@code ]} respectively.</li>
 * </ul>
 *
 * <p><b>Examples</b>
 * <pre>{@code
 * var parser = new PathsBasedJsonParser("user.id:LONG", "user.name", "items.[0].price:DOUBLE");
 * Object[] row = parser.parse(json, json.length);
 * long   id    = (long)   row[0];
 * String name  = (String) row[1];
 * double price = (double) row[2];
 * }</pre>
 *
 * <p><b>Result semantics</b>: {@link #parse(byte[], int)} returns a freshly-allocated array each
 * call, so callers may safely hold on to it across subsequent {@code parse()} invocations.
 * Missing paths produce a {@code null} slot in the result.
 *
 * <p><b>Thread safety</b>: instances are <i>not</i> thread-safe; create one parser per thread.
 *
 * <p><b>Known limitations</b>
 * <ul>
 *     <li>JSON unicode ({@code &#92;uXXXX}) and other backslash escapes inside string values and
 *     object keys are <em>not</em> decoded for {@code STRING} leaves and key matching - the raw
 *     UTF-8 bytes are returned/compared.</li>
 *     <li>Input documents larger than {@code 34 MiB} are not supported (the indexer buffer is fixed).</li>
 * </ul>
 */
public final class PathsBasedJsonParser {

    private static final int PADDING = 64;
    private static final int DEFAULT_CAPACITY = 34 * 1024 * 1024; // we should be able to handle jsons <= 34MiB
    private static final String SINGLE_LEFT_BRACKET = "[";
    private static final String DOUBLE_LEFT_BRACKET = "[[";
    private static final String SINGLE_RIGHT_BRACKET = "]";
    private static final String DOUBLE_RIGHT_BRACKET = "]]";
    private static final String TYPE_DELIMITER = ":";
    private static final String PATH_DELIMITER = "\\.";
    private static final ResolvedClass.ResolvedClassCategory DEFAULT_TYPE = ResolvedClass.ResolvedClassCategory.STRING;

    private final Object[] result;
    private final OnDemandJsonValue[] row;
    private final StructuralIndexer indexer;
    private final BitIndexes bitIndexes;
    private final OnDemandJsonIterator jsonIterator;
    private final byte[] paddedBuffer;
    private final OnDemandJsonValue root = new OnDemandJsonValue();

    private long currentVersion = 0;
    private OnDemandJsonValue ptr;
    private byte[] padded;

    /**
     * Builds a parser pre-configured with the given paths. The order of {@code paths} determines
     * the index of each value in the array returned by {@link #parse(byte[], int)}.
     *
     * @param paths path expressions; see class javadoc for syntax
     */
    public PathsBasedJsonParser(String... paths) {
        if (paths == null) {
            throw new IllegalArgumentException("paths must not be null");
        }
        this.bitIndexes = new BitIndexes(DEFAULT_CAPACITY);
        this.indexer = new StructuralIndexer(bitIndexes);
        this.jsonIterator = new OnDemandJsonIterator(bitIndexes, PADDING);
        this.result = new Object[paths.length];
        this.paddedBuffer = new byte[DEFAULT_CAPACITY];
        this.row = new OnDemandJsonValue[paths.length];
        constructPathTree(paths);
    }

    private void constructPathTree(String... paths) {
        for (int i = 0; i < paths.length; i++) {
            if (paths[i] == null || paths[i].isEmpty()) {
                throw new IllegalArgumentException("path at index " + i + " is null or empty");
            }
            String[] pathAndType = paths[i].split(TYPE_DELIMITER);
            ResolvedClass.ResolvedClassCategory type = DEFAULT_TYPE;
            if (pathAndType.length >= 2) {
                type = ResolvedClass.ResolvedClassCategory.valueOf(pathAndType[1]);
            }
            String path = pathAndType[0];
            // construct path tree
            OnDemandJsonValue cur = root;
            for (String step : path.split(PATH_DELIMITER)) {
                Object key;
                if (step.startsWith(SINGLE_LEFT_BRACKET) && !step.startsWith(DOUBLE_LEFT_BRACKET)) {
                    key = Integer.parseInt(step.substring(1, step.length() - 1));
                } else {
                    key = step.replace(DOUBLE_LEFT_BRACKET, SINGLE_LEFT_BRACKET)
                            .replace(DOUBLE_RIGHT_BRACKET, SINGLE_RIGHT_BRACKET);
                }
                if (!cur.getChildren().containsKey(key)) {
                    OnDemandJsonValue child = new OnDemandJsonValue();
                    child.setParent(cur);
                    cur.getChildren().put(key, child);
                }
                cur = cur.getChildren().get(key);
            }
            cur.setLeaf(true);
            cur.setType(type);
            row[i] = cur;
        }
    }

    /**
     * Parses the JSON in {@code buffer[0..len)} and returns one value per configured path. A path
     * that is not present in the JSON produces a {@code null} slot. The returned array is freshly
     * allocated, so callers may store/share it without worrying about subsequent {@code parse()} calls.
     *
     * @param buffer source bytes (UTF-8). Must contain a JSON object or array at the root
     * @param len    number of valid bytes in {@code buffer}
     * @return one element per configured path
     */
    public Object[] parse(byte[] buffer, int len) {
        if (buffer == null || len == 0) {
            return new Object[result.length];
        }
        padded = padIfNeeded(buffer, len);
        Utf8Validator.validate(padded, len);
        indexer.index(padded, len);
        jsonIterator.init(padded, len);
        this.currentVersion++;
        this.ptr = root;
        switch (padded[bitIndexes.peek()]) {
            case '{':
                parseRootObject();
                break;
            case '[':
                parseRootArray();
                break;
            default:
                throw new JsonParsingException("invalid json format, must start with { or [");
        }
        return getResult();
    }

    private void parseRootObject() {
        OnDemandJsonIterator.IteratorResult iteratorResult = jsonIterator.startIteratingRootObject();
        iteratorObjectElements(iteratorResult);
        jsonIterator.assertNoMoreJsonValues();
    }

    private void parseObject() {
        OnDemandJsonIterator.IteratorResult iteratorResult = jsonIterator.startIteratingObject();
        iteratorObjectElements(iteratorResult);
    }

    private void parseRootArray() {
        OnDemandJsonIterator.IteratorResult iteratorResult = jsonIterator.startIteratingRootArray();
        iteratorArrayElements(iteratorResult);
        jsonIterator.assertNoMoreJsonValues();
    }

    private void parseArray() {
        OnDemandJsonIterator.IteratorResult iteratorResult = jsonIterator.startIteratingArray();
        iteratorArrayElements(iteratorResult);
    }

    private void parseValue() {
        ResolvedClass.ResolvedClassCategory type = ptr.getType();
        if (type == null) {
            // path expects to descend further (non-leaf), but JSON has a primitive here.
            throw new JsonParsingException("path mismatch: expected an object or array but encountered a primitive value");
        }
        Object value = switch (type) {
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
            case STRING -> jsonIterator.getOrCompressAsString();
            default -> throw new JsonParsingException("only basic types are supported, not: " + type.name());
        };
        ptr.setValue(value);
    }

    private void iteratorObjectElements(OnDemandJsonIterator.IteratorResult result) {
        if (result == OnDemandJsonIterator.IteratorResult.NOT_EMPTY) {
            int collected = 0;
            int fieldNum = ptr.getChildren().size();
            boolean hasFields = true;
            int parentDepth = jsonIterator.getDepth() - 1;
            while (collected < fieldNum && hasFields) {
                String key = jsonIterator.getObjectKey();
                jsonIterator.moveToFieldValue();
                OnDemandJsonValue child = ptr.getChildren().get(key);
                if (child != null) {
                    // Guard against duplicate JSON keys: only increment `collected` the first time a
                    // particular requested child is visited within this parse() call.
                    if (child.getVisitedVersion() < currentVersion) {
                        child.setVisitedVersion(currentVersion);
                        collected++;
                    }
                    ptr = child;
                    parseElement();
                    ptr = ptr.getParent();
                } else {
                    jsonIterator.skipChild();
                }
                hasFields = jsonIterator.nextObjectField();
            }
            jsonIterator.skipChild(parentDepth);
        }
    }

    private void iteratorArrayElements(OnDemandJsonIterator.IteratorResult result) {
        if (result == OnDemandJsonIterator.IteratorResult.NOT_EMPTY) {
            int collected = 0;
            int fieldNum = ptr.getChildren().size();
            boolean hasFields = true;
            int index = 0;
            int parentDepth = jsonIterator.getDepth() - 2;
            while (collected < fieldNum && hasFields) {
                OnDemandJsonValue child = ptr.getChildren().get(index);
                if (child != null) {
                    ptr = child;
                    parseElement();
                    collected++;
                    ptr = ptr.getParent();
                } else {
                    jsonIterator.skipChild();
                }
                index++;
                hasFields = jsonIterator.nextArrayElement();
            }
            jsonIterator.skipChild(parentDepth);
        }
    }

    private void parseElement() {
        char currentChar = (char) padded[bitIndexes.peek()];
        if (currentChar == '{' || currentChar == '[') {
            int startOffset = bitIndexes.peek();
            if (currentChar == '{') {
                parseObject();
            } else {
                parseArray();
            }
            if (ptr.isLeaf()) {
                // The closing '}' or ']' was the last structural element consumed; using its offset
                // (instead of bitIndexes.peek(), which already points at the *next* element) avoids
                // capturing trailing whitespace between the bracket and the next structural char.
                int endOffsetInclusive = jsonIterator.lastConsumedOffset();
                ptr.setVersion(currentVersion);
                ptr.setValue(new String(padded, startOffset, endOffsetInclusive - startOffset + 1));
            }
        } else {
            if (ptr.isLeaf()) {
                ptr.setVersion(currentVersion);
            }
            parseValue();
        }
    }

    private Object[] getResult() {
        // Allocate a fresh result array so that callers may safely retain references across parse() calls.
        Object[] out = new Object[result.length];
        for (int i = 0; i < row.length; i++) {
            if (row[i].getVersion() < currentVersion) {
                out[i] = null;
            } else {
                out[i] = row[i].getValue();
            }
        }
        return out;
    }

    private byte[] padIfNeeded(byte[] buffer, int len) {
        if (len > DEFAULT_CAPACITY) {
            throw new JsonParsingException("input json exceeds max supported size (" + DEFAULT_CAPACITY + " bytes)");
        }
        if (buffer.length - len < PADDING) {
            System.arraycopy(buffer, 0, paddedBuffer, 0, len);
            return paddedBuffer;
        }
        return buffer;
    }
}
