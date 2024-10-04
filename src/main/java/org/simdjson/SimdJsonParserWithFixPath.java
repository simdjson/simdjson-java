package org.simdjson;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.RequiredArgsConstructor;

public class SimdJsonParserWithFixPath {

    @Data
    @RequiredArgsConstructor
    static class JsonNode {
        private long version = 0;
        private boolean isLeaf = false;
        private final String name;
        private String value = null;
        private JsonNode parent = null;
        private Map<String, JsonNode> children = new HashMap<>();
        private int start = -1;
        private int end = -1;
    }

    private final SimdJsonParser parser;
    private BitIndexes bitIndexes;
    private final JsonNode root = new JsonNode(null);
    private final JsonNode[] row;
    private final String[] result;
    private final String[] emptyResult;
    private JsonNode ptr;
    private byte[] buffer;
    private final int expectParseCols;
    // every time json string is processed, currentVersion will be incremented by 1
    private long currentVersion = 0;

    public SimdJsonParserWithFixPath(String... args) {
        parser = new SimdJsonParser();
        expectParseCols = args.length;
        row = new JsonNode[expectParseCols];
        result = new String[expectParseCols];
        emptyResult = new String[expectParseCols];
        for (int i = 0; i < args.length; i++) {
            emptyResult[i] = null;
        }
        for (int i = 0; i < expectParseCols; i++) {
            JsonNode cur = root;
            String[] paths = args[i].split("\\.");
            for (int j = 0; j < paths.length; j++) {
                if (!cur.getChildren().containsKey(paths[j])) {
                    JsonNode child = new JsonNode(paths[j]);
                    cur.getChildren().put(paths[j], child);
                    child.setParent(cur);
                }
                cur = cur.getChildren().get(paths[j]);
            }
            cur.setLeaf(true);
            row[i] = cur;
        }

    }

    public String[] parse(byte[] buffer, int len) {
        this.bitIndexes = parser.buildBitIndex(buffer, len);
        if (buffer == null || buffer.length == 0) {
            return emptyResult;
        }
        this.currentVersion++;
        this.ptr = root;
        this.buffer = buffer;

        switch (buffer[bitIndexes.peek()]) {
            case '{' -> {
                parseMap();
            }
            case '[' -> {
                parseList();
            }
            default -> {
                throw new RuntimeException("invalid json format");
            }
        }
        return getResult();
    }

    private String parseValue() {
        int start = bitIndexes.advance();
        int next = bitIndexes.peek();
        String field = new String(buffer, start, next - start).trim();
        if ("null".equalsIgnoreCase(field)) {
            return null;
        }
        // field type is string or type is decimal
        if (field.startsWith("\"")) {
            field = field.substring(1, field.length() - 1);
        }
        return field;
    }

    private void parseElement(String expectFieldName) {
        // if expectFieldName is null, parent is map, else is list
        if (expectFieldName == null) {
            expectFieldName = parseValue();
            bitIndexes.advance(); // skip :
        }
        if (!ptr.getChildren().containsKey(expectFieldName)) {
            skip(false);
            return;
        }
        ptr = ptr.getChildren().get(expectFieldName);
        switch (buffer[bitIndexes.peek()]) {
            case '{' -> {
                parseMap();
            }
            case '[' -> {
                parseList();
            }
            default -> {
                ptr.setValue(skip(true));
                ptr.setVersion(currentVersion);
            }
        }
        ptr = ptr.getParent();
    }

    private void parseMap() {
        if (ptr.getChildren() == null) {
            ptr.setValue(skip(true));
            ptr.setVersion(currentVersion);
            return;
        }
        ptr.setStart(bitIndexes.peek());
        bitIndexes.advance();
        while (bitIndexes.hasNext() && buffer[bitIndexes.peek()] != '}') {
            parseElement(null);
            if (buffer[bitIndexes.peek()] == ',') {
                bitIndexes.advance();
            }
        }
        ptr.setEnd(bitIndexes.peek());
        if (ptr.isLeaf()) {
            ptr.setValue(new String(buffer, ptr.getStart(), ptr.getEnd() - ptr.getStart() + 1));
            ptr.setVersion(currentVersion);
        }
        bitIndexes.advance();
    }

    private void parseList() {
        if (ptr.getChildren() == null) {
            ptr.setValue(skip(true));
            ptr.setVersion(currentVersion);
            return;
        }
        ptr.setStart(bitIndexes.peek());
        bitIndexes.advance();
        int i = 0;
        while (bitIndexes.hasNext() && buffer[bitIndexes.peek()] != ']') {
            parseElement("" + i);
            if (buffer[bitIndexes.peek()] == ',') {
                bitIndexes.advance();
            }
            i++;
        }
        ptr.setEnd(bitIndexes.peek());
        if (ptr.isLeaf()) {
            ptr.setValue(new String(buffer, ptr.getStart(), ptr.getEnd() - ptr.getStart() + 1));
            ptr.setVersion(currentVersion);
        }
        bitIndexes.advance();
    }

    private String skip(boolean retainValue) {
        int i = 0;
        int start = retainValue ? bitIndexes.peek() : 0;
        switch (buffer[bitIndexes.peek()]) {
            case '{' -> {
                i++;
                while (i > 0) {
                    bitIndexes.advance();
                    if (buffer[bitIndexes.peek()] == '{') {
                        i++;
                    } else if (buffer[bitIndexes.peek()] == '}') {
                        i--;
                    }
                }
                int end = bitIndexes.peek();
                bitIndexes.advance();
                return retainValue ? new String(buffer, start, end - start + 1) : null;
            }
            case '[' -> {
                i++;
                while (i > 0) {
                    bitIndexes.advance();
                    if (buffer[bitIndexes.peek()] == '[') {
                        i++;
                    } else if (buffer[bitIndexes.peek()] == ']') {
                        i--;
                    }
                }
                int end = bitIndexes.peek();
                bitIndexes.advance();
                return retainValue ? new String(buffer, start, end - start + 1) : null;
            }
            default -> {
                return parseValue();
            }
        }
    }

    private String[] getResult() {
        for (int i = 0; i < expectParseCols; i++) {
            if (row[i].getVersion() < currentVersion) {
                result[i] = null;
                continue;
            }
            result[i] = row[i].getValue();
        }
        return result;
    }
}
