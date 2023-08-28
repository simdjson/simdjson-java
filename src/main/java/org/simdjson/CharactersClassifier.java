package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorShuffle;

class CharactersClassifier {

    private static final byte LOW_NIBBLE_MASK = 0x0f;

    private static final ByteVector WHITESPACE_TABLE = 
        ByteVector.fromArray(
            ByteVector.SPECIES_PREFERRED, 
            repeat(new byte[]{' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100}, ByteVector.SPECIES_PREFERRED.vectorByteSize() / 4), 
            0);

    private static final ByteVector OP_TABLE = 
        ByteVector.fromArray(
            ByteVector.SPECIES_PREFERRED, 
            repeat(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0}, ByteVector.SPECIES_PREFERRED.vectorByteSize() / 4), 
            0);

    private static byte[] repeat(byte[] array, int n) {
        byte[] result = new byte[n * array.length];
        for (int dst = 0; dst < result.length; dst += array.length) {
            System.arraycopy(array, 0, result, dst, array.length);
        }
        return result;
    }

    private static final int bytesPerChunk = ByteVector.SPECIES_PREFERRED.vectorByteSize();
    private static final int nChunks = 64 / bytesPerChunk;
    private static final VectorShuffle[] shuffles = new VectorShuffle[nChunks];
    private static final ByteVector[] whites = new ByteVector[nChunks];
    private static final ByteVector[] curls = new ByteVector[nChunks];
    private static final ByteVector[] ops = new ByteVector[nChunks];


    JsonCharacterBlock classify(ByteVector[] chunks) {
        // VectorShuffle<Byte>[] shuffles = new VectorShuffle[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            shuffles[i] = extractLowNibble(chunks[i]).toShuffle();
        }

        // ByteVector[] whites = new ByteVector[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            whites[i] = WHITESPACE_TABLE.rearrange(shuffles[i]);
        }

        long whitespace = eq(chunks, whites);

        // ByteVector[] curls = new ByteVector[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            curls[i] = curlify(chunks[i]);
        }
        // ByteVector[] ops = new ByteVector[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            ops[i] = OP_TABLE.rearrange(shuffles[i]);
        }

        long op = eq(curls, ops);
        
        return new JsonCharacterBlock(whitespace, op);
    }

    private ByteVector extractLowNibble(ByteVector vector) {
        return vector.and(LOW_NIBBLE_MASK);
    }

    private ByteVector curlify(ByteVector vector) {
        // turns [ into { and ] into }
        return vector.or((byte) 0x20);
    }

    private long eq(ByteVector[] chunks, ByteVector[] masks) {
        long r = 0;
        for (int i = 0; i < chunks.length; i++) {
            r |= chunks[i].eq(masks[i]).toLong() << (i * ByteVector.SPECIES_PREFERRED.vectorByteSize());
        }  
        return r;  
    }    

}
