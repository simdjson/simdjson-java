package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorShuffle;

class CharactersClassifier {

    private static final byte LOW_NIBBLE_MASK = 0x0f;
    private static final ByteVector WHITESPACE_TABLE = ByteVector.fromArray(
            ByteVector.SPECIES_256,
            new byte[]{
                    ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
                    ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100
            },
            0
    );
    private static final ByteVector OP_TABLE = ByteVector.fromArray(
            ByteVector.SPECIES_256,
            new byte[]{
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0
            },
            0
    );

    JsonCharacterBlock classify(ByteVector chunk0, ByteVector chunk1) {
        VectorShuffle<Byte> chunk0Low = extractLowNibble(chunk0).toShuffle();
        VectorShuffle<Byte> chunk1Low = extractLowNibble(chunk1).toShuffle();

        long whitespace = eq(
                chunk0,
                WHITESPACE_TABLE.rearrange(chunk0Low),
                chunk1,
                WHITESPACE_TABLE.rearrange(chunk1Low)
        );

        ByteVector curlified0 = curlify(chunk0);
        ByteVector curlified1 = curlify(chunk1);
        long op = eq(
                curlified0,
                OP_TABLE.rearrange(chunk0Low),
                curlified1,
                OP_TABLE.rearrange(chunk1Low)
        );

        return new JsonCharacterBlock(whitespace, op);
    }

    private ByteVector extractLowNibble(ByteVector vector) {
        return vector.and(LOW_NIBBLE_MASK);
    }

    private ByteVector curlify(ByteVector vector) {
        // turns [ into { and ] into }
        return vector.or((byte) 0x20);
    }

    private long eq(ByteVector chunk0, ByteVector mask0, ByteVector chunk1, ByteVector mask1) {
        long rLo = chunk0.eq(mask0).toLong();
        long rHi = chunk1.eq(mask1).toLong();
        return rLo | (rHi << 32);
    }
}
