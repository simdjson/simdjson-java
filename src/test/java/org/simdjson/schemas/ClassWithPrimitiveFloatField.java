package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithPrimitiveFloatField {

    private final float field;

    public ClassWithPrimitiveFloatField(@JsonFieldName("field") float field) {
        this.field = field;
    }

    public float getField() {
        return field;
    }
}
