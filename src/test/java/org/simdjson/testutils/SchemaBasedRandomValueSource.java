package org.simdjson.testutils;

import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(SchemaBasedRandomValueProvider.class)
public @interface SchemaBasedRandomValueSource {

    Class<?>[] schemas();

    /**
     * If set to true at least one null will appear in every generated array.
     */
    boolean nulls();
}
