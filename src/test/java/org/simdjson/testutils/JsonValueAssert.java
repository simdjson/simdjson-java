package org.simdjson.testutils;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.simdjson.JsonValue;

public class JsonValueAssert extends AbstractAssert<JsonValueAssert, JsonValue> {

    JsonValueAssert(JsonValue actual) {
        super(actual, JsonValueAssert.class);
    }

    public JsonValueAssert isEqualTo(long expected) {
        Assertions.assertThat(actual.isLong())
                .withFailMessage("Expecting value to be long but was " + getActualType())
                .isTrue();
        Assertions.assertThat(actual.asLong()).isEqualTo(expected);
        return this;
    }

    public JsonValueAssert isEqualTo(Double expected) {
        Assertions.assertThat(actual.isDouble())
                .withFailMessage("Expecting value to be double but was " + getActualType())
                .isTrue();
        Assertions.assertThat(actual.asDouble()).isEqualTo(expected);
        return this;
    }

    public JsonValueAssert isEqualTo(String expected) {
        Assertions.assertThat(actual.isString())
                .withFailMessage("Expecting value to be string but was " + getActualType())
                .isTrue();
        Assertions.assertThat(actual.asString()).isEqualTo(expected);
        return this;
    }

    public JsonValueAssert isEqualTo(boolean expected) {
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
