package org.simdjson;

record JsonStringBlock(long quote, long inString) {

    long stringTail() {
        return inString ^ quote;
    }

    long nonQuoteInsideString(long mask) {
        return mask & inString;
    }
}
