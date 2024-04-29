package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithPrimitiveLongField {

    private final long field;

    public ClassWithPrimitiveLongField(@JsonFieldName("field") long field) {
        this.field = field;
    }

    public long getField() {
        return field;
    }
}
