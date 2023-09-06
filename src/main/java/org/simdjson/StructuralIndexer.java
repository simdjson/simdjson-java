package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static jdk.incubator.vector.VectorOperators.UNSIGNED_LE;

class StructuralIndexer {

    static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    // static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_256;
    static final int N_CHUNKS = 64 / SPECIES.vectorByteSize();
    private static final MethodHandle STEP_MH;

    static {
        try {
            String stepMethodName = "step" + StructuralIndexer.N_CHUNKS;
            STEP_MH = MethodHandles.lookup().findVirtual(StructuralIndexer.class, stepMethodName, MethodType.methodType(void.class, byte[].class, int.class, int.class));
        } 
        catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
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

    // public void step(byte[] buffer, int offset, int blockIndex) {
    //     try {
    //         STEP_MH.invokeExact(this, buffer, offset, blockIndex);
    //     }
    //     catch (Throwable t) 
    //     {
    //         throw new RuntimeException(t);
    //     }
    // }

    public void step(byte[] buffer, int offset, int blockIndex) {
        // step4(buffer, offset, blockIndex);
        switch (N_CHUNKS) {
            case 1: step1(buffer, offset, blockIndex); break;
            case 2: step2(buffer, offset, blockIndex); break;
            case 4: step4(buffer, offset, blockIndex); break;
            default: throw new RuntimeException("Unsupported vector width: " + N_CHUNKS * 64);
        }
    }

    void step1(byte[] buffer, int offset, int blockIndex) {
        ByteVector chunk0 = ByteVector.fromArray(ByteVector.SPECIES_512, buffer, offset);       
        JsonStringBlock strings = stringScanner.next(chunk0);
        JsonCharacterBlock characters = classifier.classify(chunk0);
        long unescaped = lteq(chunk0, (byte) 0x1F);
        finishStep(characters, strings, unescaped, blockIndex);
    }

    void step2(byte[] buffer, int offset, int blockIndex) {
        ByteVector chunk0 = ByteVector.fromArray(ByteVector.SPECIES_256, buffer, offset);
        ByteVector chunk1 = ByteVector.fromArray(ByteVector.SPECIES_256, buffer, offset + 32);
        JsonStringBlock strings = stringScanner.next(chunk0, chunk1);
        JsonCharacterBlock characters = classifier.classify(chunk0, chunk1);
        long unescaped = lteq(chunk0, chunk1, (byte) 0x1F);
        finishStep(characters, strings, unescaped, blockIndex);
    }

    void step4(byte[] buffer, int offset, int blockIndex) {
        ByteVector chunk0 = ByteVector.fromArray(ByteVector.SPECIES_128, buffer, offset);
        ByteVector chunk1 = ByteVector.fromArray(ByteVector.SPECIES_128, buffer, offset + 16);
        ByteVector chunk2 = ByteVector.fromArray(ByteVector.SPECIES_128, buffer, offset + 32);
        ByteVector chunk3 = ByteVector.fromArray(ByteVector.SPECIES_128, buffer, offset + 48);
        JsonStringBlock strings = stringScanner.next(chunk0, chunk1, chunk2, chunk3);
        JsonCharacterBlock characters = classifier.classify(chunk0, chunk1, chunk2, chunk3);
        long unescaped = lteq(chunk0, chunk1, chunk2, chunk3, (byte) 0x1F);
        finishStep(characters, strings, unescaped, blockIndex);
    }

    void finishStep(JsonCharacterBlock characters, JsonStringBlock strings, long unescaped, int blockIndex) {
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

    private long lteq(ByteVector chunk0, ByteVector chunk1, ByteVector chunk2, ByteVector chunk3, byte scalar) {
        long r0 = chunk0.compare(UNSIGNED_LE, scalar).toLong();
        long r1 = chunk1.compare(UNSIGNED_LE, scalar).toLong();
        long r2 = chunk2.compare(UNSIGNED_LE, scalar).toLong();
        long r3 = chunk3.compare(UNSIGNED_LE, scalar).toLong();
        return r0 | (r1 << 16) | (r2 << 32) | (r3 << 48);
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
