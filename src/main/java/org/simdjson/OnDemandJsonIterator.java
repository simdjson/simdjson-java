package org.simdjson;

import java.util.Arrays;

import static org.simdjson.CharacterUtils.isStructuralOrWhitespace;

/**
 * A forward-only cursor over a JSON document, returned by {@link SimdJsonParser#parseOnDemand(byte[], int)}.
 * <p>
 * The parser runs only stage 1 (UTF-8 validation and structural indexing) and builds no tape. Values are decoded
 * when the caller asks for them, in document order, and everything the caller does not ask for is skipped with
 * {@link #skipChild()}. Nothing is allocated per value, except by the getters that return a boxed value.
 * <p>
 * The root of the document must be an object or an array, entered with {@link #startIteratingRootObject()} or
 * {@link #startIteratingRootArray()}. Once the root value has been read to its end, any further content in the
 * document is reported as an error.
 *
 * <h2>Lifecycle</h2>
 * Each parser owns one cursor, and {@code parseOnDemand} returns that same instance every time. The cursor is valid
 * until the next call to any {@code parse} or {@code parseOnDemand} method of its parser, and it must not be used
 * again after it has thrown an exception. Like the parser, it is not thread-safe.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * byte[] name = new byte[64 + OnDemandJsonIterator.STRING_BUFFER_PADDING];
 * OnDemandJsonIterator it = parser.parseOnDemand(json, json.length);
 * if (it.startIteratingRootObject() == IteratorResult.NOT_EMPTY) {
 *     do {
 *         int nameLength = it.getFieldName(name);
 *         it.moveToFieldValue();
 *         if (Arrays.equals(name, 0, nameLength, ID, 0, ID.length)) {
 *             id = it.getNonNullLong();
 *         } else {
 *             it.skipChild();
 *         }
 *     } while (it.nextObjectField());
 * }
 * }</pre>
 *
 * <h2>String buffers</h2>
 * {@link #getString(byte[])} and {@link #getFieldName(byte[])} decode into a caller-supplied array. The decoder
 * stores whole SIMD vectors, so it may write up to {@link #STRING_BUFFER_PADDING} bytes past the end of the decoded
 * string. An array of at least {@code n + STRING_BUFFER_PADDING} bytes is always large enough for a string whose
 * decoded length is at most {@code n}; the decoded length never exceeds the number of bytes between the quotes in the
 * input. A smaller array may cause an {@link IndexOutOfBoundsException}.
 *
 * <h2>Depth</h2>
 * {@link #getDepth()} is the nesting level of the cursor's position. The root value is at depth 1, and the fields
 * and elements of a container at depth {@code d} are at depth {@code d + 1}. Reading or skipping a value at depth
 * {@code d} leaves the depth at {@code d - 1}, and so does closing a container at depth {@code d}
 * ({@link #nextObjectField()} or {@link #nextArrayElement()} returning {@code false}). Entering a container differs
 * between objects and arrays:
 * <ul>
 *     <li>{@link #startIteratingObject()} leaves the depth at {@code d}: the cursor is at a field name, which is not a
 *     value. {@link #moveToFieldValue()} then moves to depth {@code d + 1}.</li>
 *     <li>{@link #startIteratingArray()} moves to depth {@code d + 1}: the cursor is at the first element.
 *     {@link #nextArrayElement()} returning {@code true} moves to depth {@code d + 1} again.</li>
 * </ul>
 * In both cases, after a field value or an element has been read the depth is {@code d} again. To skip the rest of
 * a container from anywhere inside it, pass {@code d - 1} to {@link #skipChild(int)}. The simplest way to get that
 * number is to read the depth just before entering the container:
 * <pre>{@code
 * int parentDepth = it.getDepth() - 1;
 * if (it.startIteratingObject() == IteratorResult.NOT_EMPTY) {
 *     int nameLength = it.getFieldName(name);
 *     it.moveToFieldValue();
 *     if (!Arrays.equals(name, 0, nameLength, TYPE, 0, TYPE.length)) {
 *         it.skipChild(parentDepth); // the rest of this object, including its closing brace
 *         return;
 *     }
 *     ...
 * }
 * }</pre>
 * Right after a successful {@code startIteratingObject()} the same number is {@code getDepth() - 1}; right after a
 * successful {@code startIteratingArray()} it is {@code getDepth() - 2}.
 */
