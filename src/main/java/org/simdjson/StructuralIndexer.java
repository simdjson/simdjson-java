package org.simdjson;

import jdk.incubator.vector.ByteVector;

import static jdk.incubator.vector.ByteVector.SPECIES_512; 
import static jdk.incubator.vector.VectorOperators.UNSIGNED_LE;

class StructuralIndexer {

    private final JsonStringScanner stringScanner;
    private final CharactersClassifier classifier;
    private final BitIndexes bitIndexes;

    private long prevStructurals = 0;
    private long unescapedCharsError = 0;
    private long prevScalar = 0;

    StructuralIndexer(BitIndexes bitIndexes) {
        this.stringScanner = new JsonStringScanner();
        this.classifier = new CharactersClassifier();
        this.bitIndexes = bitIndexes;
    }

    void step(byte[] buffer, int offset, int blockIndex) {
        ByteVector chunk = ByteVector.fromArray(SPECIES_512, buffer, offset);

        JsonStringBlock strings = stringScanner.next(chunk);
        JsonCharacterBlock characters = classifier.classify(chunk);

        long scalar = characters.scalar();
        long nonQuoteScalar = scalar & ~strings.quote();
        long followsNonQuoteScalar = nonQuoteScalar << 1 | prevScalar;
        prevScalar = nonQuoteScalar >>> 63;
        long unescaped = lteq(chunk, (byte) 0x1F);
        // TODO: utf-8 validation
        long potentialScalarStart = scalar & ~followsNonQuoteScalar;
        long potentialStructuralStart = characters.op() | potentialScalarStart;
        bitIndexes.write(blockIndex, prevStructurals);
        prevStructurals = potentialStructuralStart & ~strings.stringTail();
        unescapedCharsError |= strings.nonQuoteInsideString(unescaped);
    }

    private long lteq(ByteVector chunk, byte scalar) {
        return chunk.compare(UNSIGNED_LE, scalar).toLong();
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
