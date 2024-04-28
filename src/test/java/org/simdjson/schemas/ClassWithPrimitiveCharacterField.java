package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithPrimitiveCharacterField {

    private final char field;

    public ClassWithPrimitiveCharacterField(@JsonFieldName("field") char field) {
        this.field = field;
    }

    public char getField() {
        return field;
    }
}
