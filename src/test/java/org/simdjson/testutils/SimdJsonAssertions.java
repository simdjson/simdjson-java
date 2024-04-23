package org.simdjson.testutils;

import org.assertj.core.api.Assertions;
import org.simdjson.JsonValue;

public class SimdJsonAssertions extends Assertions {

    public static JsonValueAssert assertThat(JsonValue actual) {
        return new JsonValueAssert(actual);
    }
}
