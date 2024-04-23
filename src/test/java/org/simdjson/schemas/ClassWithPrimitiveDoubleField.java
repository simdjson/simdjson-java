package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithPrimitiveDoubleField {

    private final double field;

    public ClassWithPrimitiveDoubleField(@JsonFieldName("field") double field) {
        this.field = field;
    }

    public double getField() {
        return field;
    }
}
