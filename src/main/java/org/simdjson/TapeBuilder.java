package org.simdjson;

import java.util.Arrays;

import static org.simdjson.CharacterUtils.isStructuralOrWhitespace;
import static org.simdjson.Tape.END_ARRAY;
import static org.simdjson.Tape.END_OBJECT;
import static org.simdjson.Tape.FALSE_VALUE;
import static org.simdjson.Tape.NULL_VALUE;
import static org.simdjson.Tape.ROOT;
import static org.simdjson.Tape.START_ARRAY;
import static org.simdjson.Tape.START_OBJECT;
import static org.simdjson.Tape.TRUE_VALUE;

class TapeBuilder {

    private static final byte SPACE = 0x20;

    private final Tape tape;
    private final byte[] stringBuffer;
    private final OpenContainer[] openContainers;
    private final int padding;
    private final NumberParser numberParser;
    private final StringParser stringParser;

    TapeBuilder(int capacity, int depth, int padding) {
        this.tape = new Tape(capacity);
        this.openContainers = new OpenContainer[depth];
        this.padding = padding;
        for (int i = 0; i < openContainers.length; i++) {
            openContainers[i] = new OpenContainer();
        }
        this.stringBuffer = new byte[capacity];
        this.numberParser = new NumberParser(tape);
        this.stringParser = new StringParser(tape, stringBuffer);
    }

    void visitDocumentStart() {
        startContainer(0);
    }

    void visitDocumentEnd() {
        tape.append(0, ROOT);
        tape.write(0, tape.getCurrentIdx(), ROOT);
    }

    void visitEmptyObject() {
        emptyContainer(START_OBJECT, END_OBJECT);
    }

    void visitEmptyArray() {
        emptyContainer(START_ARRAY, END_ARRAY);
    }

    void visitRootPrimitive(byte[] buffer, int idx, int len) {
        switch (buffer[idx]) {
            case '"' -> visitString(buffer, idx);
            case 't' -> visitRootTrueAtom(buffer, idx);
            case 'f' -> visitRootFalseAtom(buffer, idx);
            case 'n' -> visitRootNullAtom(buffer, idx);
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> visitRootNumber(buffer, idx, len);
            default -> throw new JsonParsingException("Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'.");
        }
    }

    void visitPrimitive(byte[] buffer, int idx) {
        switch (buffer[idx]) {
            case '"' -> visitString(buffer, idx);
            case 't' -> visitTrueAtom(buffer, idx);
            case 'f' -> visitFalseAtom(buffer, idx);
            case 'n' -> visitNullAtom(buffer, idx);
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> visitNumber(buffer, idx);
            default -> throw new JsonParsingException("Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'.");
        }
    }

    void visitObjectStart(int depth) {
        startContainer(depth);
    }

    void incrementCount(int depth) {
        openContainers[depth].count++;
    }

    void visitObjectEnd(int depth) {
        endContainer(START_OBJECT, END_OBJECT, depth);
    }

    void visitArrayStart(int depth) {
        startContainer(depth);
    }

    void visitArrayEnd(int depth) {
        endContainer(START_ARRAY, END_ARRAY, depth);
    }

    private void visitTrueAtom(byte[] buffer, int idx) {
        boolean valid = isTrue(buffer, idx) && isStructuralOrWhitespace(buffer[idx + 4]);
        if (!valid) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'true'.");
        }
        tape.append(0, TRUE_VALUE);
    }

    private void visitRootTrueAtom(byte[] buffer, int idx) {
        if (!isTrue(buffer, idx)) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'true'.");
        }
        tape.append(0, TRUE_VALUE);
    }

    private boolean isTrue(byte[] buffer, int idx) {
        return buffer[idx] == 't'
                && buffer[idx + 1] == 'r'
                && buffer[idx + 2] == 'u'
                && buffer[idx + 3] == 'e';
    }

    private void visitFalseAtom(byte[] buffer, int idx) {
        boolean valid = isFalse(buffer, idx) && isStructuralOrWhitespace(buffer[idx + 5]);
        if (!valid) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'false'.");
        }
        tape.append(0, FALSE_VALUE);
    }

    private void visitRootFalseAtom(byte[] buffer, int idx) {
        if (!isFalse(buffer, idx)) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'false'.");
        }
        tape.append(0, FALSE_VALUE);
    }

    private boolean isFalse(byte[] buffer, int idx) {
        return buffer[idx] == 'f'
                && buffer[idx + 1] == 'a'
                && buffer[idx + 2] == 'l'
                && buffer[idx + 3] == 's'
                && buffer[idx + 4] == 'e';
    }

    private void visitNullAtom(byte[] buffer, int idx) {
        boolean valid = isNull(buffer, idx) && isStructuralOrWhitespace(buffer[idx + 4]);
        if (!valid) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'null'.");
        }
        tape.append(0, NULL_VALUE);
    }

    private void visitRootNullAtom(byte[] buffer, int idx) {
        if (!isNull(buffer, idx)) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'null'.");
        }
        tape.append(0, NULL_VALUE);
    }

    private boolean isNull(byte[] buffer, int idx) {
        return buffer[idx] == 'n'
                && buffer[idx + 1] == 'u'
                && buffer[idx + 2] == 'l'
                && buffer[idx + 3] == 'l';
    }

    void visitKey(byte[] buffer, int idx) {
        visitString(buffer, idx);
    }

    private void visitString(byte[] buffer, int idx) {
        stringParser.parseString(buffer, idx);
    }

    private void visitNumber(byte[] buffer, int idx) {
        numberParser.parseNumber(buffer, idx);
    }

    private void visitRootNumber(byte[] buffer, int idx, int len) {
        int remainingLen = len - idx;
        byte[] copy = new byte[remainingLen + padding];
        System.arraycopy(buffer, idx, copy, 0, remainingLen);
        Arrays.fill(copy, remainingLen, remainingLen + padding, SPACE);
        numberParser.parseNumber(copy, 0);
    }

    private void startContainer(int depth) {
        openContainers[depth].tapeIndex = tape.getCurrentIdx();
        openContainers[depth].count = 0;
        tape.skip();
    }

    private void endContainer(char start, char end, int depth) {
        int startTapeIndex = openContainers[depth].tapeIndex;
        tape.append(startTapeIndex, end);
        int count = openContainers[depth].count;
        count = Math.min(count, 0xFFFFFF);
        tape.write(startTapeIndex, tape.getCurrentIdx() | ((long) count << 32), start);
    }

    private void emptyContainer(char start, char end) {
        tape.append(tape.getCurrentIdx() + 2, start);
        tape.append(tape.getCurrentIdx(), end);
    }

    void reset() {
        tape.reset();
        stringParser.reset();
    }

    JsonValue createJsonValue(byte[] buffer) {
        return new JsonValue(tape, 1, stringBuffer, buffer);
    }

    private static class OpenContainer {
        int tapeIndex;
        int count;
    }
}
