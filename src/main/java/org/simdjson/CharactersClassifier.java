package org.simdjson;

import jdk.incubator.vector.ByteVector;

class CharactersClassifier {
    private static final byte LOW_NIBBLE_MASK = 0x0f;

    private static final ByteVector WHITESPACE_TABLE_128 =
        ByteVector.fromArray(
            ByteVector.SPECIES_128,
            new byte[]{
                ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
            },
            0);
    private static final ByteVector WHITESPACE_TABLE_256 =
        ByteVector.fromArray(
            ByteVector.SPECIES_256,
            new byte[]{
                ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
                ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
            },
            0);
    private static final ByteVector WHITESPACE_TABLE_512 =
        ByteVector.fromArray(
            ByteVector.SPECIES_512,
            new byte[]{
                ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
                ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
                ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
                ' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100,
            },
            0);

    private static final ByteVector OP_TABLE_128 =
        ByteVector.fromArray(
            ByteVector.SPECIES_128,
            new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
            },
            0);
    private static final ByteVector OP_TABLE_256 =
        ByteVector.fromArray(
            ByteVector.SPECIES_256,
            new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
            },
            0);
    private static final ByteVector OP_TABLE_512 =
        ByteVector.fromArray(
            ByteVector.SPECIES_512,
            new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0,
            },
            0);

    static JsonCharacterBlock classify(ByteVector chunk0) {
        var chunk0Low = extractLowNibble(chunk0).toShuffle();
        var whitespace = eq(chunk0, WHITESPACE_TABLE_512.rearrange(chunk0Low));
        var curlified0 = curlify(chunk0);
        var op = eq(curlified0, OP_TABLE_512.rearrange(chunk0Low));
        return new JsonCharacterBlock(whitespace, op);
    }

    static JsonCharacterBlock classify(ByteVector chunk0, ByteVector chunk1) {
        var chunk0Low = extractLowNibble(chunk0).toShuffle();
        var chunk1Low = extractLowNibble(chunk1).toShuffle();
        var whitespace = eq(chunk0, WHITESPACE_TABLE_256.rearrange(chunk0Low), chunk1, WHITESPACE_TABLE_256.rearrange(chunk1Low));
        var curlified0 = curlify(chunk0);
        var curlified1 = curlify(chunk1);
        var op = eq(curlified0, OP_TABLE_256.rearrange(chunk0Low), curlified1, OP_TABLE_256.rearrange(chunk1Low));
        return new JsonCharacterBlock(whitespace, op);
    }

    static JsonCharacterBlock classify(ByteVector chunk0, ByteVector chunk1, ByteVector chunk2, ByteVector chunk3) {
        var chunk0Low = extractLowNibble(chunk0).toShuffle();
        var chunk1Low = extractLowNibble(chunk1).toShuffle();
        var chunk2Low = extractLowNibble(chunk2).toShuffle();
        var chunk3Low = extractLowNibble(chunk3).toShuffle();
        var whitespace = eq(
            chunk0, WHITESPACE_TABLE_128.rearrange(chunk0Low),
            chunk1, WHITESPACE_TABLE_128.rearrange(chunk1Low),
            chunk2, WHITESPACE_TABLE_128.rearrange(chunk2Low),
            chunk3, WHITESPACE_TABLE_128.rearrange(chunk3Low)
        );
        var curlified0 = curlify(chunk0);
        var curlified1 = curlify(chunk1);
        var curlified2 = curlify(chunk2);
        var curlified3 = curlify(chunk3);
        var op = eq(
            curlified0, OP_TABLE_128.rearrange(chunk0Low),
            curlified1, OP_TABLE_128.rearrange(chunk1Low),
            curlified2, OP_TABLE_128.rearrange(chunk2Low),
            curlified3, OP_TABLE_128.rearrange(chunk3Low)
        );
        return new JsonCharacterBlock(whitespace, op);
    }

    private static ByteVector extractLowNibble(ByteVector vector) {
        return vector.and(LOW_NIBBLE_MASK);
    }

    private static ByteVector curlify(ByteVector vector) {
        // turns [ into { and ] into }
        return vector.or((byte) 0x20);
    }

    private static long eq(ByteVector chunk0, ByteVector mask0) {
        return chunk0.eq(mask0).toLong();
    }

    private static long eq(ByteVector chunk0, ByteVector mask0, ByteVector chunk1, ByteVector mask1) {
        long r0 = chunk0.eq(mask0).toLong();
        long r1 = chunk1.eq(mask1).toLong();
        return r0 | (r1 << 32);
    }

    private static long eq(ByteVector chunk0, ByteVector mask0, ByteVector chunk1, ByteVector mask1, ByteVector chunk2, ByteVector mask2, ByteVector chunk3, ByteVector mask3) {
        long r0 = chunk0.eq(mask0).toLong();
        long r1 = chunk1.eq(mask1).toLong();
        long r2 = chunk2.eq(mask2).toLong();
        long r3 = chunk3.eq(mask3).toLong();
        return (r0 & 0xFFFFL) | ((r1 & 0xFFFFL) << 16) | ((r2 & 0xFFFFL) << 32) | ((r3 & 0xFFFFL) << 48);
    }
}

