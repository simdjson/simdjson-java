package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorShuffle;

class CharactersClassifier {

    private static final byte LOW_NIBBLE_MASK = 0x0f;

    private static final ByteVector WHITESPACE_TABLE = 
        ByteVector.fromArray(
            StructuralIndexer.BYTE_SPECIES,
            repeat(new byte[]{' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100}, StructuralIndexer.BYTE_SPECIES.vectorByteSize() / 4),
            0);

    private static final ByteVector OP_TABLE = 
        ByteVector.fromArray(
            StructuralIndexer.BYTE_SPECIES,
            repeat(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0}, StructuralIndexer.BYTE_SPECIES.vectorByteSize() / 4),
            0);

    private static byte[] repeat(byte[] array, int n) {
        byte[] result = new byte[n * array.length];
        for (int dst = 0; dst < result.length; dst += array.length) {
            System.arraycopy(array, 0, result, dst, array.length);
        }
        return result;
    }

    JsonCharacterBlock classify(ByteVector chunk0) {
        VectorShuffle<Byte> chunk0Low = extractLowNibble(chunk0).toShuffle();
        long whitespace = eq(chunk0, WHITESPACE_TABLE.rearrange(chunk0Low));
        ByteVector curlified0 = curlify(chunk0);
        long op = eq(curlified0, OP_TABLE.rearrange(chunk0Low));
        return new JsonCharacterBlock(whitespace, op);
    }

    JsonCharacterBlock classify(ByteVector chunk0, ByteVector chunk1) {
        VectorShuffle<Byte> chunk0Low = extractLowNibble(chunk0).toShuffle();
        VectorShuffle<Byte> chunk1Low = extractLowNibble(chunk1).toShuffle();
        long whitespace = eq(chunk0, WHITESPACE_TABLE.rearrange(chunk0Low), chunk1, WHITESPACE_TABLE.rearrange(chunk1Low));
        ByteVector curlified0 = curlify(chunk0);
        ByteVector curlified1 = curlify(chunk1);
        long op = eq(curlified0, OP_TABLE.rearrange(chunk0Low), curlified1, OP_TABLE.rearrange(chunk1Low));
        return new JsonCharacterBlock(whitespace, op);
    }

    private ByteVector extractLowNibble(ByteVector vector) {
        return vector.and(LOW_NIBBLE_MASK);
    }

    private ByteVector curlify(ByteVector vector) {
        // turns [ into { and ] into }
        return vector.or((byte) 0x20);
    }

    private long eq(ByteVector chunk0, ByteVector mask0) {
        return chunk0.eq(mask0).toLong();
    }       

    private long eq(ByteVector chunk0, ByteVector mask0, ByteVector chunk1, ByteVector mask1) {
        long r0 = chunk0.eq(mask0).toLong();
        long r1 = chunk1.eq(mask1).toLong();
        return r0 | (r1 << 32);
    }       
}