public class OnDemandJsonIterator {

    /**
     * The number of bytes that {@link #getString(byte[])} and {@link #getFieldName(byte[])} may write past the end of
     * the decoded string: the size of one SIMD vector of the selected species (32 or 64).
     */
    public static final int STRING_BUFFER_PADDING = StringParser.MAX_BYTES_WRITTEN_PAST_STRING;

    private static final byte SPACE = 0x20;
    private static final int[] SKIP_DEPTH_PER_CHARACTER = new int[127];

    static {
        Arrays.fill(SKIP_DEPTH_PER_CHARACTER, 0);
        SKIP_DEPTH_PER_CHARACTER['['] = 1;
        SKIP_DEPTH_PER_CHARACTER['{'] = 1;
        SKIP_DEPTH_PER_CHARACTER[']'] = -1;
        SKIP_DEPTH_PER_CHARACTER['}'] = -1;
    }

    private final BitIndexes indexer;
    private final int padding;
    private final StringParser stringParser = new StringParser();
    private final NumberParser numberParser = new NumberParser();

    private byte[] buffer;
    private int len;
    private int depth;

    OnDemandJsonIterator(BitIndexes indexer, int padding) {
        this.indexer = indexer;
        this.padding = padding;
    }

    void init(byte[] buffer, int len) {
        if (indexer.isEnd()) {
            throw new JsonParsingException("No structural element found.");
        }
        this.buffer = buffer;
        this.len = len;
        this.depth = 1;
    }

    /**
     * Returns the byte offset in the input of the next structural token, that is, of the first byte of the value the
     * cursor is at. Called where a value is expected, it gives the start of that value, for example the offset of
     * the opening brace of an object before {@link #startIteratingObject()}. The cursor does not move.
     *
     * @return the offset of the next token, or the length of the document if the cursor has reached its end
     */
    public int peekOffset() {
        if (indexer.hasNext()) {
            return indexer.peek();
        }
        return len;
    }

    /**
     * Returns the byte offset in the input of the most recently consumed structural token. Once an object or an
     * array has been read to its end, or skipped, this is the offset of its closing brace or bracket, so together
     * with {@link #peekOffset()} taken before the value it gives the exact span of the value:
     * <pre>{@code
     * int start = it.peekOffset();
     * it.skipChild();
     * int end = it.lastTokenOffset() + 1; // exclusive
     * }</pre>
     * For a scalar value, this is the offset of its first byte.
     *
     * @return the offset of the last consumed token
     * @throws IllegalStateException if no token has been consumed yet
     */
    public int lastTokenOffset() {
        if (indexer.isStart()) {
            throw new IllegalStateException("No token has been consumed yet.");
        }
        return indexer.lastIndex();
    }

    /**
     * Returns the JSON type of the value the cursor is at, determined from its first byte. The cursor does not move,
     * and the value is not validated: a malformed literal such as {@code nul} is reported here as {@link ValueType#NULL}
     * and rejected by the getter. Call it where a value is expected: after {@link #moveToFieldValue()}, after
     * {@link #startIteratingArray()} returned {@link IteratorResult#NOT_EMPTY}, or after {@link #nextArrayElement()}
     * returned {@code true}.
     *
     * @return the type of the next value
     * @throws JsonParsingException if the next token cannot start a value, or if the end of the document has been
     *                              reached
     */
    public ValueType peekType() {
        if (!indexer.hasNext()) {
            throw new JsonParsingException("Expected a value but reached end of buffer.");
        }
        int idx = indexer.peek();
        return switch (buffer[idx]) {
            case '{' -> ValueType.OBJECT;
            case '[' -> ValueType.ARRAY;
            case '"' -> ValueType.STRING;
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> ValueType.NUMBER;
            case 't', 'f' -> ValueType.BOOLEAN;
            case 'n' -> ValueType.NULL;
            default -> throw new JsonParsingException("Unrecognized value starting at " + idx
                    + ". Expected: object, array, string, number, 'true', 'false' or 'null'.");
        };
    }

