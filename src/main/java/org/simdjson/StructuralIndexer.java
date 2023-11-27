package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;

import static jdk.incubator.vector.VectorOperators.UNSIGNED_LE;

class StructuralIndexer {

    static final VectorSpecies<Integer> INT_SPECIES;
    static final VectorSpecies<Byte> BYTE_SPECIES;
    static final int N_CHUNKS;

    static {
        String species = System.getProperty("org.simdjson.species", "preferred");
        switch (species) {
            case "preferred" -> {
                BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
                INT_SPECIES = IntVector.SPECIES_PREFERRED;
            }
            case "512" -> {
                BYTE_SPECIES = ByteVector.SPECIES_512;
                INT_SPECIES = IntVector.SPECIES_512;
            }
            case "256" -> {
                BYTE_SPECIES = ByteVector.SPECIES_256;
                INT_SPECIES = IntVector.SPECIES_256;
            }
            default -> throw new IllegalArgumentException("Unsupported vector species: " + species);
        }
        N_CHUNKS = 64 / BYTE_SPECIES.vectorByteSize();
        assertSupportForSpecies(BYTE_SPECIES);
        assertSupportForSpecies(INT_SPECIES);
    }

    private static void assertSupportForSpecies(VectorSpecies<?> species) {
        if (species.vectorShape() != VectorShape.S_256_BIT && species.vectorShape() != VectorShape.S_512_BIT) {
            throw new IllegalArgumentException("Unsupported vector species: " + species);
        }
    }

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
        switch (N_CHUNKS) {
            case 1: step1(buffer, offset, blockIndex); break;
            case 2: step2(buffer, offset, blockIndex); break;
            default: throw new RuntimeException("Unsupported vector width: " + N_CHUNKS * 64);
        }
    }

    private void step1(byte[] buffer, int offset, int blockIndex) {
        ByteVector chunk0 = ByteVector.fromArray(ByteVector.SPECIES_512, buffer, offset);
        JsonStringBlock strings = stringScanner.next(chunk0);
        JsonCharacterBlock characters = classifier.classify(chunk0);
        long unescaped = lteq(chunk0, (byte) 0x1F);
        finishStep(characters, strings, unescaped, blockIndex);
    }

    private void step2(byte[] buffer, int offset, int blockIndex) {
        ByteVector chunk0 = ByteVector.fromArray(ByteVector.SPECIES_256, buffer, offset);
        ByteVector chunk1 = ByteVector.fromArray(ByteVector.SPECIES_256, buffer, offset + 32);
        JsonStringBlock strings = stringScanner.next(chunk0, chunk1);
        JsonCharacterBlock characters = classifier.classify(chunk0, chunk1);
        long unescaped = lteq(chunk0, chunk1, (byte) 0x1F);
        finishStep(characters, strings, unescaped, blockIndex);
    }

    private void finishStep(JsonCharacterBlock characters, JsonStringBlock strings, long unescaped, int blockIndex) {
        long scalar = characters.scalar();
        long nonQuoteScalar = scalar & ~strings.quote();
        long followsNonQuoteScalar = nonQuoteScalar << 1 | prevScalar;
        prevScalar = nonQuoteScalar >>> 63;
        // TODO: utf-8 validation
        long potentialScalarStart = scalar & ~followsNonQuoteScalar;
        long potentialStructuralStart = characters.op() | potentialScalarStart;
        bitIndexes.write(blockIndex, prevStructurals);
        prevStructurals = potentialStructuralStart & ~strings.stringTail();
        unescapedCharsError |= strings.nonQuoteInsideString(unescaped);
    }

    private long lteq(ByteVector chunk0, byte scalar) {
        long r = chunk0.compare(UNSIGNED_LE, scalar).toLong();
        return r;
    }

    private long lteq(ByteVector chunk0, ByteVector chunk1, byte scalar) {
        long r0 = chunk0.compare(UNSIGNED_LE, scalar).toLong();
        long r1 = chunk1.compare(UNSIGNED_LE, scalar).toLong();
        return r0 | (r1 << 32);
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
