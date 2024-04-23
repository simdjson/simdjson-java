package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithIntegerField {

    private final Integer field;

    public ClassWithIntegerField(@JsonFieldName("field") Integer field) {
        this.field = field;
    }

    public Integer getField() {
        return field;
    }
}
