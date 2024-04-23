package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithStringField {

    private final String field;

    public ClassWithStringField(@JsonFieldName("field") String field) {
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
