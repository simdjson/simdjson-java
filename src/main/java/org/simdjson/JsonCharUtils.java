package org.simdjson;

class JsonCharUtils {

    private static final boolean[] STRUCTURAL_OR_WHITESPACE = new boolean[]{
            false, false, false, false, false, false, false, false,
            false, true, true, false, false, true, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            true, false, false, false, false, false, false, false,
            false, false, false, false, true, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, true, false, false, false, false, false,

            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, true, false, true, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, true, false, true, false, false,

            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,

            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false
    };

    static boolean isStructuralOrWhitespace(byte b) {
        if (b < 0) {
            return false;
        }
        return STRUCTURAL_OR_WHITESPACE[b];
    }
}
