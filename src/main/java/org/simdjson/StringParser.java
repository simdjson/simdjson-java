package org.simdjson;

import jdk.incubator.vector.ByteVector;

import static org.simdjson.CharacterUtils.escape;
import static org.simdjson.CharacterUtils.hexToInt;
import static org.simdjson.Tape.STRING;

class StringParser {

    private static final byte BACKSLASH = '\\';
    private static final byte QUOTE = '"';
    private static final int BYTES_PROCESSED = StructuralIndexer.BYTE_SPECIES.vectorByteSize();
    private static final int MIN_HIGH_SURROGATE = 0xD800;
    private static final int MAX_HIGH_SURROGATE = 0xDBFF;
    private static final int MIN_LOW_SURROGATE = 0xDC00;
    private static final int MAX_LOW_SURROGATE = 0xDFFF;

    private final Tape tape;
    private final byte[] stringBuffer;

    private int stringBufferIdx;

    StringParser(Tape tape, byte[] stringBuffer) {
        this.tape = tape;
        this.stringBuffer = stringBuffer;
    }

    void parseString(byte[] buffer, int idx) {
        tape.append(stringBufferIdx, STRING);
        int src = idx + 1;
        int dst = stringBufferIdx + Integer.BYTES;
        while (true) {
            ByteVector srcVec = ByteVector.fromArray(StructuralIndexer.BYTE_SPECIES, buffer, src);
            srcVec.intoArray(stringBuffer, dst);
            long backslashBits = srcVec.eq(BACKSLASH).toLong();
            long quoteBits = srcVec.eq(QUOTE).toLong();

            if (hasQuoteFirst(backslashBits, quoteBits)) {
                dst += Long.numberOfTrailingZeros(quoteBits);
                break;
            }
            if (hasBackslash(backslashBits, quoteBits)) {
                int backslashDist = Long.numberOfTrailingZeros(backslashBits);
                byte escapeChar = buffer[src + backslashDist + 1];
                if (escapeChar == 'u') {
                    src += backslashDist;
                    dst += backslashDist;
                    int codePoint = hexToInt(buffer, src + 2);
                    src += 6;
                    if (codePoint >= MIN_HIGH_SURROGATE && codePoint <= MAX_HIGH_SURROGATE) {
                        codePoint = parseLowSurrogate(buffer, src, codePoint);
                        src += 6;
                    } else if (codePoint >= MIN_LOW_SURROGATE && codePoint <= MAX_LOW_SURROGATE) {
                        throw new JsonParsingException("Invalid code point. The range U+DC00–U+DFFF is reserved for low surrogate.");
                    }
                    dst += storeCodePointInStringBuffer(codePoint, dst);
                } else {
                    stringBuffer[dst + backslashDist] = escape(escapeChar);
                    src += backslashDist + 2;
                    dst += backslashDist + 1;
                }
            } else {
                src += BYTES_PROCESSED;
                dst += BYTES_PROCESSED;
            }
        }
        int len = dst - stringBufferIdx - Integer.BYTES;
        IntegerUtils.toBytes(len, stringBuffer, stringBufferIdx);
        stringBufferIdx = dst;
    }

    private int parseLowSurrogate(byte[] buffer, int src, int codePoint) {
        if ((buffer[src] << 8 | buffer[src + 1]) != ('\\' << 8 | 'u')) {
            throw new JsonParsingException("Low surrogate should start with '\\u'");
        } else {
            int codePoint2 = hexToInt(buffer, src + 2);
            int lowBit = codePoint2 - MIN_LOW_SURROGATE;
            if (lowBit >> 10 == 0) {
                return (((codePoint - MIN_HIGH_SURROGATE) << 10) | lowBit) + 0x10000;
            } else {
                throw new JsonParsingException("Invalid code point. Low surrogate should be in the range U+DC00–U+DFFF.");
            }
        }
    }

    private int storeCodePointInStringBuffer(int codePoint, int dst) {
        if (codePoint < 0) {
            throw new JsonParsingException("Invalid unicode escape sequence.");
        }
        if (codePoint <= 0x7F) {
            stringBuffer[dst] = (byte) codePoint;
            return 1;
        }
        if (codePoint <= 0x7FF) {
            stringBuffer[dst] = (byte) ((codePoint >> 6) + 192);
            stringBuffer[dst + 1] = (byte) ((codePoint & 63) + 128);
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            stringBuffer[dst] = (byte) ((codePoint >> 12) + 224);
            stringBuffer[dst + 1] = (byte) (((codePoint >> 6) & 63) + 128);
            stringBuffer[dst + 2] = (byte) ((codePoint & 63) + 128);
            return 3;
        }
        if (codePoint <= 0x10FFFF) {
            stringBuffer[dst] = (byte) ((codePoint >> 18) + 240);
            stringBuffer[dst + 1] = (byte) (((codePoint >> 12) & 63) + 128);
            stringBuffer[dst + 2] = (byte) (((codePoint >> 6) & 63) + 128);
            stringBuffer[dst + 3] = (byte) ((codePoint & 63) + 128);
            return 4;
        }
        throw new IllegalStateException("Code point is greater than 0x110000.");
    }

    private boolean hasQuoteFirst(long backslashBits, long quoteBits) {
        return ((backslashBits - 1) & quoteBits) != 0;
    }

    private boolean hasBackslash(long backslashBits, long quoteBits) {
        return ((quoteBits - 1) & backslashBits) != 0;
    }

    void reset() {
        stringBufferIdx = 0;
    }
}
