package org.simdjson;

import jdk.incubator.vector.ByteVector;

import static jdk.incubator.vector.ByteVector.SPECIES_512; 
import static jdk.incubator.vector.VectorOperators.UNSIGNED_LE;

class StructuralIndexer {

    private final JsonStringScanner stringScanner;
    private final CharactersClassifier classifier;
    private final BitIndexes bitIndexes;
    private final int bytesPerChunk;
    private final int nChunks;

    private long prevStructurals = 0;
    private long unescapedCharsError = 0;
    private long prevScalar = 0;

    StructuralIndexer(BitIndexes bitIndexes) {
        this.stringScanner = new JsonStringScanner();
        this.classifier = new CharactersClassifier();
        this.bitIndexes = bitIndexes;
        this.bytesPerChunk = ByteVector.SPECIES_PREFERRED.vectorByteSize();
        this.nChunks = 64 / bytesPerChunk;
    }

    void step(byte[] buffer, int offset, int blockIndex) {
        ByteVector[] chunks = new ByteVector[nChunks];
        for (int i = 0; i < nChunks; i++) {
            chunks[i] = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, buffer, offset + i * bytesPerChunk);
        }

        JsonStringBlock strings = stringScanner.next(chunks);
        JsonCharacterBlock characters = classifier.classify(chunks);

        long scalar = characters.scalar();
        long nonQuoteScalar = scalar & ~strings.quote();
        long followsNonQuoteScalar = nonQuoteScalar << 1 | prevScalar;
        prevScalar = nonQuoteScalar >>> 63;
        long unescaped = lteq(chunks, (byte) 0x1F);
        // TODO: utf-8 validation
        long potentialScalarStart = scalar & ~followsNonQuoteScalar;
        long potentialStructuralStart = characters.op() | potentialScalarStart;
        bitIndexes.write(blockIndex, prevStructurals);
        prevStructurals = potentialStructuralStart & ~strings.stringTail();
        unescapedCharsError |= strings.nonQuoteInsideString(unescaped);
    }

    private long lteq(ByteVector[] chunks, byte scalar) {
        long r = 0;
        for (int i = 0; i < nChunks; i++) {
            r |= chunks[i].compare(UNSIGNED_LE, scalar).toLong() << (i * bytesPerChunk);
        }  
        return r;      
    }

    void finish(int blockIndex) {
        bitIndexes.write(blockIndex, prevStructurals);

        stringScanner.finish();
        if (unescapedCharsError != 0) {
            throw new JsonParsingException("Unescaped characters. Within strings, there are characters that should be escaped.");
        }
    }

    void reset() {
        stringScanner.reset();
        prevStructurals = 0;
        unescapedCharsError = 0;
        prevScalar = 0;
    }
}