    /**
     * Skips the value the cursor is at, including all of its contents. Equivalent to
     * {@code skipChild(getDepth() - 1)}: when the cursor is not at a value but at a field name or between the fields
     * or elements of a container, it skips the rest of that container, including its closing brace or bracket.
     *
     * @throws JsonParsingException if the document ends before the value does
     */
    public void skipChild() {
        skipChild(depth - 1);
    }

    /**
     * Skips tokens until the depth is {@code parentDepth} or less, and does nothing if it already is. Passing the
     * depth of a container minus one skips the rest of that container from anywhere inside it; see the class
     * documentation for how to obtain that number.
     *
     * @param parentDepth the depth to return to
     * @throws JsonParsingException if the document ends before that depth is reached
     */
    public void skipChild(int parentDepth) {
        if (depth <= parentDepth) {
            return;
        }
        int idx = indexer.getAndAdvance();
        byte character = buffer[idx];

        switch (character) {
            case '[', '{', ':', ',':
                break;
            case '"':
                if (buffer[indexer.peek()] == ':') {
                    indexer.advance(); // skip ':'
                    break;
                }
            default:
                depth--;
                if (depth <= parentDepth) {
                    assertNoMoreJsonValuesAfterRoot();
                    return;
                }
        }

        while (indexer.hasNext()) {
            idx = indexer.getAndAdvance();
            character = buffer[idx];

            int delta = SKIP_DEPTH_PER_CHARACTER[character];
            depth += delta;
            if (delta < 0 && depth <= parentDepth) {
                assertNoMoreJsonValuesAfterRoot();
                return;
            }
        }

        throw new JsonParsingException("Not enough close braces.");
    }

    Boolean getRootNonNullBoolean() {
        int idx = indexer.getAndAdvance();
        Boolean result = switch (buffer[idx]) {
            case 't' -> visitRootTrueAtom(idx);
            case 'f' -> visitRootFalseAtom(idx);
            default -> throw new JsonParsingException("Unrecognized boolean value. Expected: 'true' or 'false'.");
        };
        assertNoMoreJsonValues();
        depth--;
        return result;
    }

    Boolean getRootBoolean() {
        int idx = indexer.getAndAdvance();
        Boolean result = switch (buffer[idx]) {
            case 't' -> visitRootTrueAtom(idx);
            case 'f' -> visitRootFalseAtom(idx);
            case 'n' -> {
                visitRootNullAtom(idx);
                yield null;
            }
            default -> throw new JsonParsingException("Unrecognized boolean value. Expected: 'true', 'false' or 'null'.");
        };
        assertNoMoreJsonValues();
        depth--;
        return result;
    }

