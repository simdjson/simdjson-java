package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorShuffle;

class CharactersClassifier {

    private static final byte LOW_NIBBLE_MASK = 0x0f;
    private static final ByteVector WHITESPACE_TABLE = ByteVector.fromArray(
            ByteVector.SPECIES_512,
            new byte[]{
                    ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
                    ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
                    ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
                    ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100
            },
            0
    );
    private static final ByteVector OP_TABLE = ByteVector.fromArray(
            ByteVector.SPECIES_512,
            new byte[]{
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0
            },
            0
    );

    JsonCharacterBlock classify(ByteVector chunk) {
        VectorShuffle<Byte> chunkLow = extractLowNibble(chunk).toShuffle();

        long whitespace = eq(chunk, WHITESPACE_TABLE.rearrange(chunkLow));

        ByteVector curlified = curlify(chunk);
        long op = eq(curlified, OP_TABLE.rearrange(chunkLow));

        return new JsonCharacterBlock(whitespace, op);
    }

    private ByteVector extractLowNibble(ByteVector vector) {
        return vector.and(LOW_NIBBLE_MASK);
    }

    private ByteVector curlify(ByteVector vector) {
        // turns [ into { and ] into }
        return vector.or((byte) 0x20);
    }

    private long eq(ByteVector chunk, ByteVector mask) {
        return chunk.eq(mask).toLong();
    }
}
