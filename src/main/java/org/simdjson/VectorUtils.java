package org.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;

class VectorUtils {

    static final VectorSpecies<Integer> INT_SPECIES;
    static final VectorSpecies<Byte> BYTE_SPECIES;

    static {
        String species = System.getProperty("org.simdjson.species", "preferred");
        switch (species) {
            case "preferred" -> {
                BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
                INT_SPECIES = IntVector.SPECIES_PREFERRED;
                assertSupportForSpecies(BYTE_SPECIES);
                assertSupportForSpecies(INT_SPECIES);
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
    }

    private static void assertSupportForSpecies(VectorSpecies<?> species) {
        if (species.vectorShape() != VectorShape.S_256_BIT && species.vectorShape() != VectorShape.S_512_BIT) {
            throw new IllegalArgumentException("Unsupported vector species: " + species);
        }
    }

    static ByteVector repeat(byte[] array) {
        int n = BYTE_SPECIES.vectorByteSize() / 4;
        byte[] result = new byte[n * array.length];
        for (int dst = 0; dst < result.length; dst += array.length) {
            System.arraycopy(array, 0, result, dst, array.length);
        }
        return ByteVector.fromArray(BYTE_SPECIES, result, 0);
    }
}
