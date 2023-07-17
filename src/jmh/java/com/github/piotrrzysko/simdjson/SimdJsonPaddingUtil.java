package com.github.piotrrzysko.simdjson;

class SimdJsonPaddingUtil {

    static byte[] padded(byte[] src) {
        byte[] bufferPadded = new byte[src.length + 64];
        System.arraycopy(src, 0, bufferPadded, 0, src.length);
        return bufferPadded;
    }
}
