package org.simdjson.testutils;

public @interface MapEntry {

    String[] stringKey() default {};

    Class<?>[] classKey() default {};

    String value();
}
