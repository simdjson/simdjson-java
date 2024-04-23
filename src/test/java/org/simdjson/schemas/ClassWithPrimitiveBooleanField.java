package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithPrimitiveBooleanField {

    private final boolean field;

    public ClassWithPrimitiveBooleanField(@JsonFieldName("field") boolean field) {
        this.field = field;
    }

    public boolean getField() {
        return field;
    }
}
