package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithPrimitiveByteField {

    private final byte field;

    public ClassWithPrimitiveByteField(@JsonFieldName("field") byte field) {
        this.field = field;
    }

    public byte getField() {
        return field;
    }
}
