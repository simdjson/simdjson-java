package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithPrimitiveIntegerField {

    private final int field;

    public ClassWithPrimitiveIntegerField(@JsonFieldName("field") int field) {
        this.field = field;
    }

    public int getField() {
        return field;
    }
}
