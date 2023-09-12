package org.simdjson;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

class JsonValueAssert extends AbstractAssert<JsonValueAssert, JsonValue> {

    JsonValueAssert(JsonValue actual) {
        super(actual, JsonValueAssert.class);
    }

    static JsonValueAssert assertThat(JsonValue actual) {
        return new JsonValueAssert(actual);
    }

    JsonValueAssert isEqualTo(long expected) {
        Assertions.assertThat(actual.isLong())
                .withFailMessage("Expecting value to be long but was " + getActualType())
                .isTrue();
        Assertions.assertThat(actual.asLong()).isEqualTo(expected);
        return this;
    }

    JsonValueAssert isEqualTo(Double expected) {
        Assertions.assertThat(actual.isDouble())
                .withFailMessage("Expecting value to be double but was " + getActualType())
                .isTrue();
        Assertions.assertThat(actual.asDouble()).isEqualTo(expected);
        return this;
    }

    JsonValueAssert isEqualTo(String expected) {
        Assertions.assertThat(actual.isString())
                .withFailMessage("Expecting value to be string but was " + getActualType())
                .isTrue();
        Assertions.assertThat(actual.asString()).isEqualTo(expected);
        return this;
    }

    JsonValueAssert isEqualTo(boolean expected) {
        Assertions.assertThat(actual.isBoolean())
                .withFailMessage("Expecting value to be boolean but was " + getActualType())
                .isTrue();
        Assertions.assertThat(actual.asBoolean()).isEqualTo(expected);
        return this;
    }

    private String getActualType() {
        if (actual.isArray()) {
            return "array";
        }
        if (actual.isString()) {
            return "string";
        }
        if (actual.isLong()) {
            return "long";
        }
        if (actual.isBoolean()) {
            return "boolean";
        }
        if (actual.isDouble()) {
            return "double";
        }
        if (actual.isNull()) {
            return "null";
        }
        if (actual.isObject()) {
            return "object";
        }
        return "unknown type";
    }
}
