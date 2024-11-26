package org.simdjson;

import java.util.Arrays;

public class PathsBasedJsonParser {

    private static final int PADDING = 64;
    private static final int DEFAULT_CAPACITY = 34 * 1024 * 1024; // we should be able to handle jsons <= 34MiB
    private static final String SINGLE_LEFT_BRACKET = "[";
    private static final String DOUBLE_LEFT_BRACKET = "[[";
    private static final String SINGLE_RIGHT_BRACKET = "]";
    private static final String DOUBLE_RIGHT_BRACKET = "]]";
    private static final String TYPE_DELIMITER = ":";
    private static final String PATH_DELIMITER = "\\.";
    private static final ResolvedClass.ResolvedClassCategory DEFAULT_TYPE = ResolvedClass.ResolvedClassCategory.STRING;

    private final Object[] emptyResult;
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

    public PathsBasedJsonParser(String... args) {
        if (args == null) {
            throw new IllegalArgumentException("paths must not be null");
        }
        this.bitIndexes = new BitIndexes(DEFAULT_CAPACITY);
        this.indexer = new StructuralIndexer(bitIndexes);
        this.jsonIterator = new OnDemandJsonIterator(bitIndexes, PADDING);
        this.emptyResult = new Object[args.length];
        Arrays.fill(this.emptyResult, null);
        this.result = new Object[args.length];
        this.paddedBuffer = new byte[DEFAULT_CAPACITY];
        this.row = new OnDemandJsonValue[args.length];
        constructPathTree(args);
    }

    private void constructPathTree(String... args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null || args[i].isEmpty()) {
                throw new IllegalArgumentException("path at index " + i + " is null or empty");
            }
            String[] pathAndType = args[i].split(TYPE_DELIMITER);
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

    public Object[] parse(byte[] buffer, int len) {
        if (buffer == null || buffer.length == 0) {
            return emptyResult;
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
                if (ptr.getChildren().containsKey(key)) {
                    ptr = ptr.getChildren().get(key);
                    parseElement();
                    collected++;
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
                if (ptr.getChildren().containsKey(index)) {
                    ptr = ptr.getChildren().get(index);
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
                int endOffset = bitIndexes.peek();
                ptr.setVersion(currentVersion);
                ptr.setValue(new String(padded, startOffset, endOffset - startOffset));
            }
        } else {
            if (ptr.isLeaf()) {
                ptr.setVersion(currentVersion);
            }
            parseValue();
        }
    }

    private Object[] getResult() {
        for (int i = 0; i < result.length; i++) {
            if (row[i].getVersion() < currentVersion) {
                result[i] = null;
                continue;
            }
            result[i] = row[i].getValue();
        }
        return result;
    }

    private byte[] padIfNeeded(byte[] buffer, int len) {
        if (buffer.length - len < PADDING) {
            System.arraycopy(buffer, 0, paddedBuffer, 0, len);
            return paddedBuffer;
        }
        return buffer;
    }
}
