package org.simdjson.schemas;

import org.simdjson.annotations.JsonFieldName;

public class ClassWithPrimitiveShortField {

    private final short field;

    public ClassWithPrimitiveShortField(@JsonFieldName("field") short field) {
        this.field = field;
    }

    public short getField() {
        return field;
    }
}