    private Boolean visitRootTrueAtom(int idx) {
        boolean valid = idx + 4 <= len && isTrue(idx) && (idx + 4 == len || isStructuralOrWhitespace(buffer[idx + 4]));
        if (!valid) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'true'.");
        }
        return Boolean.TRUE;
    }

    private Boolean visitRootFalseAtom(int idx) {
        boolean valid = idx + 5 <= len && isFalse(idx) && (idx + 5 == len || isStructuralOrWhitespace(buffer[idx + 5]));
        if (!valid) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'false'.");
        }
        return Boolean.FALSE;
    }

    private void visitRootNullAtom(int idx) {
        boolean valid = idx + 4 <= len && isNull(idx) && (idx + 4 == len || isStructuralOrWhitespace(buffer[idx + 4]));
        if (!valid) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'null'.");
        }
    }

    private void visitNullAtom(int idx) {
        if (!isNull(idx)) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'null'.");
        }
    }

    private boolean isNull(int idx) {
        return buffer[idx] == 'n'
                && buffer[idx + 1] == 'u'
                && buffer[idx + 2] == 'l'
                && buffer[idx + 3] == 'l';
    }

    /**
     * Reads the boolean value the cursor is at.
     *
     * @return the value
     * @throws JsonParsingException if the value is not {@code true} or {@code false}
     */
    public boolean getNonNullBoolean() {
        int idx = indexer.getAndAdvance();
        Boolean result = switch (buffer[idx]) {
            case 't' -> visitTrueAtom(idx);
            case 'f' -> visitFalseAtom(idx);
            default -> throw new JsonParsingException("Unrecognized boolean value. Expected: 'true' or 'false'.");
        };
        depth--;
        return result;
    }

    /**
     * Reads the boolean or {@code null} value the cursor is at.
     *
     * @return the value, or {@code null} for a JSON {@code null}
     * @throws JsonParsingException if the value is not {@code true}, {@code false} or {@code null}
     */
    public Boolean getBoolean() {
        int idx = indexer.getAndAdvance();
        Boolean result = switch (buffer[idx]) {
            case 't' -> visitTrueAtom(idx);
            case 'f' -> visitFalseAtom(idx);
            case 'n' -> {
                visitNullAtom(idx);
                yield null;
            }
            default -> throw new JsonParsingException("Unrecognized boolean value. Expected: 'true', 'false' or 'null'.");
        };
        depth--;
        return result;
    }

    private Boolean visitTrueAtom(int idx) {
        boolean valid = isTrue(idx) && isStructuralOrWhitespace(buffer[idx + 4]);
        if (!valid) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'true'.");
        }
        return Boolean.TRUE;
    }

    private boolean isTrue(int idx) {
        return buffer[idx] == 't'
                && buffer[idx + 1] == 'r'
                && buffer[idx + 2] == 'u'
                && buffer[idx + 3] == 'e';
    }

    private Boolean visitFalseAtom(int idx) {
        boolean valid = isFalse(idx) && isStructuralOrWhitespace(buffer[idx + 5]);
        if (!valid) {
            throw new JsonParsingException("Invalid value starting at " + idx + ". Expected 'false'.");
        }
        return Boolean.FALSE;
    }

    private boolean isFalse(int idx) {
        return buffer[idx] == 'f'
                && buffer[idx + 1] == 'a'
                && buffer[idx + 2] == 'l'
                && buffer[idx + 3] == 's'
                && buffer[idx + 4] == 'e';
    }

    byte getRootNonNullByte() {
        depth--;
        int idx = indexer.getAndAdvance();
        byte[] copy = padRootNumber(idx);
        byte value = numberParser.parseByte(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    Byte getRootByte() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            assertNoMoreJsonValues();
            return null;
        }
        byte[] copy = padRootNumber(idx);
        byte value = numberParser.parseByte(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    byte getNonNullByte() {
        depth--;
        int idx = indexer.getAndAdvance();
        return numberParser.parseByte(buffer, len, idx);
    }

    Byte getByte() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            return null;
        }
        return numberParser.parseByte(buffer, len, idx);
    }

    short getRootNonNullShort() {
        depth--;
        int idx = indexer.getAndAdvance();
        byte[] copy = padRootNumber(idx);
        short value = numberParser.parseShort(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    Short getRootShort() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            assertNoMoreJsonValues();
            return null;
        }
        byte[] copy = padRootNumber(idx);
        short value = numberParser.parseShort(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    short getNonNullShort() {
        depth--;
        int idx = indexer.getAndAdvance();
        return numberParser.parseShort(buffer, len, idx);
    }

    Short getShort() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            return null;
        }
        return numberParser.parseShort(buffer, len, idx);
    }

    int getRootNonNullInt() {
        depth--;
        int idx = indexer.getAndAdvance();
        byte[] copy = padRootNumber(idx);
        int value = numberParser.parseInt(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    Integer getRootInt() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            assertNoMoreJsonValues();
            return null;
        }
        byte[] copy = padRootNumber(idx);
        int value = numberParser.parseInt(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    /**
     * Reads the integer or {@code null} value the cursor is at.
     *
     * @return the value, or {@code null} for a JSON {@code null}
     * @throws JsonParsingException if the value is not an integer literal in the range of {@code int}, or
     *                              {@code null}
     */
    public Integer getInt() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            return null;
        }
        return numberParser.parseInt(buffer, len, idx);
    }

    /**
     * Reads the integer value the cursor is at.
     *
     * @return the value
     * @throws JsonParsingException if the value is not an integer literal in the range of {@code int}
     */
    public int getNonNullInt() {
        depth--;
        int idx = indexer.getAndAdvance();
        return numberParser.parseInt(buffer, len, idx);
    }

    long getRootNonNullLong() {
        depth--;
        int idx = indexer.getAndAdvance();
        byte[] copy = padRootNumber(idx);
        long value = numberParser.parseLong(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    Long getRootLong() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            assertNoMoreJsonValues();
            return null;
        }
        byte[] copy = padRootNumber(idx);
        long value = numberParser.parseLong(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    /**
     * Reads the integer value the cursor is at.
     *
     * @return the value
     * @throws JsonParsingException if the value is not an integer literal in the range of {@code long}
     */
    public long getNonNullLong() {
        depth--;
        int idx = indexer.getAndAdvance();
        return numberParser.parseLong(buffer, len, idx);
    }

    /**
     * Reads the integer or {@code null} value the cursor is at.
     *
     * @return the value, or {@code null} for a JSON {@code null}
     * @throws JsonParsingException if the value is not an integer literal in the range of {@code long}, or
     *                              {@code null}
     */
    public Long getLong() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            return null;
        }
        return numberParser.parseLong(buffer, len, idx);
    }

    float getRootNonNullFloat() {
        depth--;
        int idx = indexer.getAndAdvance();
        byte[] copy = padRootNumber(idx);
        float value = numberParser.parseFloat(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    Float getRootFloat() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            assertNoMoreJsonValues();
            return null;
        }
        byte[] copy = padRootNumber(idx);
        float value = numberParser.parseFloat(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    double getRootNonNullDouble() {
        depth--;
        int idx = indexer.getAndAdvance();
        byte[] copy = padRootNumber(idx);
        double value = numberParser.parseDouble(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    Double getRootDouble() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            assertNoMoreJsonValues();
            return null;
        }
        byte[] copy = padRootNumber(idx);
        double value = numberParser.parseDouble(copy, len, 0);
        assertNoMoreJsonValues();
        return value;
    }

    private byte[] padRootNumber(int idx) {
        int remainingLen = len - idx;
        byte[] copy = new byte[remainingLen + padding];
        System.arraycopy(buffer, idx, copy, 0, remainingLen);
        Arrays.fill(copy, remainingLen, remainingLen + padding, SPACE);
        return copy;
    }

    /**
     * Reads the number the cursor is at as the nearest {@code double}. Integer literals are accepted.
     *
     * @return the value
     * @throws JsonParsingException if the value is not a number
     */
    public double getNonNullDouble() {
        depth--;
        int idx = indexer.getAndAdvance();
        return numberParser.parseDouble(buffer, len, idx);
    }

    /**
     * Reads the number the cursor is at as the nearest {@code double}, or {@code null}. Integer literals are accepted.
     *
     * @return the value, or {@code null} for a JSON {@code null}
     * @throws JsonParsingException if the value is not a number or {@code null}
     */
    public Double getDouble() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            return null;
        }
        return numberParser.parseDouble(buffer, len, idx);
    }

    /**
     * Reads the number the cursor is at as the nearest {@code float}. Integer literals are accepted.
     *
     * @return the value
     * @throws JsonParsingException if the value is not a number
     */
    public float getNonNullFloat() {
        depth--;
        int idx = indexer.getAndAdvance();
        return numberParser.parseFloat(buffer, len, idx);
    }

    /**
     * Reads the number the cursor is at as the nearest {@code float}, or {@code null}. Integer literals are accepted.
     *
     * @return the value, or {@code null} for a JSON {@code null}
     * @throws JsonParsingException if the value is not a number or {@code null}
     */
    public Float getFloat() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            return null;
        }
        return numberParser.parseFloat(buffer, len, idx);
    }

    /**
     * Copies the number literal the cursor is at, byte for byte, into {@code destination}, without parsing it. This
     * is for callers that pass the literal on verbatim, for example into a CSV file, where parsing it and formatting
     * it again would cost time and could change its text ({@code 1.50} or {@code 1e2}). Integers and decimals are
     * copied alike.
     * <p>
     * Only the first byte is checked: the literal must start with {@code -} or a digit. The rest is copied up to the
     * next structural character or whitespace and is not validated; use one of the number getters when the value
     * must be checked against the JSON grammar.
     *
     * @param destination the array the literal is copied into, starting at index 0
     * @return the length of the literal in bytes
     * @throws JsonParsingException     if the value does not start like a number (this includes {@code null})
     * @throws IllegalArgumentException if the literal is longer than {@code destination}
     */
    public int getRawNumber(byte[] destination) {
        depth--;
        int start = indexer.getAndAdvance();
        byte first = buffer[start];
        if (first != '-' && (first < '0' || first > '9')) {
            throw new JsonParsingException("Invalid value starting at " + start + ". Expected number.");
        }
        int end = start + 1;
        while (end < len && !isStructuralOrWhitespace(buffer[end])) {
            end++;
        }
        int length = end - start;
        if (length > destination.length) {
            throw new IllegalArgumentException("The number starting at " + start + " has " + length
                    + " bytes, but the destination array has only " + destination.length + ".");
        }
        System.arraycopy(buffer, start, destination, 0, length);
        return length;
    }

    int getRootString(byte[] stringBuffer) {
        depth--;
        int idx = indexer.getAndAdvance();
        int len = switch (buffer[idx]) {
            case '"' -> stringParser.parseString(buffer, idx, stringBuffer);
            case 'n' -> {
                visitRootNullAtom(idx);
                yield -1;
            }
            default -> throw new JsonParsingException("Invalid value starting at " + idx + ". Expected either string or 'null'.");
        };
        assertNoMoreJsonValues();
        return len;
    }

    /**
     * Decodes the string value the cursor is at into {@code stringBuffer}, starting at index 0, as UTF-8 with all
     * escape sequences resolved.
     * <p>
     * The decoder may write up to {@link #STRING_BUFFER_PADDING} bytes past the decoded string, so
     * {@code stringBuffer} must be at least {@code n + STRING_BUFFER_PADDING} bytes long for a string whose decoded
     * length is {@code n}; see the class documentation. Bytes past the returned length are unspecified.
     *
     * @param stringBuffer the array to decode into
     * @return the decoded length in bytes, or {@code -1} for a JSON {@code null}
     * @throws JsonParsingException      if the value is not a string or {@code null}, or contains an invalid escape
     *                                   sequence
     * @throws IndexOutOfBoundsException if the decoder needs more room than {@code stringBuffer} has
     */
    public int getString(byte[] stringBuffer) {
        depth--;
        int idx = indexer.getAndAdvance();
        return switch (buffer[idx]) {
            case '"' -> stringParser.parseString(buffer, idx, stringBuffer);
            case 'n' -> {
                visitNullAtom(idx);
                yield -1;
            }
            default -> throw new JsonParsingException("Invalid value starting at " + idx + ". Expected either string or 'null'.");
        };
    }

    char getNonNullChar() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == '"') {
            return stringParser.parseChar(buffer, idx);
        }
        throw new JsonParsingException("Invalid value starting at " + idx + ". Expected string.");
    }

    Character getChar() {
        depth--;
        int idx = indexer.getAndAdvance();
        return switch (buffer[idx]) {
            case '"' -> stringParser.parseChar(buffer, idx);
            case 'n' -> {
                visitNullAtom(idx);
                yield null;
            }
            default -> throw new JsonParsingException("Invalid value starting at " + idx + ". Expected either string or 'null'.");
        };
    }

    char getRootNonNullChar() {
        depth--;
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == '"') {
            char character = stringParser.parseChar(buffer, idx);
            assertNoMoreJsonValues();
            return character;
        }
        throw new JsonParsingException("Invalid value starting at " + idx + ". Expected string.");
    }

    Character getRootChar() {
        depth--;
        int idx = indexer.getAndAdvance();
        Character character = switch (buffer[idx]) {
            case '"' -> stringParser.parseChar(buffer, idx);
            case 'n' -> {
                visitRootNullAtom(idx);
                yield null;
            }
            default -> throw new JsonParsingException("Invalid value starting at " + idx + ". Expected either string or 'null'.");
        };
        assertNoMoreJsonValues();
        return character;
    }

    /**
     * Enters the array the cursor is at. Use {@link #startIteratingRootArray()} for the root of the document.
     * <p>
     * On {@link IteratorResult#NOT_EMPTY} the cursor is at the first element, one level deeper. On
     * {@link IteratorResult#EMPTY} and {@link IteratorResult#NULL} the value has been consumed entirely.
     *
     * @return whether the array is empty, {@code null}, or has elements
     * @throws JsonParsingException if the value is neither an array nor {@code null}
     */
    public IteratorResult startIteratingArray() {
        int idx = indexer.peek();
        if (buffer[idx] == 'n') {
            visitNullAtom(idx);
            indexer.advance();
            depth--;
            return IteratorResult.NULL;
        }
        if (buffer[idx] != '[') {
            throw unexpectedCharException(idx, '[');
        }
        idx = indexer.advanceAndGet();
        if (buffer[idx] == ']') {
            indexer.advance();
            depth--;
            return IteratorResult.EMPTY;
        }
        depth++;
        return IteratorResult.NOT_EMPTY;
    }

    /**
     * Enters the array at the root of the document. Behaves like {@link #startIteratingArray()}, and in addition
     * checks that the document ends with the array.
     *
     * @return whether the array is empty, {@code null}, or has elements
     * @throws JsonParsingException if the root value is neither an array nor {@code null}, if the array is not closed
     *                              at the end of the document, or if the document has more content after an empty
     *                              array or {@code null}
     */
    public IteratorResult startIteratingRootArray() {
        int idx = indexer.peek();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            indexer.advance();
            depth--;
            assertNoMoreJsonValues();
            return IteratorResult.NULL;
        }
        if (buffer[idx] != '[') {
            throw unexpectedCharException(idx, '[');
        }
        if (buffer[indexer.getLast()] != ']') {
            throw new JsonParsingException("Unclosed array. Missing ']' for starting '['.");
        }
        idx = indexer.advanceAndGet();
        if (buffer[idx] == ']') {
            indexer.advance();
            depth--;
            assertNoMoreJsonValues();
            return IteratorResult.EMPTY;
        }
        depth++;
        return IteratorResult.NOT_EMPTY;
    }

    /**
     * Moves past the element that has just been read or skipped.
     *
     * @return {@code true} if the cursor is now at the next element, {@code false} if the array has been closed
     * @throws JsonParsingException if neither a comma nor the closing bracket follows, or if the array is the root
     *                              value and the document has more content after it
     */
    public boolean nextArrayElement() {
        int idx = indexer.getAndAdvance();
        if (buffer[idx] == ']') {
            depth--;
            assertNoMoreJsonValuesAfterRoot();
            return false;
        } else if (buffer[idx] == ',') {
            depth++;
            return true;
        } else {
            throw new JsonParsingException("Missing comma between array values");
        }
    }

    /**
     * Enters the object the cursor is at. Use {@link #startIteratingRootObject()} for the root of the document.
     * <p>
     * On {@link IteratorResult#NOT_EMPTY} the cursor is at the first field name, at the same depth (see the class
     * documentation). On {@link IteratorResult#EMPTY} and {@link IteratorResult#NULL} the value has been consumed
     * entirely.
     *
     * @return whether the object is empty, {@code null}, or has fields
     * @throws JsonParsingException if the value is neither an object nor {@code null}
     */
    public IteratorResult startIteratingObject() {
        int idx = indexer.peek();
        if (buffer[idx] == 'n') {
            visitNullAtom(idx);
            indexer.advance();
            depth--;
            return IteratorResult.NULL;
        }
        if (buffer[idx] != '{') {
            throw unexpectedCharException(idx, '{');
        }
        idx = indexer.advanceAndGet();
        if (buffer[idx] == '}') {
            indexer.advance();
            depth--;
            return IteratorResult.EMPTY;
        }
        return IteratorResult.NOT_EMPTY;
    }

    /**
     * Enters the object at the root of the document. Behaves like {@link #startIteratingObject()}, and in addition
     * checks that the document ends with the object.
     *
     * @return whether the object is empty, {@code null}, or has fields
     * @throws JsonParsingException if the root value is neither an object nor {@code null}, if the object is not
     *                              closed at the end of the document, or if the document has more content after an
     *                              empty object or {@code null}
     */
    public IteratorResult startIteratingRootObject() {
        int idx = indexer.peek();
        if (buffer[idx] == 'n') {
            visitRootNullAtom(idx);
            indexer.advance();
            depth--;
            assertNoMoreJsonValues();
            return IteratorResult.NULL;
        }
        if (buffer[idx] != '{') {
            throw unexpectedCharException(idx, '{');
        }
        if (buffer[indexer.getLast()] != '}') {
            throw new JsonParsingException("Unclosed object. Missing '}' for starting '{'.");
        }
        idx = indexer.advanceAndGet();
        if (buffer[idx] == '}') {
            indexer.advance();
            depth--;
            assertNoMoreJsonValues();
            return IteratorResult.EMPTY;
        }
        return IteratorResult.NOT_EMPTY;
    }

    /**
     * Moves past the field value that has just been read or skipped.
     *
     * @return {@code true} if the cursor is now at the next field name, {@code false} if the object has been closed
     * @throws JsonParsingException if neither a comma nor the closing brace follows, or if the object is the root
     *                              value and the document has more content after it
     */
    public boolean nextObjectField() {
        int idx = indexer.getAndAdvance();
        byte character = buffer[idx];
        if (character == '}') {
            depth--;
            assertNoMoreJsonValuesAfterRoot();
            return false;
        } else if (character == ',') {
            return true;
        } else {
            throw unexpectedCharException(idx, ',');
        }
    }

    /**
     * Moves from the field name that has just been read to the field value, one level deeper.
     *
     * @throws JsonParsingException if no colon follows the field name
     */
    public void moveToFieldValue() {
        int idx = indexer.getAndAdvance();
        if (buffer[idx] != ':') {
            throw unexpectedCharException(idx, ':');
        }
        depth++;
    }

    /**
     * Decodes the field name the cursor is at into {@code stringBuffer}, starting at index 0, as UTF-8 with all escape
     * sequences resolved. The depth does not change; call {@link #moveToFieldValue()} next.
     * <p>
     * The same buffer requirement as for {@link #getString(byte[])} applies: {@code stringBuffer} must be at least
     * {@code n + STRING_BUFFER_PADDING} bytes long for a field name whose decoded length is {@code n}. Bytes past the
     * returned length are unspecified.
     *
     * @param stringBuffer the array to decode into
     * @return the decoded length in bytes
     * @throws JsonParsingException      if the cursor is not at a field name, or the name contains an invalid escape
     *                                   sequence
     * @throws IndexOutOfBoundsException if the decoder needs more room than {@code stringBuffer} has
     */
    public int getFieldName(byte[] stringBuffer) {
        int idx = indexer.getAndAdvance();
        if (buffer[idx] != '"') {
            throw unexpectedCharException(idx, '"');
        }
        return stringParser.parseString(buffer, idx, stringBuffer);
    }

    /**
     * Returns the nesting level of the cursor's position: 1 for the root value. See the class documentation for how
     * each method changes it, and for how to use it with {@link #skipChild(int)}.
     *
     * @return the current depth
     */
    public int getDepth() {
        return depth;
    }

    private JsonParsingException unexpectedCharException(int idx, char expected) {
        if (indexer.isPastEnd()) {
            return new JsonParsingException("Expected '" + expected + "' but reached end of buffer.");
        } else {
            return new JsonParsingException("Expected '" + expected + "' but got: '" + (char) buffer[idx] + "'.");
        }
    }

    void assertNoMoreJsonValues() {
        if (indexer.hasNext()) {
            throw new JsonParsingException("More than one JSON value at the root of the document, or extra characters at the end of the JSON!");
        }
    }

    // The root value has been read to its end exactly when the depth drops to 0.
    private void assertNoMoreJsonValuesAfterRoot() {
        if (depth == 0) {
            assertNoMoreJsonValues();
        }
    }

    /**
     * The outcome of entering an object or an array.
     */
    public enum IteratorResult {
        /**
         * The container has no fields or elements; it has been consumed.
         */
        EMPTY,
        /**
         * The value is {@code null}; it has been consumed.
         */
        NULL,
        /**
         * The container has at least one field or element, and the cursor is at the first one.
         */
        NOT_EMPTY
    }

    /**
     * The JSON type of a value, as returned by {@link #peekType()}.
     */
    public enum ValueType {
        /**
         * An object: the value starts with <code>&#123;</code>.
         */
        OBJECT,
        /**
         * An array: the value starts with {@code [}.
         */
        ARRAY,
        /**
         * A string: the value starts with {@code "}.
         */
        STRING,
        /**
         * A number: the value starts with {@code -} or a digit.
         */
        NUMBER,
        /**
         * {@code true} or {@code false}.
         */
        BOOLEAN,
        /**
         * {@code null}.
         */
        NULL
    }
}
