package org.simdjson;

import jdk.incubator.vector.ByteVector;

import static org.simdjson.CharacterUtils.escape;
import static org.simdjson.CharacterUtils.hexToInt;

class StringParser {

    private static final byte BACKSLASH = '\\';
    private static final byte QUOTE = '"';
    private static final int BYTES_PROCESSED = StructuralIndexer.BYTE_SPECIES.vectorByteSize();
    private static final int MIN_HIGH_SURROGATE = 0xD800;
    private static final int MAX_HIGH_SURROGATE = 0xDBFF;
    private static final int MIN_LOW_SURROGATE = 0xDC00;
    private static final int MAX_LOW_SURROGATE = 0xDFFF;

    int parseString(byte[] buffer, int idx, byte[] stringBuffer, int stringBufferIdx) {
        int dst = doParseString(buffer, idx, stringBuffer, stringBufferIdx + Integer.BYTES);
        int len = dst - stringBufferIdx - Integer.BYTES;
        IntegerUtils.toBytes(len, stringBuffer, stringBufferIdx);
        return dst;
    }

    int parseString(byte[] buffer, int idx, byte[] stringBuffer) {
        return doParseString(buffer, idx, stringBuffer, 0);
    }

    private int doParseString(byte[] buffer, int idx, byte[] stringBuffer, int offset) {
        int src = idx + 1;
        int dst = offset;
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
                    dst += storeCodePointInStringBuffer(codePoint, dst, stringBuffer);
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
        return dst;
    }

    char parseChar(byte[] buffer, int startIdx) {
        int idx = startIdx + 1;
        char character;
        if (buffer[idx] == '\\') {
            byte escapeChar = buffer[idx + 1];
            if (escapeChar == 'u') {
                int codePoint = hexToInt(buffer, idx + 2);
                if (codePoint >= MIN_HIGH_SURROGATE && codePoint <= MAX_LOW_SURROGATE) {
                    throw new JsonParsingException("Invalid code point. Should be within the range U+0000–U+D777 or U+E000–U+FFFF.");
                }
                if (codePoint < 0) {
                    throw new JsonParsingException("Invalid unicode escape sequence.");
                }
                character = (char) codePoint;
                idx += 6;
            } else {
                character = (char) escape(escapeChar);
                idx += 2;
            }
        } else if (buffer[idx] >= 0) {
            // We have an ASCII character
            character = (char) buffer[idx];
            idx++;
        } else if ((buffer[idx] & 0b11100000) == 0b11000000) {
            // We have a two-byte UTF-8 character
            int codePoint = (buffer[idx] & 0b00011111) << 6 | (buffer[idx + 1] & 0b00111111);
            character = (char) codePoint;
            idx += 2;
        } else if ((buffer[idx] & 0b11110000) == 0b11100000) {
            // We have a three-byte UTF-8 character
            int codePoint = (buffer[idx] & 0b00001111) << 12 | (buffer[idx + 1] & 0b00111111) << 6 | (buffer[idx + 2] & 0b00111111);
            character = (char) codePoint;
            idx += 3;
        } else {
            throw new JsonParsingException("String cannot be deserialized to a char. Expected a single 16-bit code unit character.");
        }
        if (buffer[idx] != '"') {
            throw new JsonParsingException("String cannot be deserialized to a char. Expected a single-character string.");
        }
        return character;
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

    private int storeCodePointInStringBuffer(int codePoint, int dst, byte[] stringBuffer) {
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
}
